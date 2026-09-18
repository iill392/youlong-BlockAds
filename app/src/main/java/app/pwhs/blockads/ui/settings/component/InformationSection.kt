package app.pwhs.blockads.ui.settings.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.pwhs.blockads.R

@Composable
fun InformationSection(
    onNavigateToAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SectionHeader(
            title = stringResource(R.string.settings_category_info),
            icon = Icons.Default.Info,
            description = stringResource(R.string.settings_category_info_desc)
        )
        SettingsCard {
            SettingItem(
                icon = Icons.Default.Info,
                title = stringResource(R.string.settings_about),
                desc = stringResource(R.string.settings_about_desc),
                onClick = onNavigateToAbout
            )
        }
    }
}
