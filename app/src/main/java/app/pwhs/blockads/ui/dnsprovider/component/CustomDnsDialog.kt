package app.pwhs.blockads.ui.dnsprovider.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pwhs.blockads.R
import app.pwhs.blockads.data.entities.DnsProtocol
import app.pwhs.blockads.ui.theme.TextSecondary

@Composable
fun CustomDnsDialog(
    upstreamDns: String,
    errorText: String? = null,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Build display value from current settings
    var editDns by remember { mutableStateOf(upstreamDns) }

    // Detect protocol from input
    val detectedProtocol by remember {
        derivedStateOf {
            val input = editDns.trim()
            when {
                input.startsWith("https://", ignoreCase = true) -> DnsProtocol.DOH
                input.startsWith("tls://", ignoreCase = true) -> DnsProtocol.DOT
                input.startsWith("quic://", ignoreCase = true) -> DnsProtocol.DOQ
                input.isNotBlank() -> DnsProtocol.PLAIN
                else -> null
            }
        }
    }

    AlertDialog(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dns_custom_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.settings_custom_dns_server),
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_custom_dns_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editDns,
                    onValueChange = { editDns = it },
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorText != null,
                    supportingText = errorText?.let { { Text(color = MaterialTheme.colorScheme.error, text = it) } },
                    placeholder = { Text(stringResource(R.string.settings_custom_dns_placeholder)) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                // Protocol detection badge
                if (detectedProtocol != null && editDns.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (icon, label, color) = when (detectedProtocol) {
                            DnsProtocol.DOH -> Triple(
                                Icons.Default.Security,
                                stringResource(R.string.settings_detected_doh),
                                MaterialTheme.colorScheme.primary
                            )
                            DnsProtocol.DOT -> Triple(
                                Icons.Default.Security,
                                stringResource(R.string.settings_detected_dot),
                                MaterialTheme.colorScheme.tertiary
                            )
                            DnsProtocol.DOQ -> Triple(
                                Icons.Default.Security,
                                stringResource(R.string.settings_detected_doq),
                                MaterialTheme.colorScheme.secondary
                            )
                            else -> Triple(
                                Icons.Default.Wifi,
                                stringResource(R.string.settings_detected_plain),
                                TextSecondary
                            )
                        }
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = color
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(editDns) },
                enabled = editDns.isNotBlank()
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
