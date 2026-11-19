package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppOperatingMode
import com.example.model.LoaderManifest
import com.example.model.LoaderMetadataEntry
import com.example.model.SyncProgressState
import com.example.ui.theme.LocalAppPalette
import java.util.Locale

/**
 * First-launch / Mode Switcher Dialog allowing user to choose Online (Live Stream) vs Offline (Local Sync).
 */
@Composable
fun AppModeSelectionDialog(
    currentMode: AppOperatingMode,
    manifestVersion: Int,
    entryCount: Int,
    onSelectOnline: () -> Unit,
    onSelectOfflineAndSync: () -> Unit,
    onSelectOfflineOnly: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalAppPalette.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardBg,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Database & Loader Operating Mode",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.textPrimary
                )
                Text(
                    text = "Manifest v$manifestVersion · $entryCount verified Firehose loaders",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = palette.accentPrimary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Option 1: Online Mode
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (currentMode == AppOperatingMode.ONLINE) Color(0xFF06B6D4).copy(alpha = 0.14f) else palette.cardAltBg,
                    border = BorderStroke(
                        width = if (currentMode == AppOperatingMode.ONLINE) 1.5.dp else 1.dp,
                        color = if (currentMode == AppOperatingMode.ONLINE) Color(0xFF06B6D4) else palette.cardBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectOnline() }
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF06B6D4), modifier = Modifier.size(18.dp))
                            Text(
                                text = "ONLINE MODE (Live Stream)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF06B6D4)
                            )
                        }
                        Text(
                            text = "Zero storage footprint. Reads model_map.json live from repo and streams required loader into RAM only when a device connects.",
                            fontSize = 10.5.sp,
                            color = palette.textSecondary,
                            lineHeight = 14.sp
                        )
                    }
                }

                // Option 2: Offline Mode
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (currentMode == AppOperatingMode.OFFLINE) Color(0xFF10B981).copy(alpha = 0.14f) else palette.cardAltBg,
                    border = BorderStroke(
                        width = if (currentMode == AppOperatingMode.OFFLINE) 1.5.dp else 1.dp,
                        color = if (currentMode == AppOperatingMode.OFFLINE) Color(0xFF10B981) else palette.cardBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectOfflineAndSync() }
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                            Text(
                                text = "OFFLINE MODE (Download & Sync)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF10B981)
                            )
                        }
                        Text(
                            text = "Downloads model_map.json and all loaders to internal storage for 100% disconnected shop work. Auto-syncs updates when online.",
                            fontSize = 10.5.sp,
                            color = palette.textSecondary,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSelectOfflineAndSync,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Offline + Sync Now", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onSelectOfflineOnly,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, palette.cardBorder),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("Offline Only", fontSize = 10.5.sp, color = palette.textSecondary)
                }
                Button(
                    onClick = onSelectOnline,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4)),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Use Online", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF082F49))
                }
            }
        }
    )
}

/**
 * Interactive Modal for browsing bkerler/Loaders manifest, switching Online/Offline, and triggering Sync.
 */
