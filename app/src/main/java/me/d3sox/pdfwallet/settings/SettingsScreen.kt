package me.d3sox.pdfwallet.settings

import android.content.res.Resources
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import me.d3sox.pdfwallet.R
import me.d3sox.pdfwallet.backup.BackupDialogs
import me.d3sox.pdfwallet.backup.BackupMessage
import me.d3sox.pdfwallet.backup.BackupViewModel
import me.d3sox.pdfwallet.lock.authenticate
import me.d3sox.pdfwallet.lock.canAuthenticate
import me.d3sox.pdfwallet.ui.LocalSnackbarHostState
import me.d3sox.pdfwallet.ui.components.IconColor
import me.d3sox.pdfwallet.ui.components.SettingsGroup
import me.d3sox.pdfwallet.ui.components.SettingsGroupScope
import me.d3sox.pdfwallet.ui.components.SettingsRow
import me.d3sox.pdfwallet.ui.components.SettingsSwitchRow
import me.d3sox.pdfwallet.ui.components.SingleChoiceDialog
import me.d3sox.pdfwallet.ui.theme.supportsDynamicColor

private enum class SettingsDialog { LOCK_TIMEOUT, BARCODE_MODE, THEME, TAG_FILTER_MODE }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        LazyColumn(contentPadding = innerPadding, modifier = Modifier.fillMaxSize()) {
            item {
                SettingsGroup(stringResource(R.string.settings_section_security)) {
                    if (canAuthenticate || settings.requireAuthentication) {
                        switchItem(
                            title = R.string.require_authentication,
                            icon = R.drawable.security_24,
                            color = IconColor.GREEN,
                            checked = settings.requireAuthentication,
                            onCheckedChange = { activity.authenticate(onSuccess = vm::onAuthenticationToggled) },
                        )
                    }
                    if (settings.requireAuthentication) {
                        row { shapes ->
                            SettingsRow(
                                shapes = shapes,
                                title = stringResource(R.string.preference_lock_timeout),
                                summary = settings.lockTimeout.label(),
                                icon = R.drawable.timer_24px,
                                iconColor = IconColor.PURPLE,
                                onClick = { dialog = SettingsDialog.LOCK_TIMEOUT },
                            )
                        }
                    }
                    switchItem(
                        title = R.string.preference_prevent_screenshots,
                        summary = R.string.preference_prevent_screenshots_description,
                        icon = R.drawable.screenshot_24,
                        color = IconColor.BLUE,
                        checked = settings.blockScreenshots,
                        enabled = !settings.requireAuthentication,
                        onCheckedChange = { checked -> vm.update { it.copy(preventScreenshots = checked) } },
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        switchItem(
                            title = R.string.preference_add_show_on_locked_screen,
                            summary = R.string.preference_add_show_on_locked_screen_description,
                            icon = R.drawable.door_open_24,
                            color = IconColor.ORANGE,
                            checked = settings.showOnLockScreen,
                            onCheckedChange = { checked -> vm.update { it.copy(showOnLockScreen = checked) } },
                        )
                    }
                }
            }
            item {
                SettingsGroup(stringResource(R.string.settings_section_barcodes)) {
                    choiceItem(
                        title = R.string.preference_barcode,
                        value = settings.barcodeSearchMode.label,
                        icon = R.drawable.qr_code_24,
                        color = IconColor.BLUE,
                        onClick = { dialog = SettingsDialog.BARCODE_MODE },
                    )
                    switchItem(
                        title = R.string.preference_half_size_barcodes,
                        summary = R.string.preference_half_size_barcodes_description,
                        icon = R.drawable.barcode_reader_24px,
                        color = IconColor.TEAL,
                        checked = settings.halfSizeBarcodes,
                        onCheckedChange = { checked -> vm.update { it.copy(halfSizeBarcodes = checked) } },
                    )
                    switchItem(
                        title = R.string.preference_split_screen,
                        summary = R.string.preference_split_screen_description,
                        icon = R.drawable.splitscreen_24,
                        color = IconColor.PURPLE,
                        checked = settings.splitScreen,
                        onCheckedChange = { checked -> vm.update { it.copy(splitScreen = checked) } },
                    )
                    switchItem(
                        title = R.string.preference_sync_pages,
                        summary = R.string.preference_sync_pages_description,
                        icon = R.drawable.sync_24,
                        color = IconColor.GREEN,
                        checked = settings.syncPages,
                        onCheckedChange = { checked -> vm.update { it.copy(syncPages = checked) } },
                    )
                    switchItem(
                        title = R.string.preference_reopen_at_last_position,
                        summary = R.string.preference_reopen_at_last_position_description,
                        icon = R.drawable.history_24px,
                        color = IconColor.BLUE,
                        checked = settings.reopenAtLastPosition,
                        onCheckedChange = { checked -> vm.update { it.copy(reopenAtLastPosition = checked) } },
                    )
                    switchItem(
                        title = R.string.preference_barcode_generation,
                        summary = R.string.preference_barcode_generation_description,
                        icon = R.drawable.construction_24px,
                        color = IconColor.AMBER,
                        checked = settings.generateNewBarcode,
                        onCheckedChange = { checked -> vm.update { it.copy(generateNewBarcode = checked) } },
                    )
                }
            }
            item {
                SettingsGroup(stringResource(R.string.settings_section_display)) {
                    choiceItem(
                        title = R.string.preference_force_theme,
                        value = settings.themeMode.label,
                        icon = R.drawable.contrast_24px,
                        color = IconColor.PURPLE,
                        onClick = { dialog = SettingsDialog.THEME },
                    )
                    if (supportsDynamicColor) {
                        switchItem(
                            title = R.string.preference_dynamic_color,
                            summary = R.string.preference_dynamic_color_description,
                            icon = R.drawable.palette_24,
                            color = IconColor.PINK,
                            checked = settings.dynamicColor,
                            onCheckedChange = { checked -> vm.update { it.copy(dynamicColor = checked) } },
                        )
                    }
                    switchItem(
                        title = R.string.preference_invert_pdf_colors,
                        summary = R.string.preference_invert_pdf_colors_description,
                        icon = R.drawable.invert_colors_24px,
                        color = IconColor.TEAL,
                        checked = settings.invertPdfColors,
                        onCheckedChange = { checked -> vm.update { it.copy(invertPdfColors = checked) } },
                    )
                    switchItem(
                        title = R.string.preference_page_numbers,
                        summary = R.string.preference_page_numbers_description,
                        icon = R.drawable.format_list_numbered_24,
                        color = IconColor.BLUE,
                        checked = settings.showPageNumbers,
                        onCheckedChange = { checked -> vm.update { it.copy(showPageNumbers = checked) } },
                    )
                    switchItem(
                        title = R.string.preference_max_brightness,
                        summary = R.string.preference_max_brightness_description,
                        icon = R.drawable.brightness_high_24,
                        color = IconColor.AMBER,
                        checked = settings.fullBrightness,
                        onCheckedChange = { checked -> vm.update { it.copy(fullBrightness = checked) } },
                    )
                }
            }
            item {
                SettingsGroup(stringResource(R.string.tags)) {
                    choiceItem(
                        title = R.string.tag_filter_mode_description,
                        value = settings.tagFilterMode.label,
                        icon = R.drawable.tag_24,
                        color = IconColor.ORANGE,
                        onClick = { dialog = SettingsDialog.TAG_FILTER_MODE },
                    )
                }
            }
            item {
                SettingsGroup(stringResource(R.string.settings_section_backup)) {
                    choiceItem(
                        title = R.string.create_backup,
                        value = R.string.create_backup_summary,
                        icon = R.drawable.save_24,
                        color = IconColor.BLUE,
                        onClick = backupVm::onCreateBackup,
                    )
                    choiceItem(
                        title = R.string.restore_backup,
                        value = R.string.restore_backup_summary,
                        icon = R.drawable.settings_backup_restore_24,
                        color = IconColor.GREEN,
                        // backups have no registered MIME type
                        onClick = { restorePicker.launch(arrayOf("*/*")) },
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
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
        SettingsDialog.LOCK_TIMEOUT -> SingleChoiceDialog(
            title = stringResource(R.string.preference_lock_timeout),
            options = LockTimeout.entries,
            selected = settings.lockTimeout,
            label = { it.label() },
            onSelect = { timeout -> vm.update { it.copy(lockTimeout = timeout) } },
            onDismiss = { dialog = null },
        )
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

@Composable
private fun LockTimeout.label(): String = when (this) {
    LockTimeout.IMMEDIATELY -> stringResource(R.string.lock_timeout_immediately)
    else -> duration.inWholeMinutes.toInt().let { pluralStringResource(R.plurals.lock_timeout_minutes, it, it) }
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

private fun backupFileName() = "pdf-docket-backup-${LocalDate.now()}.pdfdocket"

private fun Resources.message(message: BackupMessage): String = when (message) {
    BackupMessage.Created -> getString(R.string.backup_created)
    BackupMessage.CreateFailed -> getString(R.string.error_creating_backup)
    is BackupMessage.Restored -> getQuantityString(R.plurals.backup_restored, message.count, message.count)
    BackupMessage.NotABackup -> getString(R.string.error_not_a_backup)
    BackupMessage.RestoreFailed -> getString(R.string.error_reading_backup)
}

@Suppress("LongParameterList")
private fun SettingsGroupScope.switchItem(
    title: Int,
    icon: Int,
    color: IconColor,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: Int? = null,
    enabled: Boolean = true,
) = row { shapes ->
    SettingsSwitchRow(
        shapes = shapes,
        title = stringResource(title),
        summary = summary?.let { stringResource(it) },
        icon = icon,
        iconColor = color,
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
    )
}

private fun SettingsGroupScope.choiceItem(title: Int, value: Int, icon: Int, color: IconColor, onClick: () -> Unit) =
    row { shapes ->
        SettingsRow(
            shapes = shapes,
            title = stringResource(title),
            summary = stringResource(value),
            icon = icon,
            iconColor = color,
            onClick = onClick,
        )
    }

