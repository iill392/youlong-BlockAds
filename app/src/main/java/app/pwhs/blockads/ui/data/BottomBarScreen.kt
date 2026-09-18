package app.pwhs.blockads.ui.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.pwhs.blockads.R

enum class BottomBarScreen(
    @StringRes val labelRes: Int,
    @DrawableRes val icon: Int,
) {
    Home(
        labelRes = R.string.nav_home,
        icon = R.drawable.ic_home
    ),

    FilterSetup(
        labelRes = R.string.nav_filter,
        icon = R.drawable.ic_shield
    ),

    Firewall(
        labelRes = R.string.settings_firewall,
        icon = R.drawable.ic_fire
    ),

    DomainRule(
        labelRes = R.string.domain_rules_title,
        icon = R.drawable.ic_crown
    ),

    Settings(
        labelRes = R.string.nav_settings,
        icon = R.drawable.ic_setting
    )
}