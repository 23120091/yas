# ============================================================================
# YAS PLATFORM — COMPLETE DEPLOYMENT GUIDE
# ============================================================================
# Đọc kỹ file này trước khi deploy. Mỗi bước đều có lý do và cách verify.
#
# TL;DR — Quick Start:
#   ./setup-all.sh dev --teardown    # Xóa hết, deploy từ đầu
#   ./setup-all.sh dev               # Giữ data, chỉ fix infra + resync ArgoCD
#
# Nếu chưa có ArgoCD hoặc muốn bootstrap lại từ đầu, đọc phần "ArgoCD Bootstrap".
# ============================================================================

## 1. ARCHITECTURE OVERVIEW

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  INFRASTRUCTURE (setup-cluster.sh + setup-redis.sh + setup-keycloak.sh)    │
│  ├── postgres-{env}      : PostgreSQL cluster (Zalando operator)           │
│  ├── kafka-{env}         : Kafka cluster (Strimzi operator)                │
│  ├── elasticsearch-{env} : Elasticsearch (ECK operator)                    │
│  ├── redis-{env}         : Redis (Bitnami Helm)                            │
│  ├── keycloak-{env}      : Keycloak + realm "Yas"                          │
│  ├── zookeeper-{env}     : Zookeeper                                       │
│  └── observability-{env} : Loki, Tempo, Promtail, OTel Collector           │
├─────────────────────────────────────────────────────────────────────────────┤
│  ARGOCD BOOTSTRAP                                                           │
│  ├── AppProject "yas"    : Security boundary, allowed repos/destinations   │
│  ├── yas-bootstrap       : App of Apps — watches applicationsets/          │
│  ├── yas-configuration   : ConfigMaps + Secrets (sync-wave: -1)            │
│  └── yas-{env}           : ApplicationSet — generates per-service Apps     │
├─────────────────────────────────────────────────────────────────────────────┤
│  APPLICATIONS (managed by ArgoCD)                                           │
│  ├── dev/staging/production : Per-environment namespace                    │
│  └── Services: product, cart, inventory, order, payment, media, ...        │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 2. DEPENDENCY CHAIN (Bắt buộc phải đợi)

```
PostgreSQL ready
    │
    ├──► Keycloak pod running
    │       │
    │       └──► Realm "Yas" imported  ← BFF cần cái này, không là CrashLoopBackOff
    │
    └──► Java apps (Liquibase tạo bảng on startup)
            │
            └──► sampledata seeding (INSERT dữ liệu mẫu)

Kafka CRDs registered
    │
    └──► Kafka cluster created

Elasticsearch ready
    │
    └──► search service khởi động (tạo ES index)

Redis (không phụ thuộc gì, chạy anytime)
```

| Bước | Phụ thuộc vào | Nếu không đợi |
|------|---------------|---------------|
| Keycloak | PostgreSQL ready | Pod CrashLoopBackOff (không kết nối được DB) |
| Realm import | Keycloak pod running | Job fail, realm không tồn tại |
| BFF (backoffice/storefront) | Realm "Yas" tồn tại | CrashLoopBackOff (OIDC discovery 404) |
| Java apps | PostgreSQL + DB tồn tại | CrashLoopBackOff ( Liquibase fail hoặc relation không tồn tại) |
| search | Elasticsearch ready | CrashLoopBackOff (kết nối ES 401/404) |

## 3. ARGOCD BOOTSTRAP (Chỉ cần làm 1 lần hoặc khi xóa hết)

### 3.1 Khi nào cần bootstrap lại?

- Cluster mới, chưa có ArgoCD
- Chạy `kubectl delete appproject -n argocd yas` hoặc xóa nhầm bootstrap app
- `setup-all.sh dev --teardown` (script đã xóa AppProject + bootstrap)

### 3.2 Bootstrap commands

