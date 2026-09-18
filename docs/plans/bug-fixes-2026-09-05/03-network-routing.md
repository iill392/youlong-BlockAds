# 3. Kết nối và DNS routing — thay đổi nhỏ, kiểm tra riêng từng mode

[Mục lục](../2026-09-05-bug-fix-plan.md) · Baseline code: `a997619`.


## Preflight code

**Đính chính kiến trúc so với bình luận issue cũ:** AdBlockVpnService hiện thêm 0.0.0.0/0 trong direct mode; GoTunnelAdapter gọi StartFull khi không dùng WireGuard. IPv6 ::/0 không được route, và direct mode vẫn allowBypass. Một số comment DNS-only trong cùng file đã lỗi thời. Kế hoạch phải dựa trên nhánh thực thi này.

- **#145 — đường bypass tồn tại trong code:** IPv6 public đi ngoài tunnel theo thiết kế hiện tại; direct mode cho phép bypass. Resolver.Query còn fallback sang plain DNS sau lỗi primary. Cần packet capture và cấu hình người dùng để biết đường nào gây kết quả ISP DNS; không kết luận chỉ do Private DNS.
- **#162 — cần tái hiện lại trên StartFull:** không còn đủ cơ sở giải thích toàn bộ mất mạng bằng DNS-only. Kiểm tra IPv6 không được route, app bị exclude, protect socket và lockdown trên thiết bị. Không đánh dấu đã sửa.
- **#226 / #172 — cơ chế khớp triệu chứng, chưa xác nhận gốc:** resolver.go đặt timeout 5 giây rồi mới thử fallback plain tuần tự; có thể giải thích latency khoảng 5000 ms. Cần biết primary có reachable, response/error và network binding để xác định lý do timeout.
- **#137 — đã có bản sửa về hướng đi:** engine.handleSplitDNSForward hiện inject packet vào router WireGuard; nguyên nhân cũ “dùng socket ngoài VPN” không còn áp dụng nguyên trạng. Cần kiểm tra response source có được đổi từ DNS nội bộ về DNS ảo mà client hỏi không; hàm thay destination nhưng không ghi mapping ở đây. Đồng thời kiểm tra IPv6 client với DNS IPv4, và excludeLan khi DNS WireGuard nằm trong 10/8. Đây là nghi vấn, chưa chứng minh bằng tunnel thật.
- **#196 / #104 — chưa chốt nguyên nhân:** direct mode hiện full IPv4, nên phải kiểm tra passthrough private IP, TLS và network binding; thiếu DNS log không đủ để kết luận ứng dụng dùng DoH. Cần endpoint HA/LAN và log connect thực tế.
- **#212 — nghi vấn hỗ trợ ICMP:** StartFull đăng ký TCP/UDP handlers; chưa thấy đường relay ICMP ra mạng trong lớp full-tunnel đã đọc. Cần kiểm tra gVisor/dependency và chạy ping thiết bị trước khi xác nhận.

Bằng chứng: [AdBlockVpnService](../../../app/src/main/java/app/pwhs/blockads/service/AdBlockVpnService.kt), [GoTunnelAdapter](../../../app/src/main/java/app/pwhs/blockads/service/GoTunnelAdapter.kt), [resolver.go](../../../tunnel/resolver.go), [engine.go](../../../tunnel/engine.go), [fulltunnel.go](../../../tunnel/fulltunnel.go).

## Kế hoạch thực hiện

| Issue | Điều tra và hướng xử lý | Tiêu chí hoàn thành |
|---|---|---|
| #226, #172 | Tái hiện DNS lỗi/chậm ~5 giây; kiểm tra resolver.go, timeout/fallback, network binding, cache và chuyển Wi-Fi/mobile. Xác minh endpoint cấu hình có truy cập được. | Không lặp timeout trên mạng tốt; fallback hoạt động theo cấu hình; lỗi upstream có chẩn đoán rõ. |
| #196, #104 | Home Assistant và IP LAN 10.x không truy cập; phân biệt DNS, IP trực tiếp, route LAN và HTTPS interception. | Truy cập LAN và HA hoạt động trong mode hỗ trợ; tắt filter không còn bị chặn ngoài ý muốn. |
| #212 | MacroDroid ping timeout; xác định đường ICMP trong mode thực tế và mức hỗ trợ của tunnel. | Ping hoạt động nếu được hỗ trợ; nếu chưa hỗ trợ, ghi giới hạn rõ và không tuyên bố đã sửa bằng whitelist app. |
| #137 | Kiểm tra split DNS đi tới DNS nội bộ qua WireGuard trong resolver.go, wireguard.go, outbound_wireguard.go. | Domain nội bộ được resolve trong tunnel; domain công khai đi đúng cấu hình; không rò truy vấn split DNS ra resolver công khai khi tunnel lỗi. |
| #145 | Tái hiện DNS ISP với Private DNS/DoH của app, fallback và IPv6; đọc lại thay đổi bị revert. | Chứng minh truy vấn được hỗ trợ đi đúng resolver; phát hiện/cảnh báo đường bypass chưa hỗ trợ; không gây mất mạng. |
| #162 | Retest lockdown với StartFull IPv4 hiện tại, IPv6 bypass và app excluded; không dùng giải thích DNS-only cũ. | Chỉ công bố hỗ trợ lockdown khi TCP/UDP/DNS, IPv6 và các app bị exclude được kiểm chứng đầy đủ; nêu rõ giới hạn còn lại. |

Không áp dụng default route toàn bộ traffic chỉ để sửa DNS leak nếu engine của mode đó chưa forward được traffic tương ứng. Full-route/DoT interception là thay đổi kiến trúc riêng nếu cần, không gộp vào hotfix.
