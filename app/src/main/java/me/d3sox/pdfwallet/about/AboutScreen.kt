package me.d3sox.pdfwallet.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.d3sox.pdfwallet.BuildConfig
import me.d3sox.pdfwallet.R
import me.d3sox.pdfwallet.ui.components.IconColor
import me.d3sox.pdfwallet.ui.components.SettingsGroup
import me.d3sox.pdfwallet.ui.components.SettingsGroupScope
import me.d3sox.pdfwallet.ui.components.SettingsRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { innerPadding ->
        LazyColumn(contentPadding = innerPadding) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.app_logo),
                        contentDescription = null,
                        modifier = Modifier.size(96.dp),
                    )
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.about_description),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item {
                SettingsGroup(title = null) {
                    link(
                        R.string.source_code,
                        R.string.source_code_summary,
                        R.string.source_code_uri,
                        R.drawable.code_24px,
                        IconColor.BLUE,
                    )
                    link(
                        R.string.report_issue,
                        R.string.report_issue_summary,
                        R.string.issues_uri,
                        R.drawable.bug_report_24px,
                        IconColor.RED,
                    )
                    link(
                        R.string.changelog,
                        R.string.changelog_summary,
                        R.string.changelog_uri,
                        R.drawable.history_24px,
                        IconColor.PURPLE,
                    )
                    link(
                        R.string.privacy_policy,
                        R.string.privacy_policy_summary,
                        R.string.privacy_policy_uri,
                        R.drawable.policy_24px,
                        IconColor.GREEN,
                    )
                    link(
                        R.string.license,
                        R.string.license_summary,
                        R.string.license_uri,
                        R.drawable.license_24px,
                        IconColor.AMBER,
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.based_on) + "\n" + stringResource(R.string.icon_credits),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                )
            }
        }
    }
}

@Suppress("LongParameterList")
private fun SettingsGroupScope.link(title: Int, summary: Int, uri: Int, icon: Int, color: IconColor) = row { shapes ->
    val uriHandler = LocalUriHandler.current
    val target = stringResource(uri)
    SettingsRow(
        shapes = shapes,
        title = stringResource(title),
        summary = stringResource(summary),
        icon = icon,
        iconColor = color,
        onClick = { uriHandler.openUri(target) },
    ) {
        Icon(
            painterResource(R.drawable.open_in_new_24px),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
