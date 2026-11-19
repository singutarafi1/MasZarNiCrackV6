#include <jni.h>
#include <android/log.h>
#include <libusb.h>
#include <sys/time.h>
#include <unistd.h>

#include <atomic>
#include <chrono>
#include <cstdio>
#include <cstdint>
#include <cstring>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>

#define LOG_TAG "JinUsbNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

// ============================================================================
// 1. Obfuscated MasterKey & Security State
// ============================================================================

// MasterKey XOR-masked with 0x5A (never stored in plain text)
constexpr uint8_t kMaskedMasterKey[12] = {
    0x0E, 0x32, 0x1A, 0x2E, 0x12, 0x36, 0x1A, 0x33, 0x34, 0x3D, 0x6A, 0x6A
};

// Runtime-decrypted BROM Handshake bytes (locked until valid license unlocks session)
// Masked with 0x3C:
// {0xA0, 0x0A, 0x50, 0x05} ^ 0x3C = {0x9C, 0x36, 0x6C, 0x39}
// {0x5F, 0xF5, 0xAF, 0xFA} ^ 0x3C = {0x63, 0xC9, 0x93, 0xC6}
constexpr uint8_t kMaskedBromCmd[4] = {0x9C, 0x36, 0x6C, 0x39};
constexpr uint8_t kMaskedBromEcho[4] = {0x63, 0xC9, 0x93, 0xC6};

std::mutex g_sec_mutex;
std::atomic<bool> g_engine_unlocked{false};
std::atomic<bool> g_tamper_detected{false};
uint8_t g_active_brom_cmd[4] = {0, 0, 0, 0};
uint8_t g_active_brom_echo[4] = {0, 0, 0, 0};
std::string g_bound_hwid;
int64_t g_expiry_epoch = 0;

std::string reconstruct_master_key() {
    std::string out(12, '\0');
    for (size_t i = 0; i < 12; ++i) {
        out[i] = static_cast<char>(kMaskedMasterKey[i] ^ 0x5A);
    }
    return out;
}

bool constant_time_equals(const std::string& a, const std::string& b) {
    if (a.size() != b.size()) return false;
    uint8_t diff = 0;
    for (size_t i = 0; i < a.size(); ++i) {
        diff |= static_cast<uint8_t>(a[i] ^ b[i]);
    }
    return diff == 0;
}

// Custom 256-bit keyed ARX MAC mixer (4x64-bit lanes) for fast native integrity
void compute_native_arx_mac(
    const uint8_t* key_data, size_t key_len,
    const uint8_t* msg_data, size_t msg_len,
    uint8_t out_mac[32]
) {
    uint64_t s0 = 0x736F6D6570736575ULL;
    uint64_t s1 = 0x646F72616E646F6DULL;
    uint64_t s2 = 0x6C7967656E657261ULL;
    uint64_t s3 = 0x7465646279746573ULL;

    auto mix_round = [&]() {
        s0 += s1; s1 = (s1 << 13) | (s1 >> 51); s1 ^= s0;
        s2 += s3; s3 = (s3 << 16) | (s3 >> 48); s3 ^= s2;
        s0 += s3; s3 = (s3 << 21) | (s3 >> 43); s3 ^= s0;
        s2 += s1; s1 = (s1 << 17) | (s1 >> 47); s1 ^= s2;
    };

    for (size_t i = 0; i < key_len; ++i) {
        s0 ^= (static_cast<uint64_t>(key_data[i]) + i) * 0x9E3779B97F4A7C15ULL;
        mix_round();
    }
    for (size_t i = 0; i < msg_len; ++i) {
        s2 ^= (static_cast<uint64_t>(msg_data[i]) + (i << 8)) * 0xBF58476D1CE4E5B9ULL;
        mix_round();
    }
    for (int r = 0; r < 8; ++r) {
        s1 ^= static_cast<uint64_t>(key_len ^ (msg_len << 16) ^ r);
        mix_round();
    }

    uint64_t lanes[4] = {s0, s1, s2, s3};
    for (int l = 0; l < 4; ++l) {
        for (int b = 0; b < 8; ++b) {
            out_mac[l * 8 + b] = static_cast<uint8_t>((lanes[l] >> (b * 8)) & 0xFF);
        }
    }
}

std::string to_upper_hex(const uint8_t* data, size_t len) {
    static const char* kHex = "0123456789ABCDEF";
    std::string out;
    out.reserve(len * 2);
    for (size_t i = 0; i < len; ++i) {
        out.push_back(kHex[(data[i] >> 4) & 0x0F]);
        out.push_back(kHex[data[i] & 0x0F]);
    }
    return out;
}

bool check_runtime_tamper() {
    // Check TracerPid in /proc/self/status
    FILE* fp = fopen("/proc/self/status", "r");
    if (fp) {
        char line[256];
        while (fgets(line, sizeof(line), fp)) {
            if (strncmp(line, "TracerPid:", 10) == 0) {
                int pid = atoi(line + 10);
                if (pid != 0) {
                    fclose(fp);
                    g_tamper_detected.store(true, std::memory_order_release);
                    return true;
                }
                break;
            }
        }
        fclose(fp);
    }

    // Check /proc/self/maps for known injection frameworks
    FILE* mp = fopen("/proc/self/maps", "r");
    if (mp) {
        char line[512];
        while (fgets(line, sizeof(line), mp)) {
            if (strstr(line, "frida-agent") != nullptr ||
                strstr(line, "frida-gadget") != nullptr) {
                fclose(mp);
                g_tamper_detected.store(true, std::memory_order_release);
                return true;
            }
        }
        fclose(mp);
    }
    return g_tamper_detected.load(std::memory_order_acquire);
}

