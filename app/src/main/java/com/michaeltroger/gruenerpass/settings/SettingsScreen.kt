package com.michaeltroger.gruenerpass.settings

import android.content.res.Resources
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.michaeltroger.gruenerpass.R
import com.michaeltroger.gruenerpass.backup.BackupDialogs
import com.michaeltroger.gruenerpass.backup.BackupMessage
import com.michaeltroger.gruenerpass.backup.BackupViewModel
import com.michaeltroger.gruenerpass.lock.authenticate
import com.michaeltroger.gruenerpass.lock.canAuthenticate
import com.michaeltroger.gruenerpass.ui.LocalSnackbarHostState
import com.michaeltroger.gruenerpass.ui.components.SingleChoiceDialog
import com.michaeltroger.gruenerpass.ui.theme.supportsDynamicColor
import java.time.LocalDate

private enum class SettingsDialog { BARCODE_MODE, THEME, TAG_FILTER_MODE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
    backupVm: BackupViewModel = hiltViewModel(),
) {
    val state by vm.settings.collectAsStateWithLifecycle()
    val settings = state ?: return
    val activity = LocalActivity.current as FragmentActivity
    val canAuthenticate = remember { activity.canAuthenticate() }
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val backupState by backupVm.state.collectAsStateWithLifecycle()
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE),
        backupVm::onExportFileSelected,
    )
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(backupVm::onRestoreFileSelected)
    }
    val snackbarHostState = LocalSnackbarHostState.current
    val resources = LocalResources.current
    LaunchedEffect(backupVm) {
        backupVm.messages.collect { snackbarHostState.showSnackbar(resources.message(it)) }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        LazyColumn(contentPadding = innerPadding, modifier = Modifier.fillMaxSize()) {
            section(R.string.settings_section_security)
            if (canAuthenticate || settings.requireAuthentication) {
                item {
                    SwitchItem(
                        title = R.string.require_authentication,
                        icon = R.drawable.security_24,
                        checked = settings.requireAuthentication,
                        onCheckedChange = { activity.authenticate(onSuccess = vm::onAuthenticationToggled) },
                    )
                }
            }
            item {
                SwitchItem(
                    title = R.string.preference_prevent_screenshots,
                    summary = R.string.preference_prevent_screenshots_description,
                    icon = R.drawable.screenshot_24,
                    checked = settings.blockScreenshots,
                    enabled = !settings.requireAuthentication,
                    onCheckedChange = { checked -> vm.update { it.copy(preventScreenshots = checked) } },
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                item {
                    SwitchItem(
                        title = R.string.preference_add_show_on_locked_screen,
                        summary = R.string.preference_add_show_on_locked_screen_description,
                        icon = R.drawable.door_open_24,
                        checked = settings.showOnLockScreen,
                        onCheckedChange = { checked -> vm.update { it.copy(showOnLockScreen = checked) } },
                    )
                }
            }

            section(R.string.settings_section_barcodes)
            item {
                ChoiceItem(
                    title = R.string.preference_barcode,
                    value = settings.barcodeSearchMode.label,
                    icon = R.drawable.qr_code_24,
                    onClick = { dialog = SettingsDialog.BARCODE_MODE },
                )
            }
            item {
                SwitchItem(
                    title = R.string.preference_half_size_barcodes,
                    summary = R.string.preference_half_size_barcodes_description,
                    icon = R.drawable.barcode_reader_24px,
                    checked = settings.halfSizeBarcodes,
                    onCheckedChange = { checked -> vm.update { it.copy(halfSizeBarcodes = checked) } },
                )
            }
            item {
                SwitchItem(
                    title = R.string.preference_split_screen,
                    summary = R.string.preference_split_screen_description,
                    icon = R.drawable.splitscreen_24,
                    checked = settings.splitScreen,
                    onCheckedChange = { checked -> vm.update { it.copy(splitScreen = checked) } },
                )
            }
            item {
                SwitchItem(
                    title = R.string.preference_barcode_generation,
                    summary = R.string.preference_barcode_generation_description,
                    icon = R.drawable.construction_24px,
                    checked = settings.generateNewBarcode,
                    onCheckedChange = { checked -> vm.update { it.copy(generateNewBarcode = checked) } },
                )
            }

            section(R.string.settings_section_display)
            item {
                ChoiceItem(
                    title = R.string.preference_force_theme,
                    value = settings.themeMode.label,
                    icon = R.drawable.contrast_24px,
                    onClick = { dialog = SettingsDialog.THEME },
                )
            }
            if (supportsDynamicColor) {
                item {
                    SwitchItem(
                        title = R.string.preference_dynamic_color,
                        summary = R.string.preference_dynamic_color_description,
                        icon = R.drawable.palette_24,
                        checked = settings.dynamicColor,
                        onCheckedChange = { checked -> vm.update { it.copy(dynamicColor = checked) } },
                    )
                }
            }
            item {
                SwitchItem(
                    title = R.string.preference_invert_pdf_colors,
                    summary = R.string.preference_invert_pdf_colors_description,
                    icon = R.drawable.invert_colors_24px,
                    checked = settings.invertPdfColors,
                    onCheckedChange = { checked -> vm.update { it.copy(invertPdfColors = checked) } },
                )
            }
            item {
                SwitchItem(
                    title = R.string.preference_max_brightness,
                    summary = R.string.preference_max_brightness_description,
                    icon = R.drawable.brightness_high_24,
                    checked = settings.fullBrightness,
                    onCheckedChange = { checked -> vm.update { it.copy(fullBrightness = checked) } },
                )
            }

            section(R.string.tags)
            item {
                ChoiceItem(
                    title = R.string.tag_filter_mode_description,
                    value = settings.tagFilterMode.label,
                    icon = R.drawable.tag_24,
                    onClick = { dialog = SettingsDialog.TAG_FILTER_MODE },
                )
            }

            section(R.string.settings_section_backup)
            item {
                ChoiceItem(
                    title = R.string.create_backup,
                    value = R.string.create_backup_summary,
                    icon = R.drawable.save_24,
                    onClick = backupVm::onCreateBackup,
                )
            }
            item {
                ChoiceItem(
                    title = R.string.restore_backup,
                    value = R.string.restore_backup_summary,
                    icon = R.drawable.settings_backup_restore_24,
                    // backups have no registered MIME type
                    onClick = { restorePicker.launch(arrayOf("*/*")) },
                )
            }
        }
    }

    BackupDialogs(
        state = backupState,
        onExportPasswordSet = {
            backupVm.onExportPasswordSet(it)
            exportPicker.launch(backupFileName())
        },
        onRestorePasswordEntered = backupVm::onRestorePasswordEntered,
        onDismiss = backupVm::onDialogDismissed,
    )

    when (dialog) {
        null -> Unit
        SettingsDialog.BARCODE_MODE -> SingleChoiceDialog(
            title = stringResource(R.string.preference_barcode),
            options = BarcodeSearchMode.entries,
            selected = settings.barcodeSearchMode,
            label = { stringResource(it.label) },
            onSelect = { mode -> vm.update { it.copy(barcodeSearchMode = mode) } },
            onDismiss = { dialog = null },
        )
        SettingsDialog.THEME -> SingleChoiceDialog(
            title = stringResource(R.string.preference_force_theme),
            options = ThemeMode.entries,
            selected = settings.themeMode,
            label = { stringResource(it.label) },
            onSelect = { mode -> vm.update { it.copy(themeMode = mode) } },
            onDismiss = { dialog = null },
        )
        SettingsDialog.TAG_FILTER_MODE -> SingleChoiceDialog(
            title = stringResource(R.string.tag_filter_mode_description),
            options = TagFilterMode.entries,
            selected = settings.tagFilterMode,
            label = { stringResource(it.label) },
            onSelect = { mode -> vm.update { it.copy(tagFilterMode = mode) } },
            onDismiss = { dialog = null },
        )
    }
}

