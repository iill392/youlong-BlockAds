# 2. Filter chặn nhầm và cập nhật

[Mục lục](../2026-09-05-bug-fix-plan.md) · Baseline code: `a997619`.


## Preflight code

- **#201 — đã tái hiện lỗi parser cục bộ, chưa gắn với artifact backend:** chạy nguyên hàm parseDomainLine trích từ tunnel/compiler.go bằng Go. `||example.com^$third-party` và `||example.com^$domain=other.test` đều trả về `example.com`; `@@||example.com^` trả rỗng. Scope bị mất nên rule hạn chế ngữ cảnh trở thành chặn cả domain. Rule có path bị bỏ qua đúng trong ca thử. CustomFilterManager ưu tiên backend và có local fallback; chưa kiểm tra compiler/backend artifact nên không khẳng định đây là nguyên nhân duy nhất của list built-in.
- **#217 — chưa đủ bằng chứng:** AMP có thể bị rule nguồn hoặc chuyển đổi rule, nhưng chưa có DNS log/rule fixture. Không gộp nguyên nhân với #201 trước khi có domain và rule match.
- **#170 — xác nhận đường che giấu partial failure:** FilterUpdateWorker trả success nếu chỉ một nguồn thành công; lỗi các nguồn còn lại có thể không hiển thị. Wi-Fi-only kiểm tra active network có thể retry khi capabilities không phản ánh mạng bên dưới VPN; đây là giả thuyết cần capabilities/log thiết bị. Không thấy bằng chứng chỉ riêng việc giữ app sống khiến mất lịch WorkManager.
- **#56 / #164 — giới hạn allowlist:** compiler bỏ exception @@, không tạo dữ liệu allowlist. Cần hỗ trợ loại list rõ ràng thay vì đếm các block rule còn lại rồi coi là import thành công.

Bằng chứng: [compiler.go](../../../tunnel/compiler.go), [FilterUpdateWorker](../../../app/src/main/java/app/pwhs/blockads/worker/FilterUpdateWorker.kt), [CustomFilterManager](../../../app/src/main/java/app/pwhs/blockads/data/repository/CustomFilterManager.kt).

## Kế hoạch thực hiện

### #201, #217; tham chiếu #214

- [ ] Thu rule chính xác khiến youtube.com, raw.githubusercontent.com, t.co và AMP bị chặn; #214 chỉ tham chiếu #201, chưa có bằng chứng lỗi riêng.
- [ ] Kiểm tra compiler/parser Kotlin, tunnel/compiler.go và backend nếu danh sách được biên dịch từ xa.
- [ ] Kiểm tra rule URL/path, modifier, exception có bị biến thành chặn cả domain không; phân biệt false positive từ nguồn filter với lỗi parser.
- [ ] Không dùng whitelist cứng cho vài website để che lỗi chuyển đổi rule.
- Hoàn thành khi: fixture rule hợp lệ chặn đúng; rule không hỗ trợ không bị nới thành chặn toàn domain; exception đúng; các trang báo lỗi truy cập được với fixture đã sửa và domain quảng cáo kiểm soát vẫn bị chặn.

### #170: Cập nhật filter không chạy hoặc bị treo

- [ ] Kiểm tra FilterUpdateScheduler/Worker: Wi-Fi-only khi VPN bật, constraints, retry/backoff, timeout, hủy và cập nhật thủ công.
- [ ] Tái hiện app chạy nền lâu, Wi-Fi có VPN, mạng lỗi và chuyển mạng; việc chuyển mạng tự kích hoạt update là yêu cầu tính năng riêng.
- [ ] Hiển thị lần thành công gần nhất và lý do skip/fail; giữ bản filter tốt gần nhất nếu tải/biên dịch thất bại.
- Hoàn thành khi: job chạy khi đủ điều kiện, lỗi kết thúc hữu hạn và có lý do; cập nhật thủ công không quay vô hạn; filter cũ còn dùng được.
