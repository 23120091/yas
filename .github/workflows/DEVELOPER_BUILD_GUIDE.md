# Hướng dẫn sử dụng Workflow Developer Build (Hybrid Sandbox Namespace)

## 1. Giới thiệu tổng quan
File `.github/workflows/developer-build.yml` định nghĩa quá trình (workflow) CI/CD dành cho developer để test code của mình một cách biệt lập, an toàn và tối ưu tài nguyên trước khi merge vào `main`.

**Cơ chế hoạt động (Hybrid Sandbox):**
1. **Cô lập theo Namespace:** Thay vì đè lên môi trường chung, service bạn chọn test sẽ được deploy vào một namespace riêng tên là `sandbox`.
2. **Liên kết môi trường (Hybrid Routing):** 
   - Để tiết kiệm tài nguyên (không cần khởi tạo lại cả 19 service khác), workflow chỉ deploy đúng duy nhất service bạn cần test lên `sandbox`.
   - Các service còn lại mà service của bạn cần gọi sẽ được hệ thống định tuyến (thông qua Kubernetes **ExternalName**) trỏ chéo sang namespace môi trường thật bạn chọn (ví dụ: `dev` hoặc `staging`).
3. **Đồng bộ cấu hình tự động:** Job sẽ tự động sao chép các ConfigMaps và Secrets gốc (ví dụ các DB credentials, logback, JWT secrets) từ namespace nguồn sang namespace `sandbox` để đảm bảo kết nối thông suốt.
4. **NodePort truy cập:** Service được test sẽ tự động cấu hình kiểu `NodePort` để cho phép truy cập trực tiếp từ máy cá nhân của bạn.

---

## 2. Quy trình chi tiết để thực hiện deploy và test

Để đưa mã nguồn mới của mình lên môi trường test Sandbox:

### Bước 1: Phát triển và đẩy code lên Github (Push)
Làm việc trên một nhánh riêng tư và đẩy mã nguồn lên Github:
```bash
# Tạo và chuyển sang nhánh làm việc mới (ví dụ sửa service tax)
git checkout -b dev_tax_service

# ... Thực hiện sửa đổi code trong thư mục của service ...

# Lưu các thay đổi
git add .
git commit -m "fix: update tax calculation logic"

# Đẩy code lên GitHub
git push origin dev_tax_service
```

### Bước 2: Kích hoạt Workflow (developer_build)
1. Đăng nhập vào trang repository của dự án trên **GitHub**.
2. Chuyển sang tab **Actions**.
3. Tìm ở menu bên trái và nhấp vào workflow **Developer Build (Sandbox Deploy)**.
4. Nhấp vào nút **Run workflow** ở góc trên bên phải.
5. Nhập các thông số đầu vào:
   - **service_name (Dropdown):** Bấm chọn đúng tên service bạn vừa sửa (ví dụ: `tax`).
   - **branch_name (Text):** Nhập chính xác tên nhánh bạn vừa push lên (ví dụ: `dev_tax_service`).
   - **environment (Dropdown):** Chọn môi trường chạy các service vệ tinh còn lại (mặc định là `dev`).
6. Nhấp nút **Run workflow** (màu xanh lá) để bắt đầu.

### Bước 3: Đợi tiến trình hoàn tất và lấy thông tin truy cập
1. Đợi đến khi tiến trình (job) hiển thị trạng thái hoàn tất (dấu tích xanh).
2. Bấm vào chi tiết của lần chạy, tìm phần **Summary** của Workflow.
3. Hệ thống sẽ in ra thông tin chi tiết bao gồm **NodePort** (Ví dụ: `30123`).

### Bước 4: Cấu hình file Hosts và bắt đầu Test
Sử dụng NodePort nhận được từ Bước 3 để tiến hành trỏ IP:
1. Mở file `hosts` trên máy tính cá nhân của bạn với quyền Administrator (hoặc Sudo):
   - **Windows:** `C:\Windows\System32\drivers\etc\hosts`
   - **Linux / Mac:** `/etc/hosts`
2. Thêm dòng khai báo ánh xạ (mapping) IP ngoại (External IP) của K3s master với tên miền Sandbox tương ứng:
   ```text
   34.87.6.157    tax.sandbox.yas.local.com
   ```
3. Mở trình duyệt web hoặc công cụ test (như Postman), truy cập vào địa chỉ dạng: 
   `http://tax.sandbox.yas.local.com:<NodePort_của_bạn>` để kiểm tra.

---

## 3. Dọn dẹp môi trường (Cleanup)

Sau khi hoàn thành test, nếu bạn muốn dọn dẹp để làm trống tài nguyên cụm:
- **Để xóa hoàn toàn Sandbox:** Chạy lệnh sau trên máy `phuoc@master`:
  ```bash
  kubectl delete namespace sandbox
  ```
- **Nếu chỉ muốn gỡ bỏ service đó:**
  ```bash
  helm uninstall <tên_service> -n sandbox
  ```
- *Lưu ý:* Nếu bạn không xóa, lần chạy sau của bất kỳ developer nào trên cùng service đó sẽ tự động cập nhật đè lên bản cũ của bạn.
