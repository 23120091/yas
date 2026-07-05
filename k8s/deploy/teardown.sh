#!/bin/bash
# ============================================================================
# TEARDOWN INFRASTRUCTURE — Multi-Environment
# ============================================================================
# Deletes everything created by setup-cluster.sh, setup-keycloak.sh, and
# setup-redis.sh for a specific environment.
#
# ⚠️  WARNING: This DESTROYS ALL DATA in:
#   - PostgreSQL
#   - Kafka
#   - Elasticsearch
#   - Redis
#   - Keycloak
#   - Zookeeper
#   - Observability (Loki, Tempo)
#
# DOES NOT DELETE (managed by ArgoCD):
#   - Application pods/deployments in dev/staging/production
#   - yas-configuration ConfigMaps/Secrets
#   - ArgoCD ApplicationSets or Applications
#
# Usage:
#   ./teardown.sh <env>     (dev|staging|production|all, default: dev)
# ============================================================================

set -e
DIR="$(cd "$(dirname "$0")" && pwd)"

# Load common passwords
source "$DIR/.env"

ENV=${1:-dev}

if [ "$ENV" = "all" ]; then
  ENVS="dev staging production"
else
  ENVS="$ENV"
fi

echo "============================================"
echo " TEARDOWN for: ${ENV}"
echo "============================================"
echo ""
echo "This will DELETE all infrastructure data."
read -p "Are you sure? Type 'yes' to continue: " confirm
if [ "$confirm" != "yes" ]; then
  echo "Aborted."
  exit 0
fi

for e in $ENVS; do
  echo ""
  echo ">>> Processing environment: ${e}"
  echo ""

  # --------------------------------------------------------------------------
  # 1. Delete Helm releases first (cleaner than deleting namespaces directly)
  # --------------------------------------------------------------------------
  echo "Deleting Helm releases..."
  helm uninstall "postgres-${e}"          --namespace "postgres-${e}"          2>/dev/null || true
  helm uninstall "pgadmin-${e}"           --namespace "postgres-${e}"          2>/dev/null || true
  helm uninstall "kafka-cluster-${e}"     --namespace "kafka-${e}"             2>/dev/null || true
  helm uninstall "akhq-${e}"              --namespace "kafka-${e}"             2>/dev/null || true
  helm uninstall "elasticsearch-cluster-${e}" --namespace "elasticsearch-${e}" 2>/dev/null || true
  helm uninstall "zookeeper-${e}"         --namespace "zookeeper-${e}"         2>/dev/null || true
  helm uninstall "loki-${e}"              --namespace "observability-${e}"     2>/dev/null || true
  helm uninstall "tempo-${e}"             --namespace "observability-${e}"     2>/dev/null || true
  helm uninstall "promtail-${e}"          --namespace "observability-${e}"     2>/dev/null || true
  helm uninstall "opentelemetry-collector-${e}" --namespace "observability-${e}" 2>/dev/null || true
  helm uninstall "redis-${e}"             --namespace "redis-${e}"             2>/dev/null || true
  helm uninstall "keycloak-${e}"          --namespace "keycloak-${e}"          2>/dev/null || true
  helm uninstall "prometheus-${e}"        --namespace "observability-${e}" 2>/dev/null || true

  # --------------------------------------------------------------------------
  # 2. Force-delete PVCs to avoid "Terminating" hang
  # --------------------------------------------------------------------------
  echo "Force-deleting PVCs..."
  for ns in "postgres-${e}" "kafka-${e}" "elasticsearch-${e}" "zookeeper-${e}" "observability-${e}" "redis-${e}" "keycloak-${e}"; do
    kubectl patch pvc -n "${ns}" --all --type='json' -p='[{"op": "remove", "path": "/metadata/finalizers"}]' 2>/dev/null || true
    kubectl delete pvc -n "${ns}" --all --ignore-not-found --timeout=30s 2>/dev/null || true
  done

  # --------------------------------------------------------------------------
  # 3. Delete Zalando PostgreSQL CR (if Helm didn't clean it up)
  # --------------------------------------------------------------------------
  echo "Deleting PostgreSQL CR..."
  kubectl delete postgresql postgresql -n "postgres-${e}" --ignore-not-found --timeout=60s 2>/dev/null || true

  # --------------------------------------------------------------------------
  # 4. Delete Kafka CRs (if Helm didn't clean them up)
  # --------------------------------------------------------------------------
  echo "Deleting Kafka CRs..."
  kubectl delete kafka kafka-cluster -n "kafka-${e}" --ignore-not-found --timeout=60s 2>/dev/null || true

  # --------------------------------------------------------------------------
  # 5. Delete Elasticsearch CR (if Helm didn't clean it up)
  # --------------------------------------------------------------------------
  echo "Deleting Elasticsearch CR..."
  kubectl delete elasticsearch elasticsearch -n "elasticsearch-${e}" --ignore-not-found --timeout=60s 2>/dev/null || true

  # --------------------------------------------------------------------------
  # 6. Delete Keycloak CRs
  # --------------------------------------------------------------------------
  echo "Deleting Keycloak CRs..."
  kubectl delete keycloak keycloak -n "keycloak-${e}" --ignore-not-found --timeout=60s 2>/dev/null || true
  kubectl delete keycloakrealmimport yas-realm-kc -n "keycloak-${e}" --ignore-not-found --timeout=60s 2>/dev/null || true

  # --------------------------------------------------------------------------
  # 7. Delete namespaces (cascades anything left)
  # --------------------------------------------------------------------------
  echo "Deleting namespaces..."
  for ns in "postgres-${e}" "kafka-${e}" "elasticsearch-${e}" "zookeeper-${e}" "observability-${e}" "redis-${e}" "keycloak-${e}"; do
    kubectl delete namespace "${ns}" --ignore-not-found --timeout=120s 2>/dev/null || true
  done

  echo ""
  echo "Environment ${e} teardown complete."
done

echo "Delete Grafana"
helm uninstall grafana --namespace observability 2>/dev/null || true
kubectl delete application -n argocd grafana --ignore-not-found 2>/dev/null || true

echo ""
echo "============================================"
echo " TEARDOWN COMPLETE"
echo "============================================"
echo ""
echo "Infrastructure deleted. ArgoCD-managed apps"
echo "in dev/staging/production are still running."
echo ""
echo "To redeploy infrastructure:"
echo "  ./setup-cluster.sh <env>"
echo "  ./setup-redis.sh <env>"
echo "  ./setup-keycloak.sh <env>"
echo ""
echo "Then sync ArgoCD apps to pick up new URLs:"
echo "  argocd app sync yas-configuration-<env>"
