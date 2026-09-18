# 5. UI, hiệu năng và các issue cần phân loại

[Mục lục](../2026-09-05-bug-fix-plan.md) · Baseline code: `a997619`.


## Preflight code

- **#203 — chưa tái hiện:** PowerButton đã dùng Modifier.clickable. Không thể kết luận thiếu clickable/focus chỉ từ báo cáo; cần D-pad traversal và focus indicator trên Android TV.
- **#95 — có implementation refresh:** AppWhitelistScreen có IconButton gọi refreshApps và ViewModel gọi loadApps. Cần retest phiên bản hiện tại; yêu cầu force-close trong bình luận có thể đã cũ.
- **#119 — có đường ghi log hiện tại:** GoTunnelAdapter thiết lập log callback và connLogEnabled theo recordLogProvider. Chưa biết case HTTPS cụ thể bị mất log; cần xác nhận toggle, app, UID, request và DNS protocol. Shizuku vẫn là tính năng riêng.
- **#85 — thiếu ca tái hiện xác định:** cần URL/app và request quảng cáo; điểm số ad-test/YouTube không chứng minh lỗi parser hay tunnel cụ thể.
- **#151 — thiếu profile hiệu năng:** service startup có load/sync filter trước khi sẵn sàng; chưa đo nên chưa quy kết đây là bottleneck.
- **#167 — chưa kiểm tra runtime/layout:** chỉ đưa vào backlog xác minh UI/fallback, chưa gọi là bug code đã xác nhận.
- **#56 / #164:** kết quả parser allowlist nằm ở [nhóm filter](02-filters.md).

Bằng chứng: [PowerButton](../../../app/src/main/java/app/pwhs/blockads/ui/home/component/PowerButton.kt), [AppWhitelistScreen](../../../app/src/main/java/app/pwhs/blockads/ui/whitelist/AppWhitelistScreen.kt), [GoTunnelAdapter](../../../app/src/main/java/app/pwhs/blockads/service/GoTunnelAdapter.kt).

## Kế hoạch thực hiện

- [ ] #203: sửa focus/D-pad cho Android TV; điều hướng tới nút bảo vệ, bật/tắt và quay lại được bằng remote, không cần touch.
- [ ] #119: tách báo cáo thiếu log HTTPS và DNS NextDNS khỏi yêu cầu Shizuku; tái hiện HTTPS on/off, kiểm tra resolver và log theo đúng loại traffic.
- [ ] #85: yêu cầu URL/app, phiên bản, mode, filter và request cụ thể; không dùng điểm ad-test hay YouTube đơn lẻ làm tiêu chí đúng cho mọi khả năng lọc.
- [ ] #151: đo thời gian khởi tạo theo bước tải/biên dịch filter, khởi tạo engine và VPN; chọn tối ưu từ số đo, ghi trước/sau trên cùng thiết bị.
- [ ] #95: kiểm tra danh sách app whitelist có cập nhật khi cài/gỡ app hoặc quay lại màn hình; yêu cầu tùy biến navigation là tính năng riêng.
- [ ] #56 / #164: xác nhận allowlist chưa hỗ trợ hay parser sai. Nếu chưa hỗ trợ, báo rõ loại danh sách; triển khai allowlist là hạng mục riêng, có kiểm tra exception precedence và số rule.
- [ ] #167: xem nhãn giờ biểu đồ chồng nhau và hiển thị DNS fallback; các yêu cầu test DNS trước lưu, thống kê và UI khác phân loại riêng.
- #243 và các yêu cầu DoQ, userscript, proxy, DPI, per-app domain rule không thuộc đợt sửa bug này.
