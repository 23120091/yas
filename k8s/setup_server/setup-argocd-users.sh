#!/bin/bash

# ==============================================================================
# SCRIPT CẤU HÌNH USER ARGO CD (TẠO USER + PHÂN QUYỀN + ĐỔI MẬT KHẨU)
# Yêu cầu: Đã cài đặt kubectl, argocd cli, python3 và đã có quyền admin cluster.
# ==============================================================================

NAMESPACE="argocd"
DEFAULT_PASSWORD="Password123"

echo "🚀 BƯỚC 1: KHAI BÁO USER TRONG CONFIGMAP (argocd-cm)..."

kubectl patch configmap argocd-cm -n $NAMESPACE --type=merge -p \
'{"data": {
  "accounts.thong": "login",
  "accounts.thuan": "login",
  "accounts.dang": "login",
  "accounts.phuoc": "login"
}}'
echo "Đã khai báo 4 users thành công."

echo "----------------------------------------------------------------"

echo "BƯỚC 2: PHÂN QUYỀN CHO USER (argocd-rbac-cm)..."

kubectl patch configmap argocd-rbac-cm -n argocd --type=merge -p \
'{"data": {
  "policy.csv": "p, role:admin, *, *, *, allow\ng, thong, role:admin\ng, thuan, role:admin\ng, dang, role:admin\ng, phuoc, role:admin"
}}'

echo "Đã cấp quyền admin thành công."

echo "----------------------------------------------------------------"

echo "BƯỚC 3: TẠO BCRYPT HASH CHO TỪNG USER (Cùng mật khẩu chung)..."
HASH_THONG=$(argocd account bcrypt --password $DEFAULT_PASSWORD)
HASH_THUAN=$(argocd account bcrypt --password $DEFAULT_PASSWORD)
HASH_DANG=$(argocd account bcrypt --password $DEFAULT_PASSWORD)
HASH_PHUOC=$(argocd account bcrypt --password $DEFAULT_PASSWORD)

echo "----------------------------------------------------------------"

echo "BƯỚC 4: TẠO FILE PATCH JSON CHO SECRET..."
cat > /tmp/patch-argocd.json <<EOF
{
  "stringData": {
    "accounts.thong.password": "$HASH_THONG",
    "accounts.thuan.password": "$HASH_THUAN",
    "accounts.dang.password": "$HASH_DANG",
    "accounts.phuoc.password": "$HASH_PHUOC"
  }
}
EOF

echo "Kiểm tra nội dung file patch:"
cat /tmp/patch-argocd.json

echo "----------------------------------------------------------------"

echo "BƯỚC 5: APPLY PATCH VÀO ARGOCD-SECRET..."
kubectl patch secret argocd-secret -n $NAMESPACE \
  --type=merge \
  --patch-file /tmp/patch-argocd.json
echo "Đã cập nhật Secret thành công."

echo "----------------------------------------------------------------"

echo "BƯỚC 6: RESTART ARGOCD-SERVER ĐỂ CẬP NHẬT CACHE..."
kubectl rollout restart deployment argocd-server -n $NAMESPACE

echo "Đang chờ rollout hoàn tất..."
kubectl rollout status deployment argocd-server -n $NAMESPACE

echo "----------------------------------------------------------------"

echo "BƯỚC 7: KIỂM TRA LẠI SECRET ĐÃ ĐƯỢC UPDATE..."
echo "=== Trạng thái băm mật khẩu của các account ===" 
kubectl get secret argocd-secret -n $NAMESPACE -o json | \
  python3 -c "
import json, sys, base64
s = json.load(sys.stdin)
data = s.get('data', {})
for k, v in data.items():
    if 'password' in k:
        decoded = base64.b64decode(v).decode()
        print(f'{k}: {decoded[:10]}...')  # chỉ in 10 ký tự đầu để check
"

echo "HOÀN TẤT! BẠN CÓ THỂ ĐĂNG NHẬP VỚI CÁC USER BẰNG MẬT KHẨU: $DEFAULT_PASSWORD"