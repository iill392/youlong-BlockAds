package app.pwhs.blockads.ui.settings.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.pwhs.blockads.R

@Composable
fun InterfaceSection(
    onNavigateToAppearance: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SectionHeader(
            title = stringResource(R.string.settings_category_interface),
            icon = Icons.Default.Palette,
            description = stringResource(R.string.settings_category_interface_desc)
        )
        SettingsCard(onClick = onNavigateToAppearance) {
            SettingItem(
                icon = Icons.Default.Palette,
                title = stringResource(R.string.settings_category_interface),
                desc = stringResource(R.string.settings_category_interface_desc)
            )
        }
    }
}