std::string jstring_to_std(JNIEnv* env, jstring jstr) {
    if (!env || !jstr) return "";
    const char* chars = env->GetStringUTFChars(jstr, nullptr);
    if (!chars) return "";
    std::string res(chars);
    env->ReleaseStringUTFChars(jstr, chars);
    return res;
}

// Compute 8-char signature tag for key prefix "MTK-XXXX-XXXX-XXXX"
std::string compute_key_sig8(const std::string& prefix) {
    std::string key = reconstruct_master_key() + "::KEYGEN_V3_SALT";
    uint8_t mac[32];
    compute_native_arx_mac(
        reinterpret_cast<const uint8_t*>(key.data()), key.size(),
        reinterpret_cast<const uint8_t*>(prefix.data()), prefix.size(),
        mac
    );
    return to_upper_hex(mac, 4); // 8 hex chars
}

// Compute full 32-char signatureHex for Firestore & offline validation
std::string compute_full_sig32(const std::string& tag, const std::string& payload) {
    std::string key = reconstruct_master_key() + "::" + tag;
    uint8_t mac[32];
    compute_native_arx_mac(
        reinterpret_cast<const uint8_t*>(key.data()), key.size(),
        reinterpret_cast<const uint8_t*>(payload.data()), payload.size(),
        mac
    );
    return to_upper_hex(mac, 16); // 32 hex chars
}

// Verify key format "MTK-DDDD-RRRR-RRRR-SSSSSSSS" and return durationDays (>0) or -1
int verify_key_and_extract_days(const std::string& key_code) {
    // Expected length: "MTK-" (4) + "DDDD" (4) + "-" (1) + "RRRR" (4) + "-" (1) + "RRRR" (4) + "-" (1) + "SSSSSSSS" (8) = 27
    if (key_code.size() != 27) return -1;
    if (key_code.compare(0, 4, "MTK-") != 0) return -1;
    if (key_code[8] != '-' || key_code[13] != '-' || key_code[18] != '-') return -1;

    std::string prefix = key_code.substr(0, 18); // "MTK-DDDD-RRRR-RRRR"
    std::string provided_sig = key_code.substr(19, 8);
    std::string expected_sig = compute_key_sig8(prefix);

    if (!constant_time_equals(provided_sig, expected_sig)) {
        return -1;
    }

    std::string dur_str = key_code.substr(4, 4);
    int days = atoi(dur_str.c_str());
    if (days == 9999) return 36500; // Lifetime
    if (days <= 0 || days > 36500) return -1;
    return days;
}

// ============================================================================
// 2. LibUSB Context & Handle Management
// ============================================================================

std::mutex g_ctx_mutex;
libusb_context* g_ctx = nullptr;

libusb_context* get_or_init_context() {
    std::lock_guard<std::mutex> lock(g_ctx_mutex);
    if (!g_ctx) {
        libusb_set_option(nullptr, LIBUSB_OPTION_NO_DEVICE_DISCOVERY);
        int rc = libusb_init(&g_ctx);
        if (rc != LIBUSB_SUCCESS) {
            LOGE("libusb_init failed: %s", libusb_strerror(rc));
            g_ctx = nullptr;
            return nullptr;
        }
    }
    return g_ctx;
}

struct JinUsbHandle {
    libusb_device_handle* dev_handle = nullptr;
    int ctrl_iface = -1;
    int data_iface = -1;
    uint8_t ep_in = 0;
    uint8_t ep_out = 0;
    uint16_t max_pkt_in = 512;
    uint16_t max_pkt_out = 512;
    std::atomic<bool> closing{false};

    ~JinUsbHandle() {
        if (dev_handle) {
            if (data_iface >= 0) {
                libusb_release_interface(dev_handle, data_iface);
            }
            if (ctrl_iface >= 0 && ctrl_iface != data_iface) {
                libusb_release_interface(dev_handle, ctrl_iface);
            }
            libusb_close(dev_handle);
            dev_handle = nullptr;
        }
    }
};

std::mutex g_handles_mutex;
std::unordered_map<jlong, std::shared_ptr<JinUsbHandle>> g_handles;
std::atomic<jlong> g_next_handle_id{1};

jlong register_handle(std::shared_ptr<JinUsbHandle> handle) {
    if (!handle) return 0;
    const jlong id = g_next_handle_id.fetch_add(1, std::memory_order_relaxed);
    std::lock_guard<std::mutex> lock(g_handles_mutex);
    g_handles.emplace(id, std::move(handle));
    return id;
}

std::shared_ptr<JinUsbHandle> acquire_handle(jlong ptr) {
    if (ptr <= 0) return nullptr;
    if (!g_engine_unlocked.load(std::memory_order_acquire) ||
        g_tamper_detected.load(std::memory_order_acquire)) {
        return nullptr;
    }
    std::lock_guard<std::mutex> lock(g_handles_mutex);
    auto it = g_handles.find(ptr);
    if (it == g_handles.end()) return nullptr;
    if (!it->second || it->second->closing.load(std::memory_order_acquire)) return nullptr;
    return it->second;
}

bool validate_array_bounds(JNIEnv* env, jbyteArray array, jint offset, jint length) {
    if (!env || array == nullptr || offset < 0 || length <= 0) {
        return false;
    }
    const jsize arr_len = env->GetArrayLength(array);
    return static_cast<int64_t>(offset) + static_cast<int64_t>(length) <= static_cast<int64_t>(arr_len);
}