private val BarcodeSearchMode.label: Int
    get() = when (this) {
        BarcodeSearchMode.DISABLED -> R.string.preference_barcode_disabled
        BarcodeSearchMode.REGULAR -> R.string.preference_barcode_regular
        BarcodeSearchMode.EXTENDED -> R.string.preference_barcode_extended
    }

private val ThemeMode.label: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.preference_theme_system
        ThemeMode.LIGHT -> R.string.preference_theme_light
        ThemeMode.DARK -> R.string.preference_theme_dark
    }

private val TagFilterMode.label: Int
    get() = when (this) {
        TagFilterMode.ALL -> R.string.filter_tag_and_mode
        TagFilterMode.ANY -> R.string.filter_tag_or_mode
    }

private const val BACKUP_MIME_TYPE = "application/octet-stream"

private fun backupFileName() = "pdfwallet-backup-${LocalDate.now()}.pdfwallet"

private fun Resources.message(message: BackupMessage): String = when (message) {
    BackupMessage.Created -> getString(R.string.backup_created)
    BackupMessage.CreateFailed -> getString(R.string.error_creating_backup)
    is BackupMessage.Restored -> getQuantityString(R.plurals.backup_restored, message.count, message.count)
    BackupMessage.NotABackup -> getString(R.string.error_not_a_backup)
    BackupMessage.RestoreFailed -> getString(R.string.error_reading_backup)
}

private fun LazyListScope.section(title: Int) {
    item(contentType = "section") {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        )
    }
}

@Composable
@Suppress("LongParameterList")
private fun SwitchItem(
    title: Int,
    icon: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: Int? = null,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = summary?.let { { Text(stringResource(it)) } },
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        modifier = Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
    )
}

@Composable
private fun ChoiceItem(title: Int, value: Int, icon: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(value)) },
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
