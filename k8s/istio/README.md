# Service Mesh — Istio trên YAS

## Tổng quan

YAS sử dụng **Istio** làm service mesh với các tính năng:
- **mTLS STRICT**: Mã hóa toàn bộ traffic giữa các service (PeerAuthentication)
- **AuthorizationPolicy**: Kiểm soát service-to-service access (demo: search → product)
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

- **product-allow-callers**: Cho phép `storefront-bff`, `backoffice-bff`, `order`, `search`, `cart`, `rating`, `recommendation`, `sampledata` gọi service `product`. Các service khác (vd: `payment`) bị từ chối (403).
- **search-allow-callers**: Kiểm soát traffic vào `search`, chỉ cho phép `product`, `storefront-bff`, `backoffice-bff`.

### 3. VirtualService Retry/Timeout

**File**: `k8s/istio/virtual-service-order.yaml`

Áp dụng cho cả 3 môi trường (dev/staging/production). Mỗi môi trường có rule riêng với `sourceLabels` tương ứng (`order-dev`, `order-staging`, `order-production`).

| Rule | Host | Retry | PerTryTimeout | Timeout |
|------|------|-------|---------------|---------|
| order→tax | tax | 3 lần | 2s | 10s |
| order→cart | cart | 2 lần | 2s | 10s |

Retry on: `connect-failure, refused-stream, unavailable, 503`

### 4. Kiali Prometheus

**File**: `k8s/argocd/applicationsets/kiali.yaml`

Đã thêm `external_services.prometheus.url` vào Helm values để Kiali kết nối đến Prometheus (`prometheus-dev-server.observability-dev:80`).

## Triển khai

### Bước 1: Apply PeerAuthentication (mTLS)

```bash
kubectl apply -f k8s/istio/peer-authentication.yaml
```

### Bước 2: Apply AuthorizationPolicy

```bash
kubectl apply -f k8s/istio/authorization-policy.yaml
```

### Bước 3: Apply VirtualService (retry/timeout)

```bash
kubectl apply -f k8s/istio/virtual-service-order.yaml
```

### Bước 4: Cập nhật Kiali Prometheus URL

Push changes to Git → ArgoCD tự động sync. Hoặc thủ công:

```bash
kubectl patch application kiali-server -n argocd --type merge \
  -p '{"spec":{"source":{"helm":{"values":"external_services:\n  prometheus:\n    url: http://prometheus-dev-server.observability-dev:80\n"}}}}'
```

### Bước 5: Kiểm tra Kiali

```bash
kubectl rollout restart deployment kiali -n istio-system
kubectl rollout status deployment kiali -n istio-system
```

Truy cập: https://kiali.tthong.dev

## Test Plan

### Test 1: AuthorizationPolicy — service được phép

```bash
# search → product (ALLOWED — trong danh sách)
kubectl exec -n dev deploy/search -- wget -q -O- --timeout=5 "http://product/product?pageNo=0&pageSize=1"
# Kết quả: HTTP 401 (đến được product, cần JWT)

# Tương tự cho staging và production (thay -n staging/production)
```

### Test 2: AuthorizationPolicy — service KHÔNG được phép

```bash
# payment → product (DENIED — không trong danh sách)
kubectl exec -n dev deploy/payment -- wget -q -O- --timeout=5 "http://product/product?pageNo=0&pageSize=1"
# Kết quả: HTTP 403 (Istio RBAC từ chối)

# Tương tự cho staging và production
```

### Test 3: mTLS STRICT

```bash
# Kiểm tra tất cả pod có sidecar (2/2 ready) trên cả 3 env
kubectl get pods -n dev
kubectl get pods -n staging
kubectl get pods -n production
# Kết quả: tất cả 2/2 Running
```

### Test 4: VirtualService retry

Kiểm tra Envoy config:

```bash
# Xem retry policy trên order pod (thay dev bằng staging/production)
kubectl exec -n dev deploy/order -c istio-proxy -- curl -s "http://localhost:15000/config_dump" | grep -A10 "tax.dev.svc"
# Kết quả: num_retries: 3, per_try_timeout: 2s, timeout: 10s
```

## Kết quả logs

### Curl từ search → product
```
$ kubectl exec -n dev deploy/search -- wget -q -O- --timeout=5 "http://product/product?pageNo=0&pageSize=1"
wget: server returned error: HTTP/1.1 401 Unauthorized
```
→ Traffic đến được product (mTLS + AuthZ OK), app yêu cầu JWT.

### Curl từ payment → product
```
$ kubectl exec -n dev deploy/payment -- wget -q -O- --timeout=5 "http://product/product?pageNo=0&pageSize=1"
wget: server returned error: HTTP/1.1 403 Forbidden
```
→ Istio AuthorizationPolicy chặn (payment không trong allow list).

### Envoy config — retry policy
```
tax.dev.svc.cluster.local:8090:
  retry_policy:
    num_retries: 3
    per_try_timeout: 2s
    retry_on: connect-failure,refused-stream,unavailable,retriable-status-codes
  timeout: 10s
```

## Kiali

Truy cập https://kiali.tthong.dev để xem:
- **Graph**: Topology service mesh, traffic flow
- **Services**: Danh sách service + health
- **Workloads**: Pod + sidecar status
- **Istio Config**: Các rule PeerAuthentication, AuthorizationPolicy, VirtualService

## Troubleshooting

| Vấn đề | Nguyên nhân | Cách fix |
|--------|-------------|----------|
| Kiali "Could not fetch health" | Prometheus URL sai | Kiểm tra `external_services.prometheus.url` trong ArgoCD ApplicationSet |
| Kiali "Cannot load the graph" | Thiếu metrics hoặc tracing | Đảm bảo Prometheus scrape được istio-proxy metrics |
| Pod crash với lỗi mTLS | Service không có sidecar | Label namespace: `istio-injection=enabled` |
| 403 từ service khác | AuthorizationPolicy chặn | Thêm service vào allow list |

## Files

| File | Mô tả |
|------|-------|
| `k8s/istio/peer-authentication.yaml` | mTLS STRICT cho dev/staging/production |
| `k8s/istio/authorization-policy.yaml` | Authorization rules cho product/search (3 env) |
| `k8s/istio/virtual-service-order.yaml` | Retry/timeout cho order→tax/cart (3 env) |
| `k8s/istio/kiali-ingress.yaml` | Ingress cho kiali.tthong.dev |
| `k8s/argocd/applicationsets/kiali.yaml` | ArgoCD ApplicationSet cho Kiali |
