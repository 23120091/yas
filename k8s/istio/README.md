# YAS Istio Service Mesh

Service mesh chỉ áp dụng cho namespace `production`. `dev` và `staging` chạy như Kubernetes service thông thường để tránh lỗi UI kiểu `RBAC: access denied` khi đang demo hoặc phát triển nhanh.

## Service Giữ Lại

| Service | Lý do giữ |
| --- | --- |
| `product` | Sản phẩm, trung tâm của shop |
| `cart` | Giỏ hàng, demo flow mua hàng |
| `order` | Đơn hàng, demo flow đặt hàng và retry policy (`order -> cart/payment/inventory/tax`) |
| `customer` | Thông tin khách hàng |
| `inventory` | Kho hàng, order phụ thuộc |
| `payment` | Thanh toán, order phụ thuộc |
| `tax` | Thuế, order phụ thuộc và demo VirtualService retry |
| `media` | Upload hình ảnh sản phẩm |
| `search` | Tìm kiếm, phụ thuộc `product`, demo AuthorizationPolicy |
| `storefront-bff` | BFF cho giao diện người dùng |
| `backoffice-bff` | BFF cho quản trị |
| `swagger-ui` | API documentation |

ApplicationSet vẫn giữ đầy đủ service theo GitOps. Danh sách này chỉ mô tả các service được mở trong service mesh production allow-list.

## Manifest

- `peer-authentication.yaml`: bật mTLS STRICT trong `production`, riêng BFF và `swagger-ui` PERMISSIVE để nhận traffic từ Traefik.
- `authorization-policy.yaml`: allow-list cho BFF, UI, Swagger và các dependency nội bộ được giữ. Không dùng global `DENY` cho port `80`; trong Istio, `DENY` được xét trước `ALLOW`, nên một policy DENY toàn namespace sẽ chặn cả caller hợp lệ. Với workload đã có `ALLOW` policy, traffic không match sẽ tự bị deny.
- `virtual-service-order.yaml`: retry/timeout cho `order -> cart`, `order -> payment`, `order -> inventory`, `order -> tax`. Test retry của `tax` cũng nằm ở đây để tránh nhiều VirtualService cùng host `tax`.
- `debug-tools.yaml`: debug pod có sidecar trong `production` để curl test policy.

## Deploy

Repo dùng GitOps qua ArgoCD. Quy trình chuẩn:

```bash
git push
argocd app sync kiali-server
```

Nếu cần kiểm tra nhanh trước khi sync:

```bash
kubectl apply --dry-run=server -f k8s/istio/
```

Không `kubectl apply -f k8s/istio/` trực tiếp vào cluster production. ArgoCD quản lý app `kiali-server` với source `k8s/istio` trên branch `main`; apply tay dễ làm live state lệch Git hoặc bị ArgoCD self-heal ghi đè lại.

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
# Từ storefront-bff (được allow) -> product
# Gọi trực tiếp Kubernetes Service thì dùng context path của service (/product),
# không dùng prefix gateway /api.
kubectl exec -n production deploy/storefront-bff -c storefront-bff -- \
  wget -S -q -O - http://product:80/product/storefront/categories 2>&1

# Từ order (được allow) -> tax
kubectl exec -n production deploy/order -c order -- \
  wget -S -q -O - http://tax:80/tax/backoffice/tax-classes 2>&1
```

Kỳ vọng: không có `rbac_access_denied` hoặc `RBAC: access denied`.
HTTP `200`, `401`, `403`, hoặc `404` đều có thể là application-level tùy endpoint và auth; phân biệt bằng log `istio-proxy`:

```bash
kubectl logs -n production deploy/product -c istio-proxy --since=2m | grep rbac_access_denied || true
kubectl logs -n production deploy/tax -c istio-proxy --since=2m | grep rbac_access_denied || true
```

### AuthorizationPolicy — Denied

```bash
# Từ debug pod (không trong allow-list) → product
kubectl exec -n production deploy/istio-debug -c debug -- \
  wget -S -q -O - http://product:80/product/storefront/categories 2>&1
```

Kỳ vọng: Istio chặn request. Response thường là `403 Forbidden`, và log sidecar của `product` phải có `rbac_access_denied`.

```bash
kubectl logs -n production deploy/product -c istio-proxy --since=2m | grep rbac_access_denied
```

Nếu debug pod nhận `404 Not Found`, đó là dấu hiệu request đã đi tới application và AuthorizationPolicy chưa chặn đúng.

### Service-to-Service Connectivity (Mesh Internal)

```bash
# storefront-bff -> product
kubectl exec -n production deploy/storefront-bff -c storefront-bff -- \
  wget -S -q -O - http://product:80/product/storefront/categories 2>&1

# backoffice-bff -> customer
kubectl exec -n production deploy/backoffice-bff -c backoffice-bff -- \
  wget -S -q -O - http://customer:80/customer/backoffice/customers 2>&1

# order -> cart
kubectl exec -n production deploy/order -c order -- \
  wget -S -q -O - http://cart:80/cart/storefront/cart/items 2>&1

# order -> payment
kubectl exec -n production deploy/order -c order -- \
  wget -S -q -O - http://payment:80/payment/payment-providers 2>&1

# order -> inventory
kubectl exec -n production deploy/order -c order -- \
  wget -S -q -O - http://inventory:80/inventory/backoffice/warehouses 2>&1

# order -> tax
kubectl exec -n production deploy/order -c order -- \
  wget -S -q -O - http://tax:80/tax/backoffice/tax-classes 2>&1
```

Kỳ vọng: không có `rbac_access_denied`. Một số endpoint có thể trả `401`/`403` application-level vì thiếu token; đó không phải lỗi mesh nếu log sidecar không match RBAC deny.

### VirtualService Retry

```bash
# Xem đủ 4 retry policy cho order dependencies
kubectl get virtualservice \
  order-to-cart-retry \
  order-to-payment-retry \
  order-to-inventory-retry \
  order-to-tax-retry \
  -n production

# Xem chi tiết retry config
kubectl get virtualservice order-to-tax-retry -n production -o yaml
kubectl get virtualservice order-to-payment-retry -n production -o yaml
```

Test mode cho `order -> tax` dùng header `x-test-retry: true`; route này cố tình trỏ đến port `8888` không tồn tại để sinh `connect-failure` và kích hoạt retry:

```bash
kubectl exec -n production deploy/order -c order -- \
  wget -S -q -O - --header='x-test-retry: true' \
  http://tax:80/tax/backoffice/tax-classes 2>&1

kubectl logs -n production deploy/order -c istio-proxy --since=2m | \
  grep 'outbound|8888||tax.production.svc.cluster.local'
```

Kỳ vọng: request test có thể fail vì port `8888` không tồn tại, nhưng log sidecar của `order` phải cho thấy traffic đi qua outbound cluster `8888` tới `tax`. Nếu chỉ thấy `outbound|80||tax...`, header chưa match hoặc VirtualService chưa sync.

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
