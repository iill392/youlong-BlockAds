Bundled baseline rule sets
==========================

The .trie / .bloom pairs in this directory are copied into the APK so that
ad and tracker blocking works on the very first launch, with no network
access at all. Without them the native engine has no rules to match against
until the filter CDN has been reached, which leaves an install that cannot
reach that CDN blocking nothing.

They are used *in addition to* every filter list the user enables in
Settings > Filter lists; nothing here is a replacement for those.

Files
-----

  builtin_adguard_dns.trie / .bloom
      AdGuard DNS filter (178,283 rules) - comprehensive ads, trackers and
      telemetry domains.

  builtin_adguard_mobile.trie / .bloom
      AdGuard Mobile Ads filter (4,442 rules) - the mobile ad SDKs that
      serve the in-app banners and interstitials this baseline targets.

  builtin_yoyo_adservers.trie / .bloom
      Peter Lowe's Ad and tracking server list (3,559 rules) - classic ad
      and tracker hosts.

  builtin_cn_domestic.trie / .bloom
      Mainland-China ad networks (32,864 rules). The remote manifest lists no
      Chinese filter at all - its only APAC entries are ABPVN and HostsVN,
      which are Vietnamese - so domestic ad domains were previously
      unblockable regardless of how many global lists were enabled. This set
      is merged from anti-AD, EasyList China, ADgk and the AdGuard Chinese
      filter.

      It is curated rather than a raw merge: rules carrying easylist
      modifiers ($domain=, $third-party, ...) are dropped because the native
      trie cannot express them and a stripped modifier would over-block an
      entire registrable domain; cosmetic rules (a.com##.ad) and URL patterns
      are dropped; entries already covered by the sets above are removed; and
      a hard protection list keeps infrastructure roots (object storage,
      CDNs, push transports, payment gateways) as well as all .gov.cn /
      .edu.cn / .ac.cn / .mil.cn hosts blockable only at sub-domain level.

Regenerating
------------

The binaries are produced by the compiler that lives in this repository:

    tunnel.Tunnel.compileFilterList(inputPath, triePath, bloomPath)

It accepts hosts-file lines ("0.0.0.0 example.com"), plain domain-per-line
files and Adblock-style domain rules ("||example.com^"). Feed it a
downloaded copy of the source list, then drop the resulting pair in here and
bump `REVISION` in BuiltinRuleSource.kt so existing installs re-extract it.

The build-time format check lives in BuiltinRuleSource: a set whose header
does not match the native engine's expected magic/version is ignored.

Licences and attribution
------------------------

  AdGuard DNS filter, AdGuard Mobile Ads filter
      (c) AdGuard Team - https://github.com/AdguardTeam/AdguardFilters
      Licensed under the GNU General Public License v3.0.

  Peter Lowe's Ad and tracking server list
      (c) Peter Lowe - https://pgl.yoyo.org/adservers/
      Licensed under CC BY-NC-SA 4.0.

  anti-AD
      (c) privacy-protection-tools - https://github.com/privacy-protection-tools/anti-AD
      Licensed under GNU General Public License v3.0.

  EasyList China
      (c) EasyList authors - https://github.com/easylist/easylistchina
      Licensed under CC BY-SA 3.0 / GNU GPL v3.0.

  ADgk
      (c) banbendalao - https://github.com/banbendalao/ADgk
      Licensed under GNU General Public License v3.0.

  AdGuard Chinese filter
      (c) AdGuard Team - https://github.com/AdguardTeam/AdguardFilters
      Licensed under the GNU General Public License v3.0.

The remaining filter lists available in the app are fetched at runtime and
are not redistributed in this APK.