```bash
cd ~/yas/k8s/deploy

# Step 1: Tạo AppProject (security boundary)
kubectl apply -f ../argocd/projects/yas-project.yaml

# Step 2: Tạo Bootstrap Application (App of Apps)
kubectl apply -f ../argocd/bootstrap-app.yaml

# Step 3: Đợi ArgoCD tự tạo ApplicationSets từ git
sleep 15
kubectl get applicationset -n argocd
# Output phải có: yas-dev, yas-staging, yas-production, yas-configuration

# Step 4: ApplicationSets tự tạo Applications
kubectl get application -n argocd
# Output phải có: yas-configuration-dev, product-dev, cart-dev, ...
```

### 3.3 Verify bootstrap thành công

```bash
# AppProject tồn tại
kubectl get appproject -n argocd yas

# Bootstrap app Synced
kubectl get application -n argocd yas-bootstrap

# ApplicationSets generated
kubectl get applicationset -n argocd

# Applications created
kubectl get application -n argocd | wc -l   # ~25+ apps
```

## 4. SETUP-ALL.SH USAGE

### 4.1 Full redeploy (xóa hết, tạo lại từ đầu)

```bash
./setup-all.sh dev --teardown
```

**What this does:**
1. Xóa ALL ArgoCD ApplicationSets + Applications (tất cả env)
2. Xóa AppProject + bootstrap app
3. Xóa namespace dev/staging/production
4. Chạy teardown.sh (xóa infrastructure: PostgreSQL, Kafka, ES, ...)
5. Deploy lại infrastructure mới hoàn toàn
6. **Bootstrap lại ArgoCD** (apply Project + Bootstrap App)
7. Đợi infrastructure sẵn sàng
8. ArgoCD auto-sync apps

**ThờI gian:** ~10-15 phút

### 4.2 Infrastructure only (giữ apps, chỉ fix infra)

```bash
./setup-all.sh dev
```

**What this does:**
1. KHÔNG xóa ArgoCD
2. Chạy setup-cluster.sh, setup-redis.sh, setup-keycloak.sh
3. Đợi infrastructure sẵn sàng
4. Resync ArgoCD apps

**Dùng khi:**
- PostgreSQL bị lỗi, cần recreate
- Keycloak realm mất
- ES password đổi
- Không cần xóa app pods

### 4.3 Manual steps (nếu không dùng setup-all.sh)

```bash
# Phase 1: Infrastructure
./setup-cluster.sh dev      # PostgreSQL, Kafka, ES, Zookeeper, Observability
./setup-redis.sh dev        # Redis
./setup-keycloak.sh dev     # Keycloak (đã có đợi PostgreSQL trong script)

# Phase 2: Đợi key dependencies
kubectl wait --for=condition=ready pod -l application=spilo -n postgres-dev --timeout=300s
kubectl wait --for=condition=ready pod -l app=keycloak -n keycloak-dev --timeout=300s
sleep 60  # Realm import

# Phase 3: Bootstrap ArgoCD (nếu bị xóa)
kubectl apply -f ../argocd/projects/yas-project.yaml
kubectl apply -f ../argocd/bootstrap-app.yaml

# Phase 4: Force sync (nếu auto-sync tắt)
argocd app sync yas-configuration-dev
argocd app sync -l env=dev
```

## 5. VERIFY DEPLOYMENT

### 5.1 Check infrastructure

```bash
# PostgreSQL
kubectl get pods -n postgres-dev -l application=spilo
# Expected: 1/1 Running

# Kafka
kubectl get pods -n kafka-dev -l strimzi.io/kind=Kafka
# Expected: kafka-cluster-entity-operator, kafka-cluster-kafka-0

# Keycloak
kubectl get pods -n keycloak-dev -l app=keycloak
# Expected: keycloak-0 1/1 Running

# Redis
kubectl get pods -n redis-dev -l app.kubernetes.io/name=redis
# Expected: redis-master-0 1/1 Running

# Elasticsearch
kubectl get pods -n elasticsearch-dev -l elasticsearch.k8s.elastic.co/cluster-name=elasticsearch
# Expected: elasticsearch-es-default-0 1/1 Running
```

### 5.2 Check Keycloak realm

