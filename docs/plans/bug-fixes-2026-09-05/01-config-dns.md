# 1. Cấu hình và DNS — triển khai đợt đầu

[Mục lục](../2026-09-05-bug-fix-plan.md) · Baseline code: `a997619`.


## Preflight code

- **#236 — có lỗi lưu trạng thái:** `ProfileViewModel.createCustomProfile` chỉ chụp filter lúc tạo; `FilterSetupViewModel.toggleFilter` và `FilterDetailViewModel` chỉ đổi bảng filter. `ProfileManager.switchToProfile` áp lại enabledFilterUrls cũ mà không lưu lựa chọn mới của custom profile. Ca custom profile tạo từ 3 filter rồi chỉnh lên 12 có thể quay về 3. Cần tái hiện đúng thứ tự người dùng để xác nhận toàn bộ báo cáo.
- **#225 — nguyên nhân rõ trong code:** `SettingsViewModel.whitelistDomains` dùng stateIn(WhileSubscribed, emptyList), nhưng SettingsScreen không collect flow này; export đọc `.value`. Khi không có subscriber, danh sách vẫn rỗng dù DB có dữ liệu. Json mặc định bỏ trường bằng default nên whitelistDomains rỗng có thể không xuất hiện trong JSON. Sửa bằng đọc snapshot trực tiếp DAO ở thời điểm export; thêm test round-trip không cần mở màn whitelist.
- **#222 — thiếu dữ liệu DNS:** SettingsBackup không chứa dnsProtocol, dohUrl hoặc dnsProviderId; export/import chỉ lưu host upstream/fallback. DNS DoH có URL riêng nên restore host không thể khôi phục resolver đầy đủ. dnsResponseType/highContrast có trong schema nhưng không được export/import. switchToProfile chạy cuối còn ghi đè filter và SafeSearch vừa restore; custom profile chỉ lưu type, không có định nghĩa profile để dựng lại trên app sạch.
- **#228 — xác nhận so sánh sai tầng:** DnsProviderScreen onSave và DnsProviderViewModel.setCustomDns so host đã bỏ tls:// với fallbackDns. Trường hợp fallback = 1.1.1.1 sẽ từ chối tls://1.1.1.1. Không thấy kiểm tra trùng toàn bộ danh sách built-in như mô tả issue; cần xác nhận fallback của người báo.

Bằng chứng: [SettingsViewModel](../../../app/src/main/java/app/pwhs/blockads/ui/settings/SettingsViewModel.kt), [SettingsBackup](../../../app/src/main/java/app/pwhs/blockads/data/entities/SettingsBackup.kt), [ProfileManager](../../../app/src/main/java/app/pwhs/blockads/data/entities/ProfileManager.kt), [DNS ViewModel](../../../app/src/main/java/app/pwhs/blockads/ui/dnsprovider/DnsProviderViewModel.kt).

## Kế hoạch thực hiện

### #236: Profile làm mất lựa chọn filter

- [ ] Tái hiện chọn 12 filter, chuyển sang mặc định rồi quay lại, kể cả sau khi tắt/mở app.
- [ ] Kiểm tra ProfileManager, ProtectionProfileDao, ProfileViewModel và đồng bộ filter khi đổi profile.
- [ ] Xác định nơi ghi đè snapshot profile; bảo đảm lưu/áp dụng nhất quán và không bị đồng bộ remote ghi đè.
- Hoàn thành khi: ID và trạng thái của cả 12 filter được giữ qua chuyển profile, restart và refresh catalog; profile khác không bị đổi theo.

### #225, #222: Backup/restore thiếu dữ liệu

- [ ] Lập bảng đối chiếu trường SettingsBackup với preferences, database và profile; kiểm tra thứ tự restore profile có ghi đè DNS/filter vừa nhập không.
- [ ] Tái hiện export → kiểm tra JSON → restore trong dữ liệu app sạch trên thiết bị thử nghiệm.
- [ ] Kiểm tra whitelist domain, DNS chính/dự phòng, custom rule, firewall, app whitelist và profile; phân biệt import gộp với thay thế theo hành vi sản phẩm hiện có.
- [ ] Bổ sung schema/migration hoặc sửa thứ tự restore chỉ khi xác nhận thiếu; tách logic backup khỏi UI nếu cần mở rộng.
- Hoàn thành khi: round-trip giữ các trường thuộc phạm vi backup; file cũ vẫn đọc được; file hỏng được báo lỗi và không để cấu hình bị cập nhật dở dang.

### #228: DNS khác giao thức bị xem là trùng

- [ ] Kiểm tra DnsProviderViewModel, DnsProvider, DnsProtocol và quy tắc chuẩn hóa endpoint.
- [ ] Dùng danh tính endpoint có giao thức, host, port và path khi phù hợp; tránh so sánh chỉ IP.
- Hoàn thành khi: DNS thường và tls://1.1.1.1 cùng tồn tại; endpoint tương đương thật sự vẫn bị phát hiện trùng; có ca IPv6, port và DoH path.
