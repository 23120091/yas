# YAS Istio Service Mesh

Service mesh chỉ áp dụng cho namespace `production`. `dev` và `staging` chạy như Kubernetes service thông thường để tránh lỗi UI kiểu `RBAC: access denied` khi đang demo hoặc phát triển nhanh.

## Service Giữ Lại

| Service | Lý do giữ |
| --- | --- |
| `product` | Sản phẩm, trung tâm của shop |
| `cart` | Giỏ hàng, demo flow mua hàng |
| `order` | Đơn hàng, demo flow đặt hàng và retry policy |
| `customer` | Thông tin khách hàng |
| `inventory` | Kho hàng, order phụ thuộc |
| `tax` | Thuế, order phụ thuộc và demo VirtualService retry |
| `media` | Upload hình ảnh sản phẩm |
| `search` | Tìm kiếm, phụ thuộc `product`, demo AuthorizationPolicy |
| `storefront-bff` | BFF cho giao diện người dùng |
| `backoffice-bff` | BFF cho quản trị |
| `swagger-ui` | API documentation |

ApplicationSet vẫn giữ đầy đủ service theo GitOps. Danh sách này chỉ mô tả các service được mở trong service mesh production allow-list.

## Manifest

- `peer-authentication.yaml`: bật mTLS STRICT trong `production`, riêng BFF và `swagger-ui` PERMISSIVE để nhận traffic từ Traefik.
- `authorization-policy.yaml`: default deny cho `production`, chỉ mở đúng BFF, UI, Swagger và các dependency nội bộ được giữ. Rule allow dựa trên service principal.
- `virtual-service-order.yaml`: retry/timeout cho `order -> tax`, `order -> cart`, `order -> inventory`.
- `tax-retry.yaml`: retry chung cho `tax`, gồm production mode + test mode fault injection.
- `debug-tools.yaml`: debug pod có sidecar trong `production` để curl test policy.

## Deploy

Repo dùng GitOps qua ArgoCD. Quy trình chuẩn:

```bash
git push
argocd app sync kiali-server --force
```

Nếu cần kiểm tra nhanh trước khi sync:

```bash
kubectl apply --dry-run=server -f k8s/istio/
```

## Test Plan

### Dev không bị mesh chặn

```bash
curl -i https://storefront-dev.tthong.dev/api/product/storefront/categories
kubectl get authorizationpolicy,peerauthentication,virtualservice -n dev
```

Kỳ vọng: không còn response `RBAC: access denied`; namespace `dev` không còn Istio policy.

### mTLS & Sidecar Injection

```bash
# Kiểm tra namespace có label injection
kubectl get namespace production --show-labels | grep istio-injection

# Pod phải có 2 containers (app + istio-proxy)
kubectl get pods -n production -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{len .spec.containers}{"\n"}{end}'

# PeerAuthentication STRICT đang chạy
kubectl get peerauthentication -n production
```

### AuthorizationPolicy — Allowed

```bash
# Từ storefront-bff (được allow) → product
kubectl exec -n production deploy/storefront-bff -c storefront-bff -- \
  wget -q -O - http://product:80/api/product/storefront/categories

# Từ order (được allow) → tax
ORDER_POD=$(kubectl get pod -n production -l app.kubernetes.io/name=order -o jsonpath='{.items[0].metadata.name}')
kubectl exec -it $ORDER_POD -n production -c order -- \
  wget -S -q -O - http://tax:80/tax/backoffice/tax-classes 2>&1
```

Kỳ vọng: HTTP 200/404 (application-level), không phải Istio `403 RBAC: access denied`.

### AuthorizationPolicy — Denied

```bash
# Từ debug pod (không trong allow-list) → product
kubectl exec -n production deploy/istio-debug -c debug -- \
  wget -S -q -O - http://product:80/api/product/storefront/categories 2>&1
```

Kỳ vọng: `403 Forbidden` hoặc `RBAC: access denied`.

### Service-to-Service Connectivity (Mesh Internal)

```bash
# storefront-bff → product
kubectl exec -it deploy/storefront-bff -n production -c storefront-bff -- \
  wget -q -O - http://product:80/api/products 2>&1 | head -3

# backoffice-bff → customer
kubectl exec -it deploy/backoffice-bff -n production -c backoffice-bff -- \
  wget -q -O - http://customer:80/api/customers 2>&1 | head -3

# order → cart
kubectl exec -it deploy/order -n production -c order -- \
  wget -q -O - http://cart:80/api/carts 2>&1 | head -3
```

### VirtualService Routing

```bash
# List tất cả VirtualService
kubectl get virtualservice -n production

```

### Ingress (BFF từ ngoài vào)

```bash
# Kiểm tra AuthorizationPolicy cho phép ingress
kubectl get authorizationpolicy allow-ingress-to-storefront-bff -n production -o yaml

```

### ServiceEntry (Egress ra ngoài)

```bash
# Kiểm tra ServiceEntry cho Keycloak
kubectl get serviceentry allow-identity-external -n production -o yaml
```

### Kiali — Service Mesh Topology
