package app.pwhs.blockads.di

import app.pwhs.blockads.BuildConfig
import app.pwhs.blockads.data.AppDatabase
import app.pwhs.blockads.data.datastore.AppPreferences
import app.pwhs.blockads.data.entities.ProfileManager
import app.pwhs.blockads.data.remote.FilterDownloadManager
import app.pwhs.blockads.data.remote.api.CustomFilterApi
import app.pwhs.blockads.data.repository.CustomFilterManager
import app.pwhs.blockads.data.repository.FilterListRepository
import app.pwhs.blockads.ui.dnsprovider.DnsProviderViewModel
import app.pwhs.blockads.ui.filter.detail.FilterDetailViewModel
import app.pwhs.blockads.ui.filter.FilterSetupViewModel
import app.pwhs.blockads.ui.home.HomeViewModel
import app.pwhs.blockads.ui.logs.LogViewModel
import app.pwhs.blockads.ui.onboarding.OnboardingViewModel
import app.pwhs.blockads.ui.profile.ProfileViewModel
import app.pwhs.blockads.ui.appearance.AppearanceViewModel
import app.pwhs.blockads.ui.settings.SettingsViewModel
import app.pwhs.blockads.ui.statistics.StatisticsViewModel
import app.pwhs.blockads.ui.whitelist.AppWhitelistViewModel
import app.pwhs.blockads.ui.appmanagement.AppManagementViewModel
import app.pwhs.blockads.ui.customrules.CustomRulesViewModel
import app.pwhs.blockads.ui.domainrules.DomainRulesViewModel
import app.pwhs.blockads.ui.firewall.FirewallViewModel
import app.pwhs.blockads.ui.splash.SplashViewModel
import app.pwhs.blockads.ui.wireguard.WireGuardEditViewModel
import app.pwhs.blockads.ui.wireguard.WireGuardImportViewModel
import app.pwhs.blockads.ui.httpsfiltering.HttpsFilteringViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.endpoint
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import timber.log.Timber

