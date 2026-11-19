package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.LocalAppPalette
import com.example.util.DeviceSafetySnapshot

@Composable
fun StartupSafetyNoticeDialog(
    snapshot: DeviceSafetySnapshot,
    isFirstInstallRequireAgree: Boolean,
    onAgreeOrDismiss: () -> Unit
) {
    val palette = LocalAppPalette.current
    val warnAmber = Color(0xFFF59E0B)
    val dangerRed = Color(0xFFEF4444)
    val passGreen = Color(0xFF10B981)

    Dialog(
        onDismissRequest = {
            if (!isFirstInstallRequireAgree) onAgreeOrDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = !isFirstInstallRequireAgree,
            dismissOnClickOutside = !isFirstInstallRequireAgree,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = palette.cardBg,
            border = BorderStroke(
                width = 1.5.dp,
                brush = Brush.horizontalGradient(listOf(palette.accentPrimary, warnAmber))
            ),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 480.dp)
                .testTag("startup_safety_notice_dialog")
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = warnAmber.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, warnAmber),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "System Safety Notice",
                                tint = warnAmber,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SYSTEM SAFETY CHECK",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.textPrimary
                        )
                    }
                }

                HorizontalDivider(color = palette.divider)

                // Live Device Check Rows (Battery / OTG / Android + RAM + Storage)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val batStatusColor = if (snapshot.isBatterySafe) passGreen else dangerRed
                    val batStatusText = when {
                        snapshot.isCharging -> "${snapshot.batteryPercent}% (Charging)"
                        snapshot.batteryPercent >= 50 -> "${snapshot.batteryPercent}% (OK)"
                        else -> "${snapshot.batteryPercent}% (LOW < 50%)"
                    }
                    SafetyCheckRow(
                        icon = if (snapshot.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                        title = "Master Battery (>= 50%)",
                        subtitle = "Flash / Backup လုပ်နေစဉ် ပါဝါမပြတ်စေရန် အားအနည်းဆုံး 50% ရှိသင့်သည်",
                        statusLabel = batStatusText,
                        statusColor = batStatusColor
                    )

                    SafetyCheckRow(
                        icon = Icons.Default.Usb,
                        title = "USB Cable & OTG Connector",
                        subtitle = "Data ကြိုးကောင်းနှင့် OTG Connector မလှုပ်မယိုင် ခိုင်မြဲကြောင်း စစ်ဆေးပါ",
                        statusLabel = if (snapshot.hasUsbOtgHost) "OTG HOST OK" else "NO USB HOST",
                        statusColor = if (snapshot.hasUsbOtgHost) passGreen else dangerRed
                    )

                    SafetyCheckRow(
                        icon = Icons.Default.PhoneAndroid,
                        title = "${snapshot.deviceName} (${snapshot.androidVersionLabel})",
                        subtitle = "API Level ${snapshot.sdkInt} • System Environment",
                        statusLabel = "READY",
                        statusColor = palette.accentPrimary
                    )

                    SafetyCheckRow(
                        icon = Icons.Default.Memory,
                        title = "RAM & Available Storage",
                        subtitle = "ROM Backup ယူရန် Storage လုံလောက်မှု ရှိမရှိ စစ်ဆေးပါ",
                        statusLabel = "RAM ${snapshot.formattedRam} | Free ${snapshot.formattedFreeStorage}",
                        statusColor = if (snapshot.isStorageSafe) passGreen else warnAmber
                    )
                }

                // Short & Clear Risk Disclaimer Card
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = dangerRed.copy(alpha = if (palette.isDark) 0.12f else 0.08f),
                    border = BorderStroke(1.dp, dangerRed.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = dangerRed,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "အသုံးပြုခွင့်နှင့် ဘေးအန္တရာယ် သတိပေးချက်",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = dangerRed
                            )
                        }
                        Text(
                            text = "• Flash / Write / Format ပြုလုပ်နေစဉ် ကြိုးပြုတ်ခြင်း၊ အားကုန်ခြင်းကြောင့် ဖုန်း Dead/Brick ဖြစ်နိုင်ပါသည်။\n" +
                                "• မည်သည့် Flash/Unlock မဆို မလုပ်မီ NV Data (IMEI) နှင့် Preloader ကို ဦးစွာ Backup ယူထားပါ။\n" +
                                "• ဤ Tool ကို နည်းပညာရှင် (Technician Use) တာဝန်ယူမှုဖြင့်သာ အသုံးပြုရန် ဖြစ်ပါသည်။",
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp,
                            color = palette.textPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Button(
                    onClick = onAgreeOrDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFirstInstallRequireAgree) palette.startButtonBg else palette.accentPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("startup_notice_agree_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isFirstInstallRequireAgree) {
                            "Agree & Continue (သဘောတူသည်)"
                        } else {
                            "OK / Close"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun SafetyCheckRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    statusLabel: String,
    statusColor: Color
) {
    val palette = LocalAppPalette.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = palette.cardAltBg,
        border = BorderStroke(1.dp, palette.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = statusColor.copy(alpha = 0.14f),
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = statusColor.copy(alpha = 0.14f),
                        border = BorderStroke(0.8.dp, statusColor.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = statusLabel,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EveryLaunchWarningBanner(
    visible: Boolean,
    snapshot: DeviceSafetySnapshot,
    onInspectDetails: () -> Unit,
    onDismissSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val isCritical = snapshot.hasAnyCriticalWarning
    val bannerColor = if (isCritical) Color(0xFFEF4444) else Color(0xFFF59E0B)

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Surface(
            color = bannerColor.copy(alpha = if (palette.isDark) 0.16f else 0.11f),
            border = BorderStroke(1.dp, bannerColor.copy(alpha = 0.65f)),
            shape = RoundedCornerShape(8.dp),
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("every_launch_warning_banner")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onInspectDetails() }
                    .padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isCritical) Icons.Default.Warning else Icons.Default.Storage,
                    contentDescription = "Launch Safety Warning",
                    tint = bannerColor,
                    modifier = Modifier.size(16.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    val headline = when {
                        !snapshot.isBatterySafe ->
                            "⚠ သတိပေးချက်: Master Bat ${snapshot.batteryPercent}% (<50%) — အားသွင်းပြီးမှ Flash/Dump လုပ်ပါ"
                        !snapshot.isStorageSafe ->
                            "⚠ သတိပေးချက်: Free Storage (${snapshot.formattedFreeStorage}) နည်းနေသည် • Bat ${snapshot.batteryPercent}%"
                        !snapshot.hasUsbOtgHost ->
                            "⚠ သတိပေးချက်: USB OTG Host မတွေ့ပါ • Bat ${snapshot.batteryPercent}% • Free ${snapshot.formattedFreeStorage}"
                        else ->
                            "⚠ သတိပေးချက်: OTG ကြိုးတည်ငြိမ်စွာ တပ်ဆင်ပါ • Bat ${snapshot.batteryPercent}% • Free ${snapshot.formattedFreeStorage}"
                    }
                    Text(
                        text = headline,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (palette.isDark) Color(0xFFFEF3C7) else Color(0xFF78350F),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 13.sp
                    )
                    Text(
                        text = "${snapshot.androidVersionLabel} • RAM ${snapshot.formattedRam} • အသေးစိတ်ကြည့်ရန် နှိပ်ပါ",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onDismissSession,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(36.dp)
                        .testTag("dismiss_launch_warning_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss Warning",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
