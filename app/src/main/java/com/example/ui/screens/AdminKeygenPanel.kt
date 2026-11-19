package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.CloudLicenseKeyRecord
import com.example.security.LicenseManager
import com.example.ui.theme.LocalAppPalette
import kotlinx.coroutines.launch

@Composable
fun AdminMasterKeyPanel(
    onUseGeneratedKey: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val palette = LocalAppPalette.current
    val scope = rememberCoroutineScope()
    val currentUser by LicenseManager.currentUser.collectAsState()
    val localKeys by LicenseManager.localGeneratedKeys.collectAsState()

    var masterKeyInput by remember { mutableStateOf("") }
    var showMasterKeyPassword by remember { mutableStateOf(false) }
    var isMasterKeyUnlocked by remember { mutableStateOf(false) }
    var adminStatusMsg by remember { mutableStateOf<String?>(null) }
    var adminStatusIsError by remember { mutableStateOf(false) }
    var selectedDurationDays by remember { mutableIntStateOf(30) }
    var selectedBatchCount by remember { mutableIntStateOf(1) }
    var noteInput by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }

    val cloudKeysFlow = remember(currentUser?.uid, isMasterKeyUnlocked) {
        val uid = currentUser?.uid.orEmpty()
        if (isMasterKeyUnlocked && uid.isNotBlank()) {
            LicenseManager.observeAdminCloudKeys(context, uid)
        } else null
    }
    val cloudKeys by (cloudKeysFlow?.collectAsState(initial = emptyList())
        ?: remember { mutableStateOf(emptyList()) })

    val displayedKeys = remember(cloudKeys, localKeys) {
        (cloudKeys + localKeys).distinctBy { it.keyCode }
    }

    if (!isMasterKeyUnlocked) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = masterKeyInput,
                onValueChange = { masterKeyInput = it },
                label = { Text("MasterKey") },
                singleLine = true,
                visualTransformation = if (showMasterKeyPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showMasterKeyPassword = !showMasterKeyPassword }) {
                        Icon(
                            imageVector = if (showMasterKeyPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle MasterKey Visibility"
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.accentPrimary,
                    unfocusedBorderColor = palette.cardBorder,
                    focusedTextColor = palette.textPrimary,
                    unfocusedTextColor = palette.textPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_master_key_input")
            )

            if (adminStatusMsg != null) {
                Text(
                    text = adminStatusMsg.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFEF4444),
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = {
                    if (LicenseManager.verifyMasterKey(masterKeyInput)) {
                        isMasterKeyUnlocked = true
                        adminStatusMsg = null
                        adminStatusIsError = false
                    } else {
                        adminStatusIsError = true
                        adminStatusMsg = "Invalid MasterKey"
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = palette.accentPrimary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("verify_master_key_button")
            ) {
                Text(
                    text = "UNLOCK ADMIN",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF10B981).copy(alpha = 0.14f),
                border = BorderStroke(1.dp, Color(0xFF10B981)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ADMIN VERIFIED",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                }
            }

            Text(
                text = "Duration",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
            )

            val durations = listOf(
                1 to "1D",
                7 to "7D",
                30 to "30D",
                90 to "90D",
                365 to "1Y",
                36500 to "Lifetime"
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                durations.chunked(3).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowItems.forEach { (days, label) ->
                            val selected = selectedDurationDays == days
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) palette.accentPrimary.copy(alpha = 0.18f) else palette.cardAltBg,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) palette.accentPrimary else palette.cardBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedDurationDays = days }
                                    .testTag("keygen_duration_$days")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) palette.accentPrimary else palette.textPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Text(
                text = "Batch",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(1, 5, 10).forEach { count ->
                    val selected = selectedBatchCount == count
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) palette.accentSecondary.copy(alpha = 0.18f) else palette.cardAltBg,
                        border = BorderStroke(
                            1.dp,
                            if (selected) palette.accentSecondary else palette.cardBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedBatchCount = count }
                            .testTag("keygen_count_$count")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = "$count",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) palette.accentSecondary else palette.textPrimary
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = noteInput,
                onValueChange = { noteInput = it },
                label = { Text("Note") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.accentPrimary,
                    unfocusedBorderColor = palette.cardBorder,
                    focusedTextColor = palette.textPrimary,
                    unfocusedTextColor = palette.textPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("keygen_note_input")
            )

            Button(
                onClick = {
                    if (isGenerating) return@Button
                    isGenerating = true
                    adminStatusMsg = null
                    scope.launch {
                        val res = LicenseManager.adminGenerateAndPublishKeys(
                            context = context,
                            masterKey = masterKeyInput,
                            durationDays = selectedDurationDays,
                            count = selectedBatchCount,
                            note = noteInput
                        )
                        isGenerating = false
                        res.onSuccess { created ->
                            val first = created.firstOrNull()?.keyCode.orEmpty()
                            if (first.isNotEmpty()) {
                                onUseGeneratedKey(first)
                            }
                            adminStatusIsError = false
                            adminStatusMsg = if (currentUser != null) {
                                "Generated ${created.size} key(s) • Synced"
                            } else {
                                "Generated ${created.size} key(s) • Local"
                            }
                        }.onFailure { err ->
                            adminStatusIsError = true
                            adminStatusMsg = err.message ?: "Generation failed"
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = palette.startButtonBg),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("generate_one_time_keys_button")
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = "GENERATE KEYS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }

            if (adminStatusMsg != null) {
                Text(
                    text = adminStatusMsg.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (adminStatusIsError) Color(0xFFEF4444) else Color(0xFF10B981)
                )
            }

            if (displayedKeys.isNotEmpty()) {
                HorizontalDivider(color = palette.divider)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Keys (${displayedKeys.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                    Text(
                        text = "COPY UNUSED",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.accentPrimary,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clickable {
                                val unusedText = displayedKeys
                                    .filter { it.status == "UNUSED" }
                                    .joinToString("\n") { it.keyCode }
                                if (unusedText.isNotBlank()) {
                                    copyTextToClipboard(context, "License Keys", unusedText)
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    displayedKeys.take(15).forEach { item ->
                        AdminKeyRowCard(
                            record = item,
                            onCopy = {
                                copyTextToClipboard(context, "Key", item.keyCode)
                            },
                            onFill = {
                                onUseGeneratedKey(item.keyCode)
                            },
                            onResetOrRevoke = { targetStatus ->
                                scope.launch {
                                    LicenseManager.adminUpdateKeyStatus(
                                        context = context,
                                        masterKey = masterKeyInput,
                                        keyCode = item.keyCode,
                                        newStatus = targetStatus
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminKeyRowCard(
    record: CloudLicenseKeyRecord,
    onCopy: () -> Unit,
    onFill: () -> Unit,
    onResetOrRevoke: (String) -> Unit
) {
    val palette = LocalAppPalette.current
    val statusColor = when (record.status) {
        "UNUSED" -> Color(0xFF10B981)
        "USED" -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = palette.cardAltBg,
        border = BorderStroke(1.dp, palette.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = record.keyCode,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.textPrimary
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusColor.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, statusColor)
                ) {
                    Text(
                        text = record.status,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            val durText = if (record.durationDays >= 3650) "Lifetime" else "${record.durationDays}D"
            val hwidInfo = if (record.boundHwid.isNotBlank()) " • ${record.boundHwid}" else ""
            Text(
                text = "$durText$hwidInfo",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = palette.accentPrimary.copy(alpha = 0.14f),
                    modifier = Modifier
                        .clickable { onCopy() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Key",
                            tint = palette.accentPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "COPY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.accentPrimary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = palette.accentSecondary.copy(alpha = 0.14f),
                    modifier = Modifier
                        .clickable { onFill() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "USE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentSecondary
                    )
                }

                if (record.status != "UNUSED") {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.14f),
                        modifier = Modifier
                            .clickable { onResetOrRevoke("UNUSED") }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset Key",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "RESET",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                }

                if (record.status != "REVOKED") {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.14f),
                        modifier = Modifier
                            .clickable { onResetOrRevoke("REVOKED") }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "REVOKE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF4444)
                        )
                    }
                }
            }
        }
    }
}

internal fun copyTextToClipboard(context: Context, label: String, text: String) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }
}
