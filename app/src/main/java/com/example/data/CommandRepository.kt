package com.example.data

import com.example.model.ActionGroup
import com.example.model.ServiceActionItem
import com.example.model.ServiceBrand

object CommandRepository {

    val supportedPartitions: List<String> = listOf(
        "Auto Detect (from file)",
        "boot (Kernel / Ramdisk - All Android)",
        "init_boot (Android 13–16 Ramdisk)",
        "vendor_boot (Android 11–16 Vendor)",
        "vbmeta (AVB 2.0 Verification)",
        "vbmeta_system (System AVB)",
        "vbmeta_vendor (Vendor AVB)",
        "super (Dynamic Partition A10–A16)",
        "system (System Partition - All Android)",
        "vendor (Vendor Partition)",
        "product (Logical Partition)",
        "system_ext (Logical Partition)",
        "odm (Logical Partition)",
        "recovery (Recovery Image - All Android)",
        "dtbo (Device Tree Overlay)",
        "modem (Baseband Radio)",
        "persist (Sensor & MAC Persist)",
        "userdata (User Storage)",
        "cache (Legacy Cache Partition A5–A11)",
        "metadata (FBE Encryption A10–A16)"
    )

    val allActions: List<ServiceActionItem> = listOf(
        // =========================================================================
        // 1. UNIVERSAL (ALL ANDROID 5 – 16)
        // =========================================================================
        // --- Fastboot Info ---
        ServiceActionItem(
            id = "uni_fb_info_getvar_all",
            title = "Read All Bootloader Variables (All Android)",
            command = "fastboot getvar all",
            description = "Android 5 မှ 16 အထိ Bootloader အချက်အလက်အားလုံး (Partition, Slot, Lock state, Serial) ကို ဖတ်ယူသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.INFO
        ),
        ServiceActionItem(
            id = "uni_fb_info_bootloader_ver",
            title = "Read Bootloader & Baseband Version",
            command = "fastboot getvar version-bootloader",
            description = "ဖုန်း၏ လက်ရှိ Bootloader Firmware ဗားရှင်းကို စစ်ဆေးသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.INFO
        ),
        ServiceActionItem(
            id = "uni_fb_info_product",
            title = "Read Device Product & Board Codename",
            command = "fastboot getvar product",
            description = "Firmware ရွေးချယ်ရာတွင် လိုအပ်သော Device Board Codename ကို ဖတ်ယူသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.INFO
        ),
        ServiceActionItem(
            id = "uni_fb_info_unlocked",
            title = "Check Bootloader Unlocked State",
            command = "fastboot getvar unlocked",
            description = "Android ဗားရှင်းအားလုံးတွင် Bootloader Lock/Unlock အခြေအနေ (yes/no) ကို စစ်ဆေးသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.INFO
        ),
        ServiceActionItem(
            id = "uni_fb_info_secure",
            title = "Check Secure Boot State",
            command = "fastboot getvar secure",
            description = "Bootloader Secure Boot ဖွင့်ထားခြင်း ရှိ/မရှိ စစ်ဆေးသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.INFO
        ),
        ServiceActionItem(
            id = "uni_fb_info_unlock_ability",
            title = "Check OEM Unlock Ability Flag (A6–A16)",
            command = "fastboot flashing get_unlock_ability",
            description = "Developer Options ထဲတွင် OEM Unlock ဖွင့်ထားခြင်း ရှိ/မရှိ စစ်ဆေးသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.INFO
        ),
        ServiceActionItem(
            id = "uni_fb_info_slot",
            title = "Read Active A/B Boot Slot (A7–A16)",
            command = "fastboot getvar current-slot",
            description = "A/B Partition ဖုန်းများတွင် လက်ရှိ Boot တက်နေသော Slot (a သို့မဟုတ် b) ကို စစ်ဆေးသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.INFO
        ),
        ServiceActionItem(
            id = "uni_fb_info_userspace",
            title = "Check Userspace Fastbootd Status (A10–A16)",
            command = "fastboot getvar is-userspace",
            description = "လက်ရှိ Mode သည် Bootloader လား သို့မဟုတ် Android 10–16 Fastbootd လား စစ်ဆေးသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.INFO
        ),

        // --- Fastboot Unlock ---
        ServiceActionItem(
            id = "uni_fb_unlock_flashing",
            title = "Unlock Bootloader (Modern A6–A16)",
            command = "fastboot flashing unlock",
            description = "Android 6 မှ 16 အထိ Standard Bootloader ကို Unlock ပြုလုပ်သည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "uni_fb_unlock_critical",
            title = "Unlock Critical Partitions (A7–A16)",
            command = "fastboot flashing unlock_critical",
            description = "Bootloader နှင့်အတူ Preloader/ABL Critical Partition များကိုပါ Unlock လုပ်သည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "uni_fb_unlock_oem_legacy",
            title = "Unlock Bootloader (Legacy A5–A9 / OEM)",
            command = "fastboot oem unlock",
            description = "Android 5 မှ 9 အထိ ဖုန်းအဟောင်းများနှင့် OEM Bootloader များကို Unlock ပြုလုပ်သည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "uni_fb_unlock_oem_go",
            title = "Confirm Legacy OEM Unlock-Go",
            command = "fastboot oem unlock-go",
            description = "MTK/Qualcomm ဖုန်းအဟောင်းများတွင် OEM Unlock အတည်ပြု Command ပေးပို့သည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "uni_fb_lock_flashing",
            title = "Relock Bootloader (Modern A6–A16)",
            command = "fastboot flashing lock",
            description = "Android 6 မှ 16 အထိ Bootloader ကို ပြန်လည် Lock ခတ်သည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "uni_fb_lock_oem_legacy",
            title = "Relock Bootloader (Legacy A5–A9 / OEM)",
            command = "fastboot oem lock",
            description = "Android 5 မှ 9 အထိ Legacy Bootloader ကို ပြန်လည် Lock ခတ်သည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "uni_fb_unlock_erase_frp",
            title = "Erase FRP Partition (All Android)",
            command = "fastboot erase frp",
            description = "Fastboot မှတစ်ဆင့် Google Account (FRP) Partition ကို ရှင်းလင်းသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "uni_fb_unlock_erase_config",
            title = "Erase Legacy FRP Config Partition (A5–A8)",
            command = "fastboot erase config",
            description = "Android 5–8 ဖုန်းအဟောင်းများရှိ FRP Config Partition ကို ရှင်းလင်းသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "uni_fb_unlock_erase_persistent",
            title = "Erase Persistent FRP Partition (AOSP/SPD)",
            command = "fastboot erase persistent",
            description = "SPD/Unisoc နှင့် AOSP ဖုန်းများရှိ Persistent FRP Partition ကို ရှင်းလင်းသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.UNLOCK
        ),

        // --- Fastboot Service ---
        ServiceActionItem(
            id = "uni_fb_svc_wipe_all",
            title = "Factory Reset (Wipe Userdata + Cache/Meta)",
            command = "fastboot -w",
            description = "Android ဗားရှင်းအားလုံးတွင် Userdata နှင့် Cache/Metadata ကို အပြီးရှင်းလင်းသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "uni_fb_svc_erase_userdata",
            title = "Erase Userdata Partition Only",
            command = "fastboot erase userdata",
            description = "ဖုန်းအတွင်းရှိ User Data Partition ကိုသာ ဖျက်ထုတ်သည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "uni_fb_svc_erase_cache",
            title = "Erase Cache Partition (A5–A11)",
            command = "fastboot erase cache",
            description = "Legacy နှင့် Non-A/B ဖုန်းများ၏ Cache Partition ကို ရှင်းလင်းသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "uni_fb_svc_erase_metadata",
            title = "Erase FBE Encryption Metadata (A10–A16)",
            command = "fastboot erase metadata",
            description = "Android 10–16 FBE Encryption Key Metadata Partition ကို ဖျက်သည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "uni_fb_svc_erase_persist",
            title = "Erase Persist Sensor Partition",
            command = "fastboot erase persist",
            description = "Sensor/Calibration သိမ်းဆည်းသော Persist Partition ကို ရှင်းလင်းသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "uni_fb_svc_erase_modemst",
            title = "Reset Baseband NV (Modemst1 & Modemst2)",
            command = "fastboot erase modemst1",
            description = "Qualcomm Baseband EFS Cache (modemst1 & modemst2) ကို Reset ချသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "uni_fb_svc_slot_a",
            title = "Set Active Boot Slot -> SLOT A (A7–A16)",
            command = "fastboot set_active a",
            description = "Boot တက်မည့် Active Slot ကို Slot A သို့ ပြောင်းလဲသတ်မှတ်သည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "uni_fb_svc_slot_b",
            title = "Set Active Boot Slot -> SLOT B (A7–A16)",
            command = "fastboot set_active b",
            description = "Boot တက်မည့် Active Slot ကို Slot B သို့ ပြောင်းလဲသတ်မှတ်သည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "uni_fb_svc_fastbootd",
            title = "Switch to Userspace Fastbootd (A10–A16)",
            command = "fastboot reboot fastboot",
            description = "Dynamic Partition (super/system/vendor) ရေးသားရန် Fastbootd သို့ ပြောင်းသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "uni_fb_svc_snap_cancel",
            title = "Cancel Virtual A/B Snapshot (A11–A16)",
            command = "fastboot snapshot-update cancel",
            description = "OTA Update တန်းလန်းဖြစ်နေသော Virtual A/B Snapshot ကို ပယ်ဖျက်သည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "uni_fb_svc_snap_merge",
            title = "Merge Virtual A/B Snapshot (A11–A16)",
            command = "fastboot snapshot-update merge",
            description = "Virtual A/B Snapshot Partition များကို အပြီးသတ် ပေါင်းစည်းပေးသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "uni_fb_svc_create_logical",
            title = "Create Logical Partition system_a (A10–A16)",
            command = "fastboot create-logical-partition system_a 3221225472",
            description = "Fastbootd အတွင်း၌ Dynamic Logical Partition အသစ် တည်ဆောက်သည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "uni_fb_svc_resize_logical",
            title = "Resize Logical Partition system_a (A10–A16)",
            command = "fastboot resize-logical-partition system_a 3221225472",
            description = "Fastbootd အတွင်း၌ Logical Partition အရွယ်အစားကို ပြန်ညှိသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "uni_fb_svc_delete_logical",
            title = "Delete Logical Partition product_b (A10–A16)",
            command = "fastboot delete-logical-partition product_b",
            description = "Super Partition နေရာလွတ်ရစေရန် မသုံးသော product_b ကို ဖျက်သည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "uni_fb_svc_reboot_sys",
            title = "Reboot to Normal System",
            command = "fastboot reboot",
            description = "Fastboot Mode မှ သာမန်ဖုန်းမျက်နှာပြင်သို့ Reboot ချသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "uni_fb_svc_reboot_bl",
            title = "Reboot Back to Bootloader",
            command = "fastboot reboot-bootloader",
            description = "Fastboot Bootloader Mode သို့ ပြန်လည် Reboot ချသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "uni_fb_svc_reboot_rec",
            title = "Reboot to Recovery Mode",
            command = "fastboot reboot recovery",
            description = "Fastboot မှ Recovery Mode သို့ တိုက်ရိုက် ဝင်ရောက်သည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "uni_fb_svc_continue",
            title = "Continue Normal System Boot",
            command = "fastboot continue",
            description = "ဖုန်းကို ပိတ်မချဘဲ လက်ရှိ Kernel မှ ဆက်လက် Boot တက်စေသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "uni_fb_svc_edl",
            title = "Reboot Fastboot to Qualcomm EDL 9008",
            command = "fastboot oem edl",
            description = "Fastboot Mode မှ Qualcomm Emergency Download (9008) သို့ ဝင်ရောက်သည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.SERVICE
        ),

        // --- ADB Info ---
        ServiceActionItem(
            id = "uni_adb_info_devices",
            title = "Check Connected ADB Devices",
            command = "adb devices -l",
            description = "ချိတ်ဆက်ထားသော ADB ဖုန်းများနှင့် မော်ဒယ်အချက်အလက်ကို စစ်ဆေးသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "uni_adb_info_model",
            title = "Read Device Model Name",
            command = "getprop ro.product.model",
            description = "Android ဗားရှင်းအားလုံးတွင် ဖုန်း၏ Model Name အတိအကျကို ဖတ်ယူသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "uni_adb_info_manufacturer",
            title = "Read Manufacturer & Brand",
            command = "getprop ro.product.manufacturer",
            description = "ဖုန်းထုတ်လုပ်သည့် Brand နှင့် Manufacturer အမည်ကို စစ်ဆေးသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "uni_adb_info_android_ver",
            title = "Read Android OS Version (5–16)",
            command = "getprop ro.build.version.release",
            description = "လက်ရှိ ဖုန်းတွင် တင်ထားသော Android Version ကို စစ်ဆေးသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "uni_adb_info_sdk",
            title = "Read Android API / SDK Level",
            command = "getprop ro.build.version.sdk",
            description = "Android API Level (ဥပမာ API 21=A5, API 35=A15, API 36=A16) ကို ဖတ်ယူသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "uni_adb_info_hardware",
            title = "Read Chipset & Hardware Platform",
            command = "getprop ro.board.platform",
            description = "Qualcomm / MediaTek / Exynos / Kirin SoC Platform ကို စစ်ဆေးသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "uni_adb_info_sec_patch",
            title = "Read Security Patch Level",
            command = "getprop ro.build.version.security_patch",
            description = "ဖုန်း၏ လက်ရှိ Android Security Patch ခုနှစ်နှင့် လကို ဖတ်ယူသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "uni_adb_info_avb",
            title = "Check Verified Boot State",
            command = "getprop ro.boot.verifiedbootstate",
            description = "Verified Boot အခြေအနေ (green = Locked, orange = Unlocked) ကို စစ်ဆေးသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "uni_adb_info_slot",
            title = "Check Active A/B Boot Slot (A7–A16)",
            command = "getprop ro.boot.slot_suffix",
            description = "လက်ရှိ Boot တက်ထားသော Slot (_a သို့မဟုတ် _b) ကို စစ်ဆေးသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "uni_adb_info_dyn_parts",
            title = "Check Dynamic Partitions Support (A10–A16)",
            command = "getprop ro.boot.dynamic_partitions",
            description = "Super / Dynamic Partition စနစ် ပါဝင်ခြင်း ရှိ/မရှိ စစ်ဆေးသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "uni_adb_info_battery",
            title = "Read Battery Health & Temperature",
            command = "dumpsys battery",
            description = "ဘက်ထရီ ရာခိုင်နှုန်း၊ ဗို့အားနှင့် အပူချိန်ကို စစ်ဆေးသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "uni_adb_info_disk",
            title = "Inspect Partition Mounts & Free Space",
            command = "df -h",
            description = "Partition တစ်ခုချင်းစီ၏ ပမာဏနှင့် နေရာလွတ်ကို စစ်ဆေးသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "uni_adb_info_uname",
            title = "Read Linux Kernel Version & Arch",
            command = "uname -a",
            description = "Linux Kernel Version နှင့် CPU Architecture (arm64/armv7) ကို ဖတ်ယူသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "uni_adb_info_getprop_all",
            title = "Read All System Properties",
            command = "getprop",
            description = "ဖုန်းတစ်ခုလုံး၏ System Properties အားလုံးကို ဖတ်ယူသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.INFO,
            isAdb = true
        ),

        // --- ADB Unlock ---
        ServiceActionItem(
            id = "uni_adb_unlock_settings",
            title = "FRP Helper: Open System Settings",
            command = "am start -n com.android.settings/com.android.settings.Settings",
            description = "Android ဗားရှင်းအားလုံးတွင် System Settings မျက်နှာပြင်ကို တိုက်ရိုက်ဖွင့်ပေးသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.UNLOCK,
            isAdb = true
        ),
        ServiceActionItem(
            id = "uni_adb_unlock_browser",
            title = "FRP Helper: Launch Web Browser",
            command = "am start -a android.intent.action.VIEW -d \"https://google.com\"",
            description = "ADB မှတစ်ဆင့် Browser ကို တိုက်ရိုက်ဖွင့်လှစ်ပေးသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.UNLOCK,
            isAdb = true
        ),
        ServiceActionItem(
            id = "uni_adb_unlock_add_account",
            title = "FRP Helper: Open Add Account Screen",
            command = "am start -a android.settings.ADD_ACCOUNT_SETTINGS",
            description = "Google/Account အသစ်ထည့်သွင်းသည့် မျက်နှာပြင်ကို တိုက်ရိုက်ဖွင့်ပေးသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.UNLOCK,
            isAdb = true
        ),
        ServiceActionItem(
            id = "uni_adb_unlock_accessibility",
            title = "FRP Helper: Open Accessibility Menu",
            command = "am start -a android.settings.ACCESSIBILITY_SETTINGS",
            description = "Accessibility Settings မျက်နှာပြင်ကို တိုက်ရိုက်ဖွင့်လှစ်ပေးသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.UNLOCK,
            isAdb = true
        ),
        ServiceActionItem(
            id = "uni_adb_unlock_dev_opts",
            title = "Open Developer Options (OEM Unlock)",
            command = "am start -a android.settings.APPLICATION_DEVELOPMENT_SETTINGS",
            description = "OEM Unlocking ဖွင့်ရန် Developer Options ကို တိုက်ရိုက်ဖွင့်ပေးသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.UNLOCK,
            isAdb = true
        ),
        ServiceActionItem(
            id = "uni_adb_unlock_provisioned",
            title = "Bypass Setup Wizard (Provisioned Flag)",
            command = "settings put global device_provisioned 1",
            description = "Setup Wizard ကျော်လွှားရန် device_provisioned flag ကို 1 သို့ သတ်မှတ်သည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.UNLOCK,
            isAdb = true
        ),
        ServiceActionItem(
            id = "uni_adb_unlock_user_setup",
            title = "Bypass User Setup Complete Flag",
            command = "settings put secure user_setup_complete 1",
            description = "Home Screen သို့ ဝင်ရောက်နိုင်ရန် user_setup_complete flag ကို 1 သို့ ပြောင်းသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.UNLOCK,
            isAdb = true
        ),
        ServiceActionItem(
            id = "uni_adb_unlock_legacy_content",
            title = "Legacy Setup Bypass (Content Insert A5–A9)",
            command = "content insert --uri content://settings/secure --bind name:s:user_setup_complete --bind value:s:1",
            description = "Android 5 မှ 9 အထိ ဖုန်းအဟောင်းများတွင် FRP Setup ကျော်လွှားရန် အသုံးပြုသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.UNLOCK,
            isAdb = true
        ),

        // --- ADB Service ---
        ServiceActionItem(
            id = "uni_adb_svc_pkg_all",
            title = "List All System & User Packages",
            command = "pm list packages",
            description = "ဖုန်းအတွင်းရှိ System နှင့် User App Package အားလုံးကို စာရင်းထုတ်ပြသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),
        ServiceActionItem(
            id = "uni_adb_svc_pkg_3rd",
            title = "List Third-Party User Apps Only",
            command = "pm list packages -3",
            description = "User ကိုယ်တိုင် သွင်းထားသော ပြင်ပ App များကိုသာ စစ်ဆေးသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),
        ServiceActionItem(
            id = "uni_adb_svc_pkg_disabled",
            title = "List Disabled Packages",
            command = "pm list packages -d",
            description = "Disable ပြုလုပ်ထားသော Package များစာရင်းကို စစ်ဆေးသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),
        ServiceActionItem(
            id = "uni_adb_svc_trim_cache",
            title = "Clear App Cache Storage (All Android)",
            command = "pm trim-caches 1024M",
            description = "Android ဗားရှင်းအားလုံးတွင် App Cache များကို ရှင်းလင်းပြီး နေရာလွတ်ထုတ်ပေးသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),
        ServiceActionItem(
            id = "uni_adb_svc_dexopt",
            title = "Run ART App Speed Optimization (A7–A16)",
            command = "cmd package bg-dexopt-job",
            description = "ဖုန်းပေါ့ပါးမြန်ဆန်စေရန် Android ART Background Dexopt ကို Run ပေးသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),
        ServiceActionItem(
            id = "uni_adb_svc_wm_reset",
            title = "Reset Screen DPI to Default",
            command = "wm density reset",
            description = "ပြောင်းလဲထားသော ဖုန်းမျက်နှာပြင် DPI ကို မူရင်းအတိုင်း ပြန်ထားသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),
        ServiceActionItem(
            id = "uni_adb_svc_reboot_sys",
            title = "Reboot Phone Normally",
            command = "adb reboot",
            description = "ADB Mode မှ ဖုန်းကို ပုံမှန်အတိုင်း Reboot ချသည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),
        ServiceActionItem(
            id = "uni_adb_svc_reboot_bl",
            title = "Reboot to Fastboot Bootloader",
            command = "adb reboot bootloader",
            description = "ADB Mode မှ Fastboot Bootloader သို့ တိုက်ရိုက် ဝင်ရောက်သည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),
        ServiceActionItem(
            id = "uni_adb_svc_reboot_fbd",
            title = "Reboot to Userspace Fastbootd (A10–A16)",
            command = "adb reboot fastboot",
            description = "ADB Mode မှ Userspace Fastbootd သို့ တိုက်ရိုက် ဝင်ရောက်သည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),
        ServiceActionItem(
            id = "uni_adb_svc_reboot_rec",
            title = "Reboot to Recovery Mode",
            command = "adb reboot recovery",
            description = "ADB Mode မှ System Recovery သို့ တိုက်ရိုက် ဝင်ရောက်သည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),
        ServiceActionItem(
            id = "uni_adb_svc_reboot_sideload",
            title = "Reboot to OTA Sideload Mode",
            command = "adb reboot sideload-auto-reboot",
            description = "ADB Sideload Mode သို့ တိုက်ရိုက် ဝင်ရောက်သည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),
        ServiceActionItem(
            id = "uni_adb_svc_reboot_edl",
            title = "Reboot to Qualcomm EDL 9008 Mode",
            command = "adb reboot edl",
            description = "ADB Mode မှ Qualcomm EDL 9008 Mode သို့ တိုက်ရိုက် ဝင်ရောက်သည်။",
            brand = ServiceBrand.UNIVERSAL,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),

        // =========================================================================
        // 2. XIAOMI / REDMI / POCO
        // =========================================================================
        ServiceActionItem(
            id = "xm_fb_info_device",
            title = "Read Xiaomi Bootloader Lock Status",
            command = "fastboot oem device-info",
            description = "Xiaomi ဖုန်း၏ Bootloader Unlock ဖြစ်/မဖြစ် နှင့် Critical Lock အခြေအနေကို ဖတ်သည်။",
            brand = ServiceBrand.XIAOMI,
            group = ActionGroup.INFO
        ),
        ServiceActionItem(
            id = "xm_fb_info_anti",
            title = "Check Anti-Rollback (ARB) Index",
            command = "fastboot getvar anti",
            description = "Xiaomi Firmware Downgrade လုပ်ရာတွင် Brick မဖြစ်စေရန် ARB Index ကို စစ်သည်။",
            brand = ServiceBrand.XIAOMI,
            group = ActionGroup.INFO
        ),
        ServiceActionItem(
            id = "xm_fb_info_product",
            title = "Read Xiaomi Board Codename",
            command = "fastboot getvar product",
            description = "Xiaomi Model Codename (ဥပမာ whyred, garnet, marble) ကို စစ်ဆေးသည်။",
            brand = ServiceBrand.XIAOMI,
            group = ActionGroup.INFO
        ),
        ServiceActionItem(
            id = "xm_fb_info_lks",
            title = "Check MediaTek LKS Lock State",
            command = "fastboot oem lks",
            description = "MTK Xiaomi/Redmi ဖုန်းများတွင် Bootloader LKS Lock အခြေအနေကို စစ်သည်။",
            brand = ServiceBrand.XIAOMI,
            group = ActionGroup.INFO
        ),
        ServiceActionItem(
            id = "xm_fb_unlock_token",
            title = "Read Mi Unlock Bootloader Token",
            command = "fastboot getvar token",
            description = "Xiaomi Bootloader Unlock လုပ်ရာတွင် လိုအပ်သော Mi Token ကို ထုတ်ယူသည်။",
            brand = ServiceBrand.XIAOMI,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "xm_fb_unlock_std",
            title = "Unlock Bootloader (Xiaomi / Poco)",
            command = "fastboot flashing unlock",
            description = "Xiaomi Bootloader ကို Fastboot Flashing Unlock ဖြင့် Unlock လုပ်သည်။",
            brand = ServiceBrand.XIAOMI,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "xm_fb_unlock_oem",
            title = "Unlock Bootloader (Legacy MIUI)",
            command = "fastboot oem unlock",
            description = "MIUI ဗားရှင်းအဟောင်း Xiaomi ဖုန်းများအား OEM Unlock ပြုလုပ်သည်။",
            brand = ServiceBrand.XIAOMI,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "xm_fb_unlock_lock",
            title = "Relock Xiaomi Bootloader",
            command = "fastboot oem lock",
            description = "Xiaomi Bootloader ကို မူလအတိုင်း ပြန်လည် Lock ခတ်သည်။",
            brand = ServiceBrand.XIAOMI,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "xm_fb_unlock_frp",
            title = "Erase Xiaomi FRP Partition",
            command = "fastboot erase frp",
            description = "Bootloader ပွင့်ပြီးသော Xiaomi ဖုန်းများတွင် FRP Partition ကို ရှင်းလင်းသည်။",
            brand = ServiceBrand.XIAOMI,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "xm_fb_svc_edl",
            title = "Switch Fastboot to Qualcomm EDL 9008",
            command = "fastboot oem edl",
            description = "Fastboot မှ Qualcomm EDL 9008 Mode သို့ တိုက်ရိုက် ပြောင်းသည်။",
            brand = ServiceBrand.XIAOMI,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "xm_fb_svc_wipe",
            title = "Wipe Userdata & Cache/Metadata",
            command = "fastboot -w",
            description = "Xiaomi User Data နှင့် Cache/Metadata ကို အပြီးရှင်းလင်းသည်။",
            brand = ServiceBrand.XIAOMI,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "xm_fb_svc_poweroff",
            title = "Power Off Phone from Fastboot",
            command = "fastboot oem poweroff",
            description = "Fastboot Mode အတွင်းမှ ဖုန်းပါဝါကို လုံးဝပိတ်ချသည်။",
            brand = ServiceBrand.XIAOMI,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "xm_fb_svc_reboot",
            title = "Reboot Xiaomi to System",
            command = "fastboot reboot",
            description = "Fastboot မှ သာမန်စနစ်သို့ Reboot ချသည်။",
            brand = ServiceBrand.XIAOMI,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "xm_adb_info_miui",
            title = "Read MIUI / HyperOS Version",
            command = "getprop ro.miui.ui.version.name",
            description = "ADB မှ Xiaomi ဖုန်း၏ MIUI သို့မဟုတ် HyperOS ဗားရှင်းကို ဖတ်ယူသည်။",
            brand = ServiceBrand.XIAOMI,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "xm_adb_info_region",
            title = "Read Xiaomi Region (CN / Global / EU)",
            command = "getprop ro.miui.region",
            description = "Xiaomi ဖုန်း၏ Region သတ်မှတ်ချက်ကို ဖတ်ယူသည်။",
            brand = ServiceBrand.XIAOMI,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "xm_adb_info_codename",
            title = "Read Xiaomi Market & Mod Device",
            command = "getprop ro.product.mod_device",
            description = "Xiaomi Device Codename နှင့် Regional ROM Variant ကို စစ်ဆေးသည်။",
            brand = ServiceBrand.XIAOMI,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "xm_adb_unlock_finddevice",
            title = "Disable Xiaomi Find Device Service",
            command = "pm disable-user --user 0 com.xiaomi.finddevice",
            description = "Xiaomi Find Device Background Service ကို ယာယီပိတ်ပင်သည်။",
            brand = ServiceBrand.XIAOMI,
            group = ActionGroup.UNLOCK,
            isAdb = true
        ),
        ServiceActionItem(
            id = "xm_adb_unlock_miaccount",
            title = "Disable Mi Account Cloud Service",
            command = "pm disable-user --user 0 com.xiaomi.account",
            description = "Mi Account Background Client ကို ယာယီပိတ်ပင်သည်။",
            brand = ServiceBrand.XIAOMI,
            group = ActionGroup.UNLOCK,
            isAdb = true
        ),
        ServiceActionItem(
            id = "xm_adb_unlock_settings",
            title = "Open MIUI Settings / Dev Options",
            command = "am start -a android.settings.APPLICATION_DEVELOPMENT_SETTINGS",
            description = "Mi Unlock Status ထည့်ရန် Developer Options ကို တိုက်ရိုက်ဖွင့်ပေးသည်။",
            brand = ServiceBrand.XIAOMI,
            group = ActionGroup.UNLOCK,
            isAdb = true
        ),
        ServiceActionItem(
            id = "xm_adb_svc_msa",
            title = "Disable MIUI / HyperOS System Ads (MSA)",
            command = "pm disable-user --user 0 com.miui.msa.global",
            description = "MIUI/HyperOS စနစ်အတွင်းရှိ ကြော်ငြာဝန်ဆောင်မှု (MSA) ကို ပိတ်ပင်သည်။",
            brand = ServiceBrand.XIAOMI,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),
        ServiceActionItem(
            id = "xm_adb_svc_analytics",
            title = "Disable Xiaomi Analytics Tracker",
            command = "pm disable-user --user 0 com.miui.analytics",
            description = "နောက်ခံတွင် အလုပ်လုပ်နေသော Xiaomi Analytics ကို ပိတ်ပင်သည်။",
            brand = ServiceBrand.XIAOMI,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),
        ServiceActionItem(
            id = "xm_adb_svc_daemon",
            title = "Disable MIUI Background Daemon",
            command = "pm disable-user --user 0 com.miui.daemon",
            description = "MIUI System Daemon Service ကို ပိတ်ပင်ပြီး ဘက်ထရီသက်သာစေသည်။",
            brand = ServiceBrand.XIAOMI,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),
        ServiceActionItem(
            id = "xm_adb_svc_reboot_edl",
            title = "Reboot Xiaomi to EDL 9008",
            command = "adb reboot edl",
            description = "ADB Mode မှ Xiaomi Qualcomm EDL 9008 Mode သို့ ဝင်ရောက်သည်။",
            brand = ServiceBrand.XIAOMI,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),

        // =========================================================================
        // 3. HUAWEI / HONOR
        // =========================================================================
        ServiceActionItem(
            id = "hw_fb_info_bootinfo",
            title = "Read Bootloader & FRP Lock Status",
            command = "fastboot oem get-bootinfo",
            description = "Huawei/Honor ဖုန်း၏ Bootloader Lock နှင့် FRP Lock အခြေအနေကို စစ်ဆေးသည်။",
            brand = ServiceBrand.HUAWEI,
            group = ActionGroup.INFO
        ),
        ServiceActionItem(
            id = "hw_fb_info_build",
            title = "Read Firmware Build Number",
            command = "fastboot oem get-build-number",
            description = "Huawei Firmware Build ဗားရှင်းအပြည့်အစုံကို ဖတ်ယူသည်။",
            brand = ServiceBrand.HUAWEI,
            group = ActionGroup.INFO
        ),
        ServiceActionItem(
            id = "hw_fb_info_model",
            title = "Read Exact Product Model",
            command = "fastboot oem get-product-model",
            description = "Huawei/Honor မော်ဒယ်နံပါတ်အမှန်ကို Fastboot မှ ဖတ်ယူသည်။",
            brand = ServiceBrand.HUAWEI,
            group = ActionGroup.INFO
        ),
        ServiceActionItem(
            id = "hw_fb_info_psid",
            title = "Read Serial & IMEI PSID Info",
            command = "fastboot oem get-psid",
            description = "Serial Number နှင့် IMEI/MEID PSID အချက်အလက်ကို ဖတ်ယူသည်။",
            brand = ServiceBrand.HUAWEI,
            group = ActionGroup.INFO
        ),
        ServiceActionItem(
            id = "hw_fb_info_cust",
            title = "Read Vendor & Country Cust Version",
            command = "fastboot oem oeminforead-CUSTOM_VERSION",
            description = "Firmware Region/Cust Version (C432, C636 စသည်) ကို စစ်ဆေးသည်။",
            brand = ServiceBrand.HUAWEI,
            group = ActionGroup.INFO
        ),
        ServiceActionItem(
            id = "hw_fb_unlock_backdoor",
            title = "Check Factory FBlock / Backdoor State",
            command = "fastboot oem backdoor info",
            description = "HiSilicon Kirin ဖုန်းများတွင် FBlock (Factory Backdoor) အခြေအနေ စစ်ဆေးသည်။",
            brand = ServiceBrand.HUAWEI,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "hw_fb_unlock_oem",
            title = "Huawei Bootloader OEM Unlock",
            command = "fastboot oem unlock",
            description = "Huawei Bootloader Unlock Code/Session ရှိပါက Unlock ပြုလုပ်သည်။",
            brand = ServiceBrand.HUAWEI,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "hw_fb_unlock_relock",
            title = "Huawei Bootloader OEM Relock",
            command = "fastboot oem relock",
            description = "Huawei Bootloader ကို ပြန်လည် Lock ခတ်သည်။",
            brand = ServiceBrand.HUAWEI,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "hw_fb_unlock_frp",
            title = "Erase Huawei FRP Partition",
            command = "fastboot erase frp",
            description = "Huawei Fastboot FRP Partition ကို ရှင်းလင်းသည်။",
            brand = ServiceBrand.HUAWEI,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "hw_fb_svc_erecovery",
            title = "Reboot to Huawei eRecovery Mode",
            command = "fastboot oem reboot-erecovery",
            description = "Fastboot မှ Huawei eRecovery Mode သို့ တိုက်ရိုက် ဝင်ရောက်သည်။",
            brand = ServiceBrand.HUAWEI,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "hw_fb_svc_wipe",
            title = "Erase Userdata Partition",
            command = "fastboot erase userdata",
            description = "Huawei Userdata Partition ကို ဖျက်ထုတ်သည်။",
            brand = ServiceBrand.HUAWEI,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "hw_fb_svc_reboot",
            title = "Reboot Huawei Normally",
            command = "fastboot reboot",
            description = "Fastboot မှ သာမန်စနစ်သို့ ပြန်လည် Reboot ချသည်။",
            brand = ServiceBrand.HUAWEI,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "hw_adb_info_emui",
            title = "Read EMUI / MagicOS Version",
            command = "getprop ro.build.version.emui",
            description = "Huawei/Honor ဖုန်း၏ EMUI သို့မဟုတ် MagicOS ဗားရှင်းကို ဖတ်ယူသည်။",
            brand = ServiceBrand.HUAWEI,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "hw_adb_info_chipset",
            title = "Read Kirin / SoC Platform Hardware",
            command = "getprop ro.hardware",
            description = "HiSilicon Kirin သို့မဟုတ် Snapdragon Chipset ကို စစ်ဆေးသည်။",
            brand = ServiceBrand.HUAWEI,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "hw_adb_info_sysver",
            title = "Read Huawei System Base Version",
            command = "getprop ro.comp.hl.product_base_version",
            description = "HarmonyOS / EMUI Base Package Version ကို ဖတ်ယူသည်။",
            brand = ServiceBrand.HUAWEI,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "hw_adb_unlock_hwsettings",
            title = "Open Huawei System Settings",
            command = "am start -n com.android.settings/com.android.settings.HWSettings",
            description = "Huawei/EMUI System Settings မျက်နှာပြင်ကို တိုက်ရိုက်ဖွင့်ပေးသည်။",
            brand = ServiceBrand.HUAWEI,
            group = ActionGroup.UNLOCK,
            isAdb = true
        ),
        ServiceActionItem(
            id = "hw_adb_unlock_setup",
            title = "Bypass Huawei Setup Wizard Flag",
            command = "settings put secure user_setup_complete 1",
            description = "EMUI/HarmonyOS Setup Wizard ကျော်လွှားနိုင်ရန် Setup Flag ပြောင်းပေးသည်။",
            brand = ServiceBrand.HUAWEI,
            group = ActionGroup.UNLOCK,
            isAdb = true
        ),
        ServiceActionItem(
            id = "hw_adb_svc_ota",
            title = "Disable Huawei System OTA Updater",
            command = "pm disable-user --user 0 com.huawei.android.hwouc",
            description = "Huawei Software Update (HWOUC) အလိုအလျောက်တက်ခြင်းကို ပိတ်ပင်သည်။",
            brand = ServiceBrand.HUAWEI,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),
        ServiceActionItem(
            id = "hw_adb_svc_appgallery",
            title = "Disable Huawei AppGallery Background",
            command = "pm disable-user --user 0 com.huawei.appmarket",
            description = "Huawei AppGallery နောက်ခံဝန်ဆောင်မှုကို ယာယီပိတ်ပင်သည်။",
            brand = ServiceBrand.HUAWEI,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),
        ServiceActionItem(
            id = "hw_adb_svc_reboot_bl",
            title = "Reboot Huawei to Fastboot",
            command = "adb reboot bootloader",
            description = "ADB Mode မှ Huawei Fastboot Mode သို့ ဝင်ရောက်သည်။",
            brand = ServiceBrand.HUAWEI,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),

        // =========================================================================
        // 4. VIVO / IQOO (BBK)
        // =========================================================================
        ServiceActionItem(
            id = "vv_fb_info_all",
            title = "Read Vivo Bootloader Variables",
            command = "fastboot getvar all",
            description = "Vivo/iQOO Bootloader အချက်အလက်အားလုံးကို ဖတ်ယူသည်။",
            brand = ServiceBrand.VIVO,
            group = ActionGroup.INFO
        ),
        ServiceActionItem(
            id = "vv_fb_info_unlocked",
            title = "Check Vivo Bootloader Lock Status",
            command = "fastboot getvar unlocked",
            description = "Vivo Bootloader Unlock ဖြစ်/မဖြစ် စစ်ဆေးသည်။",
            brand = ServiceBrand.VIVO,
            group = ActionGroup.INFO
        ),
        ServiceActionItem(
            id = "vv_fb_info_product",
            title = "Read Vivo Board Product Model",
            command = "fastboot getvar product",
            description = "Vivo/iQOO Product Board Name ကို စစ်ဆေးသည်။",
            brand = ServiceBrand.VIVO,
            group = ActionGroup.INFO
        ),
        ServiceActionItem(
            id = "vv_fb_unlock_bbk",
            title = "BBK Bootloader Unlock (Classic Vivo)",
            command = "fastboot bbk unlock_vivo",
            description = "Vivo ဖုန်းများအား BBK OEM Command ဖြင့် Bootloader Unlock ပြုလုပ်သည်။",
            brand = ServiceBrand.VIVO,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "vv_fb_unlock_bbk_lock",
            title = "BBK Bootloader Relock (Classic Vivo)",
            command = "fastboot bbk lock_vivo",
            description = "Vivo ဖုန်းများအား BBK OEM Command ဖြင့် Bootloader ပြန်ပိတ်သည်။",
            brand = ServiceBrand.VIVO,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "vv_fb_unlock_bsp",
            title = "Vivo BSP Bootloader Unlock (Newer)",
            command = "fastboot vivo_bsp unlock_vivo",
            description = "Vivo မော်ဒယ်အသစ်များတွင် vivo_bsp Command ဖြင့် Bootloader Unlock လုပ်သည်။",
            brand = ServiceBrand.VIVO,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "vv_fb_unlock_bsp_lock",
            title = "Vivo BSP Bootloader Relock (Newer)",
            command = "fastboot vivo_bsp lock_vivo",
            description = "Vivo မော်ဒယ်အသစ်များတွင် vivo_bsp Command ဖြင့် Bootloader ပြန်ပိတ်သည်။",
            brand = ServiceBrand.VIVO,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "vv_fb_svc_edl",
            title = "Switch Fastboot to Qualcomm EDL Mode",
            command = "fastboot oem reboot-edl",
            description = "Vivo Fastboot Mode မှ Qualcomm 9008 EDL Mode သို့ ပြောင်းလဲသည်။",
            brand = ServiceBrand.VIVO,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "vv_fb_svc_wipe",
            title = "Wipe Vivo Userdata & Metadata",
            command = "fastboot -w",
            description = "Vivo Userdata နှင့် Metadata ကို ရှင်းလင်းသည်။",
            brand = ServiceBrand.VIVO,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "vv_fb_svc_reboot",
            title = "Reboot Vivo to System",
            command = "fastboot reboot",
            description = "Fastboot မှ သာမန်စနစ်သို့ Reboot ချသည်။",
            brand = ServiceBrand.VIVO,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "vv_adb_info_os",
            title = "Read FuntouchOS / OriginOS Version",
            command = "getprop ro.vivo.os.build.display.id",
            description = "Vivo ဖုန်း၏ FuntouchOS သို့မဟုတ် OriginOS Build ကို ဖတ်ယူသည်။",
            brand = ServiceBrand.VIVO,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "vv_adb_info_model",
            title = "Read Vivo Internal Product Model",
            command = "getprop ro.vivo.product.model",
            description = "Vivo/iQOO Product Model အမှန်ကို ADB မှ စစ်ဆေးသည်။",
            brand = ServiceBrand.VIVO,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "vv_adb_info_android",
            title = "Read Vivo Android OS Release",
            command = "getprop ro.build.version.release",
            description = "Vivo Android Version (5 မှ 16) ကို စစ်ဆေးသည်။",
            brand = ServiceBrand.VIVO,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "vv_adb_unlock_settings",
            title = "Open Vivo System Settings (FRP)",
            command = "am start -n com.android.settings/com.android.settings.Settings",
            description = "Vivo ဖုန်းတွင် Settings မျက်နှာပြင်ကို တိုက်ရိုက်ဖွင့်ပေးသည်။",
            brand = ServiceBrand.VIVO,
            group = ActionGroup.UNLOCK,
            isAdb = true
        ),
        ServiceActionItem(
            id = "vv_adb_unlock_account",
            title = "Disable Vivo Account Client",
            command = "pm disable-user --user 0 com.vivo.account",
            description = "Vivo Account Background Client ကို ယာယီပိတ်ပင်သည်။",
            brand = ServiceBrand.VIVO,
            group = ActionGroup.UNLOCK,
            isAdb = true
        ),
        ServiceActionItem(
            id = "vv_adb_svc_ota",
            title = "Disable Vivo / BBK System Updater",
            command = "pm disable-user --user 0 com.bbk.updater",
            description = "Vivo အလိုအလျောက် OTA Update တက်ခြင်းကို ပိတ်ပင်သည်။",
            brand = ServiceBrand.VIVO,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),
        ServiceActionItem(
            id = "vv_adb_svc_appstore",
            title = "Disable V-Appstore Background",
            command = "pm disable-user --user 0 com.vivo.appstore",
            description = "Vivo V-Appstore နောက်ခံဝန်ဆောင်မှုကို ပိတ်ပင်သည်။",
            brand = ServiceBrand.VIVO,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),
        ServiceActionItem(
            id = "vv_adb_svc_reboot_bl",
            title = "Reboot Vivo to Fastboot Mode",
            command = "adb reboot bootloader",
            description = "ADB မှ Vivo Fastboot Mode သို့ ဝင်ရောက်သည်။",
            brand = ServiceBrand.VIVO,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),

        // =========================================================================
        // 5. OPPO / REALME / ONEPLUS
        // =========================================================================
        ServiceActionItem(
            id = "op_fb_info_device",
            title = "Read OEM Bootloader Lock State",
            command = "fastboot oem device-info",
            description = "Oppo/Realme/OnePlus Bootloader Lock အခြေအနေကို စစ်ဆေးသည်။",
            brand = ServiceBrand.OPPO_ONEPLUS,
            group = ActionGroup.INFO
        ),
        ServiceActionItem(
            id = "op_fb_info_serial",
            title = "Read PCBA / Chip Serial Number",
            command = "fastboot getvar serialno",
            description = "Motherboard Serial Number ကို Fastboot မှ ဖတ်ယူသည်။",
            brand = ServiceBrand.OPPO_ONEPLUS,
            group = ActionGroup.INFO
        ),
        ServiceActionItem(
            id = "op_fb_info_userspace",
            title = "Check Oplus Fastbootd Userspace",
            command = "fastboot getvar is-userspace",
            description = "ColorOS/OxygenOS Userspace Fastbootd Mode ဟုတ်/မဟုတ် စစ်ဆေးသည်။",
            brand = ServiceBrand.OPPO_ONEPLUS,
            group = ActionGroup.INFO
        ),
        ServiceActionItem(
            id = "op_fb_unlock_std",
            title = "Unlock Bootloader (OnePlus / Realme)",
            command = "fastboot flashing unlock",
            description = "OnePlus နှင့် Realme ဖုန်းများတွင် Bootloader Unlock ပြုလုပ်သည်။",
            brand = ServiceBrand.OPPO_ONEPLUS,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "op_fb_unlock_crit",
            title = "Unlock Critical Partitions (Oplus)",
            command = "fastboot flashing unlock_critical",
            description = "OnePlus/Oppo Critical Partition များကို Unlock လုပ်သည်။",
            brand = ServiceBrand.OPPO_ONEPLUS,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "op_fb_unlock_oem",
            title = "Unlock Bootloader (Legacy Oplus)",
            command = "fastboot oem unlock",
            description = "ColorOS/OxygenOS ဗားရှင်းအဟောင်းများကို OEM Unlock ပြုလုပ်သည်။",
            brand = ServiceBrand.OPPO_ONEPLUS,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "op_fb_unlock_lock",
            title = "Relock Oplus Bootloader",
            command = "fastboot flashing lock",
            description = "Bootloader ကို မူလအတိုင်း ပြန်လည် Lock ခတ်သည်။",
            brand = ServiceBrand.OPPO_ONEPLUS,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "op_fb_svc_wipe",
            title = "Wipe Userdata & Metadata",
            command = "fastboot -w",
            description = "Userdata နှင့် FBE Metadata ကို အပြီးရှင်းလင်းသည်။",
            brand = ServiceBrand.OPPO_ONEPLUS,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "op_fb_svc_fastbootd",
            title = "Enter Userspace Fastbootd (A10–A16)",
            command = "fastboot reboot fastboot",
            description = "Dynamic Super Partition ရေးသားရန် Fastbootd သို့ ပြောင်းသည်။",
            brand = ServiceBrand.OPPO_ONEPLUS,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "op_fb_svc_poweroff",
            title = "Power Off Phone from Bootloader",
            command = "fastboot oem poweroff",
            description = "Bootloader Mode မှ ဖုန်းပါဝါကို တိုက်ရိုက် ပိတ်ချသည်။",
            brand = ServiceBrand.OPPO_ONEPLUS,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "op_fb_svc_reboot",
            title = "Reboot Oplus to System",
            command = "fastboot reboot",
            description = "Fastboot မှ သာမန်စနစ်သို့ Reboot ချသည်။",
            brand = ServiceBrand.OPPO_ONEPLUS,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "op_adb_info_coloros",
            title = "Read ColorOS / RealmeUI / OxygenOS",
            command = "getprop ro.build.version.oplusrom",
            description = "Oplus ROM (ColorOS/RealmeUI/OxygenOS) ဗားရှင်းကို ဖတ်ယူသည်။",
            brand = ServiceBrand.OPPO_ONEPLUS,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "op_adb_info_region",
            title = "Read NV Pipeline Region Code",
            command = "getprop ro.oplus.pipeline.region",
            description = "Oppo/Realme/OnePlus NV Region သတ်မှတ်ချက်ကို စစ်ဆေးသည်။",
            brand = ServiceBrand.OPPO_ONEPLUS,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "op_adb_info_prj",
            title = "Read Oplus Project Code (prjname)",
            command = "getprop ro.boot.prjname",
            description = "Firmware Project Number အတိအကျကို စစ်ဆေးသည်။",
            brand = ServiceBrand.OPPO_ONEPLUS,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "op_adb_unlock_settings",
            title = "Open ColorOS / Oplus Settings (FRP)",
            command = "am start -n com.android.settings/com.android.settings.Settings",
            description = "Oppo/Realme/OnePlus Settings မျက်နှာပြင်ကို တိုက်ရိုက်ဖွင့်ပေးသည်။",
            brand = ServiceBrand.OPPO_ONEPLUS,
            group = ActionGroup.UNLOCK,
            isAdb = true
        ),
        ServiceActionItem(
            id = "op_adb_unlock_usercenter",
            title = "Disable HeyTap Account Center",
            command = "pm disable-user --user 0 com.heytap.usercenter",
            description = "HeyTap/Oppo Account Background Service ကို ပိတ်ပင်သည်။",
            brand = ServiceBrand.OPPO_ONEPLUS,
            group = ActionGroup.UNLOCK,
            isAdb = true
        ),
        ServiceActionItem(
            id = "op_adb_svc_heytap",
            title = "Disable HeyTap Cloud System Service",
            command = "pm disable-user --user 0 com.heytap.cloud",
            description = "HeyTap Cloud နောက်ခံ စနစ်ဝန်ဆောင်မှုကို ပိတ်ပင်သည်။",
            brand = ServiceBrand.OPPO_ONEPLUS,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),
        ServiceActionItem(
            id = "op_adb_svc_ota",
            title = "Disable Oppo / Realme OTA Updater",
            command = "pm disable-user --user 0 com.oppo.ota",
            description = "ColorOS အလိုအလျောက် OTA Software Update ကို ပိတ်ပင်သည်။",
            brand = ServiceBrand.OPPO_ONEPLUS,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),
        ServiceActionItem(
            id = "op_adb_svc_reboot_bl",
            title = "Reboot Oplus to Fastboot",
            command = "adb reboot bootloader",
            description = "ADB Mode မှ Bootloader/Fastboot Mode သို့ ဝင်ရောက်သည်။",
            brand = ServiceBrand.OPPO_ONEPLUS,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),

        // =========================================================================
        // 6. SAMSUNG GALAXY
        // =========================================================================
        ServiceActionItem(
            id = "sam_fb_info_userspace",
            title = "Check Samsung Fastbootd Status (A10–A16)",
            command = "fastboot getvar is-userspace",
            description = "Samsung Recovery Fastbootd Mode အလုပ်လုပ်နေခြင်း ရှိ/မရှိ စစ်ဆေးသည်။",
            brand = ServiceBrand.SAMSUNG,
            group = ActionGroup.INFO
        ),
        ServiceActionItem(
            id = "sam_fb_info_product",
            title = "Read Samsung Product & Slot Info",
            command = "fastboot getvar all",
            description = "Samsung Fastbootd မှ Partition နှင့် Product အချက်အလက်ကို ဖတ်ယူသည်။",
            brand = ServiceBrand.SAMSUNG,
            group = ActionGroup.INFO
        ),
        ServiceActionItem(
            id = "sam_fb_unlock_ability",
            title = "Check Samsung OEM Unlock Flag",
            command = "fastboot flashing get_unlock_ability",
            description = "Fastbootd မှ OEM Unlock ခွင့်ပြုချက် Flag ကို စစ်ဆေးသည်။",
            brand = ServiceBrand.SAMSUNG,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "sam_fb_unlock_std",
            title = "Fastbootd Flashing Unlock",
            command = "fastboot flashing unlock",
            description = "Supported Samsung Fastbootd ဖုန်းများတွင် Unlock Command ပေးပို့သည်။",
            brand = ServiceBrand.SAMSUNG,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "sam_fb_svc_rec",
            title = "Reboot from Fastbootd to Recovery",
            command = "fastboot reboot recovery",
            description = "Samsung Fastbootd Mode မှ Recovery Mode သို့ ပြန်ဝင်သည်။",
            brand = ServiceBrand.SAMSUNG,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "sam_fb_svc_wipe",
            title = "Erase Samsung Userdata (Fastbootd)",
            command = "fastboot erase userdata",
            description = "Fastbootd မှ Samsung Userdata ကို ရှင်းလင်းသည်။",
            brand = ServiceBrand.SAMSUNG,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "sam_fb_svc_reboot",
            title = "Reboot Samsung to System",
            command = "fastboot reboot",
            description = "Fastbootd မှ သာမန်ဖုန်းမျက်နှာပြင်သို့ ပြန်ဝင်သည်။",
            brand = ServiceBrand.SAMSUNG,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "sam_adb_info_csc",
            title = "Read Active CSC Country Code",
            command = "getprop ro.csc.sales_code",
            description = "Samsung ဖုန်း၏ လက်ရှိ CSC Region Code (THL, XXV, EUX, XSG) ကို စစ်ဆေးသည်။",
            brand = ServiceBrand.SAMSUNG,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "sam_adb_info_bl",
            title = "Read Bootloader & Binary Bit (U/S)",
            command = "getprop ro.boot.bootloader",
            description = "Firmware တင်ရာတွင် အရေးကြီးသော Samsung Bootloader Binary Version ကို စစ်သည်။",
            brand = ServiceBrand.SAMSUNG,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "sam_adb_info_knox",
            title = "Check Knox Warranty Bit (0x0 / 0x1)",
            command = "getprop ro.boot.warranty_bit",
            description = "Samsung Knox Warranty Bit အခြေအနေကို စစ်ဆေးသည်။",
            brand = ServiceBrand.SAMSUNG,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "sam_adb_info_pda",
            title = "Read Samsung PDA Firmware Build",
            command = "getprop ro.build.PDA",
            description = "Samsung Official AP/PDA Build Version ကို ဖတ်ယူသည်။",
            brand = ServiceBrand.SAMSUNG,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "sam_adb_unlock_settings",
            title = "Open Samsung Settings (FRP Helper)",
            command = "am start -n com.android.settings/com.android.settings.Settings",
            description = "Samsung FRP Screen မှ Settings ကို တိုက်ရိုက်ဖွင့်ပေးသည်။",
            brand = ServiceBrand.SAMSUNG,
            group = ActionGroup.UNLOCK,
            isAdb = true
        ),
        ServiceActionItem(
            id = "sam_adb_unlock_launcher",
            title = "Launch OneUI / TouchWiz Home",
            command = "am start -n com.sec.android.app.launcher/.activities.LauncherActivity",
            description = "Samsung Home Launcher မျက်နှာပြင်ကို တိုက်ရိုက်ဖွင့်လှစ်ပေးသည်။",
            brand = ServiceBrand.SAMSUNG,
            group = ActionGroup.UNLOCK,
            isAdb = true
        ),
        ServiceActionItem(
            id = "sam_adb_unlock_kgclient",
            title = "Disable Knox Guard Client (KG)",
            command = "pm disable-user --user 0 com.samsung.android.kgclient",
            description = "Samsung Knox Guard (KG) Background Service ကို ပိတ်ပင်သည်။",
            brand = ServiceBrand.SAMSUNG,
            group = ActionGroup.UNLOCK,
            isAdb = true
        ),
        ServiceActionItem(
            id = "sam_adb_svc_download",
            title = "Reboot to Odin Download Mode",
            command = "adb reboot download",
            description = "ခလုတ်နှိပ်စရာမလိုဘဲ Samsung Odin Download Mode သို့ တိုက်ရိုက်ဝင်သည်။",
            brand = ServiceBrand.SAMSUNG,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),
        ServiceActionItem(
            id = "sam_adb_svc_ota",
            title = "Disable Samsung OTA Software Update",
            command = "pm disable-user --user 0 com.wssyncmldm",
            description = "Samsung အလိုအလျောက် System Update (OTA) တက်ခြင်းကို ပိတ်ပင်သည်။",
            brand = ServiceBrand.SAMSUNG,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),
        ServiceActionItem(
            id = "sam_adb_svc_soagent",
            title = "Disable Samsung SOAgent Update",
            command = "pm disable-user --user 0 com.sec.android.soagent",
            description = "Samsung Security/Software Update Agent ကို ပိတ်ပင်သည်။",
            brand = ServiceBrand.SAMSUNG,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),
        ServiceActionItem(
            id = "sam_adb_svc_recovery",
            title = "Reboot Samsung to Recovery Mode",
            command = "adb reboot recovery",
            description = "ADB Mode မှ Samsung Recovery Mode သို့ ဝင်ရောက်သည်။",
            brand = ServiceBrand.SAMSUNG,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),

        // =========================================================================
        // 7. MOTOROLA / GOOGLE PIXEL
        // =========================================================================
        ServiceActionItem(
            id = "moto_fb_info_cid",
            title = "Read Motorola Carrier ID (CID)",
            command = "fastboot getvar cid",
            description = "Motorola ဖုန်း၏ Carrier ID (CID) နှင့် Region အချက်အလက်ကို စစ်ဆေးသည်။",
            brand = ServiceBrand.MOTO_PIXEL,
            group = ActionGroup.INFO
        ),
        ServiceActionItem(
            id = "pixel_fb_info_bl",
            title = "Read Pixel / Moto Bootloader Version",
            command = "fastboot getvar version-bootloader",
            description = "Google Pixel / Motorola Bootloader ဗားရှင်းအတိအကျကို စစ်ဆေးသည်။",
            brand = ServiceBrand.MOTO_PIXEL,
            group = ActionGroup.INFO
        ),
        ServiceActionItem(
            id = "pixel_fb_info_slot",
            title = "Read Active A/B Slot (Pixel/Moto)",
            command = "fastboot getvar current-slot",
            description = "Pixel နှင့် Motorola A/B Active Boot Slot ကို စစ်ဆေးသည်။",
            brand = ServiceBrand.MOTO_PIXEL,
            group = ActionGroup.INFO
        ),
        ServiceActionItem(
            id = "moto_fb_unlock_data",
            title = "Read Motorola OEM Unlock Data String",
            command = "fastboot oem get_unlock_data",
            description = "Motorola တရားဝင် Bootloader Unlock Key တောင်းရန် Unlock Data ထုတ်ယူသည်။",
            brand = ServiceBrand.MOTO_PIXEL,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "pixel_fb_unlock_std",
            title = "Unlock Bootloader (Pixel / Moto A6–A16)",
            command = "fastboot flashing unlock",
            description = "Google Pixel နှင့် Motorola Bootloader ကို Unlock ပြုလုပ်သည်။",
            brand = ServiceBrand.MOTO_PIXEL,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "pixel_fb_unlock_crit",
            title = "Unlock Critical Partitions (Pixel)",
            command = "fastboot flashing unlock_critical",
            description = "Google Pixel Critical Partition များကို Unlock လုပ်သည်။",
            brand = ServiceBrand.MOTO_PIXEL,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "moto_fb_unlock_oem",
            title = "Unlock Bootloader (Legacy Moto A5–A8)",
            command = "fastboot oem unlock",
            description = "Motorola ဖုန်းအဟောင်းများကို OEM Unlock ပြုလုပ်သည်။",
            brand = ServiceBrand.MOTO_PIXEL,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "pixel_fb_unlock_lock",
            title = "Relock Bootloader (Pixel / Moto)",
            command = "fastboot flashing lock",
            description = "Bootloader ကို မူလအတိုင်း ပြန်လည် Lock ခတ်သည်။",
            brand = ServiceBrand.MOTO_PIXEL,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "moto_fb_svc_mode_set",
            title = "Enable Motorola Fastboot Factory Mode",
            command = "fastboot oem fb_mode_set",
            description = "Motorola Firmware တင်ရာတွင် Fastboot ၌ ဆက်နေစေရန် Factory Mode ဖွင့်သည်။",
            brand = ServiceBrand.MOTO_PIXEL,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "moto_fb_svc_mode_clear",
            title = "Fix Fastboot Loop (fb_mode_clear)",
            command = "fastboot oem fb_mode_clear",
            description = "ဖုန်းဖွင့်တိုင်း Fastboot သို့သာ ပြန်ရောက်နေသော Motorola Loop ကို ဖြေရှင်းသည်။",
            brand = ServiceBrand.MOTO_PIXEL,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "pixel_fb_svc_wipe",
            title = "Wipe Userdata & Metadata",
            command = "fastboot -w",
            description = "Pixel/Motorola Userdata နှင့် Metadata ကို ရှင်းလင်းသည်။",
            brand = ServiceBrand.MOTO_PIXEL,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "pixel_fb_svc_reboot",
            title = "Reboot Pixel / Moto to System",
            command = "fastboot reboot",
            description = "Fastboot မှ သာမန်စနစ်သို့ Reboot ချသည်။",
            brand = ServiceBrand.MOTO_PIXEL,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "moto_pixel_adb_info_sku",
            title = "Read Carrier & Hardware SKU",
            command = "getprop ro.boot.hardware.sku",
            description = "Pixel/Motorola ဖုန်း၏ Hardware SKU မော်ဒယ်ကို စစ်ဆေးသည်။",
            brand = ServiceBrand.MOTO_PIXEL,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "moto_pixel_adb_info_fp",
            title = "Read Official Build Fingerprint",
            command = "getprop ro.build.fingerprint",
            description = "Pixel/Motorola Official Firmware Build Fingerprint ကို ဖတ်ယူသည်။",
            brand = ServiceBrand.MOTO_PIXEL,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "moto_pixel_adb_unlock_dev",
            title = "Open Developer Options (OEM Unlock)",
            command = "am start -a android.settings.APPLICATION_DEVELOPMENT_SETTINGS",
            description = "Pixel/Motorola OEM Unlocking ဖွင့်ရန် Developer Options ဖွင့်ပေးသည်။",
            brand = ServiceBrand.MOTO_PIXEL,
            group = ActionGroup.UNLOCK,
            isAdb = true
        ),
        ServiceActionItem(
            id = "moto_pixel_adb_unlock_setup",
            title = "Bypass Setup Wizard Flag",
            command = "settings put global device_provisioned 1",
            description = "Pixel/Motorola Setup Wizard ကျော်လွှားရန် Flag သတ်မှတ်ပေးသည်။",
            brand = ServiceBrand.MOTO_PIXEL,
            group = ActionGroup.UNLOCK,
            isAdb = true
        ),
        ServiceActionItem(
            id = "moto_adb_svc_ota",
            title = "Disable Motorola System OTA Updater",
            command = "pm disable-user --user 0 com.motorola.ccc.ota",
            description = "Motorola System OTA Update ဝန်ဆောင်မှုကို ပိတ်ပင်သည်။",
            brand = ServiceBrand.MOTO_PIXEL,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),
        ServiceActionItem(
            id = "moto_pixel_adb_svc_bl",
            title = "Reboot Pixel / Moto to Bootloader",
            command = "adb reboot bootloader",
            description = "ADB မှ Fastboot Bootloader Mode သို့ ဝင်ရောက်သည်။",
            brand = ServiceBrand.MOTO_PIXEL,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),

        // =========================================================================
        // 8. INFINIX / TECNO / ITEL (TRANSSION)
        // =========================================================================
        ServiceActionItem(
            id = "trn_fb_info_ability",
            title = "Check Bootloader Unlock Permission",
            command = "fastboot flashing get_unlock_ability",
            description = "Infinix/Tecno/Itel ဖုန်းတွင် OEM Unlock ခွင့်ပြုချက် ရှိ/မရှိ စစ်ဆေးသည်။",
            brand = ServiceBrand.TRANSSION,
            group = ActionGroup.INFO
        ),
        ServiceActionItem(
            id = "trn_fb_info_all",
            title = "Read Transsion Bootloader Variables",
            command = "fastboot getvar all",
            description = "Infinix/Tecno/Itel Bootloader Partition နှင့် Lock State ကို ဖတ်ယူသည်။",
            brand = ServiceBrand.TRANSSION,
            group = ActionGroup.INFO
        ),
        ServiceActionItem(
            id = "trn_fb_unlock_std",
            title = "Unlock Transsion Bootloader (Modern)",
            command = "fastboot flashing unlock",
            description = "Infinix/Tecno/Itel Bootloader ကို Unlock ပြုလုပ်သည်။",
            brand = ServiceBrand.TRANSSION,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "trn_fb_unlock_oem",
            title = "Unlock Transsion Bootloader (Legacy)",
            command = "fastboot oem unlock",
            description = "Transsion ဖုန်းအဟောင်းများကို OEM Unlock ပြုလုပ်သည်။",
            brand = ServiceBrand.TRANSSION,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "trn_fb_unlock_frp",
            title = "Erase Transsion FRP Partition",
            command = "fastboot erase frp",
            description = "Infinix/Tecno/Itel FRP Partition ကို ရှင်းလင်းသည်။",
            brand = ServiceBrand.TRANSSION,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "trn_fb_svc_fastbootd",
            title = "Enter Fastbootd for Super Partition",
            command = "fastboot reboot fastboot",
            description = "Infinix/Tecno Super Partition ရေးသားရန် Fastbootd သို့ ပြောင်းသည်။",
            brand = ServiceBrand.TRANSSION,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "trn_fb_svc_wipe",
            title = "Wipe Userdata & Metadata",
            command = "fastboot -w",
            description = "Transsion Userdata နှင့် Metadata ကို အပြီးရှင်းလင်းသည်။",
            brand = ServiceBrand.TRANSSION,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "trn_fb_svc_reboot",
            title = "Reboot Transsion to System",
            command = "fastboot reboot",
            description = "Fastboot မှ သာမန်စနစ်သို့ Reboot ချသည်။",
            brand = ServiceBrand.TRANSSION,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "trn_adb_info_hios",
            title = "Read HiOS / XOS System Build",
            command = "getprop ro.build.display.id",
            description = "Infinix HiOS သို့မဟုတ် Tecno XOS Firmware Build ကို ဖတ်ယူသည်။",
            brand = ServiceBrand.TRANSSION,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "trn_adb_info_osver",
            title = "Read HiOS / XOS OS Version",
            command = "getprop ro.os.version.release",
            description = "Transsion HiOS/XOS Version ကို စစ်ဆေးသည်။",
            brand = ServiceBrand.TRANSSION,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "trn_adb_unlock_settings",
            title = "Open Transsion Settings (FRP)",
            command = "am start -n com.android.settings/com.android.settings.Settings",
            description = "Infinix/Tecno/Itel Settings မျက်နှာပြင်ကို တိုက်ရိုက်ဖွင့်ပေးသည်။",
            brand = ServiceBrand.TRANSSION,
            group = ActionGroup.UNLOCK,
            isAdb = true
        ),
        ServiceActionItem(
            id = "trn_adb_unlock_setup",
            title = "Bypass Transsion Setup Wizard Flag",
            command = "settings put secure user_setup_complete 1",
            description = "HiOS/XOS Setup Wizard ကျော်လွှားနိုင်ရန် Setup Flag သတ်မှတ်ပေးသည်။",
            brand = ServiceBrand.TRANSSION,
            group = ActionGroup.UNLOCK,
            isAdb = true
        ),
        ServiceActionItem(
            id = "trn_adb_svc_ota",
            title = "Disable Transsion System Update",
            command = "pm disable-user --user 0 com.transsion.systemupdate",
            description = "Infinix/Tecno OTA System Update ကို ပိတ်ပင်သည်။",
            brand = ServiceBrand.TRANSSION,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),
        ServiceActionItem(
            id = "trn_adb_svc_phoenix",
            title = "Disable Phoenix Browser Bloatware",
            command = "pm disable-user --user 0 com.transsion.phoenix",
            description = "Transsion ဖုန်းများတွင် ပါဝင်သော Phoenix Browser ကို ပိတ်ပင်သည်။",
            brand = ServiceBrand.TRANSSION,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),
        ServiceActionItem(
            id = "trn_adb_svc_bl",
            title = "Reboot Transsion to Bootloader",
            command = "adb reboot bootloader",
            description = "ADB Mode မှ Fastboot Bootloader သို့ ဝင်ရောက်သည်။",
            brand = ServiceBrand.TRANSSION,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),

        // =========================================================================
        // 9. ASUS ROG / SONY XPERIA
        // =========================================================================
        ServiceActionItem(
            id = "asus_fb_info_device",
            title = "Read Asus Bootloader & Root Status",
            command = "fastboot oem device-info",
            description = "Asus ROG / Zenfone Bootloader ပွင့်/မပွင့် အခြေအနေကို စစ်ဆေးသည်။",
            brand = ServiceBrand.ASUS_SONY,
            group = ActionGroup.INFO
        ),
        ServiceActionItem(
            id = "sony_fb_info_baseband",
            title = "Read Sony Xperia Baseband Info",
            command = "fastboot getvar version-baseband",
            description = "Sony Xperia Baseband Modem ဗားရှင်းကို Fastboot မှ ဖတ်ယူသည်။",
            brand = ServiceBrand.ASUS_SONY,
            group = ActionGroup.INFO
        ),
        ServiceActionItem(
            id = "asus_fb_unlock_go",
            title = "Execute Asus OEM Unlock Trigger",
            command = "fastboot oem asus-go",
            description = "Asus ဖုန်းများအား OEM Unlock Trigger Command ပေးပို့သည်။",
            brand = ServiceBrand.ASUS_SONY,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "sony_fb_unlock_std",
            title = "Unlock Bootloader (Sony / Asus)",
            command = "fastboot flashing unlock",
            description = "Sony Xperia / Asus Bootloader ကို Unlock ပြုလုပ်သည်။",
            brand = ServiceBrand.ASUS_SONY,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "sony_fb_unlock_oem",
            title = "Unlock Bootloader (Legacy Sony OEM)",
            command = "fastboot oem unlock",
            description = "Sony Xperia မော်ဒယ်အဟောင်းများကို OEM Unlock ပြုလုပ်သည်။",
            brand = ServiceBrand.ASUS_SONY,
            group = ActionGroup.UNLOCK
        ),
        ServiceActionItem(
            id = "asus_sony_fb_svc_wipe",
            title = "Erase Userdata & Cache",
            command = "fastboot -w",
            description = "Asus/Sony Userdata နှင့် Cache/Metadata ကို ရှင်းလင်းသည်။",
            brand = ServiceBrand.ASUS_SONY,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "asus_sony_fb_svc_reboot",
            title = "Reboot Asus / Sony to System",
            command = "fastboot reboot",
            description = "Fastboot မှ သာမန်စနစ်သို့ Reboot ချသည်။",
            brand = ServiceBrand.ASUS_SONY,
            group = ActionGroup.SERVICE
        ),
        ServiceActionItem(
            id = "asus_sony_adb_info_fp",
            title = "Read Firmware & Model Fingerprint",
            command = "getprop ro.build.fingerprint",
            description = "Asus/Sony Official Build Fingerprint အပြည့်အစုံကို ဖတ်ယူသည်။",
            brand = ServiceBrand.ASUS_SONY,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "asus_sony_adb_info_bb",
            title = "Read Baseband Modem Version",
            command = "getprop gsm.version.baseband",
            description = "Asus/Sony Baseband Radio ဗားရှင်းကို စစ်ဆေးသည်။",
            brand = ServiceBrand.ASUS_SONY,
            group = ActionGroup.INFO,
            isAdb = true
        ),
        ServiceActionItem(
            id = "asus_sony_adb_unlock_dev",
            title = "Open Developer Options (OEM Unlock)",
            command = "am start -a android.settings.APPLICATION_DEVELOPMENT_SETTINGS",
            description = "Asus/Sony OEM Unlock ဖွင့်ရန် Developer Options ကို ဖွင့်ပေးသည်။",
            brand = ServiceBrand.ASUS_SONY,
            group = ActionGroup.UNLOCK,
            isAdb = true
        ),
        ServiceActionItem(
            id = "asus_sony_adb_unlock_setup",
            title = "Bypass Setup Wizard Flag",
            command = "settings put global device_provisioned 1",
            description = "Setup Wizard ကျော်လွှားရန် device_provisioned flag ကို 1 သတ်မှတ်သည်။",
            brand = ServiceBrand.ASUS_SONY,
            group = ActionGroup.UNLOCK,
            isAdb = true
        ),
        ServiceActionItem(
            id = "asus_adb_svc_ota",
            title = "Disable Asus DM System Updater",
            command = "pm disable-user --user 0 com.asus.dm",
            description = "Asus Software Update (DM Client) ကို ပိတ်ပင်သည်။",
            brand = ServiceBrand.ASUS_SONY,
            group = ActionGroup.SERVICE,
            isAdb = true
        ),
        ServiceActionItem(
            id = "asus_sony_adb_svc_bl",
            title = "Reboot Asus / Sony to Bootloader",
            command = "adb reboot bootloader",
            description = "ADB Mode မှ Fastboot Bootloader Mode သို့ ဝင်ရောက်သည်။",
            brand = ServiceBrand.ASUS_SONY,
            group = ActionGroup.SERVICE,
            isAdb = true
        )
    )
}