@Composable
fun LoaderRepositoryBrowserModal(
    operatingMode: AppOperatingMode,
    manifest: LoaderManifest?,
    syncState: SyncProgressState,
    repositoryUrl: String,
    matchedEntry: LoaderMetadataEntry?,
    onSwitchMode: (AppOperatingMode) -> Unit,
    onSyncAll: () -> Unit,
    onCancelSync: () -> Unit,
    onRefreshManifest: () -> Unit,
    onSelectEntry: (LoaderMetadataEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalAppPalette.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedBrandFilter by remember { mutableStateOf("ALL") }

    val entries = manifest?.entries ?: emptyList()
    val brands = remember(entries) {
        listOf("ALL") + entries.map { it.brand }.distinct().sorted()
    }
    val filteredEntries = remember(entries, searchQuery, selectedBrandFilter) {
        val q = searchQuery.trim().lowercase(Locale.US)
        entries.filter { e ->
            val brandOk = selectedBrandFilter == "ALL" || e.brand.equals(selectedBrandFilter, ignoreCase = true)
            val queryOk = q.isEmpty() ||
                e.brand.lowercase(Locale.US).contains(q) ||
                e.model.lowercase(Locale.US).contains(q) ||
                e.soc.lowercase(Locale.US).contains(q) ||
                e.msmHwidHex.lowercase(Locale.US).contains(q) ||
                e.fileName.lowercase(Locale.US).contains(q)
            brandOk && queryOk
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardBg,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Qualcomm Loader Catalog",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.textPrimary
                    )
                    Text(
                        text = "bkerler/Loaders · Manifest v${manifest?.version ?: 105} · ${entries.size} entries",
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (operatingMode == AppOperatingMode.ONLINE) Color(0xFF06B6D4).copy(alpha = 0.16f) else Color(0xFF10B981).copy(alpha = 0.16f),
                    border = BorderStroke(
                        1.dp,
                        if (operatingMode == AppOperatingMode.ONLINE) Color(0xFF06B6D4) else Color(0xFF10B981)
                    ),
                    modifier = Modifier.clickable {
                        onSwitchMode(if (operatingMode == AppOperatingMode.ONLINE) AppOperatingMode.OFFLINE else AppOperatingMode.ONLINE)
                    }
                ) {
                    Text(
                        text = if (operatingMode == AppOperatingMode.ONLINE) "ONLINE" else "OFFLINE",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (operatingMode == AppOperatingMode.ONLINE) Color(0xFF06B6D4) else Color(0xFF10B981),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Source URL + Sync Status Bar
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = palette.cardAltBg,
                    border = BorderStroke(1.dp, palette.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Repo: ${repositoryUrl.removePrefix("https://")}",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = palette.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Refresh",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.accentPrimary,
                                modifier = Modifier.clickable { onRefreshManifest() }
                            )
                        }

                        if (syncState.isSyncing) {
                            Text(
                                text = syncState.currentFileName,
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF10B981),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            LinearProgressIndicator(
                                progress = { (syncState.percentage / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp),
                                color = Color(0xFF10B981),
                                trackColor = palette.cardBorder
                            )
                        } else if (syncState.error != null) {
                            Text(
                                text = "Sync Error: ${syncState.error}",
                                fontSize = 9.5.sp,
                                color = Color(0xFFEF4444)
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (matchedEntry != null) {
                                        "Active: ${matchedEntry.fileName}"
                                    } else {
                                        "Last sync: ${syncState.lastSyncTime}"
                                    },
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (matchedEntry != null) Color(0xFF10B981) else palette.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, Color(0xFF10B981)),
                                    modifier = Modifier.clickable { onSyncAll() }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Sync, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(12.dp))
                                        Text("Sync Offline", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                                    }
                                }
                            }
                        }
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search brand, SoC, MSM HWID (0x000E10E1)...", fontSize = 10.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(15.dp)) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = palette.textPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.accentPrimary,
                        unfocusedBorderColor = palette.cardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Brand filter chips (scrollable)
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    brands.take(5).forEach { b ->
                        val active = b == selectedBrandFilter
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = if (active) palette.accentPrimary.copy(alpha = 0.18f) else palette.cardAltBg,
                            border = BorderStroke(1.dp, if (active) palette.accentPrimary else palette.cardBorder),
                            modifier = Modifier.clickable { selectedBrandFilter = b }
                        ) {
                            Text(
                                text = b,
                                fontSize = 9.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) palette.accentPrimary else palette.textSecondary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = palette.divider)

                // Catalog List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredEntries, key = { it.id }) { entry ->
                        val isMatched = matchedEntry?.id == entry.id
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = if (isMatched) Color(0xFF10B981).copy(alpha = 0.12f) else palette.cardAltBg,
                            border = BorderStroke(1.dp, if (isMatched) Color(0xFF10B981) else palette.cardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectEntry(entry) }
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${entry.brand} · ${entry.model}",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = entry.storageType,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.accentPrimary
                                    )
                                }
                                Text(
                                    text = "${entry.soc} · HWID ${entry.msmHwidHex}",
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = palette.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = entry.fileName,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isMatched) Color(0xFF10B981) else palette.accentSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (syncState.isSyncing) {
                OutlinedButton(
                    onClick = onCancelSync,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color(0xFFEF4444))
                ) {
                    Text("Cancel Sync", fontSize = 10.5.sp, color = Color(0xFFEF4444))
                }
            } else {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accentPrimary),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Close", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    )
}
