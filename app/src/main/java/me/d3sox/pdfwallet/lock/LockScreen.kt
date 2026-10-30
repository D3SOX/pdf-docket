package me.d3sox.pdfwallet.lock

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import me.d3sox.pdfwallet.R

@Composable
fun LockScreen(onAuthenticated: () -> Unit, onAuthenticationDisabled: () -> Unit) {
    val activity = LocalActivity.current as FragmentActivity
    val authenticate = { activity.authenticate(onSuccess = onAuthenticated) }
    // e.g. the screen lock of the device was removed after enabling authentication
    var canAuthenticate by remember { mutableStateOf(activity.canAuthenticate()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canAuthenticate = activity.canAuthenticate()
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        // prompt once automatically, afterwards the user can retry with the button
        lifecycle.withResumed { if (activity.canAuthenticate()) authenticate() }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(32.dp))
            if (canAuthenticate) {
                Button(onClick = authenticate) {
                    Text(stringResource(R.string.authenticate))
                }
            } else {
                Text(
                    text = stringResource(R.string.authentication_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onAuthenticationDisabled) {
                    Text(stringResource(R.string.disable_authentication))
                }
            }
        }
    }
}
