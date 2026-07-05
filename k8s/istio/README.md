# Service Mesh — Istio trên YAS

## Tổng quan

YAS sử dụng **Istio** làm service mesh với các tính năng:
- **mTLS STRICT**: Mã hóa toàn bộ traffic giữa các service (PeerAuthentication)
- **AuthorizationPolicy**: Kiểm soát service-to-service access theo mô hình zero-trust
- **VirtualService retry/timeout**: Retry policy cho order → cart/tax
- **AuthorizationPolicy cho order dependencies**: Chỉ cho phép `order` gọi `cart`, `inventory`, `payment`, `tax` trong flow đặt hàng
- **Kiali**: Visualize topology, health, traffic graph
- **Grafana + Tempo**: Metrics và tracing stack cho project; Kiali liên kết sang Grafana/Tempo, không dùng Jaeger riêng

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
- **allow-order-to-cart / allow-order-to-inventory / allow-order-to-payment / allow-order-to-tax**: Mở đúng các dependency cần cho luồng đặt hàng trong namespace `dev`.
- **product-allow-callers**: Cho phép `storefront-bff`, `backoffice-bff`, `order`, `search`, `cart`, `rating`, `recommendation`, `sampledata` gọi service `product`.
- **search-allow-callers**: Kiểm soát traffic vào `search`, chỉ cho phép `product`, `storefront-bff`, `backoffice-bff`.

### 3. VirtualService Retry/Timeout

**File**: `k8s/istio/virtual-service-order.yaml`

Áp dụng cho cả 3 môi trường (dev/staging/production). Mỗi môi trường có rule riêng với `sourceLabels` tương ứng (`order-dev`, `order-staging`, `order-production`).

| Rule | Host | Retry | PerTryTimeout | Timeout | Ghi chú |
|------|------|-------|---------------|---------|---------|
| order→tax | tax | 3 lần | 2s | 10s | Dependency quan trọng, dùng để demo retry |
| order→cart | cart | 2 lần | 2s | 10s | Dependency quan trọng trong checkout |
| order→inventory | inventory | Không cấu hình retry riêng | - | - | Được kiểm soát bằng AuthorizationPolicy |
| order→payment | payment | Không cấu hình retry riêng | - | - | Được kiểm soát bằng AuthorizationPolicy |

Retry on: `connect-failure, refused-stream, unavailable, 503, 5xx`

### 4. Kiali external services

**File**: `k8s/argocd/applicationsets/kiali.yaml`

Đã cấu hình Kiali để dùng đúng observability stack của project:

- **Prometheus**: `http://prometheus-dev-server.observability-dev:80`
- **Grafana**: `http://grafana-grafana.observability:80/`
- **Tracing provider = Tempo**: `http://tempo-dev.observability-dev:3200/`

Project này dùng **Grafana + Tempo** cho tracing. Không triển khai Jaeger riêng, nên nếu Kiali hiển thị Jaeger mặc định hoặc `Version: unknown` thì đó là dấu hiệu Kiali chưa được cấu hình tracing đúng.

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

Sau khi sync, vào Kiali kiểm tra lại phần `Mesh` / `About`:
- Grafana phải trỏ về `grafana.tthong.dev`
- Tracing provider phải là **Tempo**
- Không còn phụ thuộc vào Jaeger mặc định của Istio add-on

### Bước 5: Kiểm tra Kiali

```bash
kubectl get application kiali-server -n argocd
kubectl get pods -n istio-system
```

Truy cập: https://kiali.tthong.dev

## Test Plan


### Test 1: AuthorizationPolicy — service được phép

```bash
# search -> product (ALLOWED — trong danh sách)
kubectl exec -n dev deploy/search -- wget -q -O- --timeout=5 "http://product/product?pageNo=0&pageSize=1"
```

Kỳ vọng:
- trả về `401 Unauthorized` hoặc response của ứng dụng
- không bị Istio chặn bằng `403 Forbidden`


### Test 2: AuthorizationPolicy — service không được phép

```bash
# payment -> product (DENIED — không trong allow list của product)
kubectl exec -n dev deploy/payment -- wget -q -O- --timeout=5 "http://product/product?pageNo=0&pageSize=1"
```

Kỳ vọng:
- trả về `403 Forbidden`


### Test 3: mTLS STRICT

```bash
kubectl get pods -n dev
kubectl get pods -n staging
kubectl get pods -n production
```

Kỳ vọng:
- workload trong mesh hiển thị `2/2` containers ready


### Test 4: VirtualService retry

```bash
# Xem retry policy trên order pod
kubectl exec -n dev deploy/order -c istio-proxy -- curl -s "http://localhost:15000/config_dump" | grep -A10 "tax.dev.svc"
kubectl exec -n dev deploy/order -c istio-proxy -- curl -s "http://localhost:15000/config_dump" | grep -A10 "cart.dev.svc"
```

