# 4. Lifecycle, firewall và profile Android

[Mục lục](../2026-09-05-bug-fix-plan.md) · Baseline code: `a997619`.


## Preflight code

- **#206 — nguyên nhân rõ cho Root-only:** BootReceiver yêu cầu autoReconnect && vpnEnabled. Chỉ AdBlockVpnService ghi setVpnEnabled; RootProxyService không ghi khi RUNNING/STOP. Người chỉ dùng Root Proxy có thể giữ vpnEnabled=false nên boot không khởi động, dù root retry đã có. Cần trạng thái ý định bảo vệ dùng chung các mode, không chỉ vá set true mà quên tắt chủ động.
- **#152 — giới hạn firewall xác nhận:** FirewallManager và engine áp rule ở DNS; fulltunnel.go TCP/UDP passthrough không áp rule mỗi connection. IP cache, IP trực tiếp và kết nối đã mở có thể tiếp tục. Đây là thiếu enforcement tầng kết nối, không thể sửa đầy đủ bằng thêm domain block. UID unknown và push qua dịch vụ hệ thống là các ca cần phân biệt khi tái hiện WhatsApp.
- **#148 — nghi vấn phát hiện VPN sai phạm vi:** VpnUtils quét allNetworks và xem bất kỳ TRANSPORT_VPN là VPN khác nếu service không chạy; không phân biệt owner/profile. Cần thiết bị Work Profile chứng minh network của Personal Profile có xuất hiện với caller này; chưa khẳng định từ static code.
- **#221 / #244 — chưa có nguyên nhân VPN tự ngắt:** cần log exit/lifecycle trên Pixel. RootProxy onTaskRemoved có teardown iptables; watchdog hiện chỉ kiểm tra rules, phần kiểm tra engine vẫn TODO. Những điểm này liên quan Root mode, không được dùng làm kết luận cho báo cáo Pixel VPN.
- **#130 — cần xác nhận bản sửa hiện có:** lịch sử có sửa root-shell lookup DNS hot path. RootProxy hiện dùng snapshotter nền. Retest đúng Android 16 + KernelSU; không coi log IPv6 lỗi là nguyên nhân duy nhất khi chưa đo luồng DNS.

Bằng chứng: [BootReceiver](../../../app/src/main/java/app/pwhs/blockads/service/BootReceiver.kt), [RootProxyService](../../../app/src/main/java/app/pwhs/blockads/service/RootProxyService.kt), [FirewallManager](../../../app/src/main/java/app/pwhs/blockads/service/FirewallManager.kt), [VpnUtils](../../../app/src/main/java/app/pwhs/blockads/utils/VpnUtils.kt), [fulltunnel.go](../../../tunnel/fulltunnel.go).

## Kế hoạch thực hiện

| Issue | Công việc | Tiêu chí hoàn thành |
|---|---|---|
| #206 | Kiểm tra BootReceiver, RootProxyResumeWorker, RootProxyService, root sẵn sàng chậm và giới hạn khởi động foreground service. | Reboot tự khôi phục khi đã bật auto-reconnect; khi tắt tùy chọn thì không tự bật; retry có giới hạn và không tạo service trùng. |
| #221; liên quan #244 | Thu log service bị dừng, crash, Doze và thay đổi mạng; kiểm tra VpnRetryManager/VpnResumeWorker. | Phục hồi được các trường hợp hệ điều hành cho phép; thao tác tắt chủ động của người dùng được tôn trọng. Watchdog 5–100 giây là yêu cầu tính năng, không mặc định là giải pháp. |
| #152 | Tái hiện WhatsApp với firewall, UID, IPv4/IPv6 và kết nối đang mở; phân biệt push hệ thống với traffic trực tiếp của app. | Traffic app bị chặn đúng Wi-Fi/mobile theo rule; bỏ chặn hoạt động; log xác định được UID hoặc nêu rõ không xác định. |
| #148 | Kiểm tra VpnUtils và phát hiện VPN khác trong Work Profile so với Personal Profile. | Hai profile độc lập chạy đúng trường hợp Android hỗ trợ; vẫn xử lý xung đột VPN trong cùng profile. |
| #130 | Retest Root Proxy Android 16 + KernelSU sau các sửa /proc/root shell hiện có; kiểm tra iptables IPv4/IPv6 và log mới. | DNS không bị nghẽn bởi tra UID; blocking có kiểm chứng; lỗi quyền/rule được báo rõ. Chỉ đóng sau xác nhận. |