struct AsyncCtrlState {
    std::atomic<int> completed{0};
    int status = LIBUSB_TRANSFER_ERROR;
    int actual_length = 0;
};

void LIBUSB_CALL async_ctrl_callback(libusb_transfer* transfer) {
    auto* state = static_cast<AsyncCtrlState*>(transfer->user_data);
    if (state) {
        state->status = transfer->status;
        state->actual_length = transfer->actual_length;
        state->completed.store(1, std::memory_order_release);
    }
}

void detect_interfaces_and_endpoints(
    libusb_device* dev,
    int req_ctrl_iface,
    int req_data_iface,
    int req_ep_in,
    int req_ep_out,
    JinUsbHandle* out
) {
    libusb_config_descriptor* cfg = nullptr;
    if (libusb_get_active_config_descriptor(dev, &cfg) != LIBUSB_SUCCESS || !cfg) {
        out->ctrl_iface = req_ctrl_iface;
        out->data_iface = req_data_iface >= 0 ? req_data_iface : 0;
        out->ep_in = static_cast<uint8_t>(req_ep_in);
        out->ep_out = static_cast<uint8_t>(req_ep_out);
        return;
    }

    int found_ctrl = req_ctrl_iface;
    int found_data = req_data_iface;
    uint8_t found_in = req_ep_in > 0 ? static_cast<uint8_t>(req_ep_in) : 0;
    uint8_t found_out = req_ep_out > 0 ? static_cast<uint8_t>(req_ep_out) : 0;
    uint16_t pkt_in = 512;
    uint16_t pkt_out = 512;

    for (uint8_t i = 0; i < cfg->bNumInterfaces; ++i) {
        const libusb_interface& iface = cfg->interface[i];
        if (iface.num_altsetting <= 0) continue;
        const libusb_interface_descriptor& alt = iface.altsetting[0];

        if (found_ctrl < 0 && alt.bInterfaceClass == LIBUSB_CLASS_COMM) {
            found_ctrl = alt.bInterfaceNumber;
        }

        if (req_data_iface >= 0 && alt.bInterfaceNumber != req_data_iface) {
            continue;
        }

        uint8_t cand_in = 0;
        uint8_t cand_out = 0;
        uint16_t cand_pkt_in = 512;
        uint16_t cand_pkt_out = 512;
        for (uint8_t e = 0; e < alt.bNumEndpoints; ++e) {
            const libusb_endpoint_descriptor& ep = alt.endpoint[e];
            if ((ep.bmAttributes & LIBUSB_TRANSFER_TYPE_MASK) == LIBUSB_TRANSFER_TYPE_BULK) {
                if ((ep.bEndpointAddress & LIBUSB_ENDPOINT_DIR_MASK) == LIBUSB_ENDPOINT_IN) {
                    if (!cand_in) {
                        cand_in = ep.bEndpointAddress;
                        cand_pkt_in = ep.wMaxPacketSize > 0 ? ep.wMaxPacketSize : 512;
                    }
                } else {
                    if (!cand_out) {
                        cand_out = ep.bEndpointAddress;
                        cand_pkt_out = ep.wMaxPacketSize > 0 ? ep.wMaxPacketSize : 512;
                    }
                }
            }
        }

        if (cand_in && cand_out) {
            found_data = alt.bInterfaceNumber;
            if (!found_in) found_in = cand_in;
            if (!found_out) found_out = cand_out;
            pkt_in = cand_pkt_in;
            pkt_out = cand_pkt_out;
            break;
        }
    }

    libusb_free_config_descriptor(cfg);

    out->ctrl_iface = found_ctrl;
    out->data_iface = found_data >= 0 ? found_data : 0;
    out->ep_in = found_in;
    out->ep_out = found_out;
    out->max_pkt_in = pkt_in;
    out->max_pkt_out = pkt_out;
}

// ============================================================================
// 3. Hidden JNI Implementation Functions (Registered via RegisterNatives)
// ============================================================================

jlong jni_open(
    JNIEnv*, jobject,
    jint fd, jint ctrlIface, jint dataIface, jint epIn, jint epOut
) {
    if (fd < 0) return 0;
    if (check_runtime_tamper() || !g_engine_unlocked.load(std::memory_order_acquire)) {
        LOGE("USB Engine is locked by license security gate.");
        return 0;
    }

    libusb_context* ctx = get_or_init_context();
    if (!ctx) return 0;

    libusb_device_handle* dev_handle = nullptr;
    int rc = libusb_wrap_sys_device(ctx, static_cast<intptr_t>(fd), &dev_handle);
    if (rc != LIBUSB_SUCCESS || !dev_handle) {
        LOGE("libusb_wrap_sys_device failed (%d): %s", rc, libusb_strerror(rc));
        return 0;
    }

    libusb_set_auto_detach_kernel_driver(dev_handle, 1);

    auto handle = std::make_shared<JinUsbHandle>();
    handle->dev_handle = dev_handle;
    detect_interfaces_and_endpoints(
        libusb_get_device(dev_handle),
        ctrlIface, dataIface, epIn, epOut, handle.get()
    );

    if (handle->ctrl_iface >= 0 && handle->ctrl_iface != handle->data_iface) {
        if (libusb_kernel_driver_active(dev_handle, handle->ctrl_iface) == 1) {
            libusb_detach_kernel_driver(dev_handle, handle->ctrl_iface);
        }
        libusb_claim_interface(dev_handle, handle->ctrl_iface);
    }

    if (handle->data_iface >= 0) {
        if (libusb_kernel_driver_active(dev_handle, handle->data_iface) == 1) {
            libusb_detach_kernel_driver(dev_handle, handle->data_iface);
        }
        rc = libusb_claim_interface(dev_handle, handle->data_iface);
        if (rc != LIBUSB_SUCCESS) {
            LOGE("claim data_iface %d failed: %s", handle->data_iface, libusb_strerror(rc));
        }
    }

    return register_handle(std::move(handle));
}

