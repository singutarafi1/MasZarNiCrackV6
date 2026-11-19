package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalAppPalette

@Composable
fun AboutScreen(
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val warnRed = Color(0xFFEF4444)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.screenBg)
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
            .testTag("about_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = palette.cardBg,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.5.dp,
                    brush = Brush.horizontalGradient(
                        listOf(palette.accentPrimary, palette.accentSecondary)
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = palette.accentPrimary.copy(alpha = 0.16f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = "About Tool",
                                tint = palette.accentPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "MTK & QC Unlock Tool",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.textPrimary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = palette.accentPrimary.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, palette.accentPrimary)
                ) {
                    Text(
                        text = "PRO v3.2",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.accentPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Concise Safety Warning Card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = warnRed.copy(alpha = if (palette.isDark) 0.12f else 0.08f),
            border = BorderStroke(1.dp, warnRed.copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("about_warning_card")
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Safety Notice",
                        tint = warnRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "သတိပေးချက်",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = warnRed
                    )
                }

                Text(
                    text = "• Flash / Unlock မလုပ်မီ NVRAM / NVDATA (IMEI) ကို အရင် Backup ယူထားပါ။\n" +
                        "• လုပ်ဆောင်နေစဉ် USB OTG ကြိုး မပြုတ်စေရန်နှင့် ဘက်ထရီ 50% အထက်ရှိရန် သတိပြုပါ။\n" +
                        "• တရားဝင် ဝန်ဆောင်မှုနှင့် Technician လုပ်ငန်းအတွက်သာ အသုံးပြုရန်။",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                    color = palette.textPrimary
                )
            }
        }

        // Credits Card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = palette.cardBg,
            border = BorderStroke(1.dp, palette.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("about_credits_card")
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Developer",
                            tint = palette.accentPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Developed by ZarNi",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.textPrimary
                        )
                    }
                }

                HorizontalDivider(color = palette.divider)

                Text(
                    text = "Product by MobileAnswer",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.accentPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
