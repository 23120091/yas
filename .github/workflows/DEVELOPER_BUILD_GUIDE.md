# Hướng dẫn sử dụng Workflow Developer Build (ArgoCD Sandbox Deploy)

## 1. Giới thiệu tổng quan
File `.github/workflows/developer-build.yml` định nghĩa một quá trình (workflow) CI/CD dành cho developer để test code của mình trên môi trường Dev (hoặc Sandbox) một cách nhanh chóng và tiết kiệm tài nguyên.

**Workflow này áp dụng giải pháp "Chạy đè tag bằng ArgoCD CLI" và hoạt động như sau:**
1. **Lựa chọn Đơn giản:** Cho phép developer chọn 1 Service duy nhất cần test từ danh sách (Dropdown) và nhập tên nhánh (Branch) chứa mã nguồn mới.
2. **Build & Push Nhanh chóng:** Tự động build Docker image cho riêng service đó. Image tag sẽ được gán theo định dạng `tên_nhánh-mã_sha_commit` (ví dụ: `dev_tax-a1b2c3d`).
3. **Deploy Siêu tốc (ArgoCD Override):** Thay vì chạy lại Helm cho hàng loạt service, Job sẽ dùng lệnh `argocd app set` để can thiệp trực tiếp vào ArgoCD, **ép (override) đổi tag** của riêng service đó sang image mới. Quá trình này diễn ra chỉ trong vài giây.
4. **Giữ nguyên trạng thái Git:** Cấu hình trên Git (file values.yaml của môi trường dev) vẫn giữ nguyên là tag `latest` hoặc `main`.

> **⚠️ LƯU Ý BẢO MẬT/XUNG ĐỘT:** 
> - Vì bạn đang can thiệp trực tiếp vào cụm qua ArgoCD, sự thay đổi tag này chỉ mang tính tạm thời (không lưu vào Git).
> - Nếu nhiều developer cùng thao tác test trên một service ở cùng thời điểm, người chạy job sau sẽ ghi đè lên tag của người chạy trước. 

---

## 2. Quy trình chi tiết để thực hiện deploy và test

Để đưa mã nguồn mới của mình lên môi trường test, hãy thực hiện lần lượt các bước dưới đây:

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
5. Sẽ chỉ có 2 thông tin bạn cần nhập:
   - **service_name (Dropdown):** Bấm chọn đúng tên service bạn vừa sửa (ví dụ: `tax`).
   - **branch_name (Text):** Nhập chính xác tên nhánh bạn vừa push lên (ví dụ: `dev_tax_service`).
6. Nhấp nút **Run workflow** (màu xanh lá) để hệ thống chạy Build và Override tag.

### Bước 3: Đợi tiến trình hoàn tất và Kiểm tra
1. Quá trình build và deploy kiểu mới diễn ra rất nhanh. Hãy đợi dấu tích xanh báo hoàn tất.
2. Bấm vào chi tiết lần chạy, xem phần **Summary** để xác nhận Tag mới đã được cập nhật thành công qua ArgoCD.
3. Tiến hành test service thông qua Domain/URL (hoặc Postman) của môi trường Dev/Sandbox hiện tại mà bạn vẫn thường dùng.

### Bước 4: Dọn dẹp môi trường (Reset) sau khi test xong
Vì đây là thao tác ép tag tạm thời, sau khi bạn test xong và merge code, hoặc muốn nhường môi trường lại cho người khác, bạn cần trả service về trạng thái mặc định:

**Cách 1 (Khuyên dùng):** Truy cập vào giao diện Web UI của ArgoCD. Tìm đến Application của service đó, nhấp vào nút **Sync** (đảm bảo nó đối chiếu lại với cấu hình trên Git gốc, tự kéo tag `latest` về lại).

**Cách 2 (Bằng lệnh CLI):** Nếu bạn có công cụ ArgoCD CLI trên máy:
```bash
argocd app unset tên_service --helm-set backend.image.tag --core
```
Lệnh này sẽ gỡ bỏ ép buộc tag, giúp hệ thống tự phục hồi về nguyên trạng theo GitOps.
