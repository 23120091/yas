#!/bin/bash
# ============================================================================
# SETUP ALL — Complete Infrastructure + ArgoCD Bootstrap
# ============================================================================
# Deploys EVERYTHING for a specific environment from scratch.
#
# WHAT THIS DOES:
#   1. (Optional) Full teardown — infrastructure + ArgoCD apps
#   2. Deploy infrastructure: PostgreSQL, Kafka, ES, Redis, Keycloak
#   3. Wait for each dependency to be ready
#   4. Re-deploy ArgoCD: Project → Bootstrap App → ApplicationSets
#   5. ArgoCD then auto-deploys all microservices
#
# PREREQUISITES:
#   - ArgoCD must already be installed in the cluster (argocd namespace)
#   - Helm 3, yq, kubectl configured
#   - cluster-config-{env}.yaml exists
#
# USAGE:
#   ./setup-all.sh <env>     (dev|staging|production, default: dev)
#
#   # With full teardown first (DESTROYS ALL DATA):
#   ./setup-all.sh dev --teardown
#
#   # Without teardown (keep existing data, just fix infra + resync ArgoCD):
#   ./setup-all.sh dev
# ============================================================================

set -e
DIR="$(cd "$(dirname "$0")" && pwd)"

# Load common passwords
source "$DIR/.env"

ENV=${1:-dev}
TEARDOWN=${2:-""}

if [ "$ENV" = "--teardown" ] || [ "$ENV" = "--clean" ]; then
    echo "ERROR: First argument must be environment. Usage: ./setup-all.sh dev --teardown"
    exit 1
fi

echo ""
echo "============================================"
echo " YAS PLATFORM SETUP"
echo " Environment: ${ENV}"
echo " Teardown:    ${TEARDOWN}"
echo "============================================"
echo ""

# --------------------------------------------------------------------------
# PHASE 0: Full teardown (if requested)
# --------------------------------------------------------------------------
if [ "$TEARDOWN" = "--teardown" ] || [ "$TEARDOWN" = "--clean" ]; then
    echo ""
    echo ">>> PHASE 0: FULL TEARDOWN (infrastructure + ALL ArgoCD resources)"
    echo ""
    read -p "This DESTROYS ALL DATA and ALL ArgoCD apps across ALL environments. Type 'yes' to continue: " confirm
    if [ "$confirm" != "yes" ]; then
        echo "Aborted."
        exit 0
    fi

    # 0.1 Delete ALL ArgoCD ApplicationSets (cascade = delete all managed pods across ALL envs)
    echo "[0.1] Deleting ALL ArgoCD ApplicationSets..."
    kubectl delete applicationset -n argocd --all --cascade=foreground --ignore-not-found --timeout=120s 2>/dev/null || true

    # 0.2 Delete ALL ArgoCD Applications
    echo "[0.2] Deleting ALL ArgoCD Applications..."
    kubectl delete application -n argocd --all --cascade=foreground --ignore-not-found --timeout=120s 2>/dev/null || true

    # 0.3 Delete ArgoCD AppProject
    echo "[0.3] Deleting ArgoCD AppProject 'yas'..."
    kubectl delete appproject -n argocd yas --ignore-not-found --timeout=60s 2>/dev/null || true

    # 0.4 Delete bootstrap app
    echo "[0.4] Deleting ArgoCD bootstrap app..."
    kubectl delete application -n argocd yas-bootstrap --cascade=foreground --ignore-not-found --timeout=120s 2>/dev/null || true

    # 0.5 Wait for app namespaces to empty
    echo "[0.5] Waiting for app namespaces to empty..."
    for ns in dev staging production; do
        kubectl delete namespace "${ns}" --ignore-not-found --timeout=120s 2>/dev/null || true
    done
    sleep 10

    # 0.6 Run infrastructure teardown
    echo "[0.6] Running infrastructure teardown..."
    ./teardown.sh "${ENV}"

    echo ""
    echo ">>> PHASE 0 complete. All resources destroyed."
    echo ""
fi

# --------------------------------------------------------------------------
# PHASE 1: Infrastructure
# --------------------------------------------------------------------------
echo ""
echo ">>> PHASE 1: DEPLOYING INFRASTRUCTURE"
echo ""

# 1.1 Cluster infrastructure (PostgreSQL, Kafka, ES, Zookeeper, Observability)
echo "[1.1] Deploying cluster infrastructure..."
./setup-cluster.sh "${ENV}"