jint jni_setup_cdc(
    JNIEnv*, jobject,
    jlong ptr, jint baudRate, jint dataBits, jint stopBits, jint parity,
    jboolean dtr, jboolean rts
) {
    auto handle = acquire_handle(ptr);
    if (!handle || !handle->dev_handle) return LIBUSB_ERROR_INVALID_PARAM;

    int target_iface = handle->ctrl_iface >= 0 ? handle->ctrl_iface : handle->data_iface;
    if (target_iface < 0) target_iface = 0;

    uint8_t line_coding[7] = {
        static_cast<uint8_t>(baudRate & 0xFF),
        static_cast<uint8_t>((baudRate >> 8) & 0xFF),
        static_cast<uint8_t>((baudRate >> 16) & 0xFF),
        static_cast<uint8_t>((baudRate >> 24) & 0xFF),
        static_cast<uint8_t>(stopBits),
        static_cast<uint8_t>(parity),
        static_cast<uint8_t>(dataBits)
    };

    int rc1 = libusb_control_transfer(
        handle->dev_handle,
        0x21, 0x20, 0x0000, static_cast<uint16_t>(target_iface),
        line_coding, sizeof(line_coding), 1000
    );

    uint16_t ctrl_state = (dtr ? 0x01 : 0x00) | (rts ? 0x02 : 0x00);
    int rc2 = libusb_control_transfer(
        handle->dev_handle,
        0x21, 0x22, ctrl_state, static_cast<uint16_t>(target_iface),
        nullptr, 0, 1000
    );

    return (rc1 >= 0 && rc2 >= 0) ? 0 : (rc2 < 0 ? rc2 : rc1);
}

jint jni_ctrl_transfer(
    JNIEnv* env, jobject,
    jlong ptr, jint reqType, jint req, jint value, jint index,
    jbyteArray buffer, jint length, jint timeoutMs
) {
    auto handle = acquire_handle(ptr);
    if (!handle || !handle->dev_handle) return LIBUSB_ERROR_INVALID_PARAM;
    if (length < 0 || length > 65535) return LIBUSB_ERROR_INVALID_PARAM;

    std::vector<uint8_t> tmp;
    uint8_t* data_ptr = nullptr;
    const bool is_in = (reqType & LIBUSB_ENDPOINT_IN) != 0;

    if (length > 0) {
        if (!validate_array_bounds(env, buffer, 0, length)) {
            return LIBUSB_ERROR_INVALID_PARAM;
        }
        tmp.resize(static_cast<size_t>(length));
        if (!is_in) {
            env->GetByteArrayRegion(buffer, 0, length, reinterpret_cast<jbyte*>(tmp.data()));
        }
        data_ptr = tmp.data();
    }

    int rc = libusb_control_transfer(
        handle->dev_handle,
        static_cast<uint8_t>(reqType),
        static_cast<uint8_t>(req),
        static_cast<uint16_t>(value),
        static_cast<uint16_t>(index),
        data_ptr,
        static_cast<uint16_t>(length),
        static_cast<unsigned int>(timeoutMs > 0 ? timeoutMs : 0)
    );

    if (rc > 0 && is_in && buffer != nullptr) {
        const jint copy_len = (rc <= length) ? rc : length;
        if (validate_array_bounds(env, buffer, 0, copy_len)) {
            env->SetByteArrayRegion(buffer, 0, copy_len, reinterpret_cast<const jbyte*>(tmp.data()));
        }
    }
    return rc;
}

