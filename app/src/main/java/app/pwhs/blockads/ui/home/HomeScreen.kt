package app.pwhs.blockads.ui.home

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DataSaverOn
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pwhs.blockads.R
import app.pwhs.blockads.data.datastore.AppPreferences
import app.pwhs.blockads.data.repository.FilterListRepository
import app.pwhs.blockads.ui.home.component.AnimatedProgressBar
import app.pwhs.blockads.ui.home.component.BlockRateRing
import app.pwhs.blockads.ui.home.component.DailyStatsChart
import app.pwhs.blockads.ui.home.component.HomeAppBar
import app.pwhs.blockads.ui.home.component.HomeSectionHeader
import app.pwhs.blockads.ui.home.component.HomeSegmentedControl
import app.pwhs.blockads.ui.home.component.MetricTile
import app.pwhs.blockads.ui.home.component.PowerButton
import app.pwhs.blockads.ui.home.component.Reveal
import app.pwhs.blockads.ui.home.component.StatsChart
import app.pwhs.blockads.ui.logs.data.LogFilterStatus
import app.pwhs.blockads.ui.theme.AccentBlue
import app.pwhs.blockads.ui.theme.DangerRed
import app.pwhs.blockads.ui.theme.SecurityOrange
import app.pwhs.blockads.ui.theme.TextSecondary
import app.pwhs.blockads.ui.theme.TextTertiary
import app.pwhs.blockads.utils.AppConstants.AVG_AD_SIZE_KB
import app.pwhs.blockads.utils.VpnUtils
import app.pwhs.blockads.utils.formatCount
import app.pwhs.blockads.utils.formatDataSize
import app.pwhs.blockads.utils.formatTimeSince
import app.pwhs.blockads.utils.formatUptimeShort
import app.pwhs.blockads.utils.profileIcon
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import org.koin.androidx.compose.koinViewModel

