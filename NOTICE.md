# NOTICE / 归属声明

本文件依据 **GNU General Public License v3.0** 的要求，
声明本衍生作品（Derivative Work）的来源与归属。

---

## 1. 上游项目

| 项目 | 内容 |
|---|---|
| 名称 | BlockAds — Android ad blocker |
| 仓库 | <https://github.com/pass-with-high-score/blockads-android> |
| 作者 | Nguyen Quang Minh（GitHub: [@nqmgaming](https://github.com/nqmgaming)） |
| 许可证 | GNU General Public License v3.0 |
| 版权 | Copyright (C) 2025 Nguyen Quang Minh |

**本仓库 `iill392/youlong-BlockAds` 是基于上述项目的二次开发，并非原创作品。**
上游项目的架构设计、绝大多数源代码、UI 设计、Go tunnel 引擎（`tunnel/`）以及
全部既有功能，其著作权均归原作者及上游贡献者所有。

---

## 2. 本衍生作品

| 项目 | 内容 |
|---|---|
| 名称 | 游龙广告插件 (Yulong BlockAds) |
| 仓库 | <https://github.com/iill392/youlong-BlockAds> |
| 维护者 | [@iill392](https://github.com/iill392) |
| 许可证 | GNU General Public License v3.0（与上游一致，GPL 传染性要求） |
| 版权 | Copyright (C) 2026 iill392（仅限新增与修改部分） |

### 本仓库相对上游新增/修改的内容

- `app/src/main/assets/builtin_adguard_dns.*`、`builtin_adguard_mobile.*`、`builtin_yoyo_adservers.*`、`builtin_cn_domestic.*`
  （内置离线规则二进制文件）
- `app/src/main/java/app/pwhs/blockads/data/repository/BuiltinRuleSource.kt`
- `app/src/main/java/app/pwhs/blockads/data/remote/FilterDownloadManager.kt` 中的镜像改写与流式下载逻辑
- `app/src/main/java/app/pwhs/blockads/di/AppModule.kt`、`data/repository/FilterListRepository.kt`、
  `service/vpn/VpnEngineCoordinator.kt`、`service/AdBlockVpnService.kt`、`service/RootProxyService.kt` 的超时与并发调整
- UI 精简相关改动（引导页、设置页）
- 规则编译工具（为生成上述二进制规则而编写）

**除上述文件外，其余代码均来自上游项目。**

---

## 3. GPL-3.0 合规说明

由于上游项目以 GPL-3.0 发布，任何衍生作品的分发必须满足：

1. ✅ **同样以 GPL-3.0 许可**——本仓库 `LICENSE` 为 GPL-3.0 全文，未做修改
2. ✅ **提供完整源代码**——本仓库全部源代码公开可获取
3. ✅ **保留版权声明**——本文件及 `README.md` 均明确标注上游作者与版权
4. ✅ **声明修改内容**——见上节「本仓库相对上游新增/修改的内容」
5. ✅ **不附加额外限制**——本仓库未对 GPL 授予的权利附加任何额外限制

---

## 4. 第三方资源

### 4.1 内置规则数据集

打包进 APK 的规则源自以下开源项目，均按其各自许可证使用：

| 来源 | 许可证 |
|---|---|
| [AdGuard DNS filter](https://github.com/AdguardTeam/AdguardFilters) | GPL-3.0 |
| [AdGuard Mobile Ads filter](https://github.com/AdguardTeam/AdguardFilters) | GPL-3.0 |
| [Peter Lowe's Ad and tracking server list](https://pgl.yoyo.org/adservers/) | CC BY-NC-SA 4.0 |
| [anti-AD](https://github.com/privacy-protection-tools/anti-AD) | MIT |
| [EasyList China](https://github.com/easylist/easylistchina) | GPL-3.0 / CC BY-SA 3.0 |
| [ADgk](https://github.com/banbendalao/ADgk) | MIT |
| [AdGuard Chinese filter](https://github.com/AdguardTeam/AdguardFilters) | GPL-3.0 |
| [StevenBlack/hosts](https://github.com/StevenBlack/hosts) | MIT |
| [Hagezi DNS Blocklists](https://github.com/hagezi/dns-blocklists) | GPL-3.0 |
| [ABPVN](https://abpvn.com/) | GPL-3.0 |
| [HostsVN](https://github.com/bigdargon/hostsVN) | MIT |
| [blockads-default-filter](https://github.com/pass-with-high-score/blockads-default-filter) | GPL-3.0 |

详细的逐文件署名同时记录在 `app/src/main/assets/README-builtin-rules.txt`。

### 4.2 Go tunnel 引擎

`app/libs/tunnel.aar` 与 `blockadstv/libs/tunnel.aar` 由 `tunnel/` 目录下的 Go 源码编译而来，
该部分源自上游项目，并依赖：

- [gVisor](https://gvisor.dev/)（Apache-2.0）
- [WireGuard](https://www.wireguard.com/)（GPL-2.0）
- [gomobile](https://pkg.go.dev/golang.org/x/mobile/cmd/gomobile)（BSD-3-Clause）

### 4.3 图标与主题

`fastlane/metadata/` 与 `app/src/main/res/mipmap-*` 下的应用图标、
`win10-res/` 下的 Windows 10 主题资源，其原始版权归各自权利人所有。

---

## 5. 联系方式

如你是上述任一项目的权利人，认为本仓库的使用方式不当，请通过
[GitHub Issues](https://github.com/iill392/youlong-BlockAds/issues) 联系，我们会立即处理。