```bash
# Realm phải trả về JSON
kubectl run -n keycloak-dev debug --rm -i --restart=Never --image=curlimages/curl \
  -- curl -s http://keycloak-service:80/realms/Yas/ | head -5

# Expected output:
# {"realm":"Yas","public_key":"..."}
```

### 5.3 Check database

```bash
# Tables đã được tạo chưa
kubectl exec -n postgres-dev postgresql-0 -- psql -U yasadminuser -d product -c "\dt"

# Expected: brand, category, product, product_attribute, ...

# Có dữ liệu chưa
kubectl exec -n postgres-dev postgresql-0 -- psql -U yasadminuser -d product -c "SELECT COUNT(*) FROM product;"
# Expected: > 0 (sau khi sampledata seed)
```

### 5.4 Check ArgoCD apps

```bash
# Tất cả app phải Synced + Healthy
kubectl get application -n argocd -l env=dev

# Nếu có app OutOfSync hoặc Unknown:
argocd app sync <app-name>
# hoặc
kubectl get application -n argocd <app-name> -o yaml | grep -A 10 "status:"
```

### 5.5 Check pods

```bash
kubectl get pods -n dev

# Expected: ~20 pods, tất cả 1/1 Running
# Nếu có pod 0/1 hoặc CrashLoopBackOff, check logs:
kubectl logs -n dev <pod-name> --tail=50
```

## 6. TROUBLESHOOTING

### 6.1 BFF CrashLoopBackOff (Keycloak realm chưa sẵn sàng)

**Symptom:** `backoffice-bff` hoặc `storefront-bff` restart liên tục

**Logs:**
```
Unable to resolve Configuration with the provided Issuer of "http://identity-dev.tthong.dev/realms/Yas"
```

**Fix:**
```bash
# 1. Check realm
kubectl run -n keycloak-dev debug --rm -i --restart=Never --image=curlimages/curl \
  -- curl -s http://keycloak-service:80/realms/Yas/.well-known/openid-configuration

# 2. Nếu realm chưa tồn tại, xóa realm import job để recreate
kubectl delete keycloakrealmimport -n keycloak-dev yas-realm-kc
# Keycloak operator sẽ tạo lại job

# 3. Restart BFF
kubectl rollout restart deployment -n dev backoffice-bff
kubectl rollout restart deployment -n dev storefront-bff
```

### 6.2 Java app "relation does not exist" (Liquibase chưa chạy)

**Symptom:**
```
ERROR: relation "product" does not exist
```

**Fix:**
```bash
# 1. Pod chưa restart sau khi DB recreate
kubectl rollout restart deployment -n dev --all

# 2. Đợi 2-3 phút cho Liquibase
sleep 120

# 3. Verify
kubectl exec -n postgres-dev postgresql-0 -- psql -U yasadminuser -d product -c "\dt"
```

### 6.3 Elasticsearch 401 (password sai)

**Symptom:** `search` pod CrashLoopBackOff

**Logs:**
```
401, [es/indices.exists] Expecting a response body, but none was sent
```

**Fix:**
```bash
# 1. Lấy ECK password mới
ES_PASS=$(kubectl get secret elasticsearch-es-elastic-user -n elasticsearch-dev -o jsonpath='{.data.elastic}' | base64 -d)

# 2. Update secret trong dev namespace
kubectl patch secret -n dev yas-elasticsearch-credentials-secret \
  --type='json' -p='[{"op": "replace", "path": "/data/password", "value":"'$(echo -n "$ES_PASS" | base64 -w 0)'"}]'

# 3. Restart search
kubectl rollout restart deployment -n dev search
```

### 6.4 ArgoCD apps "Unknown" status

**Symptom:** `kubectl get application -n argocd` shows `Unknown` instead of `Synced`

**Fix:**
```bash
# 1. Check ApplicationSet có generate đúng không
kubectl get applicationset -n argocd yas-dev -o yaml | grep -A 5 "status:"

# 2. Nếu ApplicationSet bị lỗi, xóa và để ArgoCD recreate
kubectl delete applicationset -n argocd yas-dev
# ArgoCD bootstrap app sẽ tạo lại từ git

# 3. Hoặc force sync bootstrap
argocd app sync yas-bootstrap
```