Kỳ vọng:
- `tax` có `num_retries: 3`
- `cart` có `num_retries: 2`


### Test 5: AuthorizationPolicy cho order dependencies

```bash
# order -> inventory (ALLOWED theo policy)
kubectl exec -n dev deploy/order -- wget -S -O- --timeout=5 "http://inventory/" || true

# order -> payment (ALLOWED theo policy)
kubectl exec -n dev deploy/order -- wget -S -O- --timeout=5 "http://payment/" || true
```

Kỳ vọng:
- request đến được service
- có thể trả `401`, `404`, hoặc mã ứng dụng khác tùy endpoint
- không bị Istio chặn bằng `403 Forbidden`


### Test 6: Edge path storefront/backoffice

```bash
kubectl get authorizationpolicy -n dev
kubectl get endpoints -n dev storefront-bff storefront-ui backoffice-bff backoffice-ui
kubectl get pods -n dev
```

Kỳ vọng:
- `allow-ingress-to-storefront-bff` và `allow-ingress-to-backoffice-bff` tồn tại
- `storefront-bff` và `backoffice-bff` có endpoints ready
- website truy cập được qua Traefik



## Deliverables

- **YAML manifests**:
  - `k8s/istio/peer-authentication.yaml`
  - `k8s/istio/authorization-policy.yaml`
  - `k8s/istio/virtual-service-order.yaml`
- **Kiali topology screenshot**:
  - chụp graph thể hiện `Traefik -> storefront-bff/backoffice-bff -> business services`
  - nhấn mạnh flow đặt hàng: `order -> cart`, `order -> inventory`, `order -> payment`, `order -> tax`
- **Test evidence**:
  - log ALLOW, ví dụ `search -> product` trả `401`
  - log DENY, ví dụ `payment -> product` trả `403`
  - evidence retry cho `order -> tax` và `order -> cart`
  - evidence `order -> inventory` và `order -> payment` không bị policy chặn
- **README triển khai**:
  - mô tả merge + sync ArgoCD
  - mô tả cách kiểm tra Kiali
  - mô tả cách chạy test plan và expected result
  - nêu rõ project dùng **Tempo thay cho Jaeger**

## Files

| File | Mô tả |
|------|-------|
| `k8s/istio/peer-authentication.yaml` | mTLS STRICT cho dev/staging/production |
| `k8s/istio/authorization-policy.yaml` | Authorization rules cho edge BFF, UI, business services, order dependencies, product/search |
| `k8s/istio/virtual-service-order.yaml` | Retry/timeout cho order->tax/cart (3 env) |
| `k8s/istio/debug-tools.yaml` | Debug deployment có sidecar Istio trong dev/staging/production để chạy `curl`/`wget` test policy |
| `k8s/argocd/applicationsets/kiali.yaml` | ArgoCD Application quản lý Kiali Helm chart + path `k8s/istio` |

## Debug pod dùng cho demo/test mesh

**File**: `k8s/istio/debug-tools.yaml`

Repo hiện có sẵn `Deployment/istio-debug` trong cả `dev`, `staging`, `production` để test mTLS, authorization policy và retry mà không cần thêm shell vào image ứng dụng.

Đặc điểm:
- Chạy image `curlimages/curl`
- Có `sh` để `kubectl exec`
- Có annotation `sidecar.istio.io/inject: "true"` để traffic đi qua Envoy sidecar
- Được quản lý qua ArgoCD cùng nhóm manifest `k8s/istio/`

Lệnh kiểm tra nhanh:

```bash
kubectl get pods -n dev -l app.kubernetes.io/name=istio-debug
kubectl get pods -n staging -l app.kubernetes.io/name=istio-debug
kubectl get pods -n production -l app.kubernetes.io/name=istio-debug
```

Ví dụ test từ debug pod:

```bash
# DEV
kubectl exec -n dev deploy/istio-debug -c debug -- curl -i http://product/product?pageNo=0&pageSize=1
kubectl exec -n dev deploy/istio-debug -c debug -- curl -i http://inventory/inventory/backoffice/warehouses

# STAGING
kubectl exec -n staging deploy/istio-debug -c debug -- curl -i http://search/storefront/search_suggest?keyword=ip

# PRODUCTION
kubectl exec -n production deploy/istio-debug -c debug -- curl -i http://tax/tax
```

Gợi ý demo policy:
- Request được phép: curl từ `istio-debug` tới endpoint public/permit-all hoặc endpoint không bị hạn chế bởi AuthorizationPolicy
- Request bị chặn: curl tới service chỉ cho phép BFF gọi, để nhận `403`/`RBAC: access denied`
- Retry: gọi endpoint đã gắn `VirtualService` retry rồi đối chiếu log/Kiali
