# Service Mesh — Istio trên YAS

## Tổng quan

YAS sử dụng **Istio** làm service mesh với các tính năng:
- **mTLS STRICT**: Mã hóa toàn bộ traffic giữa các service (PeerAuthentication)
- **AuthorizationPolicy**: Kiểm soát service-to-service access theo mô hình zero-trust
- **VirtualService retry/timeout**: Retry policy cho order → cart/tax
- **Kiali**: Visualize topology, health, traffic graph

## Kiến trúc

```
                     ┌──────────────┐
                     │   Traefik    │ (ingress controller)
                     └──────┬───────┘
                            │
              ┌─────────────┼─────────────┐
              │             │             │
        storefront-bff  backoffice-bff  kiali (istio-system)
              │             │
         ┌────┴────┐   ┌────┴────┐
         │  cart   │   │  order  │
         │product  │   │customer │
         │  tax    │   │product  │
         │   ...   │   │  tax    │
         └─────────┘   └─────────┘
```

Tất cả pod trong namespace `dev`, `staging`, `production` đều có **Istio sidecar (Envoy proxy)** injected (2/2 containers).

## Cấu hình đã triển khai

### 1. mTLS STRICT

**File**: `k8s/istio/peer-authentication.yaml`

Áp dụng cho cả 3 môi trường:

```yaml
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: mtls-strict-dev
  namespace: dev
spec:
  mtls:
    mode: STRICT
---
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: mtls-strict-staging
  namespace: staging
spec:
  mtls:
    mode: STRICT
---
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: mtls-strict-production
  namespace: production
spec:
  mtls:
    mode: STRICT
```

Bắt buộc mọi traffic trong namespace `dev`, `staging`, `production` phải dùng mutual TLS.

### 2. AuthorizationPolicy

**File**: `k8s/istio/authorization-policy.yaml`

- **deny-all-by-default**: Bật zero-trust cho namespace `dev`, mặc định khóa mọi kết nối nội bộ nếu không có rule ALLOW tương ứng.
- **allow-ingress-to-storefront-bff / allow-ingress-to-backoffice-bff**: Cho phép traffic từ Traefik/browser vào 2 BFF edge services.
- **allow-bff-to-storefront-ui / allow-bff-to-backoffice-ui**: Cho phép BFF truy cập UI tương ứng trong mesh.
- **allow-bff-to-* (cart/customer/inventory/location/media/order/rating/recommendation/tax)**: Chỉ cho phép 2 BFF gọi xuống các business services được chỉ định.
- **product-allow-callers**: Cho phép `storefront-bff`, `backoffice-bff`, `order`, `search`, `cart`, `rating`, `recommendation`, `sampledata` gọi service `product`.
- **search-allow-callers**: Kiểm soát traffic vào `search`, chỉ cho phép `product`, `storefront-bff`, `backoffice-bff`.

### 3. VirtualService Retry/Timeout

**File**: `k8s/istio/virtual-service-order.yaml`

Áp dụng cho cả 3 môi trường (dev/staging/production). Mỗi môi trường có rule riêng với `sourceLabels` tương ứng (`order-dev`, `order-staging`, `order-production`).

| Rule | Host | Retry | PerTryTimeout | Timeout |
|------|------|-------|---------------|---------|
| order→tax | tax | 3 lần | 2s | 10s |
| order→cart | cart | 2 lần | 2s | 10s |

Retry on: `connect-failure, refused-stream, unavailable, 503, 5xx`

### 4. Kiali Prometheus

**File**: `k8s/argocd/applicationsets/kiali.yaml`

Đã thêm `external_services.prometheus.url` vào Helm values để Kiali kết nối đến Prometheus (`prometheus-dev-server.observability-dev:80`).

## Triển khai

Repo này dùng **GitOps với ArgoCD**. Không `kubectl apply` trực tiếp các manifest ứng dụng/service mesh trong trạng thái bình thường. Quy trình chuẩn là:

1. Sửa manifest trong git
2. Merge vào `main`
3. ArgoCD phát hiện commit mới
4. Sync Application đang quản lý `k8s/istio`

Trong repo hiện tại, các manifest dưới `k8s/istio/` được quản lý bởi Application `kiali-server` qua multi-source ArgoCD Application.

### Bước 1: Apply PeerAuthentication (mTLS)

```bash
# Commit/merge thay đổi vào main
# Sau đó sync ArgoCD app quản lý k8s/istio
argocd app sync kiali-server --force
```

### Bước 2: Apply AuthorizationPolicy

```bash
# Policies nằm trong k8s/istio/authorization-policy.yaml
# Sau merge main, sync lại app:
argocd app sync kiali-server --force
```

### Bước 3: Apply VirtualService (retry/timeout)

```bash
# VirtualService cũng được quản lý qua k8s/istio/
argocd app sync kiali-server --force
```

### Bước 4: Cập nhật Kiali Prometheus URL

Push changes to Git → ArgoCD tự động sync. Có thể force refresh/sync thủ công:

```bash
kubectl annotate application kiali-server -n argocd argocd.argoproj.io/refresh=hard --overwrite
argocd app sync kiali-server --force
```

### Bước 5: Kiểm tra Kiali

```bash
kubectl get application kiali-server -n argocd
kubectl get pods -n istio-system
```

Truy cập: https://kiali.tthong.dev