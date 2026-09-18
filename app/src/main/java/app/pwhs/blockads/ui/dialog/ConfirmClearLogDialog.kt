package app.pwhs.blockads.ui.dialog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.pwhs.blockads.R
import app.pwhs.blockads.ui.theme.DangerRed

@Composable
fun ConfirmClearLogDialog(
    modifier: Modifier = Modifier,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_clear_logs)) },
        text = { Text(stringResource(R.string.clear_logs_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onClear) {
                Text(
                    stringResource(R.string.settings_clear_logs),
                    color = DangerRed
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}