# 1.2 Redis (independent, can run in parallel)
echo "[1.2] Deploying Redis..."
./setup-redis.sh "${ENV}"

# 1.3 Wait for PostgreSQL to be fully ready
echo "[1.3] Waiting for PostgreSQL to be ready..."
kubectl wait --for=condition=ready pod -l application=spilo -n "postgres-${ENV}" --timeout=300s
echo "      PostgreSQL is ready."

# 1.4 Wait for Kafka CRDs to be registered
echo "[1.4] Waiting for Kafka CRDs..."
kubectl wait --for=condition=established crd/kafkas.kafka.strimzi.io --timeout=120s 2>/dev/null || sleep 30
echo "      Kafka CRDs ready."

# 1.5 Wait for Elasticsearch to be ready
echo "[1.5] Waiting for Elasticsearch..."
kubectl wait --for=condition=ready elasticsearch elasticsearch -n "elasticsearch-${ENV}" --timeout=300s 2>/dev/null || sleep 60
echo "      Elasticsearch is ready."

# 1.6 Keycloak (depends on PostgreSQL)
echo "[1.6] Deploying Keycloak..."
./setup-keycloak.sh "${ENV}"

# 1.7 Wait for Keycloak pod
echo "[1.7] Waiting for Keycloak pod..."
kubectl wait --for=condition=ready pod -l app=keycloak -n "keycloak-${ENV}" --timeout=300s
echo "      Keycloak pod is ready."

# 1.8 Wait for realm import job
echo "[1.8] Waiting for realm import job..."
sleep 30
kubectl wait --for=condition=complete job/yas-realm-kc -n "keycloak-${ENV}" --timeout=120s 2>/dev/null || {
    echo "      WARNING: Realm import job may still be running. Continuing anyway..."
}