val appModule = module {

    // HTTP Client
    //
    // Timeouts are sized for the filter-update traffic, which dominates this
    // client: the compiled rule binaries are 1-2 MB each and the filter CDN can
    // serve them at ~24 KB/s, so a single request legitimately runs for 60-90 s.
    // The old 60 s request timeout and 30 s socket timeout aborted those
    // transfers every time, which left the engine with no rules and the block
    // rate stuck at 0%. Background filter syncs are the only long-running
    // callers; interactive requests are far smaller and stay well inside these
    // ceilings. Connect stays tight: a host that cannot be reached should fail
    // fast so the mirror fallback in FilterDownloadManager can kick in.
    single {
        HttpClient(CIO) {
            engine {
                requestTimeout = 300_000
                endpoint {
                    connectTimeout = 30_000
                    // CIO defaults socketTimeout to INFINITE_TIMEOUT_MS. A server
                    // that completes the TCP handshake but then never sends a byte
                    // would hang the caller forever. This was one of the causes of
                    // "turning it on sometimes takes hours".
                    socketTimeout = 60_000
                }
            }

            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        Timber.d(message)
                    }
                }
                val logLevel = if (BuildConfig.DEBUG) LogLevel.INFO else LogLevel.NONE
                level = logLevel
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 300_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 60_000
            }
        }
    }

    // DNS Clients (Removed - now handled by Go tunnel)

    // Database
    single { AppDatabase.getInstance(androidContext()) }
    single { get<AppDatabase>().dnsLogDao() }
    single { get<AppDatabase>().filterListDao() }
    single { get<AppDatabase>().whitelistDomainDao() }
    single { get<AppDatabase>().dnsErrorDao() }
    single { get<AppDatabase>().customDnsRuleDao() }
    single { get<AppDatabase>().protectionProfileDao() }
    single { get<AppDatabase>().firewallRuleDao() }

    // Preferences
    single { AppPreferences(androidContext()) }

    // Repository
    single { FilterDownloadManager(androidContext(), get()) }
    single {
        FilterListRepository(
            context = androidContext(),
            filterListDao = get(),
            whitelistDomainDao = get(),
            customDnsRuleDao = get(),
            client = get(),
            downloadManager = get()
        )
    }
    single { CustomFilterApi(get()) }
    single {
        CustomFilterManager(
            context = androidContext(),
            client = get(),
            filterListDao = get(),
            customFilterApi = get()
        )
    }

    // Profile Manager
    single {
        ProfileManager(
            profileDao = get(),
            filterListDao = get(),
            appPrefs = get(),
            filterRepo = get()
        )
    }

    // ViewModels
    viewModel {
        HomeViewModel(
            appPrefs = get(),
            dnsLogDao = get(),
            filterRepo = get(),
            profileDao = get(),
            filterListDao = get()
        )
    }
    viewModel { StatisticsViewModel(dnsLogDao = get()) }
    viewModel {
        LogViewModel(
            dnsLogDao = get(),
            filterListDao = get(),
            whitelistDomainDao = get(),
            customDnsRuleDao = get(),
            filterListRepository = get(),
            appPrefs = get(),
            application = androidApplication()
        )
    }
    viewModel {
        SettingsViewModel(
            appPrefs = get(),
            filterRepo = get(),
            dnsLogDao = get(),
            whitelistDomainDao = get(),
            filterListDao = get(),
            customDnsRuleDao = get(),
            profileDao = get(),
            profileManager = get(),
            firewallRuleDao = get(),
            application = androidApplication()
        )
    }
    viewModel {
        FilterSetupViewModel(
            filterRepo = get(),
            filterListDao = get(),
            customFilterManager = get(),
            profileManager = get(),
            application = androidApplication()
        )
    }
    viewModel { (filterId: Long) ->
        FilterDetailViewModel(
            filterId = filterId,
            filterListDao = get(),
            dnsLogDao = get(),
            filterRepo = get(),
            profileManager = get(),
            application = androidApplication(),
            customFilterManager = get()
        )
    }
    viewModel {
        AppWhitelistViewModel(
            appPrefs = get(),
            application = androidApplication()
        )
    }
    viewModel {
        app.pwhs.blockads.ui.trustednetworks.TrustedNetworksViewModel(
            appPrefs = get(),
            application = androidApplication()
        )
    }
    viewModel {
        CustomRulesViewModel(
            customDnsRuleDao = get(),
            filterListRepository = get(),
            application = androidApplication()
        )
    }
    viewModel {
        DnsProviderViewModel(
            appPrefs = get(),
            application = androidApplication()
        )
    }
    viewModel {
        AppManagementViewModel(
            appPrefs = get(),
            dnsLogDao = get(),
            application = androidApplication(),
        )
    }
    viewModel {
        OnboardingViewModel(
            appPrefs = get(),
            application = androidApplication()
        )
    }
    viewModel {
        ProfileViewModel(
            profileManager = get(),
            profileDao = get(),
            filterListDao = get(),
            application = androidApplication()
        )
    }
    viewModel {
        FirewallViewModel(
            appPrefs = get(),
            firewallRuleDao = get(),
            application = androidApplication()
        )
    }
    viewModel {
        AppearanceViewModel(
            appPrefs = get(),
            application = androidApplication()
        )
    }
    viewModel {
        SplashViewModel(
            appPrefs = get(),
        )
    }
    viewModel {
        DomainRulesViewModel(
            whitelistDomainDao = get(),
            customDnsRuleDao = get(),
            application = androidApplication()
        )
    }
    viewModel {
        WireGuardImportViewModel(
            application = androidApplication()
        )
    }
    viewModel {
        WireGuardEditViewModel(
            application = androidApplication()
        )
    }
    viewModel {
        HttpsFilteringViewModel(
            application = androidApplication()
        )
    }
}