jint jni_ctrl_async_cancel(
    JNIEnv* env, jobject,
    jlong ptr, jint reqType, jint req, jint value, jint index,
    jbyteArray buffer, jint length, jint cancelAfterUs
) {
    auto handle = acquire_handle(ptr);
    libusb_context* ctx = get_or_init_context();
    if (!handle || !handle->dev_handle || !ctx) return LIBUSB_ERROR_INVALID_PARAM;
    if (length < 0 || length > 65535) return LIBUSB_ERROR_INVALID_PARAM;

    if (length > 0 && !validate_array_bounds(env, buffer, 0, length)) {
        return LIBUSB_ERROR_INVALID_PARAM;
    }

    const size_t total_len = LIBUSB_CONTROL_SETUP_SIZE + static_cast<size_t>(length);
    std::vector<uint8_t> xfer_buf(total_len, 0);
    const bool is_in = (reqType & LIBUSB_ENDPOINT_IN) != 0;

    if (!is_in && length > 0) {
        env->GetByteArrayRegion(
            buffer, 0, length,
            reinterpret_cast<jbyte*>(xfer_buf.data() + LIBUSB_CONTROL_SETUP_SIZE)
        );
    }

    libusb_fill_control_setup(
        xfer_buf.data(),
        static_cast<uint8_t>(reqType),
        static_cast<uint8_t>(req),
        static_cast<uint16_t>(value),
        static_cast<uint16_t>(index),
        static_cast<uint16_t>(length)
    );

    libusb_transfer* xfer = libusb_alloc_transfer(0);
    if (!xfer) return LIBUSB_ERROR_NO_MEM;

    AsyncCtrlState state;
    libusb_fill_control_transfer(
        xfer, handle->dev_handle, xfer_buf.data(),
        async_ctrl_callback, &state, 500
    );

    int rc = libusb_submit_transfer(xfer);
    if (rc != LIBUSB_SUCCESS) {
        libusb_free_transfer(xfer);
        return rc;
    }

    if (cancelAfterUs > 0) {
        std::this_thread::sleep_for(std::chrono::microseconds(cancelAfterUs));
    }
    if (state.completed.load(std::memory_order_acquire) == 0) {
        libusb_cancel_transfer(xfer);
    }

    int completed_flag = 0;
    while (state.completed.load(std::memory_order_acquire) == 0) {
        timeval tv{0, 50000};
        libusb_handle_events_timeout_completed(ctx, &tv, &completed_flag);
    }

    int ret = (state.status == LIBUSB_TRANSFER_COMPLETED) ? state.actual_length : -1;
    if (ret > 0 && is_in && length > 0) {
        const jint copy_len = (ret <= length) ? ret : length;
        if (validate_array_bounds(env, buffer, 0, copy_len)) {
            env->SetByteArrayRegion(
                buffer, 0, copy_len,
                reinterpret_cast<const jbyte*>(xfer_buf.data() + LIBUSB_CONTROL_SETUP_SIZE)
            );
        }
    }
    libusb_free_transfer(xfer);
    return ret;
}

jint jni_bulk_write(
    JNIEnv* env, jobject,
    jlong ptr, jbyteArray data, jint offset, jint length, jint timeoutMs, jboolean sendZlp
) {
    auto handle = acquire_handle(ptr);
    if (!handle || !handle->dev_handle || handle->ep_out == 0) return LIBUSB_ERROR_INVALID_PARAM;
    if (offset < 0 || length < 0) return LIBUSB_ERROR_INVALID_PARAM;

    if (length == 0) {
        int transferred = 0;
        int rc = libusb_bulk_transfer(
            handle->dev_handle, handle->ep_out, nullptr, 0, &transferred,
            static_cast<unsigned int>(timeoutMs > 0 ? timeoutMs : 0)
        );
        return (rc == LIBUSB_SUCCESS) ? 0 : rc;
    }

    if (!validate_array_bounds(env, data, offset, length)) {
        return LIBUSB_ERROR_INVALID_PARAM;
    }

    std::vector<uint8_t> buf(static_cast<size_t>(length));
    env->GetByteArrayRegion(data, offset, length, reinterpret_cast<jbyte*>(buf.data()));

    int transferred = 0;
    int rc = libusb_bulk_transfer(
        handle->dev_handle, handle->ep_out,
        buf.data(), length, &transferred,
        static_cast<unsigned int>(timeoutMs > 0 ? timeoutMs : 0)
    );

    if (rc == LIBUSB_ERROR_PIPE && !handle->closing.load(std::memory_order_acquire)) {
        libusb_clear_halt(handle->dev_handle, handle->ep_out);
        rc = libusb_bulk_transfer(
            handle->dev_handle, handle->ep_out,
            buf.data(), length, &transferred,
            static_cast<unsigned int>(timeoutMs > 0 ? timeoutMs : 0)
        );
    }

    if (rc == LIBUSB_SUCCESS && sendZlp && handle->max_pkt_out > 0 && (transferred % handle->max_pkt_out == 0) &&
        !handle->closing.load(std::memory_order_acquire)) {
        int zlp_tx = 0;
        libusb_bulk_transfer(
            handle->dev_handle, handle->ep_out,
            nullptr, 0, &zlp_tx,
            static_cast<unsigned int>(timeoutMs > 0 ? timeoutMs : 0)
        );
    }

    if (rc == LIBUSB_SUCCESS || (rc == LIBUSB_ERROR_TIMEOUT && transferred > 0)) {
        return transferred;
    }
    return rc;
}

