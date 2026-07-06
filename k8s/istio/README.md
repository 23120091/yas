# YAS Istio Service Mesh

Service mesh chỉ áp dụng cho namespace `production`. `dev` và `staging` chạy như Kubernetes service thông thường để tránh lỗi UI kiểu `RBAC: access denied` khi đang demo hoặc phát triển nhanh.

## Service Giữ Lại

| Service | Lý do giữ |
| --- | --- |
| `product` | Sản phẩm, trung tâm của shop |
| `cart` | Giỏ hàng, demo flow mua hàng |
| `order` | Đơn hàng, demo flow đặt hàng và retry policy |
| `payment` | Dependency của `order -> payment` trong checkout |
| `customer` | Thông tin khách hàng |
| `inventory` | Kho hàng, order phụ thuộc |
| `tax` | Thuế, order phụ thuộc và demo VirtualService retry |
| `media` | Upload hình ảnh sản phẩm |
| `search` | Tìm kiếm, phụ thuộc `product`, demo AuthorizationPolicy |
| `storefront-bff` | BFF cho giao diện người dùng |
| `storefront-ui` | Giao diện cửa hàng để demo |
| `backoffice-bff` | BFF cho quản trị |
| `backoffice-ui` | Giao diện quản trị |
| `swagger-ui` | API documentation |
| `sampledata` | Seed/demo data API dùng từ storefront |

ApplicationSet vẫn giữ đầy đủ service theo GitOps. Danh sách này chỉ mô tả các service được mở trong service mesh production allow-list.

## Manifest

- `peer-authentication.yaml`: bật mTLS STRICT trong `production`, riêng BFF và `swagger-ui` PERMISSIVE để nhận traffic từ Traefik.
- `authorization-policy.yaml`: default deny cho `production`, chỉ mở đúng BFF, UI, Swagger và các dependency nội bộ được giữ. Với service API, rule allow dựa trên service principal; không dùng `paths: ["/**"]` vì wildcard này không match mọi API path trong Istio AuthorizationPolicy.
- `virtual-service-order.yaml`: retry/timeout cho `order -> tax` và `order -> cart`.
- `debug-tools.yaml`: debug pod có sidecar trong `production` để curl test policy.
## Deploy

Repo dùng GitOps qua ArgoCD. Quy trình chuẩn:

```bash
git push
argocd app sync kiali-server --force
```

Nếu cần kiểm tra nhanh trước khi sync:

```bash
kubectl apply --dry-run=server \
  -f k8s/istio/peer-authentication.yaml \
  -f k8s/istio/authorization-policy.yaml \
  -f k8s/istio/virtual-service-order.yaml \
  -f k8s/istio/debug-tools.yaml
```

## Test Plan

### Dev không bị mesh chặn

```bash
curl -i https://storefront-dev.tthong.dev/api/product/storefront/categories
kubectl get authorizationpolicy,peerauthentication,virtualservice -n dev
```

Kỳ vọng: không còn response `RBAC: access denied`; namespace `dev` không còn Istio policy production.

### mTLS production

```bash
kubectl get pods -n production
kubectl get peerauthentication -n production
```

Kỳ vọng: workload production có sidecar `2/2`; `mtls-strict-production` tồn tại.

### AuthorizationPolicy allowed

```bash
kubectl exec -n production deploy/storefront-bff -c istio-proxy -- \
  curl -s http://product/product/storefront/categories
```

Kỳ vọng: request đến được service, có thể trả mã ứng dụng nhưng không phải Istio `403 RBAC: access denied`.

### AuthorizationPolicy denied

```bash
kubectl exec -n production deploy/istio-debug -c debug -- \
  curl -i --max-time 5 http://product/product/storefront/categories
```

Kỳ vọng: `403 Forbidden` từ Istio vì `istio-debug` không nằm trong allow-list của `product`.

### Sampledata production

```bash
curl -i https://storefront.tthong.dev/api/sampledata/storefront/sampledata
```

Kỳ vọng: không còn Istio `403 RBAC: access denied`; nếu lỗi tiếp theo xuất hiện thì là lỗi ứng dụng của `sampledata`, không phải mesh policy.

### Retry policy

```bash
kubectl exec -n production deploy/order -c istio-proxy -- \
  curl -s http://localhost:15000/config_dump | grep -A10 "tax.production.svc"

kubectl exec -n production deploy/order -c istio-proxy -- \
  curl -s http://localhost:15000/config_dump | grep -A10 "cart.production.svc"
```

Kỳ vọng: `tax` có `num_retries: 3`; `cart` có `num_retries: 2`.

## Kiali

Truy cập Kiali và chọn namespace `production`:

```bash
kubectl get application kiali-server -n argocd
kubectl get pods -n istio-system
```

Topology mong đợi: `Traefik -> storefront-bff/backoffice-bff -> product/cart/order/customer/inventory/tax/media/search/payment`, kèm flow `order -> cart/payment/inventory/tax`.
