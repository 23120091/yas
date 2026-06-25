# SETUP-SERVER.md — YAS Multi-Environment Deployment Guide

## Prerequisites

- **K3s** cluster (single-node or multi-node)
- **16GB+ RAM** (32GB recommended for full stack)
- **4+ vCPU** (8 vCPU recommended)
- Tools: `helm`, `kubectl`, `yq` (https://github.com/mikefarah/yq)

## Environment Configuration

Each environment has its own config in `k8s/deploy/cluster-config-{env}.yaml`:

| File | Environment |
|------|-------------|
| `cluster-config-dev.yaml` | Development |
| `cluster-config-staging.yaml` | Staging |
| `cluster-config-production.yaml` | Production |

### Key Settings Per Environment

| Setting | dev | staging | production |
|---------|-----|---------|------------|
| domain | `yas.local.com` | `yas.local.com` | `yas.local.com` |
| envSubdomain | `dev` | `staging` | (empty) |
| Keycloak hostname | `identity.dev.yas.local.com` | `identity.staging.yas.local.com` | `identity.yas.local.com` |
| PostgreSQL replicas | 1 | 1 | 1 |
| Kafka replicas | 1 | 1 | 1 |
| Elasticsearch replicas | 1 | 1 | 1 |

### Namespace Mapping

All infrastructure uses env-specific namespaces: `{service}-{env}`

| Component | dev | staging | production |
|-----------|-----|---------|------------|
| PostgreSQL | `postgres-dev` | `postgres-staging` | `postgres-production` |
| Kafka | `kafka-dev` | `kafka-staging` | `kafka-production` |
| Elasticsearch | `elasticsearch-dev` | `elasticsearch-staging` | `elasticsearch-production` |
| Keycloak | `keycloak-dev` | `keycloak-staging` | `keycloak-production` |
| Redis | `redis-dev` | `redis-staging` | `redis-production` |
| Applications | `dev` | `staging` | `production` |

## Quick Start (dev environment)

```bash
cd k8s/deploy

# Step 1: Infrastructure (PostgreSQL, Kafka, Elasticsearch, Observability)
./setup-cluster.sh dev

# Step 2: Keycloak (Identity & Access Management)
./setup-keycloak.sh dev

# Step 3: Redis (Session storage for BFFs)
./setup-redis.sh dev

# Step 4: Shared ConfigMaps & Secrets
./deploy-yas-configuration.sh dev

# Step 5: Deploy applications via ArgoCD
kubectl apply -f ../argocd/applicationsets/dev-applicationset.yaml
```

## Step Details

### Step 1: `setup-cluster.sh <env>`

Deploys cluster-wide operators and env-specific instances:

| What | Namespace | Notes |
|------|-----------|-------|
| Zalando Postgres Operator | `postgres` | Cluster-scoped operator |
| PostgreSQL cluster | `postgres-{env}` | 1 database per service |
| Strimzi Kafka Operator | `kafka` | Watches all namespaces |
| Kafka cluster (KRaft) | `kafka-{env}` | Single-node |
| AKHQ (Kafka UI) | `kafka-{env}` | Kafka admin dashboard |
| ECK Elasticsearch Operator | `elasticsearch` | Cluster-scoped |
| Elasticsearch cluster | `elasticsearch-{env}` | With Kibana |
| Cert Manager | `cert-manager` | TLS certificate management |
| OpenTelemetry Operator | `observability` | Tracing infrastructure |
| Observability stack | `observability-{env}` | Loki + Tempo + Promtail + OTel Collector |
| ZooKeeper | `zookeeper-{env}` | Legacy dependency (not used by Kafka) |

### Step 2: `setup-keycloak.sh <env>`

- Deploys Keycloak operator CRDs and instance
- Creates Keycloak realm with YAS client configuration
- Patches CoreDNS to resolve Keycloak hostname from within the cluster

### Step 3: `setup-redis.sh <env>`

- Deploys Redis (Bitnami chart) for BFF session management

### Step 4: `deploy-yas-configuration.sh <env>`

- Deploys shared Kubernetes resources into the application namespace:
  - `yas-configuration-configmap` (shared `application.yaml`)
  - `yas-postgresql-credentials-secret` (DB username/password)
  - `yas-elasticsearch-credentials-secret` (ES username/password, read from ECK)
  - `yas-keycloak-credentials-secret` (client secrets)
  - `yas-redis-credentials-secret` (Redis password)
  - Per-service ConfigMaps (product, payment, search, etc.)
  - `reloader` (auto-restarts pods on ConfigMap/Secret changes)
- Overrides env-specific values:
  - Kafka bootstrap servers (`kafka-{env}` namespace)
  - Keycloak issuer URI (`identity.{env}.yas.local.com`)
  - Redis host (`redis-{env}` namespace)
  - Elasticsearch URL (`elasticsearch-{env}` namespace)
  - Elasticsearch credentials (reads actual ECK password from cluster)
  - BFF-specific Keycloak client issuer URIs

### Step 5: Deploy Applications via ArgoCD

```bash
kubectl apply -f ../argocd/applicationsets/dev-applicationset.yaml
```

ArgoCD deploys services in **sync waves** to avoid CPU thrashing:

| Wave | Services |
|------|----------|
| 1 | product, customer, cart |
| 2 | inventory, location, media, tax, promotion, rating, search, recommendation, sampledata, order |
| 3 | payment, payment-paypal, webhook |
| 4 | storefront-bff, backoffice-bff |
| 5 | storefront-ui, backoffice-ui |

Each wave waits for all pods to be **Healthy** before starting the next wave.

#### Image Updates (dev only)
- ArgoCD Image Updater polls GHCR every 2 minutes for new `latest` tags
- No git commits needed — updates are ArgoCD-internal
- Staging/production use explicit `stag-*` / `prod-*` tags

## Verification

```bash
# Check all infrastructure is healthy
kubectl get pods -n postgres-dev
kubectl get pods -n kafka-dev
kubectl get pods -n elasticsearch-dev
kubectl get pods -n keycloak-dev
kubectl get pods -n redis-dev

# Check applications
kubectl get pods -n dev

# Access services (port-forward from cluster to local)
kubectl port-forward -n keycloak-dev svc/keycloak-service 8080:80 &
kubectl port-forward -n postgres-dev postgresql-0 5432:5432 &

# URLs (add to /etc/hosts on your local machine):
# 127.0.0.1 api.yas.local
# 127.0.0.1 storefront.yas.local
# 127.0.0.1 backoffice.yas.local
# 127.0.0.1 identity.yas.local
```

## Staging & Production Deployment

Same scripts, different env argument:

```bash
cd k8s/deploy
./setup-cluster.sh staging       # or production
./setup-keycloak.sh staging
./setup-redis.sh staging
./deploy-yas-configuration.sh staging

# ArgoCD
kubectl apply -f ../argocd/applicationsets/staging-applicationset.yaml  # auto-sync
kubectl apply -f ../argocd/applicationsets/production-applicationset.yaml  # manual sync
```

### Key Differences

| Setting | dev | staging | production |
|---------|-----|---------|------------|
| Replicas per service | 1 | 1 | 1 |
| HPA | disabled | disabled | disabled |
| Strategy | Recreate | RollingUpdate | RollingUpdate |
| CPU limits | none | set | set |
| ArgoCD sync | auto | auto | manual |
| Image tag format | `latest` | `stag-{sha}` | `prod-{sha}` |
| Resources (requests/limits) | minimal | moderate | generous |

## Troubleshooting

### Pods stuck in Pending
```bash
kubectl describe pod -n dev <pod-name>
```
Common causes: insufficient CPU/memory, cordoned nodes, node selector mismatch.

### DNS errors (`identity.dev.yas.local.com`)
Re-run `./setup-keycloak.sh dev` — it patches CoreDNS automatically.

### Liquibase lock errors
```bash
kubectl exec -n postgres-dev postgresql-0 -- psql -U yasadminuser -d <db> \
  -c "UPDATE databasechangeloglock SET locked=false, lockgranted=null, lockedby=null WHERE id=1;"
```

### Image updater not pulling latest
Check ArgoCD Image Updater logs:
```bash
kubectl logs -n argocd -l app.kubernetes.io/name=argocd-image-updater --tail=50
```

### Elasticsearch auth errors
Re-run `./deploy-yas-configuration.sh dev` — it reads the actual ECK password.

### Force restart all services
```bash
kubectl rollout restart deployment -n dev
```

### Complete teardown
```bash
kubectl delete applicationset yas-dev -n argocd
kubectl delete namespace dev postgres-dev kafka-dev elasticsearch-dev keycloak-dev redis-dev observability-dev zookeeper-dev
```