jint jni_bulk_read(
    JNIEnv* env, jobject,
    jlong ptr, jbyteArray buffer, jint offset, jint maxLen, jint timeoutMs, jboolean readFull
) {
    auto handle = acquire_handle(ptr);
    if (!handle || !handle->dev_handle || handle->ep_in == 0) return LIBUSB_ERROR_INVALID_PARAM;
    if (!validate_array_bounds(env, buffer, offset, maxLen)) return LIBUSB_ERROR_INVALID_PARAM;

    std::vector<uint8_t> tmp(static_cast<size_t>(maxLen));

    if (!readFull) {
        int actual = 0;
        int rc = libusb_bulk_transfer(
            handle->dev_handle, handle->ep_in,
            tmp.data(), maxLen, &actual,
            static_cast<unsigned int>(timeoutMs > 0 ? timeoutMs : 0)
        );
        if (rc == LIBUSB_ERROR_PIPE && !handle->closing.load(std::memory_order_acquire)) {
            libusb_clear_halt(handle->dev_handle, handle->ep_in);
            rc = libusb_bulk_transfer(
                handle->dev_handle, handle->ep_in,
                tmp.data(), maxLen, &actual,
                static_cast<unsigned int>(timeoutMs > 0 ? timeoutMs : 0)
            );
        }
        if (actual > 0) {
            const jint copy_len = (actual <= maxLen) ? actual : maxLen;
            env->SetByteArrayRegion(buffer, offset, copy_len, reinterpret_cast<const jbyte*>(tmp.data()));
            return copy_len;
        }
        return rc;
    }

    int total = 0;
    auto start = std::chrono::steady_clock::now();
    while (total < maxLen && !handle->closing.load(std::memory_order_acquire)) {
        auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now() - start
        ).count();
        int rem_timeout = timeoutMs - static_cast<int>(elapsed);
        if (rem_timeout <= 0) break;

        int actual = 0;
        int rc = libusb_bulk_transfer(
            handle->dev_handle, handle->ep_in,
            tmp.data() + total, maxLen - total, &actual,
            static_cast<unsigned int>(rem_timeout)
        );
        if (actual > 0) {
            total += actual;
            if (total > maxLen) total = maxLen;
        }
        if (rc == LIBUSB_ERROR_PIPE) {
            libusb_clear_halt(handle->dev_handle, handle->ep_in);
            continue;
        }
        if (rc != LIBUSB_SUCCESS && rc != LIBUSB_ERROR_TIMEOUT) {
            if (total > 0) {
                env->SetByteArrayRegion(buffer, offset, total, reinterpret_cast<const jbyte*>(tmp.data()));
                return total;
            }
            return rc;
        }
        if (rc == LIBUSB_ERROR_TIMEOUT) break;
    }

    if (total > 0) {
        env->SetByteArrayRegion(buffer, offset, total, reinterpret_cast<const jbyte*>(tmp.data()));
    }
    return total;
}

jboolean jni_brom_handshake(
    JNIEnv*, jobject, jlong ptr, jint maxAttempts
) {
    auto handle = acquire_handle(ptr);
    if (!handle || !handle->dev_handle || !handle->ep_in || !handle->ep_out) return JNI_FALSE;

    uint8_t seq[4];
    uint8_t exp[4];
    {
        std::lock_guard<std::mutex> lock(g_sec_mutex);
        memcpy(seq, g_active_brom_cmd, 4);
        memcpy(exp, g_active_brom_echo, 4);
    }

    for (int attempt = 0; attempt < maxAttempts && !handle->closing.load(std::memory_order_acquire); ++attempt) {
        bool ok = true;
        for (int i = 0; i < 4; ++i) {
            uint8_t out_b = seq[i];
            int tx = 0;
            int w = libusb_bulk_transfer(handle->dev_handle, handle->ep_out, &out_b, 1, &tx, 80);
            if (w != LIBUSB_SUCCESS || tx != 1) {
                ok = false;
                break;
            }
            uint8_t in_b = 0;
            int rx = 0;
            int r = libusb_bulk_transfer(handle->dev_handle, handle->ep_in, &in_b, 1, &rx, 80);
            if (r != LIBUSB_SUCCESS || rx != 1 || in_b != exp[i]) {
                ok = false;
                break;
            }
        }
        if (ok) return JNI_TRUE;
    }
    return JNI_FALSE;
}

jint jni_clear_halt(
    JNIEnv*, jobject, jlong ptr, jboolean inEndpoint
) {
    auto handle = acquire_handle(ptr);
    if (!handle || !handle->dev_handle) return LIBUSB_ERROR_INVALID_PARAM;
    uint8_t ep = inEndpoint ? handle->ep_in : handle->ep_out;
    return ep ? libusb_clear_halt(handle->dev_handle, ep) : LIBUSB_ERROR_NOT_FOUND;
}

jint jni_reset_device(
    JNIEnv*, jobject, jlong ptr
) {
    auto handle = acquire_handle(ptr);
    if (!handle || !handle->dev_handle) return LIBUSB_ERROR_INVALID_PARAM;
    return libusb_reset_device(handle->dev_handle);
}

void jni_close(
    JNIEnv*, jobject, jlong ptr
) {
    if (ptr <= 0) return;
    std::shared_ptr<JinUsbHandle> handle;
    {
        std::lock_guard<std::mutex> lock(g_handles_mutex);
        auto it = g_handles.find(ptr);
        if (it == g_handles.end()) return;
        handle = std::move(it->second);
        g_handles.erase(it);
        if (handle) {
            handle->closing.store(true, std::memory_order_release);
        }
    }
}

// ============================================================================
// 4. Native Security, Anti-SO Swap & License Engine JNI Methods
// ============================================================================

// Anti-SO replacement mutual challenge-response
jbyteArray jni_mutual_handshake(JNIEnv* env, jobject, jbyteArray nonceArr) {
    if (!env || !nonceArr) return nullptr;
    if (check_runtime_tamper()) return nullptr;

    const jsize len = env->GetArrayLength(nonceArr);
    if (len <= 0 || len > 256) return nullptr;
    std::vector<uint8_t> nonce(static_cast<size_t>(len));
    env->GetByteArrayRegion(nonceArr, 0, len, reinterpret_cast<jbyte*>(nonce.data()));

    std::string secret = reconstruct_master_key() + "::JIN_SO_AUTH_V3";
    uint8_t mac[32];
    compute_native_arx_mac(
        reinterpret_cast<const uint8_t*>(secret.data()), secret.size(),
        nonce.data(), nonce.size(),
        mac
    );

    jbyteArray out = env->NewByteArray(32);
    if (out) {
        env->SetByteArrayRegion(out, 0, 32, reinterpret_cast<const jbyte*>(mac));
    }
    return out;
}

