
## Storefront — Route Summary for Istio

### Public Routes (no auth)
| Path | Feature | Backend calls |
|------|---------|---------------|
| `/` | Home (banners, categories, featured products) | product, search |
| `/products` | Product listing with filters | product |
| `/products/[slug]` | Product detail + ratings | product, rating |
| `/search` | Search with facets (ES-powered) | search (Elasticsearch) |
| `/about`, `/contact`, `/history` | Static pages | — |
| `/redirect?productId=` | Redirect to product slug | product |
| `/complete-payment/[capture]` | PayPal callback | payment |

### Protected Routes (auth required — backends check 403)
| Path | Feature | Backend calls |
|------|---------|---------------|
| `/cart` | Shopping cart | cart |
| `/checkout/[id]` | Checkout flow | order, cart, payment |
| `/my-orders` | Order history | order |
| `/profile` | Edit profile | customer |
| `/address`, `/address/create`, `/address/[id]/edit` | Address book | customer, location |

### Auth endpoints (handled by BFF)
- Login: `/oauth2/authorization/keycloak`
- Check auth: `/authentication` (GET)
- Logout: `/logout`

---

## Backoffice — Route Summary for Istio

All routes are **protected** (admin-only, BFF redirects to Keycloak login).

| Path | Feature | Backend calls |
|------|---------|---------------|
| `/` | Dashboard (latest products/orders/ratings) | product, order, rating |
| `/catalog/brands` | CRUD brands | product |
| `/catalog/categories` | CRUD categories (tree) | product |
| `/catalog/products` | CRUD products + export CSV | product, media |
| `/catalog/product-options` | CRUD product options | product |
| `/catalog/product-attributes` | CRUD attributes | product |
| `/catalog/product-attribute-groups` | CRUD attribute groups | product |
| `/catalog/product-templates` | CRUD templates | product |
| `/customers` | CRUD customers | customer |
| `/profile` | Edit admin profile | customer |
| `/reviews` | List/search/delete ratings | rating |
| `/sales/orders` | View/edit orders | order |
| `/sales/shipments` | Placeholder (not implemented) | — |
| `/promotion/manager-promotion` | CRUD promotions | promotion |
| `/inventory/warehouses` | CRUD warehouses | inventory |
| `/inventory/warehouse-products` | Manage product-warehouse mapping | inventory, product |
| `/inventory/warehouse-stocks` | Adjust stock + history | inventory |
| `/location/countries` | CRUD countries | location |
| `/location/state-or-provinces` | CRUD states/provinces | location |
| `/tax/tax-classes` | CRUD tax classes | tax |
| `/tax/tax-rates` | CRUD tax rates | tax |
| `/system/payment-providers` | Static list (read-only) | — |
| `/webhook` | CRUD webhooks | webhook |

### BFF auth endpoints (same pattern as storefront)
- `/oauth2/authorization/keycloak`, `/authentication/user`, `/logout`

---

## Backend Services Map (for Istio `DestinationRule`)

| Service | Used by storefront | Used by backoffice |
|---------|-------------------|-------------------|
| **product** | ✅ | ✅ |
| **cart** | ✅ | — |
| **order** | ✅ | ✅ |
| **customer** | ✅ | ✅ |
| **rating** | ✅ | ✅ |
| **search** (ES) | ✅ | — |
| **payment** | ✅ | ✅ (static) |
| **promotion** | — | ✅ |
| **inventory** | — | ✅ |
| **location** | ✅ (address forms) | ✅ |
| **tax** | — | ✅ |
| **webhook** | — | ✅ |
| **media** | ✅ (product images) | ✅ |
| **storefront-bff** (Gateway) | ✅ proxy | — |
| **backoffice-bff** (Gateway) | — | ✅ proxy |

### Istio policy notes
- **Storefront** public routes → allow all; protected routes → require valid JWT (BFF validates via TokenRelay)
- **Backoffice** → all routes require valid JWT + admin role
- Both frontends talk to their respective **BFF** (Spring Cloud Gateway), which relays tokens to backend services — no direct pod-to-pod calls from frontend
- If using `RequestAuthentication` + `AuthorizationPolicy`, target the BFF services (`storefront-bff`, `backoffice-bff`) as the entry point, since backend services only receive internal traffic from the BFF (already authenticated)

---

