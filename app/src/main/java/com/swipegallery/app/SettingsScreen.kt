package com.swipegallery.app

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SettingsPage(hasAccess: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val appVersion = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0" }

    Column(Modifier.fillMaxSize().background(Canvas)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 22.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text("Settings", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("PREFERENCES & HELP", fontSize = 10.sp, letterSpacing = 1.2.sp, color = Muted, fontWeight = FontWeight.SemiBold)
            }
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingsSection("PERMISSIONS") {
                SettingsRow(
                    Icons.Rounded.PhotoLibrary,
                    "Photo & video access",
                    if (hasAccess) "Allowed · tap to change in system settings" else "Not allowed · tap to allow in system settings",
                    onClick = { openAppSettings(context) }
                )
            }
            SettingsSection("HELP") {
                SettingsRow(Icons.Rounded.BugReport, "Report an issue", SUPPORT_EMAIL, onClick = { reportIssue(context, appVersion) })
            }
        }
        // Footer stays pinned below the scrollable sections.
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 12.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Version $appVersion", fontSize = 11.sp, color = Muted)
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Made with", fontSize = 12.sp, color = Muted)
                Icon(Icons.Rounded.Favorite, "Love", tint = Color(0xFFE45167), modifier = Modifier.padding(horizontal = 4.dp).size(14.dp))
                Text("by Nitesh K Jain", fontSize = 12.sp, color = Muted)
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, Modifier.padding(start = 6.dp, top = 10.dp, bottom = 8.dp), fontSize = 10.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold, color = Muted)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Card)) { content() }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFE6EFE9)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Green, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 15.sp)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
        if (onClick != null) Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
    }
}

private fun reportIssue(context: Context, appVersion: String) {
    val body = "\n\n---\nApp version: $appVersion\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, "Swipe Gallery: issue report")
        putExtra(Intent.EXTRA_TEXT, body)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Support email", SUPPORT_EMAIL))
        Toast.makeText(context, "No email app found. $SUPPORT_EMAIL copied to clipboard.", Toast.LENGTH_LONG).show()
    }
}