// Verify MasterKey inside C++ without exposing it to Java/Kotlin
jboolean jni_verify_master_key(JNIEnv* env, jobject, jstring candidateStr) {
    if (check_runtime_tamper()) return JNI_FALSE;
    std::string candidate = jstring_to_std(env, candidateStr);
    std::string expected = reconstruct_master_key();
    return constant_time_equals(candidate, expected) ? JNI_TRUE : JNI_FALSE;
}

// Generate One-Time Random Key: "MTK-DDDD-RRRR-RRRR-SSSSSSSS"
jstring jni_generate_one_time_key(
    JNIEnv* env, jobject,
    jstring masterKeyStr, jint durationDays, jstring randomSeedStr
) {
    if (!env || check_runtime_tamper()) return env->NewStringUTF("");
    std::string mk = jstring_to_std(env, masterKeyStr);
    if (!constant_time_equals(mk, reconstruct_master_key())) {
        return env->NewStringUTF("");
    }

    int codeDays = durationDays;
    if (durationDays >= 3650) codeDays = 9999; // Lifetime code
    else if (durationDays <= 0) codeDays = 30;

    char durBuf[8];
    snprintf(durBuf, sizeof(durBuf), "%04d", codeDays % 10000);

    std::string seed = jstring_to_std(env, randomSeedStr);
    uint8_t randMac[32];
    compute_native_arx_mac(
        reinterpret_cast<const uint8_t*>(mk.data()), mk.size(),
        reinterpret_cast<const uint8_t*>(seed.data()), seed.size(),
        randMac
    );
    std::string randHex = to_upper_hex(randMac, 4); // 8 hex chars
    std::string part1 = randHex.substr(0, 4);
    std::string part2 = randHex.substr(4, 4);

    std::string prefix = std::string("MTK-") + durBuf + "-" + part1 + "-" + part2;
    std::string sig8 = compute_key_sig8(prefix);
    std::string fullKey = prefix + "-" + sig8;

    return env->NewStringUTF(fullKey.c_str());
}

// Verify key format & cryptographic signature; returns durationDays (>0) or -1
jint jni_verify_key_format(JNIEnv* env, jobject, jstring keyCodeStr) {
    if (check_runtime_tamper()) return -1;
    std::string keyCode = jstring_to_std(env, keyCodeStr);
    return verify_key_and_extract_days(keyCode);
}

// Compute 32-char signatureHex for Firestore document record
jstring jni_compute_key_sig_hex(JNIEnv* env, jobject, jstring keyCodeStr) {
    if (!env) return nullptr;
    std::string keyCode = jstring_to_std(env, keyCodeStr);
    if (verify_key_and_extract_days(keyCode) <= 0) {
        return env->NewStringUTF("");
    }
    std::string sig32 = compute_full_sig32("FIRESTORE_SIG_V3", keyCode);
    return env->NewStringUTF(sig32.c_str());
}

// Issue hardware-bound offline certificate upon online key activation
jstring jni_issue_offline_cert(
    JNIEnv* env, jobject,
    jstring hwidStr, jstring keyCodeStr, jlong expiryEpochSec
) {
    if (!env || check_runtime_tamper()) return env->NewStringUTF("");
    std::string hwid = jstring_to_std(env, hwidStr);
    std::string keyCode = jstring_to_std(env, keyCodeStr);
    if (hwid.size() < 6 || verify_key_and_extract_days(keyCode) <= 0) {
        return env->NewStringUTF("");
    }

    std::string payload = hwid + "|" + keyCode + "|" + std::to_string(static_cast<int64_t>(expiryEpochSec));
    std::string cert = compute_full_sig32("OFFLINE_CERT_V3", payload);
    return env->NewStringUTF(cert.c_str());
}

// Validate offline certificate and unlock native USB engine + BROM handshake bytes
jint jni_unlock_usb_engine(
    JNIEnv* env, jobject,
    jstring hwidStr, jstring keyCodeStr, jlong expiryEpochSec,
    jstring offlineCertStr, jlong currentEpochSec
) {
    if (check_runtime_tamper()) {
        g_engine_unlocked.store(false, std::memory_order_release);
        return -104; // Tamper detected
    }

    std::string hwid = jstring_to_std(env, hwidStr);
    std::string keyCode = jstring_to_std(env, keyCodeStr);
    std::string offlineCert = jstring_to_std(env, offlineCertStr);

    int days = verify_key_and_extract_days(keyCode);
    if (days <= 0) {
        g_engine_unlocked.store(false, std::memory_order_release);
        return -101; // Forged or invalid key
    }

    std::string payload = hwid + "|" + keyCode + "|" + std::to_string(static_cast<int64_t>(expiryEpochSec));
    std::string expectedCert = compute_full_sig32("OFFLINE_CERT_V3", payload);

    if (!constant_time_equals(offlineCert, expectedCert)) {
        g_engine_unlocked.store(false, std::memory_order_release);
        return -102; // HWID mismatch or certificate forged
    }

    if (currentEpochSec > expiryEpochSec) {
        g_engine_unlocked.store(false, std::memory_order_release);
        return -103; // License expired
    }

    {
        std::lock_guard<std::mutex> lock(g_sec_mutex);
        for (int i = 0; i < 4; ++i) {
            g_active_brom_cmd[i] = static_cast<uint8_t>(kMaskedBromCmd[i] ^ 0x3C);
            g_active_brom_echo[i] = static_cast<uint8_t>(kMaskedBromEcho[i] ^ 0x3C);
        }
        g_bound_hwid = hwid;
        g_expiry_epoch = static_cast<int64_t>(expiryEpochSec);
        g_engine_unlocked.store(true, std::memory_order_release);
    }
    return 1; // Unlocked
}

