# Hướng dẫn sử dụng Workflow Developer Build (Sandbox Deploy)

## 1. Giới thiệu tổng quan
File `.github/workflows/developer-build.yml` định nghĩa một quá trình (workflow) CI/CD dành cho developer để test code của mình trên môi trường chung (Sandbox) trước khi gộp vào nhánh chính (`main`).

**Workflow này hoạt động như sau:**
1. **Lựa chọn Branch:** Cho phép developer nhập tên nhánh (branch) mong muốn cho TỪNG service. Mặc định là nhánh `main` cho những service không bị thay đổi.
2. **Build & Push:** Tự động kiểm tra và build Docker image cho những service có branch được chỉ định (tránh build lại nếu image đã tồn tại). Image tag được gán theo tên nhánh và mã SHA của commit.
3. **Deploy (Helm Upgrade):** Triển khai (deploy) toàn bộ 19 service vào chung một namespace mang tên `sandbox`.
4. **Cấu hình NodePort:** Tự động chuyển đổi `service.type` thành `NodePort` cho phép bạn truy cập trực tiếp từ bên ngoài mà không cần cấu hình Ingress phức tạp.
5. **Cung cấp truy cập:** Sau khi hoàn thành, Job sẽ in ra danh sách các Service và thông tin **NodePort** ở phần Summary để developer tự cấu hình và truy cập.

> **⚠️ LƯU Ý:** Do cơ chế deploy vào chung namespace `sandbox`, nếu nhiều người chạy workflow này cho các service khác nhau cùng một thời điểm, lần chạy sau sẽ **ghi đè (overwrite)** các service của lần chạy trước. Bạn nên thông báo với nhóm khi sử dụng môi trường này.

---

## 2. Quy trình chi tiết để thực hiện deploy và test

Để một developer có thể đưa mã nguồn mới của mình lên môi trường Sandbox, hãy thực hiện lần lượt các bước dưới đây:

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

### Bước 2: Kích hoạt Workflow (developer_build) thủ công
1. Đăng nhập vào trang repository của dự án trên **GitHub**.
2. Chuyển sang tab **Actions**.
3. Tìm ở menu bên trái và nhấp vào workflow **Developer Build (Sandbox Deploy)**.
4. Nhấp vào nút **Run workflow** ở góc trên bên phải.
5. Một bảng form nhập thông tin (input) sẽ xuất hiện:
   - Ở mục nhập liệu tương ứng với service bạn vừa sửa (Ví dụ mục `tax`), hãy gõ vào đúng tên nhánh của bạn: `dev_tax_service`.
   - Đối với các ô service còn lại không sửa: hãy **giữ nguyên giá trị mặc định** (là `main`).
6. Nhấp nút **Run workflow** (màu xanh lá) để hệ thống bắt đầu chạy quá trình Build và Deploy.

### Bước 3: Đợi tiến trình hoàn tất và lấy thông tin truy cập
1. Đợi đến khi tiến trình (job) hiển thị trạng thái hoàn tất (dấu tích xanh).
2. Bấm vào chi tiết của lần chạy đó, tìm trong cửa sổ log bước có tên **"Print access information"** hoặc xem trực tiếp ở trang **Summary** của Workflow.
3. Hệ thống sẽ in ra một bảng hiển thị tên các Service và **NodePort** tương ứng. Hãy ghi chú lại giá trị NodePort của Service bạn cần test.

### Bước 4: Cấu hình file Hosts và bắt đầu Test
Sử dụng NodePort nhận được từ Bước 3 để tiến hành trỏ IP:
1. Mở file `hosts` trên máy tính cá nhân của bạn với quyền Administrator (hoặc Sudo):
   - **Windows:** `C:\Windows\System32\drivers\etc\hosts`
   - **Linux / Mac:** `/etc/hosts`
2. Thêm dòng khai báo ánh xạ (mapping) IP của Node Kubernetes (K3s worker node) với tên miền tương ứng.
   *Ví dụ, nếu IP máy chủ K3s của bạn là `192.168.1.50`, bạn thêm dòng sau:*
   ```text
   192.168.1.50    tax.sandbox.yas.local.com
   ```
3. Mở trình duyệt web hoặc công cụ test (như Postman), truy cập vào địa chỉ dạng: 
   `http://tax.sandbox.yas.local.com:<NodePort_của_bạn>` để kiểm tra mã nguồn bạn vừa sửa!
