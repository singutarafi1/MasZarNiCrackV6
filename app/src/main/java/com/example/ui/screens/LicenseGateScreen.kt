package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.security.LicenseManager
import com.example.security.LicenseSessionState
import com.example.ui.theme.LocalAppPalette
import com.example.util.DeviceSafetyChecker
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

enum class GateTab {
    ACTIVATE_NEW,
    LOGIN_RESTORE,
    ADMIN_DEBUG
}

@Composable
fun LicenseGateScreen(
    sessionState: LicenseSessionState,
    onLicenseActivated: (LicenseSessionState) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalAppPalette.current
    val scope = rememberCoroutineScope()
    val currentUser by LicenseManager.currentUser.collectAsState()

    var selectedTab by remember { mutableStateOf(GateTab.ACTIVATE_NEW) }

    // Activation Fields
    var usernameInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf(currentUser?.email.orEmpty()) }
    var passwordInput by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var keyInput by remember { mutableStateOf("") }

    // Login / Restore Fields
    var loginEmailInput by remember { mutableStateOf(currentUser?.email.orEmpty()) }
    var loginPasswordInput by remember { mutableStateOf("") }
    var showLoginPassword by remember { mutableStateOf(false) }

    var isBusy by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var isErrorFeedback by remember { mutableStateOf(false) }
    val hasGms = remember(context) { DeviceSafetyChecker.isGmsAvailable(context) }
    var showOfflineFallbackBtn by remember { mutableStateOf(!hasGms) }

    LaunchedEffect(hasGms) {
        if (hasGms) {
            LicenseManager.attemptSilentAutoSignIn(context)
        }
    }

    LaunchedEffect(currentUser) {
        if (emailInput.isEmpty() && currentUser?.email != null) {
            emailInput = currentUser?.email.orEmpty()
        }
        if (loginEmailInput.isEmpty() && currentUser?.email != null) {
            loginEmailInput = currentUser?.email.orEmpty()
        }
        if (usernameInput.isEmpty() && currentUser?.displayName != null) {
            usernameInput = currentUser?.displayName.orEmpty()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.screenBg)
            .systemBarsPadding()
            .testTag("license_gate_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Compact Header Card
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
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = palette.accentPrimary.copy(alpha = 0.16f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = "Native License",
                                        tint = palette.accentPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "NATIVE UNLOCK TOOL",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = palette.textPrimary
                                )
                                Text(
                                    text = "License Activation",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.textSecondary
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (sessionState.isSoAuthentic) Color(0xFF10B981).copy(alpha = 0.15f)
                            else Color(0xFFEF4444).copy(alpha = 0.15f),
                            border = BorderStroke(
                                1.dp,
                                if (sessionState.isSoAuthentic) Color(0xFF10B981) else Color(0xFFEF4444)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (sessionState.isSoAuthentic) Icons.Default.VerifiedUser else Icons.Default.Warning,
                                    contentDescription = "SO Integrity",
                                    tint = if (sessionState.isSoAuthentic) Color(0xFF10B981) else Color(0xFFEF4444),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (sessionState.isSoAuthentic) "SO OK" else "SO ERR",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (sessionState.isSoAuthentic) Color(0xFF10B981) else Color(0xFFEF4444)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = palette.divider)

                    // Compact Device + HWID row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = "Device",
                                tint = palette.accentSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${sessionState.deviceModel} • ${sessionState.deviceHwid}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "COPY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.accentPrimary,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clickable {
                                    copyTextToClipboard(context, "HWID", sessionState.deviceHwid)
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .testTag("copy_hwid_button")
                        )
                }
            }

            // 1. APK Security & Hardware Integrity Matrix
            ApkSecurityMatrixCard(sessionState = sessionState)

            // 2. Tab Switcher: Sign Up & Activate | Sign In & Restore | (Debug) Admin
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = palette.cardAltBg,
                border = BorderStroke(1.dp, palette.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    GateTabButton(
                        title = "Sign Up / Activate",
                        icon = Icons.Default.Key,
                        selected = selectedTab == GateTab.ACTIVATE_NEW,
                        activeColor = palette.accentPrimary,
                        onClick = {
                            selectedTab = GateTab.ACTIVATE_NEW
                            feedbackMessage = null
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tab_activate_new")
                    )

                    GateTabButton(
                        title = "Sign In / Restore",
                        icon = Icons.Default.LockOpen,
                        selected = selectedTab == GateTab.LOGIN_RESTORE,
                        activeColor = palette.accentSecondary,
                        onClick = {
                            selectedTab = GateTab.LOGIN_RESTORE
                            feedbackMessage = null
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tab_login_restore")
                    )

                    if (BuildConfig.DEBUG) {
                        GateTabButton(
                            title = "Admin",
                            icon = Icons.Default.AdminPanelSettings,
                            selected = selectedTab == GateTab.ADMIN_DEBUG,
                            activeColor = Color(0xFFF59E0B),
                            onClick = {
                                selectedTab = GateTab.ADMIN_DEBUG
                                feedbackMessage = null
                            },
                            modifier = Modifier
                                .weight(0.7f)
                                .testTag("toggle_admin_keygen_button")
                        )
                    }
                }
            }

            // High-Contrast Alarm / Status Notice Box
            if (feedbackMessage != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isErrorFeedback) Color(0xFFEF4444).copy(alpha = 0.16f)
                    else Color(0xFF10B981).copy(alpha = 0.16f),
                    border = BorderStroke(
                        1.5.dp,
                        if (isErrorFeedback) Color(0xFFEF4444) else Color(0xFF10B981)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (isErrorFeedback) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isErrorFeedback) Color(0xFFEF4444) else Color(0xFF10B981),
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isErrorFeedback) "SYSTEM ALARM / NOTICE" else "SYSTEM NOTICE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isErrorFeedback) Color(0xFFEF4444) else Color(0xFF10B981)
                            )
                            Text(
                                text = feedbackMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.textPrimary
                            )
                        }
                    }
                }
            }

            when (selectedTab) {
                GateTab.ACTIVATE_NEW -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = palette.cardBg,
                        border = BorderStroke(1.dp, palette.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Option 1: Google Direct Sign-In
                            GoogleLoginDirectButton(
                                currentUser = currentUser,
                                isBusy = isBusy,
                                onSignInClick = {
                                    if (isBusy) return@GoogleLoginDirectButton
                                    isBusy = true
                                    feedbackMessage = null
                                    scope.launch {
                                        val res = LicenseManager.signInWithGoogle(context)
                                        isBusy = false
                                        res.onSuccess {
                                            isErrorFeedback = false
                                            feedbackMessage = "Signed in with Google: ${it.email}"
                                        }.onFailure {
                                            isErrorFeedback = true
                                            feedbackMessage = it.message ?: "Google Sign-In failed"
                                        }
                                    }
                                },
                                onSignOutClick = {
                                    LicenseManager.signOutUser()
                                    feedbackMessage = "Signed out from Google account."
                                }
                            )

                            // Option 2: Dedicated Email & Password Divider
                            OrDivider(label = "OR REGISTER WITH EMAIL & PASSWORD (သီးသန့်)")

                            OutlinedTextField(
                                value = usernameInput,
                                onValueChange = { usernameInput = it },
                                label = { Text("Username") },
                                singleLine = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = palette.accentPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = palette.accentPrimary,
                                    unfocusedBorderColor = palette.cardBorder,
                                    focusedTextColor = palette.textPrimary,
                                    unfocusedTextColor = palette.textPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("register_username_input")
                            )

                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = { emailInput = it },
                                label = { Text("Email Address") },
                                singleLine = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = null,
                                        tint = palette.accentPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = palette.accentPrimary,
                                    unfocusedBorderColor = palette.cardBorder,
                                    focusedTextColor = palette.textPrimary,
                                    unfocusedTextColor = palette.textPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("register_email_input")
                            )

                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                label = { Text("Password (min 6 characters)") },
                                singleLine = true,
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = palette.accentPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    IconButton(onClick = { showPassword = !showPassword }) {
                                        Icon(
                                            imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle Password"
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = palette.accentPrimary,
                                    unfocusedBorderColor = palette.cardBorder,
                                    focusedTextColor = palette.textPrimary,
                                    unfocusedTextColor = palette.textPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("register_password_input")
                            )

                            OutlinedTextField(
                                value = keyInput,
                                onValueChange = { keyInput = it.uppercase() },
                                label = { Text("License Key") },
                                placeholder = { Text("MTK-0030-XXXX-XXXX-XXXXXXXX") },
                                singleLine = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = null,
                                        tint = palette.accentPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = palette.accentPrimary,
                                    unfocusedBorderColor = palette.cardBorder,
                                    focusedTextColor = palette.textPrimary,
                                    unfocusedTextColor = palette.textPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("register_key_input")
                            )

                            Button(
                                onClick = {
                                    if (isBusy) return@Button
                                    if (usernameInput.trim().length < 2) {
                                        isErrorFeedback = true
                                        feedbackMessage = "Username min 2 characters"
                                        return@Button
                                    }
                                    if (!emailInput.trim().contains("@")) {
                                        isErrorFeedback = true
                                        feedbackMessage = "Invalid email address format"
                                        return@Button
                                    }
                                    if (passwordInput.trim().length < 6) {
                                        isErrorFeedback = true
                                        feedbackMessage = "Password must be at least 6 characters"
                                        return@Button
                                    }
                                    if (keyInput.trim().isEmpty()) {
                                        isErrorFeedback = true
                                        feedbackMessage = "Please enter your license key"
                                        return@Button
                                    }

                                    isBusy = true
                                    feedbackMessage = null
                                    scope.launch {
                                        val activateRes = LicenseManager.activateKeyWithAccount(
                                            context = context,
                                            username = usernameInput,
                                            email = emailInput,
                                            password = passwordInput,
                                            rawKeyInput = keyInput
                                        )

                                        isBusy = false
                                        activateRes.onSuccess { unlockedState ->
                                            isErrorFeedback = false
                                            feedbackMessage = "License Activated Successfully!"
                                            onLicenseActivated(unlockedState)
                                        }.onFailure { err ->
                                            isErrorFeedback = true
                                            feedbackMessage = err.message ?: "Activation failed"
                                        }
                                    }
                                },
                                enabled = !isBusy && sessionState.isSoAuthentic,
                                colors = ButtonDefaults.buttonColors(containerColor = palette.startButtonBg),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("activate_and_register_button")
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = "ACTIVATE KEY & REGISTER (လိုင်စင်ထည့်သွင်းမည်)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }

                            if (!hasGms || showOfflineFallbackBtn) {
                                androidx.compose.material3.OutlinedButton(
                                    onClick = {
                                        if (isBusy) return@OutlinedButton
                                        if (usernameInput.trim().length < 2 || !emailInput.trim().contains("@") ||
                                            passwordInput.trim().length < 6 || keyInput.trim().isEmpty()
                                        ) {
                                            isErrorFeedback = true
                                            feedbackMessage = "Fill Username, Email, Password (6+), and Key first"
                                            return@OutlinedButton
                                        }
                                        isBusy = true
                                        feedbackMessage = null
                                        scope.launch {
                                            val offlineRes = LicenseManager.activateKeyOfflineNoGms(
                                                context = context,
                                                username = usernameInput,
                                                email = emailInput,
                                                password = passwordInput,
                                                rawKeyInput = keyInput
                                            )
                                            isBusy = false
                                            offlineRes.onSuccess { unlockedState ->
                                                isErrorFeedback = false
                                                feedbackMessage = "Activated (Offline HWID Bound)"
                                                onLicenseActivated(unlockedState)
                                            }.onFailure { err ->
                                                isErrorFeedback = true
                                                feedbackMessage = err.message ?: "Offline activation failed"
                                            }
                                        }
                                    },
                                    enabled = !isBusy && sessionState.isSoAuthentic,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .testTag("activate_offline_nogms_button")
                                ) {
                                    Text(
                                        text = "OFFLINE ACTIVATE (NO GMS / CHINA ROM)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF59E0B)
                                    )
                                }
                            }
                        }
                    }
                }

                GateTab.LOGIN_RESTORE -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = palette.cardBg,
                        border = BorderStroke(1.dp, palette.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Option 1: 1-Tap Google Restore Button
                            if (currentUser != null) {
                                Button(
                                    onClick = {
                                        if (isBusy) return@Button
                                        isBusy = true
                                        feedbackMessage = null
                                        scope.launch {
                                            val res = LicenseManager.restoreLicenseWithGoogle(context)
                                            isBusy = false
                                            res.onSuccess { unlockedState ->
                                                isErrorFeedback = false
                                                feedbackMessage = "License Restored via Google Account (${currentUser?.email})!"
                                                onLicenseActivated(unlockedState)
                                            }.onFailure { err ->
                                                isErrorFeedback = true
                                                feedbackMessage = err.message ?: "Google License Restore failed"
                                            }
                                        }
                                    },
                                    enabled = !isBusy && sessionState.isSoAuthentic,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "RESTORE WITH GOOGLE (${currentUser?.email})",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                }
                            } else {
                                GoogleLoginDirectButton(
                                    currentUser = null,
                                    isBusy = isBusy,
                                    onSignInClick = {
                                        if (isBusy) return@GoogleLoginDirectButton
                                        isBusy = true
                                        feedbackMessage = null
                                        scope.launch {
                                            val signInRes = LicenseManager.signInWithGoogle(context)
                                            if (signInRes.isSuccess) {
                                                val restoreRes = LicenseManager.restoreLicenseWithGoogle(context)
                                                isBusy = false
                                                restoreRes.onSuccess { unlockedState ->
                                                    isErrorFeedback = false
                                                    feedbackMessage = "License Restored Successfully!"
                                                    onLicenseActivated(unlockedState)
                                                }.onFailure {
                                                    isErrorFeedback = false
                                                    feedbackMessage = "Google Account connected (${signInRes.getOrNull()?.email}). Please activate a key if new."
                                                }
                                            } else {
                                                isBusy = false
                                                isErrorFeedback = true
                                                feedbackMessage = signInRes.exceptionOrNull()?.message ?: "Google Sign-In failed"
                                            }
                                        }
                                    },
                                    onSignOutClick = {}
                                )
                            }

                            // Option 2: Dedicated Email & Password Divider
                            OrDivider(label = "OR RESTORE WITH EMAIL & PASSWORD (သီးသန့်)")

                            OutlinedTextField(
                                value = loginEmailInput,
                                onValueChange = { loginEmailInput = it },
                                label = { Text("Registered Email") },
                                singleLine = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = null,
                                        tint = palette.accentSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = palette.accentSecondary,
                                    unfocusedBorderColor = palette.cardBorder,
                                    focusedTextColor = palette.textPrimary,
                                    unfocusedTextColor = palette.textPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("login_email_input")
                            )

                            OutlinedTextField(
                                value = loginPasswordInput,
                                onValueChange = { loginPasswordInput = it },
                                label = { Text("Password") },
                                singleLine = true,
                                visualTransformation = if (showLoginPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = palette.accentSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    IconButton(onClick = { showLoginPassword = !showLoginPassword }) {
                                        Icon(
                                            imageVector = if (showLoginPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle Login Password"
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = palette.accentSecondary,
                                    unfocusedBorderColor = palette.cardBorder,
                                    focusedTextColor = palette.textPrimary,
                                    unfocusedTextColor = palette.textPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("login_password_input")
                            )

                            Button(
                                onClick = {
                                    if (isBusy) return@Button
                                    if (!loginEmailInput.trim().contains("@")) {
                                        isErrorFeedback = true
                                        feedbackMessage = "Invalid email address format"
                                        return@Button
                                    }
                                    if (loginPasswordInput.trim().isEmpty()) {
                                        isErrorFeedback = true
                                        feedbackMessage = "Please enter your account password"
                                        return@Button
                                    }

                                    isBusy = true
                                    feedbackMessage = null
                                    scope.launch {
                                        val loginRes = LicenseManager.loginAndRestoreAccount(
                                            context = context,
                                            email = loginEmailInput,
                                            password = loginPasswordInput
                                        )
                                        isBusy = false
                                        loginRes.onSuccess { unlockedState ->
                                            isErrorFeedback = false
                                            feedbackMessage = "License Restored Successfully!"
                                            onLicenseActivated(unlockedState)
                                        }.onFailure { err ->
                                            isErrorFeedback = true
                                            feedbackMessage = err.message ?: "Account restore failed"
                                        }
                                    }
                                },
                                enabled = !isBusy && sessionState.isSoAuthentic,
                                colors = ButtonDefaults.buttonColors(containerColor = palette.accentSecondary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("login_restore_button")
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.LockOpen,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = "SIGN IN & RESTORE LICENSE (လိုင်စင်ပြန်လည်ရယူမည်)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                GateTab.ADMIN_DEBUG -> {
                    if (BuildConfig.DEBUG) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = palette.cardBg,
                            border = BorderStroke(1.dp, palette.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                AdminMasterKeyPanel(
                                    onUseGeneratedKey = { generatedKey ->
                                        selectedTab = GateTab.ACTIVATE_NEW
                                        keyInput = generatedKey
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 3. System Security & License Warning Notice (English + Burmese)
            SecurityWarningCard(hwid = sessionState.deviceHwid)
        }
    }
}

@Composable
private fun GateTabButton(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) activeColor else Color.Transparent,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else palette.textSecondary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GoogleLoginDirectButton(
    currentUser: FirebaseUser?,
    isBusy: Boolean,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit
) {
    val palette = LocalAppPalette.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = palette.cardAltBg,
        border = BorderStroke(1.dp, palette.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.White,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "G",
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                color = Color(0xFF4285F4)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Google Account Direct (ဒါရိုက်)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                        Text(
                            text = if (currentUser != null) currentUser.email ?: "Connected" else "One-tap Google Identity sign-in",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (currentUser != null) Color(0xFF10B981) else palette.textSecondary
                        )
                    }
                }

                if (currentUser != null) {
                    Text(
                        text = "DISCONNECT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clickable { onSignOutClick() }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }

            if (currentUser == null) {
                Button(
                    onClick = { onSignInClick() },
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E293B),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF4285F4)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("google_direct_login_btn")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            color = Color(0xFF4285F4),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.White,
                            modifier = Modifier.size(18.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "G",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                    color = Color(0xFF4285F4)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = "Continue with Google (Google အကောင့်ဖြင့် ဝင်မည်)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Linked: ${currentUser.email}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF10B981),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OrDivider(label: String) {
    val palette = LocalAppPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = palette.divider)
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textSecondary,
            fontFamily = FontFamily.Monospace
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = palette.divider)
    }
}

@Composable
private fun ApkSecurityMatrixCard(sessionState: LicenseSessionState) {
    val palette = LocalAppPalette.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = palette.cardBg,
        border = BorderStroke(1.dp, palette.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "APK Security Guard",
                        tint = palette.accentPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "APK & NATIVE SECURITY MATRIX",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.textPrimary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (sessionState.isApkAuthentic && sessionState.isSoAuthentic && !sessionState.clockRollbackDetected)
                        Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f),
                    border = BorderStroke(
                        1.dp,
                        if (sessionState.isApkAuthentic && sessionState.isSoAuthentic && !sessionState.clockRollbackDetected)
                            Color(0xFF10B981) else Color(0xFFEF4444)
                    )
                ) {
                    Text(
                        text = if (sessionState.isApkAuthentic && sessionState.isSoAuthentic && !sessionState.clockRollbackDetected)
                            "ARMORED" else "SECURITY ALERT",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (sessionState.isApkAuthentic && sessionState.isSoAuthentic && !sessionState.clockRollbackDetected)
                            Color(0xFF10B981) else Color(0xFFEF4444),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            HorizontalDivider(color = palette.divider)

            // Grid of 4 Security Pillars
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Pillar 1: APK Signature
                SecurityPillarItem(
                    title = "APK Signature",
                    status = if (sessionState.isApkAuthentic) "VALID" else "TAMPERED",
                    detail = "SHA-256 Release",
                    isGood = sessionState.isApkAuthentic,
                    modifier = Modifier.weight(1f)
                )
                // Pillar 2: Native .so C++ Engine
                SecurityPillarItem(
                    title = "Native Engine",
                    status = if (sessionState.isSoAuthentic) "LOCKED" else "TAMPERED",
                    detail = "libjin_usb.so",
                    isGood = sessionState.isSoAuthentic,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Pillar 3: HWID Binding
                SecurityPillarItem(
                    title = "Device Lock",
                    status = if (sessionState.deviceHwid.isNotBlank()) "BOUND" else "UNKNOWN",
                    detail = if (sessionState.deviceHwid.length > 12) sessionState.deviceHwid.take(12) + "..." else sessionState.deviceHwid,
                    isGood = sessionState.deviceHwid.isNotBlank(),
                    modifier = Modifier.weight(1f)
                )
                // Pillar 4: Anti-Clock Rollback & Hook Guard
                SecurityPillarItem(
                    title = "Clock Guard",
                    status = if (!sessionState.clockRollbackDetected) "SECURE" else "ALERT",
                    detail = if (sessionState.clockRollbackDetected) "Rollback Detected" else "Tamper Guard OK",
                    isGood = !sessionState.clockRollbackDetected,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SecurityPillarItem(
    title: String,
    status: String,
    detail: String,
    isGood: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = palette.cardAltBg,
        border = BorderStroke(
            1.dp,
            if (isGood) Color(0xFF10B981).copy(alpha = 0.35f) else Color(0xFFEF4444).copy(alpha = 0.45f)
        ),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textSecondary
                )
                Text(
                    text = status,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isGood) Color(0xFF10B981) else Color(0xFFEF4444)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = detail,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SecurityWarningCard(hwid: String) {
    val palette = LocalAppPalette.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFEF4444).copy(alpha = 0.08f),
        border = BorderStroke(1.2.dp, Color(0xFFEF4444).copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = "SECURITY WARNING (သတိပေးချက်)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFEF4444)
                )
            }

            HorizontalDivider(color = Color(0xFFEF4444).copy(alpha = 0.25f))

            // Warning 1: HWID Device Lock
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                    modifier = Modifier.padding(top = 1.dp)
                ) {
                    Text(
                        text = "LOCK",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFEF4444),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "HWID Device Lock: Single device only ($hwid). Key sharing is blocked.",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                    Text(
                        text = "လိုင်စင်ကီးကို ဤဖုန်းတစ်ခုတည်းအတွက်သာ ကန့်သတ်ထားပြီး အခြားဖုန်းသို့ လွှဲပြောင်းမျှဝေခြင်းကို ပိတ်ပင်ထားသည်။",
                        fontSize = 10.sp,
                        color = palette.textSecondary
                    )
                }
            }

            // Warning 2: Anti-Tamper Ban
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                    modifier = Modifier.padding(top = 1.dp)
                ) {
                    Text(
                        text = "BAN",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFEF4444),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tamper Ban: Modifying APK, signature altering, or clock rollback triggers permanent HWID ban.",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                    Text(
                        text = "APK အား ပြင်ဆင်ခြင်း၊ Signature ပြောင်းလဲခြင်း သို့မဟုတ် နာရီ နောက်ပြန်လှည့်ပါက အပြီးတိုင် Ban ပါမည်။",
                        fontSize = 10.sp,
                        color = palette.textSecondary
                    )
                }
            }
        }
    }
}

