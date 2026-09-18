package app.pwhs.blockads.ui.dnsprovider.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pwhs.blockads.R
import app.pwhs.blockads.ui.theme.TextSecondary

@Composable
fun FallbackDnsDialog(
    fallbackDns: String,
    errorText: String? = null,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var editFallback by remember { mutableStateOf(fallbackDns) }

    AlertDialog(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_fallback_dns)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.settings_fallback_dns_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = editFallback,
                    onValueChange = { editFallback = it },
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorText != null,
                    supportingText = errorText?.let { { Text(color = MaterialTheme.colorScheme.error, text = it) } },
                    placeholder = { Text(stringResource(R.string.settings_fallback_dns_placeholder)) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(editFallback) }
            ) {
                Text(stringResource(R.string.dns_custom_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dns_custom_cancel))
            }
        }
    )
}
