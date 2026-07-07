package com.beacon.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.PermissionChecker
import com.beacon.data.IdentityRepository

/**
 * Staged onboarding: name -> why-we-need-permissions -> notifications (33+).
 * Explaining *why* before the system dialog is deliberate — nearby apps live
 * or die on this flow feeling trustworthy.
 */
@Composable
fun OnboardingScreen(
    identity: IdentityRepository,
    onDone: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    when (step) {
        0 -> NameStep(identity) { step = 1 }
        1 -> NearbyPermissionsStep { step = 2 }
        else -> NotificationsStep {
            identity.onboardingComplete = true
            onDone()
        }
    }
}

@Composable
private fun StepScaffold(
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        content()
    }
}

private val FUN_ADJECTIVES = listOf(
    "Cosmic", "Turbo", "Quiet", "Neon", "Mellow", "Swift", "Lucky", "Nova", "Zesty", "Echo",
)
private val FUN_NOUNS = listOf(
    "Otter", "Falcon", "Panda", "Comet", "Willow", "Pixel", "Ember", "Drift", "Maple", "Sona",
)

@Composable
private fun NameStep(identity: IdentityRepository, onNext: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(identity.displayName) }
    StepScaffold(
        title = "Welcome to Beacon",
        description = "Talk to people around you — no account, no profile. " +
            "Pick a display name; everything else about you disappears when you close the app.",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 40) name = it },
                label = { Text("Display name") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                name = "${FUN_ADJECTIVES.random()} ${FUN_NOUNS.random()}"
            }) {
                Icon(Icons.Default.Casino, contentDescription = "Roll a name")
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                identity.displayName = name
                onNext()
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Continue")
        }
        TextButton(onClick = {
            // Anonymity as a first-class starting point, not a buried toggle.
            identity.setAnonymous(true)
            onNext()
        }) {
            Text("Skip — stay anonymous")
        }
    }
}

@Composable
private fun NearbyPermissionsStep(onNext: () -> Unit) {
    val context = LocalContext.current
    val permissions = remember { PermissionSteps.nearbyPermissions() }
    var denied by rememberSaveable { mutableStateOf(false) }

    fun allGranted() = permissions.all {
        PermissionChecker.checkSelfPermission(context, it) == PermissionChecker.PERMISSION_GRANTED
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) onNext() else denied = true
    }

    StepScaffold(
        title = "How Beacon finds people",
        description = "Beacon uses Bluetooth and Wi-Fi to spot other Beacon users within a few dozen " +
            "meters — nothing goes through the internet, and your location is never uploaded anywhere.",
    ) {
        Button(
            onClick = { if (allGranted()) onNext() else launcher.launch(permissions.toTypedArray()) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Allow nearby access")
        }
        if (denied) {
            Spacer(Modifier.height(16.dp))
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Beacon can't see anyone without these permissions. " +
                            "If the dialog no longer appears, enable them in app settings.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            )
                        )
                    }) {
                        Text("Open settings")
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationsStep(onDone: () -> Unit) {
    val permission = remember { PermissionSteps.notificationPermission() }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onDone() } // skippable either way

    StepScaffold(
        title = "Stay in the loop",
        description = "A quiet notification shows while Beacon is active so you always know " +
            "when you're visible to others.",
    ) {
        Button(
            onClick = { if (permission != null) launcher.launch(permission) else onDone() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (permission != null) "Allow notifications" else "Get started")
        }
        if (permission != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDone) { Text("Skip for now") }
        }
    }
}
