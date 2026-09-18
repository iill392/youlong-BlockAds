<div align="center">
  <img src="fastlane/metadata/android/en-US/images/icon.png" width="128" height="128">
  <h1>游龙广告插件 (Yulong BlockAds)</h1>
  <p><strong>免费、开源、无广告的 Android 系统级广告拦截器</strong></p>
  <p>基于本地 VPN 的 DNS 过滤，无需 Root，不上传任何数据</p>
  <br>
  <img src="https://img.shields.io/badge/license-GPL--3.0-blue">
  <img src="https://img.shields.io/badge/minSdk-24-green">
  <img src="https://img.shields.io/badge/targetSdk-36-green">
  <img src="https://img.shields.io/badge/version-6.6.3-orange">
</div>

---

## ⚠️ 二次开发声明（必读）

> **本仓库是基于 [pass-with-high-score/blockads-android](https://github.com/pass-with-high-score/blockads-android) 的二次开发（Derivative Work），不是原创项目。**
>
> - **上游项目**：<https://github.com/pass-with-high-score/blockads-android>
> - **原作者**：Nguyen Quang Minh（[@nqmgaming](https://github.com/nqmgaming)）
> - **上游许可证**：GNU General Public License v3.0
> - **本仓库许可证**：**GNU GPL-3.0**（与上游保持一致，见 [`LICENSE`](LICENSE)）
>
> 上游项目的全部著作权、设计、架构与绝大部分代码均归属于原作者及上游贡献者。
> 本仓库仅在其基础上做了**针对中国大陆网络环境的定向优化**（详见下节）。
> 完整的上游说明文档已原样保留在 [`docs/README-upstream.md`](docs/README-upstream.md)。
>
> 根据 GPL-3.0 的要求，本衍生作品同样以 GPL-3.0 发布，完整源代码公开于此。

---

## 🚀 本仓库相对上游的主要优化

### 一、启动速度优化（国内网络环境）

上游版本首次启动时需要从 `raw.githubusercontent.com` 下载约 **62 MB** 规则文件，
该域名在中国大陆访问极慢甚至不可达，导致首次启动长时间处于「无规则」状态。

| 优化项 | 具体改动 | 效果 |
|---|---|---|
| **内置离线规则库** | 把 3 套高价值规则集（约 **18.6 万条**）编译为 Trie + Bloom 二进制格式直接打包进 APK（共 6.3 MB），首次启动**无需联网**即可生效 | 启动即拦截，彻底摆脱 CDN 依赖 |
| **默认启用列表 2 条 → 8 条** | 上游首次安装会插入全部 22 条列表（62.4 MB）；本仓库改为只默认启用 8 条精选列表（约 5 MB） | 首次下载量降低 **92%** |
| **CDN 镜像自动改写** | 把 `raw.githubusercontent.com` / `github.com/.../raw` 自动改写为 `cdn.jsdelivr.net/gh/...`，并扩充至 4 个镜像端点（cdn / fastly / gcore / testingcf） | 实测下载速度 **24 KB/s → 255 KB/s（快约 10 倍）** |
| **超时参数全面放宽** | 上游所有超时都小于真实传输耗时，导致每次下载都被中断：文件 30s→**180s**、HTTP request 60s→**300s**、socket 30s→**60s**、manifest 8s→**30s**、bootstrap 30s→**600s**、后台同步 180s→**900s** | 下载不再被误中断 |
| **并发下载** | 规则下载由串行改为 `Semaphore(4)` 受控并发，聚合阶段仍单协程串行（`StringBuilder` 不能并发写） | 多规则场景加载更快 |
| **后台恢复不阻塞启动** | 引擎兜底恢复改为异步 `launch`，不再 `await`；无可用规则时只记日志 | 冷启动不再被网络等待卡住 |
| **流式下载 + 原子落盘** | 新增 `fetchTo()` 流式写入 `.tmp` 再 `rename`，避免半写文件被引擎误读 | 稳定性提升 |

### 二、拦截率优化

| 优化项 | 具体改动 | 效果 |
|---|---|---|
| **修复「拦截率结构性恒为 0%」** | 上游旧版本 APK **不带任何内置规则**，所有规则都必须运行时下载；下载失败 ⇒ 引擎收到空规则 ⇒ `isBlocked` 永不为 1 ⇒ 拦截率恒为 0%。本仓库通过内置规则彻底消除该结构性问题 | 从「必然 0%」变为可用 |
| **新增中国大陆专用规则集** | `builtin_cn_domestic`：**29,900 个国内广告域名**（Trie 782,531 B + Bloom 59,088 B），语料来自 anti-AD、EasyList China、ADgk、AdGuard Chinese、adguard-cn-spec 等 7 个上游清单（原始 117,742 域，经清洗去重） | 国内广告联盟域名可被拦截 |
| **上游清单本身无中文规则** | 上游manifest 的 22 条列表中**一条中文规则都没有**（唯二亚太条目是越南语 ABPVN / HostsVN），国内广告域名**结构性无法拦截**——这是本优化的核心动机 | 补齐结构性短板 |
| **规则总量 12.7 万 → 64.6 万** | 8 条推荐列表（约 46 万条）+ 3 套内置规则（18.6 万条） | 约 **5 倍**覆盖 |

**规则生产过程中的精度处理**（避免误伤正常网站）：

1. **EasyList 修饰符**：`||aliyuncs.com^` 这类带 `domain=` / `##` 修饰符的行若被剥掉修饰符会**整域误封**（baidu / tmall / taobao / aliyuncs / myqcloud / umengcloud / pstatp 会被全部封掉）⇒ 含 `$` 的行一律丢弃（694 条）+ 硬保护名单
2. **畸形主机名**：空标签、连字符贴边等按 RFC 1123 校验剔除
3. **机构域名误封**：剔除 `.edu.cn` / `.gov.cn` / `.ac.cn` / `.mil.cn` 等机构后缀（25 条）
4. **保护名单 149 条**：消费站点、设备/OS 厂商、云/CDN 根、推送通道、支付网关——**只保护根域不保护子域**，因此 `s.click.taobao.com` 之类广告子域仍可正常拦截
5. **bloom 超集约束**：引擎先查 bloom 再查 trie，故 bloom 必须按超集构建，trie 才能安全压缩

### 三、界面精简

- 引导页由 8 页精简为 6 页（移除 DNS 服务器选择页、崩溃报告 opt-in 页）
- 设置中移除：Root 代理模式、YouTube 受限模式、隐私与诊断整栏
- 保留 SafeSearch、WireGuard 导入、HTTPS 过滤等原有能力
- 附带**自愈迁移**：若检测到历史遗留的 `routingMode = root` 会强制改回 `direct`，避免用户因入口消失而无法恢复

### 与上游的差异一览

- `applicationId` 改为 `com.youlong.gg`（可与上游版本并存安装）
- 新增文件：`app/src/main/assets/builtin_*.{trie,bloom}`、`data/repository/BuiltinRuleSource.kt`
- 修改文件：`FilterDownloadManager.kt`、`AppModule.kt`、`FilterListRepository.kt`、`VpnEngineCoordinator.kt`、`AdBlockVpnService.kt`、`RootProxyService.kt` 及各 UI 层文件
- 规则编译器（纯 Go stdlib 实现，可复现地生成上述二进制规则）

---

## 功能特性

* **系统级拦截**：基于 `VpnService` 的本地 DNS 过滤，无需 Root
* **内置离线规则**：3 套基线规则打包进 APK，首次启动即生效
* **中国大陆规则集**：29,900 个国内广告域名
* **多路由模式**：VPN 模式 / Root 代理模式（iptables）/ 直连模式
* **HTTPS 过滤**（BETA）：用户态 TCP/IP 栈（gVisor netstack），按应用 MITM、注入 Cosmetic CSS 与 JS scriptlet
* **WireGuard 配置导入**
* **实时 DNS 查询日志**：支持搜索与过滤
* **自定义规则**：屏蔽 / 放行规则、白名单
* **按应用过滤**：指定应用绕过 VPN
* **DNS-over-HTTPS (DoH)**：多提供商支持
* **规则自动更新**：6h / 12h / 24h / 48h 可选
* **安全防护**：拦截钓鱼、恶意软件、恶意广告域名
* **284 条精选放行名单**：保障银行、支付、政务类 App 正常工作
* **设置导出 / 导入**
* **开机自动重连**
* **Material 3 动态取色** + 7 种强调色 + 深色 / 浅色 / 跟随系统
* **快捷设置磁贴** 与 **桌面小组件**
* **多语言**：中文、英文、越南语、日语、韩语、泰语、西班牙语等
* **100% 本地**：所有数据留在设备上

---

## 下载

前往 **[Releases](https://github.com/iill392/youlong-BlockAds/releases)** 页面下载（最新版 **v6.6.3**）：

| 文件 | 说明 |
|---|---|
| `Yulong-BlockAds-vX.Y.Z-universal.apk` | **通用版**，含全部 ABI（arm64 / armv7 / x86 / x86_64），体积较大（约 59 MB） |
| `Yulong-BlockAds-vX.Y.Z-arm64.apk` | **arm64 精简版**，仅含 64 位 ARM，体积约为通用版 1/3（约 22 MB），现代手机推荐 |

> 与上游版本 `app.pwhs.blockads` 包名不同（本仓库为 `com.youlong.gg`），两者可共存。
> 但**切换来源时需先卸载**，因为签名不同。

> 注：GitHub Release 资产名会剥离非 ASCII 字符，因此资产文件使用英文名 `Yulong-BlockAds-*`，
> 安装后应用名称仍为「游龙广告插件」。

---

## 构建

### 环境要求

* [Android Studio](https://developer.android.com/studio) Ladybug 或更新版本
* JDK 17 或更高（本仓库使用 JDK 21 验证通过）
* Android SDK 36（minSdk 24）
* [Go](https://go.dev/doc/install) 1.21+（仅在需要重建 `tunnel.aar` 时使用）
* [gomobile](https://pkg.go.dev/golang.org/x/mobile/cmd/gomobile)

### 步骤

1. 克隆仓库：

   ```bash
   git clone https://github.com/iill392/youlong-BlockAds.git
   cd youlong-BlockAds
   ```

2. 配置本地 SDK 路径（`local.properties`，已加入 `.gitignore`）：

   ```properties
   sdk.dir=/path/to/Android/sdk
   ```

3. **（可选）** 重建 Go tunnel AAR（仓库已内置预编译版本，通常无需执行）：

   ```bash
   ./scripts/build_tunnel.sh
   # 或
   ./gradlew buildGoTunnel
   ```

4. 用 Android Studio 打开项目，同步 Gradle 后运行。

5. 命令行构建：

   ```bash
   ./gradlew assembleDebug
   ./gradlew assembleRelease   # 需要签名密钥
   ```

### 签名配置

在项目根目录创建 `key.properties`（**已被 `.gitignore` 排除，请勿提交**）：

```properties
storeFile=/path/to/your.keystore
storePassword=******
keyAlias=******
keyPassword=******
```

### 内置规则的重新生成

`app/src/main/assets/builtin_*.{trie,bloom}` 为自定义二进制格式：

* **Trie v2**：`magic 0x54524945 ("TRIE")` / `version 2` / `nodeCount` / `domainCount`，BFS 序序列化，域名标签反转插入
* **Bloom v1**：`magic 0x424C4F4D ("BLOM")` / `version 1` / `bitCount(u64)` / `hashCount(u32)`，FNV-1a 双哈希

修改规则后**必须递增** `BuiltinRuleSource.REVISION`，否则已安装用户不会重新解压规则。

---

## 工作原理

本应用将 DNS 查询重定向到本地，通过 `VpnService`（VPN 模式）或 iptables（Root 代理模式）接管，
再用内存高效的 Trie 结构匹配域名。命中规则的查询被本地拦截，其余流量正常放行——**没有任何数据离开你的设备**。

开启 HTTPS 过滤后，用户态 TCP/IP 栈（gVisor netstack via tun2socks）在 Go 侧终结每个 TCP/UDP 流，
通过 `ConnectivityManager.getConnectionOwnerUid()` 查出归属应用 UID，仅对选定浏览器做 MITM。
启用证书固定（cert-pinning）的应用与 284 条放行名单会干净地绕过 MITM。
Cosmetic CSS 规则与 `##+js(…)` / `#%#//scriptlet(…)` 脚本片段通过一个微型内存资源服务器注入 HTML 响应。

---

## 许可证

本项目采用 **GNU General Public License v3.0** 许可证（与上游一致）。

```
Copyright (C) 2025 Nguyen Quang Minh  (上游原作者, https://github.com/nqmgaming)
Copyright (C) 2026 iill392          (本仓库二次开发部分, https://github.com/iill392)
```

完整条款见 [`LICENSE`](LICENSE)。

> **为什么必须用 GPL-3.0？**
> 上游 `pass-with-high-score/blockads-android` 以 GPL-3.0 发布，GPL 具有**传染性（copyleft）**：
> 任何基于它修改、再分发的衍生作品，都必须同样以 GPL-3.0 开源并提供完整源代码。
> 本仓库严格遵守该条款。

---

## 致谢

* **原项目作者**：[Nguyen Quang Minh (@nqmgaming)](https://github.com/nqmgaming) —— 本项目的全部基础架构与绝大部分功能均由其开发
* **上游仓库**：<https://github.com/pass-with-high-score/blockads-android>
* **技术栈**：[Jetpack Compose](https://developer.android.com/jetpack/compose)、[Koin](https://insert-koin.io/)、[gVisor](https://gvisor.dev/)、[WireGuard](https://www.wireguard.com/)
* **规则来源**（均为各自许可证下的开源项目）：
  * [AdGuard](https://adguard.com/) / [AdGuard DNS filter](https://github.com/AdguardTeam/AdguardFilters)
  * [EasyList China](https://github.com/easylist/easylistchina)
  * [anti-AD](https://github.com/privacy-protection-tools/anti-AD)
  * [ADgk](https://github.com/banbendalao/ADgk)
  * [StevenBlack/hosts](https://github.com/StevenBlack/hosts)
  * [Hagezi DNS Blocklists](https://github.com/hagezi/dns-blocklists)
  * [Peter Lowe's Ad and tracking server list](https://pgl.yoyo.org/adservers/)
  * [ABPVN](https://abpvn.com/) / [HostsVN](https://github.com/bigdargon/hostsVN)
  * [blockads-default-filter](https://github.com/pass-with-high-score/blockads-default-filter)

---

## 免责声明

本项目仅供学习与个人使用。请遵守你所在地区的法律法规。作者不对因使用本软件造成的任何后果承担责任。

本软件**不修改、不破解任何第三方应用**，仅在本机进行 DNS 层面的域名过滤。

---

## 已知问题 / 安全说明

GitHub Dependabot 会在本仓库报出若干依赖告警，来源均已核查：

| 来源 | 数量 | 说明 |
|---|---|---|
| `tunnel/go.mod` | 20 | 上游 `blockads-tunnel` Go 模块的间接依赖（`golang.org/x/crypto`、`golang.org/x/net`、`quic-go`、`go-chi/chi`）。**这些仅影响「重新编译 tunnel.aar」的流程**，本仓库已提交编译好的 `tunnel.aar`，APK 中不包含 Go 源码 |
| `Gemfile.lock` | 5 | 仅供 CI 发布流程使用的 fastlane 开发工具链（`faraday`、`jwt`、`excon`、`addressable`、`json`），**不进入 APK** |

上游仓库同样存在这些告警。如需重新编译 `tunnel/`，建议先执行 `cd tunnel && go get -u ./... && go mod tidy` 再构建。

发布用 APK 的 `app/libs/tunnel.aar` 与 `blockadstv/libs/tunnel.aar` 为**预编译二进制**，因此上述 Go 依赖的版本不会改变已发布 APK 的行为。

---

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=iill392/youlong-BlockAds&type=Date)](https://star-history.com/#iill392/youlong-BlockAds&Date)
