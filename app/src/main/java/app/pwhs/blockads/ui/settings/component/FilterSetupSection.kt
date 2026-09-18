package app.pwhs.blockads.ui.settings.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pwhs.blockads.R
import app.pwhs.blockads.data.entities.FilterList

@Composable
fun FilterSetupSection(
    modifier: Modifier = Modifier,
    filterLists: List<FilterList>,
    onNavigateToFilterSetup: () -> Unit = {},
) {
    SettingsCard(modifier = modifier) {
        Column {
            // Filter list navigation
            SettingItem(
                icon = Icons.Default.FilterList,
                title = stringResource(R.string.filter_setup_title),
                desc = stringResource(R.string.settings_filter_lists, filterLists.count { it.isEnabled }),
                onClick = onNavigateToFilterSetup
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}