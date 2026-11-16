package me.d3sox.pdfwallet.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import me.d3sox.pdfwallet.R

private const val MULTI_LINE_MAX_LINES = 6

@Composable
fun ConfirmDialog(
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initialValue: String = "",
    text: String? = null,
    isPassword: Boolean = false,
    dismissOnConfirm: Boolean = true,
    errorText: String? = null,
    /** for longer text, which can't be confirmed with the keyboard then */
    singleLine: Boolean = true,
    /** whether the text can't be empty */
    required: Boolean = true,
) {
    val initial = { mutableStateOf(TextFieldValue(initialValue, selection = TextRange(0, initialValue.length))) }
    // a password isn't kept in the saved state
    var value by if (isPassword) {
        remember(calculation = initial)
    } else {
        rememberSaveable(stateSaver = TextFieldValue.Saver, init = initial)
    }
    val focusRequester = remember { FocusRequester() }
    val visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None
    // a password is used as it is, as when it was set
    val isValid = !required || if (isPassword) value.text.isNotEmpty() else value.text.isNotBlank()
    val confirm = {
        if (isValid) {
            onConfirm(if (isPassword) value.text else value.text.trim())
            if (dismissOnConfirm) onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                text?.let { Text(it) }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(label) },
                    isError = errorText != null,
                    supportingText = errorText?.let { { Text(it) } },
                    singleLine = singleLine,
                    maxLines = if (singleLine) 1 else MULTI_LINE_MAX_LINES,
                    visualTransformation = visualTransformation,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
                        imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
                    ),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> SingleChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                modifier = Modifier.selectableGroup(),
            ) {
                options.forEachIndexed { index, option ->
                    ListItem(
                        selected = option == selected,
                        onClick = {
                            onSelect(option)
                            onDismiss()
                        },
                        shapes = ListItemDefaults.segmentedShapes(index, options.size).withoutSelection(),
                        colors = segmentedColorsWithoutSelection(),
                        leadingContent = { RadioButton(selected = option == selected, onClick = null) },
                        modifier = Modifier.semantics { role = Role.RadioButton },
                    ) {
                        Text(label(option))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