jboolean jni_is_engine_unlocked(JNIEnv*, jobject) {
    return (g_engine_unlocked.load(std::memory_order_acquire) &&
            !g_tamper_detected.load(std::memory_order_acquire)) ? JNI_TRUE : JNI_FALSE;
}

void jni_lock_usb_engine(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_sec_mutex);
    g_engine_unlocked.store(false, std::memory_order_release);
    memset(g_active_brom_cmd, 0, 4);
    memset(g_active_brom_echo, 0, 4);
    g_bound_hwid.clear();
    g_expiry_epoch = 0;
}

} // namespace

// ============================================================================
// 5. Dynamic JNI Registration (Zero Exported Java_com_example_... Symbols!)
// ============================================================================

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    if (!vm) return JNI_ERR;
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK || !env) {
        return JNI_ERR;
    }

    // Verify caller package name via ActivityThread.currentApplication().getPackageName()
    jclass atClass = env->FindClass("android/app/ActivityThread");
    if (atClass) {
        jmethodID curAppMethod = env->GetStaticMethodID(atClass, "currentApplication", "()Landroid/app/Application;");
        if (curAppMethod) {
            jobject appObj = env->CallStaticObjectMethod(atClass, curAppMethod);
            if (appObj) {
                jclass ctxClass = env->GetObjectClass(appObj);
                jmethodID getPkgMethod = env->GetMethodID(ctxClass, "getPackageName", "()Ljava/lang/String;");
                if (getPkgMethod) {
                    auto pkgStr = static_cast<jstring>(env->CallObjectMethod(appObj, getPkgMethod));
                    std::string pkg = jstring_to_std(env, pkgStr);
                    // Only allow our official applicationId or test namespace
                    if (!pkg.empty() &&
                        pkg != "com.aistudio.mtkbrombridge.whsnkz" &&
                        pkg != "com.example") {
                        g_tamper_detected.store(true, std::memory_order_release);
                        return JNI_ERR;
                    }
                    if (pkgStr) env->DeleteLocalRef(pkgStr);
                }
                env->DeleteLocalRef(ctxClass);
                env->DeleteLocalRef(appObj);
            }
        }
        env->DeleteLocalRef(atClass);
    }
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }

    jclass clazz = env->FindClass("com/example/protocol/JinUsbNative");
    if (!clazz) return JNI_ERR;

    static const JNINativeMethod kMethods[] = {
        {"nativeOpen", "(IIIII)J", reinterpret_cast<void*>(jni_open)},
        {"nativeSetupCdcLineCoding", "(JIIIIZZ)I", reinterpret_cast<void*>(jni_setup_cdc)},
        {"nativeControlTransfer", "(JIIII[BII)I", reinterpret_cast<void*>(jni_ctrl_transfer)},
        {"nativeControlTransferAsyncCancel", "(JIIII[BII)I", reinterpret_cast<void*>(jni_ctrl_async_cancel)},
        {"nativeBulkWrite", "(J[BIIIZ)I", reinterpret_cast<void*>(jni_bulk_write)},
        {"nativeBulkRead", "(J[BIIIZ)I", reinterpret_cast<void*>(jni_bulk_read)},
        {"nativeMtkBromHandshake", "(JI)Z", reinterpret_cast<void*>(jni_brom_handshake)},
        {"nativeClearHalt", "(JZ)I", reinterpret_cast<void*>(jni_clear_halt)},
        {"nativeResetDevice", "(J)I", reinterpret_cast<void*>(jni_reset_device)},
        {"nativeClose", "(J)V", reinterpret_cast<void*>(jni_close)},
        {"nativeMutualHandshake", "([B)[B", reinterpret_cast<void*>(jni_mutual_handshake)},
        {"nativeVerifyMasterKey", "(Ljava/lang/String;)Z", reinterpret_cast<void*>(jni_verify_master_key)},
        {"nativeGenerateOneTimeKey", "(Ljava/lang/String;ILjava/lang/String;)Ljava/lang/String;", reinterpret_cast<void*>(jni_generate_one_time_key)},
        {"nativeVerifyKeyFormat", "(Ljava/lang/String;)I", reinterpret_cast<void*>(jni_verify_key_format)},
        {"nativeComputeKeySignatureHex", "(Ljava/lang/String;)Ljava/lang/String;", reinterpret_cast<void*>(jni_compute_key_sig_hex)},
        {"nativeIssueOfflineCertificate", "(Ljava/lang/String;Ljava/lang/String;J)Ljava/lang/String;", reinterpret_cast<void*>(jni_issue_offline_cert)},
        {"nativeUnlockUsbEngine", "(Ljava/lang/String;Ljava/lang/String;JLjava/lang/String;J)I", reinterpret_cast<void*>(jni_unlock_usb_engine)},
        {"nativeIsUsbEngineUnlocked", "()Z", reinterpret_cast<void*>(jni_is_engine_unlocked)},
        {"nativeLockUsbEngine", "()V", reinterpret_cast<void*>(jni_lock_usb_engine)}
    };

    if (env->RegisterNatives(clazz, kMethods, sizeof(kMethods) / sizeof(kMethods[0])) != JNI_OK) {
        return JNI_ERR;
    }

    check_runtime_tamper();
    return JNI_VERSION_1_6;
}
