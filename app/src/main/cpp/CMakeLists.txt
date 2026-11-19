cmake_minimum_required(VERSION 3.22.1)
project(jin_usb LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(LIBUSB_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/third_party/libusb")
set(LIBUSB_SRC "${LIBUSB_ROOT}/libusb")

add_library(usb1.0 STATIC
    "${LIBUSB_SRC}/core.c"
    "${LIBUSB_SRC}/descriptor.c"
    "${LIBUSB_SRC}/hotplug.c"
    "${LIBUSB_SRC}/io.c"
    "${LIBUSB_SRC}/strerror.c"
    "${LIBUSB_SRC}/sync.c"
    "${LIBUSB_SRC}/os/events_posix.c"
    "${LIBUSB_SRC}/os/threads_posix.c"
    "${LIBUSB_SRC}/os/linux_usbfs.c"
    "${LIBUSB_SRC}/os/linux_netlink.c"
)

target_include_directories(usb1.0 PUBLIC
    "${LIBUSB_SRC}"
    "${LIBUSB_SRC}/os"
    "${LIBUSB_ROOT}/android"
)

target_compile_options(usb1.0 PRIVATE
    -fvisibility=hidden
    -pthread
)

target_compile_definitions(usb1.0 PRIVATE
    HAVE_CONFIG_H=1
    _GNU_SOURCE=1
)

add_library(jin_usb SHARED
    jin_usb_jni.cpp
)

target_compile_options(jin_usb PRIVATE
    -fvisibility=hidden
    -fvisibility-inlines-hidden
    -ffunction-sections
    -fdata-sections
    -O2
)

target_link_libraries(jin_usb
    usb1.0
    log
    android
)

target_link_options(jin_usb PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,--gc-sections"
    "-Wl,--strip-all"
    "-Wl,--exclude-libs,ALL"
)