### 6.5 Keycloak redirect_uri error

**Symptom:** Login thất bại với `Invalid parameter: redirect_uri`

**Fix:**
```bash
# 1. Check realm redirect URIs
kubectl get keycloakrealmimport -n keycloak-dev yas-realm-kc -o yaml | grep -A 5 "redirectUris"

# 2. Nếu URI sai, xóa realm import và recreate
kubectl delete keycloakrealmimport -n keycloak-dev yas-realm-kc
sleep 30

# 3. Verify
kubectl run -n keycloak-dev debug --rm -i --restart=Never --image=curlimages/curl \
  -- curl -s http://keycloak-service:80/realms/Yas/.well-known/openid-configuration | grep authorization_endpoint
```

## 7. FILE STRUCTURE

```
k8s/
├── argocd/
│   ├── bootstrap-app.yaml                    # App of Apps — ROOT
│   ├── projects/
│   │   └── yas-project.yaml                  # AppProject (security boundary)
│   └── applicationsets/
│       ├── yas-configuration-applicationset.yaml   # ConfigMaps + Secrets
│       ├── dev-applicationset.yaml                 # Dev microservices
│       ├── staging-applicationset.yaml             # Staging microservices
│       └── production-applicationset.yaml          # Production microservices
│
├── deploy/
│   ├── setup-all.sh                          # ONE COMMAND to rule them all
│   ├── teardown.sh                           # Destroy infrastructure
│   ├── setup-cluster.sh                      # PostgreSQL, Kafka, ES, ...
│   ├── setup-redis.sh                        # Redis
│   ├── setup-keycloak.sh                     # Keycloak + realm import
│   ├── cluster-config-{dev,staging,prod}.yaml
│   └── postgres/
│       ├── postgresql/                       # Zalando PostgreSQL Helm chart
│       └── pgadmin/                          # pgAdmin Helm chart
│
└── charts/
    └── backend/
        ├── templates/
        │   ├── deployment.yaml
        │   ├── pvc.yaml                        # Persistence (media only)
        │   └── ...
        └── values.yaml
```

## 8. QUICK REFERENCE

| Task | Command |
|------|---------|
| Full redeploy | `./setup-all.sh dev --teardown` |
| Fix infra only | `./setup-all.sh dev` |
| Teardown infra | `./teardown.sh dev` |
| Sync ArgoCD app | `argocd app sync <app-name>` |
| Sync all dev apps | `argocd app sync -l env=dev` |
| Restart all pods | `kubectl rollout restart deployment -n dev --all` |
| Check logs | `kubectl logs -n dev deployment/<svc> --tail=50` |
| Check DB tables | `kubectl exec -n postgres-dev postgresql-0 -- psql -U yasadminuser -d product -c "\dt"` |
| Port-forward Keycloak | `kubectl port-forward -n keycloak-dev keycloak-0 8080:80` |
| ArgoCD UI | `kubectl port-forward -n argocd svc/argocd-server 8080:443` |

## 9. IMPORTANT NOTES

1. **ArgoCD auto-sync:** Tất cả ApplicationSets có `automated: {prune: true, selfHeal: true}`. Nếu xóa pod thủ công, ArgoCD sẽ tạo lại.

2. **Sync waves:**
   - Wave -1: `yas-configuration` (ConfigMaps + Secrets)
   - Wave 1: product, customer, cart
   - Wave 2: inventory, location, media, tax, ...
   - Wave 3: payment, payment-paypal, webhook
   - Wave 4: storefront-bff, backoffice-bff
   - Wave 5: storefront-ui, backoffice-ui

3. **Image updater:** ArgoCD Image Updater poll GHCR mỗi 2 phút cho dev. Staging/Production cần manual sync hoặc tag cụ thể.

4. **Data persistence:** Chỉ `media` có PVC. Các service khác stateless. PostgreSQL data lưu trên local-path storage của node.

5. **Branch protection:** `main` branch yêu cầu PR + CI gate. Đừng push trực tiếp lên main.
