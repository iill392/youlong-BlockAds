# Kế hoạch và preflight bug BlockAds

Ngày: 2026-09-05. Baseline code: `a997619` (origin/main khi kiểm tra).
Snapshot: 59 issue mở; 21 issue có nhãn bug hoặc tiêu đề [BUG], cộng các báo cáo liên quan chưa gắn nhãn.

Nguồn: [GitHub issues](https://github.com/pass-with-high-score/blockads-android/issues).

## Các nhóm

- [1. Cấu hình và DNS — triển khai đợt đầu](bug-fixes-2026-09-05/01-config-dns.md)
- [2. Filter chặn nhầm và cập nhật](bug-fixes-2026-09-05/02-filters.md)
- [3. Kết nối và DNS routing — thay đổi nhỏ, kiểm tra riêng từng mode](bug-fixes-2026-09-05/03-network-routing.md)
- [4. Lifecycle, firewall và profile Android](bug-fixes-2026-09-05/04-lifecycle-firewall.md)
- [5. UI, hiệu năng và các issue cần phân loại](bug-fixes-2026-09-05/05-ui-triage.md)

## Kết quả preflight nổi bật

- Có bằng chứng code: #236 snapshot profile không cập nhật; #225 export whitelist từ flow không có subscriber; #222 backup thiếu DNS protocol/URL; #228 so trùng host với fallback; #206 boot đọc trạng thái Root Proxy không ghi; #152 firewall chỉ ở DNS.
- Đã chạy probe nguyên hàm parser Go: modifier theo ngữ cảnh bị bỏ thành chặn domain toàn cục. Chưa xác nhận compiler backend/artifact gây #201.
- #137 đã có forwarding qua WireGuard; #95 đã có nút refresh; #130 đã có sửa DNS hot path. Cần retest, không triển khai lại nguyên nhân cũ.
- Direct mode hiện StartFull IPv4, không phải DNS-only như bình luận cũ. #145/#162 phải kiểm tra lại theo code thực thi hiện tại.

## Giới hạn xác minh

Preflight gồm đọc code, đối chiếu báo cáo/bình luận đã tải và chạy probe parser bằng Go. Chưa chạy Android build, test thiết bị, packet capture, backend compiler hoặc toàn bộ test suite; chưa sửa production code. Mỗi nhóm phân biệt bằng chứng code, giả thuyết và dữ liệu còn thiếu.

## 0. Xác nhận baseline trước khi sửa

- [ ] Build HEAD và ghi phiên bản, commit, Android, thiết bị, nguồn APK cho mỗi lần tái hiện.
- [ ] Với từng issue, ghi mode direct StartFull / HTTPS filtering / WireGuard / Root Proxy, upstream/fallback DNS, Private DNS, filter và loại mạng.
- [ ] So sánh phiên bản được báo cáo với HEAD nếu có thể; phân loại: còn lỗi, đã sửa cần xác nhận, thiếu dữ liệu, giới hạn tính năng.
- [ ] Thu log có thời gian, DNS latency, transport, kết quả upstream/fallback và lifecycle; bỏ thông tin nhạy cảm trước khi chia sẻ.
- [ ] Giữ fixture filter cố định khi kiểm thử parser để tránh kết quả thay đổi theo danh sách trực tuyến.

Code đã thấy khi lập kế hoạch: SettingsViewModel có export/import whitelistDomains; lịch sử gần đây có sửa đọc /proc và root shell trên đường xử lý DNS. Vì vậy #225 và #130 phải kiểm tra hồi quy trước, không mặc định thiếu implementation.

## Kiểm tra và phát hành

- Mỗi bản sửa có fixture/test hồi quy phản ánh hành vi lỗi, tránh test chỉ lặp lại implementation.
- Kotlin: chạy unit test liên quan và `./gradlew assembleDebug`; chọn đúng task flavor nếu dự án yêu cầu.
- Go: chạy test liên quan rồi `go test ./...` trong tunnel khi sửa engine; build lại AAR theo workflow repository và kiểm tra tích hợp APK.
- Ma trận tối thiểu theo phạm vi thay đổi: Wi-Fi/mobile, IPv4/IPv6, upstream thành công/thất bại; VPN thường/HTTPS/WireGuard/Root Proxy khi bị ảnh hưởng.
- Với lifecycle: reboot, Doze, tắt chủ động, mạng mất/kết nối lại. Với dữ liệu: restart, import cũ, file lỗi và round-trip.
- Thiếu thiết bị Root/TV/Work Profile: ghi rõ chưa kiểm chứng, cung cấp build và bước tái hiện cho người báo; không đánh dấu pass thay cho kiểm thử thật.
- Mỗi PR ghi issue, nguyên nhân đã xác nhận, hành vi trước/sau, kiểm tra đã chạy và giới hạn còn lại.
- Chỉ đóng issue khi có bằng chứng sửa trên phiên bản cụ thể; thay đổi filter/backend phải ghi artifact/version tương ứng.

## Thứ tự commit/PR dự kiến

1. #236: giữ lựa chọn filter theo profile.
2. #225/#222: sửa phần backup/restore còn tái hiện, dùng chung test round-trip.
3. #228: phân biệt endpoint DNS theo transport.
4. #201/#217: sửa parser/filter sau khi xác định rule lỗi; tách PR nếu khác nguyên nhân.
5. #170: cập nhật filter và lý do thất bại.
6. #206 rồi #221: boot/recovery, mỗi nguyên nhân một PR.
7. #137: split DNS WireGuard.
8. #226/#172/#196/#104/#212: từng bản sửa kết nối theo kết quả tái hiện.
9. #145/#162: chẩn đoán bypass/lockdown theo StartFull hiện tại trước; IPv6/full-route bổ sung là thay đổi riêng nếu cần.
10. #152/#148/#130: firewall, Work Profile, xác nhận Root Proxy.
11. #203 và các báo cáo phụ: TV, log HTTPS, refresh danh sách, hiệu năng.

Thứ tự có thể đổi khi xác nhận lỗi mất mạng diện rộng hoặc mất dữ liệu. Mỗi mục chỉ được đánh dấu hoàn thành khi tiêu chí hành vi và kiểm tra tương ứng đạt; không xem commit kế hoạch là đã sửa bug.