/**
 * Home dashboard.
 *
 * Layout principle: one open hero (power control + status) resting on an
 * ambient colour wash, then everything else grouped into a few wide panels.
 * Every value that changes over time is animated, and each block fades in with
 * a small stagger, so the page settles instead of snapping into place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onShowVpnConflictDialog: () -> Unit = {},
    onRequestVpnPermission: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
    onNavigateToStatisticsScreen: () -> Unit = {},
    onNavigateToLogScreen: (LogFilterStatus) -> Unit = {},
    onNavigateToProfileScreen: () -> Unit = {},
) {
    val vpnEnabled by viewModel.vpnEnabled.collectAsStateWithLifecycle()
    val vpnConnecting by viewModel.vpnConnecting.collectAsStateWithLifecycle()
    val vpnStopping by viewModel.vpnStopping.collectAsStateWithLifecycle()
    val blockedCount by viewModel.blockedCount.collectAsStateWithLifecycle()
    val domainCount by viewModel.domainCount.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()
    val securityThreatsBlocked by viewModel.securityThreatsBlocked.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val filterLoadFailed by viewModel.filterLoadFailed.collectAsStateWithLifecycle()
    val recentBlocked by viewModel.recentBlocked.collectAsStateWithLifecycle()
    val hourlyStats by viewModel.hourlyStats.collectAsStateWithLifecycle()
    val dailyStats by viewModel.dailyStats.collectAsStateWithLifecycle()
    val topBlockedDomains by viewModel.topBlockedDomains.collectAsStateWithLifecycle()
    val protectionUptimeMs by viewModel.protectionUptimeMs.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val securityFilterIds by viewModel.securityFilterIds.collectAsStateWithLifecycle()
    val routingMode by viewModel.routingMode.collectAsStateWithLifecycle()
    val privateDnsWarning by viewModel.privateDnsWarning.collectAsStateWithLifecycle()
    val pausedByTrusted by viewModel.pausedByTrusted.collectAsStateWithLifecycle()
    val pausedTrustedSsid by viewModel.pausedTrustedSsid.collectAsStateWithLifecycle()

    // Show the trusted-network paused state only while actually off.
    val showTrustedPause = pausedByTrusted && !vpnEnabled && !vpnConnecting && !vpnStopping
    val context = LocalContext.current
    val isRootMode = routingMode == AppPreferences.ROUTING_MODE_ROOT

    LaunchedEffect(Unit) {
        viewModel.preloadFilter()
    }

    // ── Animated presentation state ──────────────────────────────────────────
    val statusText = when {
        vpnStopping -> stringResource(R.string.status_disconnecting)
        vpnConnecting -> stringResource(R.string.status_connecting)
        vpnEnabled -> stringResource(R.string.status_protected)
        showTrustedPause -> stringResource(R.string.status_paused)
        else -> stringResource(R.string.status_unprotected)
    }
    val statusColor by animateColorAsState(
        targetValue = when {
            vpnStopping -> SecurityOrange
            vpnConnecting -> AccentBlue
            vpnEnabled -> MaterialTheme.colorScheme.primary
            showTrustedPause -> SecurityOrange
            else -> DangerRed
        },
        animationSpec = tween(500),
        label = "statusColor"
    )
    val descriptionText = when {
        vpnStopping -> stringResource(
            if (isRootMode) R.string.home_disconnecting_desc_root else R.string.home_disconnecting_desc
        )
        vpnConnecting -> stringResource(
            if (isRootMode) R.string.home_connecting_desc_root else R.string.home_connecting_desc
        )
        vpnEnabled -> stringResource(R.string.home_protected_desc)
        showTrustedPause -> stringResource(R.string.home_paused_trusted_short)
        else -> stringResource(R.string.home_unprotected_desc)
    }

    val blockRate = if (totalCount > 0) (blockedCount * 100f / totalCount) else 0f
    val animatedBlockRate by animateFloatAsState(
        targetValue = blockRate,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "blockRate"
    )
    val percentText = String.format(
        androidx.compose.ui.text.intl.Locale.current.platformLocale,
        "%.1f",
        animatedBlockRate
    ) + "%"

    val animatedTotal by animateIntAsState(totalCount, tween(700), label = "total")
    val animatedBlocked by animateIntAsState(blockedCount, tween(700), label = "blocked")
    val animatedThreats by animateIntAsState(securityThreatsBlocked, tween(700), label = "threats")
    val animatedDomains by animateIntAsState(domainCount, tween(700), label = "domains")
    val dataSavedKb = animatedBlocked * AVG_AD_SIZE_KB

    var selectedChartTab by rememberSaveable { mutableIntStateOf(0) }

    val haptic = LocalHapticFeedback.current
    val isFirstVpnChange = remember { mutableStateOf(true) }
    LaunchedEffect(vpnEnabled) {
        if (isFirstVpnChange.value) {
            isFirstVpnChange.value = false
        } else {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            HomeAppBar(
                isLoading = isLoading,
                filterLoadFailed = filterLoadFailed,
                viewModel = viewModel,
                onNavigateToStatisticsScreen = onNavigateToStatisticsScreen,
                onNavigateToLogScreen = { onNavigateToLogScreen(LogFilterStatus.ALL) }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Ambient colour wash — the whole page tint follows the current
            // protection state and cross-fades with it.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(460.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(statusColor.copy(alpha = 0.16f), Color.Transparent)
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // The header space is reserved *outside* the scroll viewport.
                    // Applied inside the scroll (as it was) the padding just
                    // scrolls away, so the first card slides up behind the
                    // translucent app bar and — with the status bar inset gone —
                    // behind the clock/battery area as well. Clipping the
                    // viewport below the bar keeps both areas clear at any
                    // scroll offset.
                    .padding(top = innerPadding.calculateTopPadding())
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = innerPadding.calculateBottomPadding())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── 1. Hero: notice + power control + status ──────────────────
                Reveal(index = 0, modifier = Modifier.fillMaxWidth()) {
                    if (privateDnsWarning) {
                        PrivateDnsNotice()
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PowerButton(
                            isActive = vpnEnabled,
                            isConnecting = vpnConnecting,
                            isStopping = vpnStopping,
                            onClick = {
                                if (!vpnConnecting && !vpnStopping) {
                                    if (vpnEnabled) {
                                        viewModel.stopVpn(context)
                                    } else {
                                        if (!isRootMode && VpnUtils.isOtherVpnActive(context)) {
                                            onShowVpnConflictDialog()
                                        } else {
                                            onRequestVpnPermission()
                                        }
                                    }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        AnimatedContent(
                            targetState = statusText,
                            transitionSpec = {
                                fadeIn(tween(260)).togetherWith(fadeOut(tween(160)))
                            },
                            label = "statusText"
                        ) { text ->
                            Text(
                                text = text,
                                style = MaterialTheme.typography.headlineMedium,
                                color = statusColor,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = descriptionText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (showTrustedPause) {
                                HeroPill(
                                    text = pausedTrustedSsid.ifEmpty {
                                        stringResource(R.string.trusted_networks_paused_title)
                                    },
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    leadIcon = Icons.Default.Wifi
                                )
                            } else {
                                HeroPill(
                                    text = when (routingMode) {
                                        AppPreferences.ROUTING_MODE_ROOT -> "Root Proxy Mode"
                                        AppPreferences.ROUTING_MODE_WIREGUARD -> "WireGuard Mode"
                                        else -> "Local VPN Mode"
                                    },
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    contentColor = TextSecondary
                                )
                            }
                            HeroPill(
                                text = activeProfile?.name ?: stringResource(R.string.profile_name_default),
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                contentColor = MaterialTheme.colorScheme.primary,
                                leadIcon = profileIcon(activeProfile?.profileType),
                                trailIconRes = R.drawable.ic_edit,
                                onClick = onNavigateToProfileScreen
                            )
                        }
                    }
                }

                // ── 2. Metrics panel ─────────────────────────────────────────
                Reveal(index = 1, modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(22.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BlockRateRing(
                                    fraction = blockRate / 100f,
                                    centerText = percentText,
                                    ringSize = 104.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(18.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.home_block_rate),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    AnimatedProgressBar(fraction = blockRate / 100f)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "${formatCount(animatedBlocked)} / ${formatCount(animatedTotal)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextSecondary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MetricTile(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.QueryStats,
                                    label = stringResource(R.string.total_queries),
                                    value = formatCount(animatedTotal),
                                    color = MaterialTheme.colorScheme.secondary,
                                    onClick = { onNavigateToLogScreen(LogFilterStatus.ALL) }
                                )
                                MetricDivider()
                                MetricTile(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.Block,
                                    label = stringResource(R.string.blocked_queries),
                                    value = formatCount(animatedBlocked),
                                    color = DangerRed,
                                    onClick = { onNavigateToLogScreen(LogFilterStatus.BLOCKED) }
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MetricTile(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.GppGood,
                                    label = stringResource(R.string.home_security_threats),
                                    value = formatCount(animatedThreats),
                                    color = SecurityOrange,
                                    onClick = { onNavigateToLogScreen(LogFilterStatus.THREATS) }
                                )
                                MetricDivider()
                                MetricTile(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.Shield,
                                    label = stringResource(R.string.home_filter_rules),
                                    value = formatCount(animatedDomains),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MetricTile(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.DataSaverOn,
                                    label = stringResource(R.string.home_data_saved),
                                    value = formatDataSize(dataSavedKb),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                MetricDivider()
                                MetricTile(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.Timer,
                                    label = stringResource(R.string.home_protection_uptime),
                                    value = formatUptimeShort(protectionUptimeMs),
                                    color = AccentBlue
                                )
                            }
                        }
                    }
                }

                // ── 3. Traffic chart ─────────────────────────────────────────
                if (hourlyStats.isNotEmpty() || dailyStats.isNotEmpty()) {
                    Reveal(index = 2, modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(26.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                HomeSectionHeader(
                                    title = stringResource(R.string.nav_statistics),
                                    modifier = Modifier.fillMaxWidth(),
                                    trailing = {
                                        HomeSegmentedControl(
                                            options = listOf(
                                                stringResource(R.string.home_chart_24h),
                                                stringResource(R.string.home_chart_7d)
                                            ),
                                            selectedIndex = selectedChartTab,
                                            onSelect = { selectedChartTab = it },
                                            modifier = Modifier.width(148.dp)
                                        )
                                    }
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                AnimatedContent(
                                    targetState = selectedChartTab,
                                    transitionSpec = {
                                        (
                                            fadeIn(tween(300)) +
                                                slideInHorizontally(tween(300)) { it / 14 }
                                            ).togetherWith(
                                            fadeOut(tween(180)) +
                                                slideOutHorizontally(tween(180)) { -it / 14 }
                                        )
                                    },
                                    label = "chartTab"
                                ) { tab ->
                                    val hasData =
                                        if (tab == 0) hourlyStats.isNotEmpty() else dailyStats.isNotEmpty()
                                    if (hasData) {
                                        if (tab == 0) {
                                            StatsChart(
                                                stats = hourlyStats,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(180.dp)
                                            )
                                        } else {
                                            DailyStatsChart(
                                                stats = dailyStats,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(180.dp)
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(180.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = stringResource(R.string.home_chart_no_data),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 4. Top blocked domains ───────────────────────────────────
                if (topBlockedDomains.isNotEmpty()) {
                    Reveal(index = 3, modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(26.dp))
                        HomeSectionHeader(
                            title = stringResource(R.string.home_top_blocked),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                topBlockedDomains.forEachIndexed { index, entry ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (index < 3) {
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                                    } else {
                                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${index + 1}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (index < 3) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    TextTertiary
                                                },
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = entry.domain,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(DangerRed.copy(alpha = 0.12f))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = formatCount(entry.count),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = DangerRed,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 5. Recent blocks ─────────────────────────────────────────
                if (recentBlocked.isNotEmpty()) {
                    Reveal(index = 4, modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(26.dp))
                        HomeSectionHeader(
                            title = stringResource(R.string.home_recent_blocked),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                recentBlocked.forEach { entry ->
                                    val blockedByIds = entry.blockedBy.split(",")
                                    val accentColor =
                                        if (blockedByIds.any {
                                                it == FilterListRepository.BLOCK_REASON_SECURITY ||
                                                    securityFilterIds.contains(it)
                                            }) {
                                            SecurityOrange
                                        } else {
                                            DangerRed
                                        }
                                    val recentAppIcon: Drawable? = remember(entry.packageName) {
                                        if (entry.packageName.isNotEmpty() && entry.packageName.contains(".")) {
                                            try {
                                                context.packageManager.getApplicationIcon(entry.packageName)
                                            } catch (e: Exception) {
                                                null
                                            }
                                        } else {
                                            null
                                        }
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (recentAppIcon != null) {
                                            Image(
                                                painter = rememberDrawablePainter(drawable = recentAppIcon),
                                                contentDescription = entry.appName,
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(RoundedCornerShape(9.dp))
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(accentColor.copy(alpha = 0.14f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(9.dp)
                                                        .clip(CircleShape)
                                                        .background(accentColor)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = entry.domain,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onBackground,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (entry.appName.isNotEmpty()) {
                                                Text(
                                                    text = entry.appName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TextSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = formatTimeSince(entry.timestamp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextTertiary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(180.dp))
            }
        }
    }
}

/**
 * Inline warning shown when Android Private DNS (Strict DoT) would bypass
 * BlockAds' DNS interception (#145).
 */
@Composable
private fun PrivateDnsNotice() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DangerRed.copy(alpha = 0.10f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(DangerRed.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = DangerRed,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.private_dns_warning_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.private_dns_warning_text),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

/** Small rounded label used in the hero (routing mode, profile, paused Wi-Fi). */
@Composable
private fun HeroPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    leadIcon: ImageVector? = null,
    trailIconRes: Int? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(containerColor)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button) { onClick() }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (leadIcon != null) {
            Icon(
                imageVector = leadIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        if (trailIconRes != null) {
            Icon(
                painter = painterResource(trailIconRes),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

/** Thin separator between the two columns of the metrics grid. */
@Composable
private fun MetricDivider() {
    VerticalDivider(
        modifier = Modifier.height(26.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
    )
}
