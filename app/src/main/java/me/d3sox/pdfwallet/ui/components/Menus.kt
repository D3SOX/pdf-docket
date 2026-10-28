package me.d3sox.pdfwallet.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CheckableDropdownMenuItem
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemShapes
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource

/**
 * Collects the items of a [MenuScope.group], so each item can get the shape for its position.
 */
class MenuGroupScope {
    internal val items = mutableListOf<@Composable (MenuItemShapes) -> Unit>()

    fun item(content: @Composable (shapes: MenuItemShapes) -> Unit) {
        items += content
    }
}

class MenuScope {
    internal val groups = mutableListOf<List<@Composable (MenuItemShapes) -> Unit>>()

    fun group(content: MenuGroupScope.() -> Unit) {
        groups += MenuGroupScope().apply(content).items
    }
}

/**
 * A Material 3 Expressive menu in one rounded container, with dividers between the [MenuScope.group]s.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveMenu(expanded: Boolean, onDismissRequest: () -> Unit, content: MenuScope.() -> Unit) {
    val groups = MenuScope().apply(content).groups.filter { it.isNotEmpty() }
    val count = groups.sumOf { it.size }
    DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismissRequest) {
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
            var index = 0
            groups.forEachIndexed { groupIndex, items ->
                if (groupIndex > 0) HorizontalDivider(Modifier.padding(MenuDefaults.HorizontalDividerPadding))
                items.forEach { item ->
                    val shapes = MenuDefaults.itemShape(index++, count)
                    // selected items show a checkbox or radio button instead of a different shape
                    item(shapes.copy(selectedShape = shapes.shape))
                }
            }
        }
    }
}

/**
 * The selectable item colors, but without highlighting selected items.
 */
@Composable
private fun unhighlightedColors() = MenuDefaults.selectableItemColors().let {
    MenuDefaults.selectableItemColors(
        selectedTextColor = it.textColor,
        selectedContainerColor = it.containerColor,
        selectedLeadingIconColor = it.leadingIconColor,
        selectedTrailingContentColor = it.trailingContentColor,
    )
}

/**
 * An item that runs [onClick] after closing the menu with [onDismiss].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun MenuGroupScope.action(
    text: @Composable () -> String,
    icon: @Composable () -> Painter,
    onDismiss: () -> Unit,
    onClick: () -> Unit,
) = item { shapes ->
    DropdownMenuItem(
        onClick = {
            onDismiss()
            onClick()
        },
        text = { Text(text()) },
        shape = shapes.shape,
        leadingIcon = { Icon(icon(), contentDescription = null) },
    )
}

fun MenuGroupScope.action(
    @StringRes label: Int,
    @DrawableRes icon: Int,
    onDismiss: () -> Unit,
    onClick: () -> Unit,
) = action({ stringResource(label) }, { painterResource(icon) }, onDismiss, onClick)

/**
 * An item with a checkbox, for options that can be combined. The menu stays open to toggle several.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun MenuGroupScope.checkable(
    text: @Composable () -> String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: (@Composable () -> Painter)? = null,
) = item { shapes ->
    CheckableDropdownMenuItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        text = { Text(text()) },
        shapes = shapes,
        leadingIcon = icon?.let { { Icon(it(), contentDescription = null) } },
        trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
        colors = unhighlightedColors(),
    )
}

/**
 * An item of a single choice, like a sort order.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun MenuGroupScope.selectable(
    text: @Composable () -> String,
    icon: @Composable () -> Unit,
    selected: Boolean,
    onDismiss: () -> Unit,
    onClick: () -> Unit,
) = item { shapes ->
    SelectableDropdownMenuItem(
        selected = selected,
        onClick = {
            onDismiss()
            onClick()
        },
        text = { Text(text()) },
        shapes = shapes,
        leadingIcon = icon,
        trailingContent = { RadioButton(selected = selected, onClick = null) },
        colors = unhighlightedColors(),
    )
}
