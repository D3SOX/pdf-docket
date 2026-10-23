package com.michaeltroger.gruenerpass.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.michaeltroger.gruenerpass.R
import com.michaeltroger.gruenerpass.ui.components.TextInputDialog

@Composable
fun BackupDialogs(
    state: BackupState,
    onExportPasswordSet: (String) -> Unit,
    onRestorePasswordEntered: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        BackupState.Idle -> Unit
        BackupState.AskingExportPassword -> NewPasswordDialog(onConfirm = onExportPasswordSet, onDismiss = onDismiss)
        is BackupState.AskingRestorePassword -> TextInputDialog(
            title = stringResource(R.string.dialog_restore_password_title),
            label = stringResource(R.string.password),
            isPassword = true,
            // closes once the restore succeeds, stays open on a wrong password
            dismissOnConfirm = false,
            errorText = if (state.isWrongPassword) stringResource(R.string.wrong_password) else null,
            onConfirm = onRestorePasswordEntered,
            onDismiss = onDismiss,
        )
        BackupState.CreatingBackup -> ProgressDialog(stringResource(R.string.creating_backup))
        BackupState.RestoringBackup -> ProgressDialog(stringResource(R.string.restoring_backup))
    }
}

@Composable
private fun NewPasswordDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val isMismatch = repeated.isNotEmpty() && repeated != password
    val isValid = password.isNotEmpty() && repeated == password
    val confirm = {
        if (isValid) onConfirm(password)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_backup_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.dialog_backup_password_description))
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.password),
                    imeAction = ImeAction.Next,
                    modifier = Modifier.focusRequester(focusRequester),
                )
                PasswordField(
                    value = repeated,
                    onValueChange = { repeated = it },
                    label = stringResource(R.string.repeat_password),
                    errorText = if (isMismatch) stringResource(R.string.passwords_dont_match) else null,
                    imeAction = ImeAction.Done,
                    onDone = confirm,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = confirm, enabled = isValid) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
@Suppress("LongParameterList")
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    imeAction: ImeAction,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    onDone: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = errorText != null,
        supportingText = errorText?.let { { Text(it) } },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun ProgressDialog(text: String) {
    AlertDialog(
        // the work can't be interrupted
        onDismissRequest = {},
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                CircularProgressIndicator()
                Text(text)
            }
        },
        confirmButton = {},
    )
}
