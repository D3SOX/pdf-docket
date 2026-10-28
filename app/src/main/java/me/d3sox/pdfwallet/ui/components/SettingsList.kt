package me.d3sox.pdfwallet.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import me.d3sox.pdfwallet.R

/**
 * Colors for the circles behind settings icons: pastel circles with dark icons in light mode,
 * and the other way around in dark mode.
 */
@Suppress("MagicNumber")
enum class IconColor(
    private val lightContainer: Long,
    private val lightContent: Long,
    private val darkContainer: Long,
    private val darkContent: Long,
) {
    BLUE(0xFFD6E3FF, 0xFF1D4F91, 0xFF1F3F6E, 0xFFD6E3FF),
    GREEN(0xFFC8EFC9, 0xFF1E6B2E, 0xFF1F4D29, 0xFFC8EFC9),
    AMBER(0xFFFFE8A3, 0xFF7A5900, 0xFF5C4300, 0xFFFFE8A3),
    ORANGE(0xFFFFDBC8, 0xFF8A3E00, 0xFF6B3000, 0xFFFFDBC8),
    RED(0xFFFFDAD6, 0xFF9C1C1C, 0xFF7A1F1F, 0xFFFFDAD6),
    PURPLE(0xFFEADDFF, 0xFF5B3A9A, 0xFF47307A, 0xFFEADDFF),
    TEAL(0xFFBEEFEA, 0xFF00695F, 0xFF004F48, 0xFFBEEFEA),
    PINK(0xFFFFD8EC, 0xFF8E2465, 0xFF6E1B4E, 0xFFFFD8EC),
    ;

    fun container(dark: Boolean) = Color(if (dark) darkContainer else lightContainer)
    fun content(dark: Boolean) = Color(if (dark) darkContent else lightContent)
}

@Composable
fun SettingsIcon(@DrawableRes icon: Int, color: IconColor) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .background(color.container(dark), CircleShape),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = color.content(dark),
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Collects the rows of a [SettingsGroup], so each row can get the shape for its position.
 */
class SettingsGroupScope {
    internal val rows = mutableListOf<@Composable (ListItemShapes) -> Unit>()

    fun row(content: @Composable (shapes: ListItemShapes) -> Unit) {
        rows += content
    }
}

/**
 * Rows in a segmented list with rounded ends, under an optional [title].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsGroup(title: String?, modifier: Modifier = Modifier, content: SettingsGroupScope.() -> Unit) {
    val rows = SettingsGroupScope().apply(content).rows
    Column(modifier.padding(horizontal = 16.dp)) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            rows.forEachIndexed { index, row ->
                row(ListItemDefaults.segmentedShapes(index = index, count = rows.size).withoutSelection())
            }
        }
    }
}

/**
 * The segmented list colors, but without highlighting checked rows, so each group keeps one background.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun segmentedColorsWithoutSelection() = ListItemDefaults.segmentedColors().run {
    copy(
        selectedContainerColor = containerColor,
        selectedContentColor = contentColor,
        selectedLeadingContentColor = leadingContentColor,
        selectedTrailingContentColor = trailingContentColor,
        selectedOverlineContentColor = overlineContentColor,
        selectedSupportingContentColor = supportingContentColor,
    )
}

/**
 * Keeps checked and selected rows in the same shape as the others.
 */
fun ListItemShapes.withoutSelection(): ListItemShapes = copy(selectedShape = shape)

/**
 * A clickable row of a [SettingsGroup].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
@Suppress("LongParameterList")
fun SettingsRow(
    shapes: ListItemShapes,
    title: String,
    @DrawableRes icon: Int,
    iconColor: IconColor,
    onClick: () -> Unit,
    summary: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        onClick = onClick,
        shapes = shapes,
        colors = segmentedColorsWithoutSelection(),
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = { SettingsIcon(icon, iconColor) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = trailing,
    ) {
        Text(title)
    }
}

/**
 * A row of a [SettingsGroup] with a switch.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
@Suppress("LongParameterList")
fun SettingsSwitchRow(
    shapes: ListItemShapes,
    title: String,
    @DrawableRes icon: Int,
    iconColor: IconColor,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
) {
    ListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        shapes = shapes,
        colors = segmentedColorsWithoutSelection(),
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = { SettingsIcon(icon, iconColor) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = { IconSwitch(checked = checked, onCheckedChange = null, enabled = enabled) },
    ) {
        Text(title)
    }
}

/**
 * A switch with a check mark when on and a dash when off.
 */
@Composable
fun IconSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        thumbContent = {
            Icon(
                painter = if (checked) {
                    rememberVectorPainter(Icons.Filled.Check)
                } else {
                    painterResource(R.drawable.remove_24px)
                },
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize),
            )
        },
    )
}
