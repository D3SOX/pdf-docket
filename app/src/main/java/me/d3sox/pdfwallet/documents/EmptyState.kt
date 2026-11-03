package me.d3sox.pdfwallet.documents

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.d3sox.pdfwallet.R

/**
 * Shown instead of the list when there are no documents, or no archived ones in the archive.
 */
@Composable
fun EmptyState(isArchive: Boolean, onAddDocument: () -> Unit, modifier: Modifier) {
    if (isArchive) {
        EmptyState(
            icon = R.drawable.archive_24px,
            title = R.string.archive_empty_title,
            description = R.string.archive_empty_description,
            modifier = modifier,
        )
    } else {
        EmptyState(
            icon = R.drawable.picture_as_pdf_24,
            title = R.string.empty_state_title,
            description = R.string.empty_state_description,
            modifier = modifier,
        ) {
            Button(onClick = onAddDocument) {
                Text(stringResource(R.string.import_pdf))
            }
        }
    }
}

@Composable
private fun EmptyState(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes description: Int,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}
