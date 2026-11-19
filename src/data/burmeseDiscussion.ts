export interface DiscussionSection {
  id: string;
  titleMm: string;
  titleEn: string;
  badge: string;
  summaryMm: string;
  summaryEn: string;
  contentMm: string[];
  contentEn: string[];
  prosMm: string[];
  consMm: string[];
  verdictMm: string;
  verdictEn: string;
}

export const BURMESE_GSM_DISCUSSIONS: DiscussionSection[] = [
  {
    id: 'qt-gsm',
    titleMm: '၁။ Qt (C++ / QML / PySide6) — Commercial GSM Tool များ၏ စံပြရွေးချယ်မှု',
    titleEn: '1. Qt (C++ / QML / PySide6) — The Gold Standard for Commercial GSM Tools',
    badge: 'Industry Standard (UnlockTool / Chimera / Octoplus)',
    summaryMm: 'ChimeraTool, Octoplus, Hydra, နဲ့ မူရင်း mtkclient GUI တို့ဟာ Qt ကို ဘာကြောင့် သုံးထားကြတာလဲ?',
    summaryEn: 'Why do ChimeraTool, Octoplus, Hydra, and mtkclient GUI rely on Qt?',
    contentMm: [
      'တကယ့် Commercial GSM Tool တွေဖြစ်တဲ့ UnlockTool, Chimera, Hydra, Octoplus တွေမှာ UI ထက် ပိုအရေးကြီးတာက "High-Throughput USB Streaming" ဖြစ်ပါတယ်။',
      'Qt C++ ကို သုံးတဲ့အခါ WinUSB, libusb-1.0, QSerialPort တို့နဲ့ C++ native level မှာ တိုက်ရိုက်ချိတ်ဆက်နိုင်တာကြောင့် Garbage Collection (GC) ကြောင့် ဖြစ်တတ်တဲ့ Micro-stuttering လုံးဝ မရှိပါဘူး။',
      'Partition Table အတွက် Qt ရဲ့ `QTableView` ဟာ Model/View Architecture ကို သုံးထားတာကြောင့် partition အခု ၁၀၀ မက ရှိတဲ့ Android Super/GPT structure တွေကို memory မစားဘဲ ချက်ချင်း render လုပ်နိုင်ပါတယ်။',
      '`QPlainTextEdit` ဟာ Log line ပေါင်း သိန်းချီ (100,000+ lines) ထွက်လာရင်တောင် maximumBlockCount နဲ့ Virtual Canvas ကြောင့် UI freeze မဖြစ်ဘဲ ချောမွေ့စွာ scroll လုပ်နိုင်ပါတယ်။',
      'Android APK အတွက်လည်း Qt for Android (Qt 6.6+) ကနေ Native NDK .so အဖြစ် တိုက်ရိုက် compile လုပ်နိုင်ပြီး JNI မှတဆင့် Android USB Host API နဲ့ ချိတ်ဆက်နိုင်ပါတယ်။',
      'Security ဘက်မှာ C++ binary ဖြစ်တဲ့အတွက် VMProtect, Themida, Obfuscator-LLVM တို့နဲ့ အလွယ်တကူ protect လုပ်နိုင်ပြီး Dongle / Smart Card (PC/SC) driver တွေကို တိုက်ရိုက် link ချိတ်နိုင်ပါတယ်။'
    ],
    contentEn: [
      'In production GSM tools like UnlockTool, Chimera, and Octoplus, sustained USB throughput and zero-copy flashing take precedence over superficial UI fluff.',
      'Using Qt with native C++ allows direct binding to WinUSB, libusb-1.0, and QSerialPort with zero GC pauses, ensuring uninterrupted flashing of 4GB–10GB partitions.',
      'QTableView handles virtualized scrolling for massive partition tables (GPT/Scatter/rawprogram.xml) with instantaneous model updates.',
      'QPlainTextEdit is optimized for high-frequency logs (100,000+ entries) without bogging down the event loop.',
      'Qt for Android provides direct NDK compilation with JNI bridge to Android UsbManager.',
      'Commercial grade security: Native C++ integrates seamlessly with VMProtect, Themida, and hardware smart card dongles (PC/SC).'
    ],
    prosMm: [
      'C++ USB Engine နှင့် Zero-copy Memory Buffer ဖြင့် တိုက်ရိုက် Flash နိုင်ခြင်း',
      'Partition Table နှင့် Huge Log Terminal အတွက် Desktop-grade UI Components များ အဆင်သင့်ပါဝင်ခြင်း',
      'Windows (.exe), Linux, macOS အပြင် Android (.apk) ပါ Build ထွက်နိုင်ခြင်း',
      'Themida / VMProtect ဖြင့် Commercial Crack ကာကွယ်မှု အလွယ်တကူ ပြုလုပ်နိုင်ခြင်း'
    ],
    consMm: [
      'Qt Library Binary Size ကြီးမားခြင်း (Standalone installer မှာ ၃၀ MB မှ ၆၀ MB ခန့် ရှိတတ်သည်)',
      'Android APK တွင် Qt Runtime စတင်ချိန် (Startup time) အနည်းငယ် ကြာနိုင်ခြင်း'
    ],
    verdictMm: 'အကယ်၍ သင့် Project သည် Commercial Box / Dongle / Subscription Tool ဖြစ်လာရန် ရည်ရွယ်ပြီး partition table ကြီးမားခြင်း၊ log အမြောက်အမြား ပြသရန် လိုအပ်ပါက Qt (C++) သည် အကောင်းဆုံး ရွေးချယ်မှု ဖြစ်ပါသည်။',
    verdictEn: 'If aiming for a commercial-grade GSM flasher with huge partition tables and heavy logging, Qt (C++) remains the unrivaled champion.'
  },
  {
    id: 'flutter-gsm',
    titleMm: '၂။ Flutter (Dart + Skia/Impeller) — Modern Cross-Platform UI + Dart FFI USB',
    titleEn: '2. Flutter (Dart + Skia/Impeller) — Modern Cross-Platform UI with Dart FFI',
    badge: 'Best UI/UX & Mobile Experience',
    summaryMm: 'Android 8 မှ Android 15 အထိ Pixel တိုင်း တူညီသော 120 FPS UI နှင့် Dart FFI မှတဆင့် C++ libusb ချိတ်ဆက်မှု',
    summaryEn: 'Identical 120 FPS UI across Android 8 to 15, powered by Skia/Impeller and Dart FFI to C++ libusb.',
    contentMm: [
      'Flutter ရဲ့ အကြီးမားဆုံး အားသာချက်က UI Engine (Impeller/Skia) ဖြစ်ပြီး OS ရဲ့ OEM Widget တွေကို အားမကိုးတဲ့အတွက် Android 8 ကော Android 15 မှာပါ ပုံစံနဲ့ animation ၁၀၀% တူညီပါတယ်။',
      'Flutter ကနေ USB Hardware ကို ထိန်းချုပ်ဖို့ Dart FFI (`dart:ffi`) ကို အသုံးပြုပြီး `libusb.so` (Android/Linux) ဒါမှမဟုတ် `libusb.dll` (Windows) ကို တိုက်ရိုက် Load လုပ်ရပါတယ်။',
      'အရေးကြီးဆုံး အချက်: USB Bulk Read/Write (Super partition 4GB flash ချိန်) မှာ Dart ရဲ့ UI Thread freeze မဖြစ်စေရန် Dart `Isolate` (Background Worker) ထဲမှာ သီးခြား Run ရပါမယ်။',
      'Android APK အတွက် Root မလိုဘဲ OTG ချိတ်ဆက်ရန်: Java/Kotlin ဘက်က `UsbManager.requestPermission()` တောင်းပြီး ရလာတဲ့ `UsbDeviceConnection.getFileDescriptor()` (fd) ကို C++ ဘက်သို့ ပို့ကာ `libusb_wrap_sys_device(ctx, fd, &handle)` နည်းလမ်းဖြင့် ချိတ်ဆက်ရပါမယ်။'
    ],
    contentEn: [
      'Flutter controls every pixel via its custom rendering engine (Impeller/Skia), guaranteeing identical layout across Android 8 through Android 15.',
      'USB hardware interfacing is handled via Dart FFI (Foreign Function Interface), dynamically binding to libusb.so on Android/Linux or libusb.dll on Windows.',
      'Critical implementation detail: USB chunk streaming must run inside a background Dart Isolate to prevent blocking the UI animation loop during 4GB+ super.img writes.',
      'Android OTG without Root: Android USB Host API grants file descriptor (fd) via UsbDeviceConnection, handed to C++ via `libusb_wrap_sys_device`.'
    ],
    prosMm: [
      'ခေတ်မီဆန်းပြားသော Modern Glassmorphism & Dark Neon UI ကို အလွယ်တကူ ရေးသားနိုင်ခြင်း',
      'Android Phone/Tablet ပေါ်တွင် အလွန်ချောမွေ့သော Touch UX ရရှိခြင်း',
      'Codebase တစ်ခုတည်းဖြင့် Windows, Android, macOS, Linux, Web ထုတ်နိုင်ခြင်း'
    ],
    consMm: [
      'Dart FFI နှင့် C++ ကြား Memory Copy (Marshalling) Overhead အနည်းငယ်ရှိနိုင်ခြင်း',
      'Dart Garbage Collector (GC) ကြောင့် extreme high-speed flashing တွင် isolate tuning သေချာလုပ်ရန် လိုအပ်ခြင်း',
      'Themida / VMProtect ကဲ့သို့ Native Protector များ Flutter Dart snapshot ပေါ်တွင် တိုက်ရိုက် protect လုပ်ရန် ခက်ခဲခြင်း'
    ],
    verdictMm: 'အကယ်၍ သင့် Tool ကို Android ဖုန်းပေါ်တွင် အဓိက သုံးမည့် OTG Mobile Flasher အဖြစ် သို့မဟုတ် အလွန်လှပသော Modern UI လိုချင်ပါက Flutter + Dart FFI သည် အကောင်းဆုံး ရွေးချယ်မှု ဖြစ်ပါသည်။',
    verdictEn: 'Ideal for mobile-first OTG flashers or tools requiring breathtaking modern UI aesthetics.'
  },
  {
    id: 'imgui-gsm',
    titleMm: '၃။ C++ Dear ImGui — Zero Overhead, Ultra-Lightweight & Hacker Grade',
    titleEn: '3. C++ Dear ImGui — Zero Overhead, Ultra-Lightweight & Hacker Grade',
    badge: 'Lightest & Highest Performance (<15 MB)',
    summaryMm: 'C++ Code တစ်ခုတည်းဖြင့် Windows .exe နှင့် Android APK (NDK NativeActivity) တိုက်ရိုက် Compile ထုတ်ခြင်း',
    summaryEn: 'Compile same C++ code directly into Windows .exe and Android APK via NDK NativeActivity.',
    contentMm: [
      'Dear ImGui ဟာ Immediate Mode GUI ဖြစ်တာကြောင့် State Management ဆိုတဲ့ အပိုဝန် မရှိဘဲ C++ Variable တွေကို Frame တိုင်းမှာ တိုက်ရိုက်ဆွဲပြပါတယ်။',
      'Engine Overhead မရှိတဲ့အတွက် Binary Size ဟာ 5MB မှ 15MB ဝန်းကျင်သာ ရှိပြီး RAM စားသုံးမှု အလွန်နည်းပါးပါတယ်။',
      'Android APK အနေနဲ့ Build လုပ်တဲ့အခါ Android NDK `android_native_app_glue` နှင့် EGL / OpenGL ES 3.0 ကို သုံးပြီး Java/Kotlin မလိုဘဲ Pure C++ APK ထုတ်နိုင်ပါတယ်။',
      'BROM / EDL handshake လုပ်နေချိန်တွင် zero latency ဖြင့် terminal log များကို render လုပ်နိုင်ပြီး USB Buffer Memory ကို UI Table ထဲသို့ pointer တိုက်ရိုက် ပေးပြနိုင်ပါတယ်။'
    ],
    contentEn: [
      'Immediate Mode GUI eliminates state synchronization overhead by rendering UI directly from C++ state structs every frame.',
      'Ultra-compact binary size (often under 10–15 MB) with negligible RAM consumption.',
      'Android APK builds via Android NDK `android_native_app_glue` and EGL/GLES without Java/Kotlin overhead.',
      'Flawless zero-latency data visualization during critical BROM / EDL handshake exploits.'
    ],
    prosMm: [
      'အလွန်သေးငယ်သော Binary Size (Portable Single .exe / APK)',
      'Frame တိုင်းတွင် C++ Data Struct များကို တိုက်ရိုက် Pointer ဖြင့် ဆွဲပြနိုင်ခြင်း (Zero Memory Copy)',
      'Game Cheat Tool, Hardware Debugger, Reverse Engineering Tool များကဲ့သို့ High FPS & Stability'
    ],
    consMm: [
      'Modern Consumer UI (Animation, Custom Font rendering, Complex Touch Gestures) ဖန်တီးရန် ခက်ခဲခြင်း',
      'Android Touch screen ပေါ်တွင် ခလုတ်သေးငယ်မှုကြောင့် Multi-touch ergonomics အားနည်းနိုင်ခြင်း'
    ],
    verdictMm: 'အကယ်၍ သင့် Tool သည် Rescue Flasher, Embedded Recovery Tool, သို့မဟုတ် Flash Drive ထဲ အလွယ်တကူ ဆောင်သွားနိုင်သော Portable Tool ဖြစ်ပါက Dear ImGui သည် အမြန်ဆန်ဆုံးနှင့် အပေါ့ပါးဆုံး ဖြစ်ပါသည်။',
    verdictEn: 'The ultimate choice for lightweight portable rescue flashers and hacker-grade hardware diagnostic consoles.'
  },
  {
    id: 'android-otg-secret',
    titleMm: '၄။ အရေးကြီးဆုံး လျှို့ဝှက်ချက် — Android တွင် Root မလိုဘဲ USB OTG ဖြင့် ဖုန်းအချင်းချင်း Flash နည်း',
    titleEn: '4. The Golden Key — Flashing via Android OTG Without Root Permissions',
    badge: 'Crucial Technical Trick for Mobile Flashing',
    summaryMm: 'Android တွင် libusb_open() ခေါ်လျှင် Permission Denied ဖြစ်တတ်သည့် ပြဿနာကို UsbDeviceConnection.getFileDescriptor() ဖြင့် ဖြေရှင်းနည်း',
    summaryEn: 'Overcoming Android libusb "Permission Denied" via UsbDeviceConnection fd wrapping.',
    contentMm: [
      'Android OS တွင် `/dev/bus/usb/...` node များကို လုံခြုံရေးအရ Root Access မရှိဘဲ တိုက်ရိုက် `open()` လုပ်ခွင့် ပိတ်ထားပါတယ်။',
      'ထို့ကြောင့် C++ သို့မဟုတ် Dart မှ `libusb_open()` ကို တိုက်ရိုက် ခေါ်ပါက "Permission Denied" (LIBUSB_ERROR_ACCESS) ဖြစ်ပါလိမ့်မယ်။',
      'ဖြေရှင်းနည်းအဆင့်များ:',
      '၁။ Android OS ဘက် (Java/Kotlin/Flutter Plugin) တွင် `UsbManager.requestPermission(device, intent)` ဖြင့် User ထံမှ OTG Permission တောင်းပါ။',
      '၂။ User က "Allow" ပေးပြီးနောက် `UsbDeviceConnection connection = usbManager.openDevice(device);` ကို ခေါ်ပါ။',
      '၃။ ထို `connection` ထံမှ OS File Descriptor `int fd = connection.getFileDescriptor();` ကို ရယူပါ။',
      '၄။ ၎င်း `fd` ကို C++ libusb ထံသို့ ပို့ပြီး `libusb_wrap_sys_device(ctx, (intptr_t)fd, &device_handle);` ကို ခေါ်လိုက်ပါက Root လုံးဝ မလိုဘဲ BROM, EDL, Fastboot USB Raw Command များကို တိုက်ရိုက် ပို့နိုင်ပါပြီ!'
    ],
    contentEn: [
      'Android restricts raw access to `/dev/bus/usb/...` device nodes unless rooted.',
      'Calling standard `libusb_open()` or `open()` directly from C++ throws `LIBUSB_ERROR_ACCESS`.',
      'Solution Architecture:',
      '1. Request user permission via Android `UsbManager.requestPermission()`.',
      '2. Open connection: `UsbDeviceConnection conn = usbManager.openDevice(usbDevice);`.',
      '3. Extract native file descriptor: `int fd = conn.getFileDescriptor();`.',
      '4. Pass fd to C++ native code: `libusb_wrap_sys_device(ctx, (intptr_t)fd, &dev_handle);`. This unlocks unrestricted raw USB bulk transfers without requiring root!'
    ],
    prosMm: [
      'Root Access လုံးဝ မလိုဘဲ ဆိုင်သုံး ဖုန်းတစ်လုံးဖြင့် အခြား ဖုန်းတစ်လုံးကို Flash / Unlock လုပ်နိုင်ခြင်း',
      'Shop technician များ PC မလိုဘဲ ဖုန်းချင်းချိတ်ပြီး FRP Bypass / Scatter Flashing ပြုလုပ်နိုင်ခြင်း'
    ],
    consMm: [
      'OTG Adapter ကောင်းမွန်ရန်နှင့် Host ဖုန်းတွင် 5V Power လုံလောက်စွာ ထုတ်ပေးနိုင်ရန် လိုအပ်ခြင်း'
    ],
    verdictMm: 'Qt for Android ဖြစ်စေ၊ Flutter + FFI ဖြစ်စေ၊ Dear ImGui ဖြစ်စေ ဤ `libusb_wrap_sys_device` နည်းလမ်းကို မဖြစ်မနေ ထည့်သွင်းရပါမည်။',
    verdictEn: 'Essential architecture requirement for any non-root Android OTG phone flasher.'
  }
];