# 1.9 Verify realm is accessible
echo "[1.9] Verifying Keycloak realm..."
REALM_STATUS=$(kubectl run -n "keycloak-${ENV}" debug --rm -i --restart=Never --image=curlimages/curl -- curl -s -o /dev/null -w "%{http_code}" http://keycloak-service:80/realms/Yas/ 2>/dev/null || echo "000")
if [ "$REALM_STATUS" = "200" ]; then
    echo "      Realm 'Yas' is accessible."
else
    echo "      WARNING: Realm returned HTTP ${REALM_STATUS}. May need manual check."
fi

echo ""
echo ">>> PHASE 1 complete. Infrastructure is ready."
echo ""

# --------------------------------------------------------------------------
# PHASE 2: ArgoCD Bootstrap
# --------------------------------------------------------------------------
echo ""
echo ">>> PHASE 2: BOOTSTRAPPING ARGOCD"
echo ""

# 2.0 Label application namespaces for Istio sidecar injection
echo "[2.0] Labeling application namespaces for Istio injection..."
for ns in dev staging production; do
    kubectl label namespace "${ns}" istio-injection=enabled --overwrite 2>/dev/null || true
    echo "      Namespace '${ns}' labeled for Istio injection."
done

# 2.1 Apply ArgoCD Project
echo "[2.1] Applying ArgoCD AppProject 'yas'..."
kubectl apply -f ../argocd/projects/yas-project.yaml

# 2.2 Apply Bootstrap Application (App of Apps)
echo "[2.2] Applying ArgoCD Bootstrap Application..."
kubectl apply -f ../argocd/bootstrap-app.yaml

# 2.3 Wait for ApplicationSets to be created
echo "[2.3] Waiting for ApplicationSets to be generated..."
sleep 10
kubectl wait --for=condition=ParametersGenerated applicationset -n argocd "yas-${ENV}" --timeout=60s 2>/dev/null || true
kubectl wait --for=condition=ParametersGenerated applicationset -n argocd yas-configuration --timeout=60s 2>/dev/null || true

# 2.4 Force sync yas-configuration first (wave -1, must deploy before apps)
echo "[2.4] Triggering sync for yas-configuration (wave -1)..."
kubectl patch application -n argocd "yas-configuration-${ENV}" \
  -p '{"metadata":{"annotations":{"argocd.argoproj.io/refresh":"hard"}}}' \
  --type merge 2>/dev/null || {
    echo "      Could not trigger sync. ArgoCD auto-sync should handle it."
}

# 2.5 Wait for yas-configuration to be synced
echo "[2.5] Waiting for yas-configuration to be ready..."
kubectl wait --for=condition=Synced application -n argocd "yas-configuration-${ENV}" --timeout=120s 2>/dev/null || sleep 30

# 2.6 Trigger sync for all applications for this environment
echo "[2.6] Triggering sync for all applications in ${ENV}..."
for app in $(kubectl get application -n argocd -l env="${ENV}" -o name 2>/dev/null); do
  kubectl patch "$app" -n argocd \
    -p '{"metadata":{"annotations":{"argocd.argoproj.io/refresh":"hard"}}}' \
    --type merge 2>/dev/null || true
done

echo ""
echo ">>> PHASE 2 complete. ArgoCD is managing all applications."
echo ""

# --------------------------------------------------------------------------
# PHASE 3: Verify
# --------------------------------------------------------------------------
echo ""
echo ">>> PHASE 3: VERIFYING DEPLOYMENT"
echo ""

sleep 10

echo "[3.1] Pods in ${ENV} namespace:"
kubectl get pods -n "${ENV}"

echo ""
echo "[3.2] Infrastructure pods:"
echo "      PostgreSQL:"
kubectl get pods -n "postgres-${ENV}" -l application=spilo --no-headers 2>/dev/null || echo "        Not found"
echo "      Kafka:"
kubectl get pods -n "kafka-${ENV}" -l strimzi.io/kind=Kafka --no-headers 2>/dev/null || echo "        Not found"
echo "      Keycloak:"
kubectl get pods -n "keycloak-${ENV}" -l app=keycloak --no-headers 2>/dev/null || echo "        Not found"
echo "      Redis:"
kubectl get pods -n "redis-${ENV}" -l app.kubernetes.io/name=redis --no-headers 2>/dev/null || echo "        Not found"

echo ""
echo "[3.3] ArgoCD Applications:"
kubectl get application -n argocd -l env="${ENV}" --no-headers 2>/dev/null | wc -l | xargs echo "      Total apps:"

echo ""
echo "============================================"
echo " SETUP COMPLETE for: ${ENV}"
echo "============================================"
echo ""
echo "Next steps:"
echo "  1. Wait ~3-5 minutes for all Java pods to start and run Liquibase."
echo "  2. Verify database tables:"
echo "     kubectl exec -n postgres-${ENV} postgresql-0 -- psql -U yasadminuser -d product -c '\\dt'"
echo "  3. Trigger sampledata seeding if needed:"
echo "     kubectl rollout restart deployment -n ${ENV} sampledata"
echo "  4. Access Keycloak admin:"
echo "     http://identity-${ENV}.tthong.dev/admin (admin/admin)"
echo "  5. Monitor ArgoCD:"
echo "     kubectl port-forward -n argocd svc/argocd-server 8080:443"
echo ""
echo "If any pods are CrashLoopBackOff, check logs:"
echo "  kubectl logs -n ${ENV} deployment/<service> --tail=50"
echo ""

echo ""
echo ">>> PHASE 4: DEPLOYING MONITORING"
echo ""

# 4.1 Deploy Prometheus per env (ArgoCD auto-syncs from ApplicationSet)
echo "[4.1] Monitoring ApplicationSet will auto-deploy Prometheus for ${ENV}..."
# Không cần lệnh gì — ArgoCD bootstrap đã tạo ApplicationSet

# 4.2 Deploy Grafana (shared) — chỉ cần 1 lần cho cả cluster
echo "[4.2] Applying Grafana shared Application..."
if ! kubectl get application -n argocd grafana >/dev/null 2>&1; then
    kubectl apply -f ../argocd/applications/grafana.yaml
    echo "      Grafana Application created."
else
    echo "      Grafana already exists."
fi

kubectl create secret generic grafana-admin-secret -n observability   --from-literal=admin-user=admin   --from-literal=admin-password=admin

# 4.3 Wait for Prometheus to be ready
echo "[4.3] Waiting for Prometheus ${ENV}..."
kubectl wait --for=condition=ready pod -l app=prometheus -n "observability-${ENV}" --timeout=180s 2>/dev/null || {
    echo "      WARNING: Prometheus pod not ready yet. Will retry later."
}

# 4.4 Verify Grafana
echo "[4.4] Grafana status:"
kubectl get pods -n observability -l app.kubernetes.io/name=grafana --no-headers 2>/dev/null || echo "      Grafana pod not found yet."

echo ""
echo ">>> PHASE 4 complete."
echo ""