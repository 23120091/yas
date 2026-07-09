#!/bin/bash
# ============================================================================
# TEARDOWN INFRASTRUCTURE — Multi-Environment
# ============================================================================
# Deletes all infrastructure created by setup-all.sh for a specific environment.
#
# DELETES:
#   - PostgreSQL cluster + pgAdmin
#   - Kafka cluster + AKHQ
#   - Elasticsearch cluster + Kibana
#   - Zookeeper
#   - Redis
#   - Loki, Tempo, Promtail, OTel Collector
#   - All PVCs and namespaces
#
# DOES NOT DELETE:
#   - Application pods in dev/staging/production (managed by ArgoCD)
#   - Operators (cluster-scoped, shared across envs)
#
# USAGE:
#   ./teardown.sh <env>     (dev|staging|production|all, default: dev)
# ============================================================================

set -e
DIR="$(cd "$(dirname "$0")" && pwd)"

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
  echo ">>> Tearing down environment: ${e}"

  PG_NS="postgres-${e}"
  KAFKA_NS="kafka-${e}"
  ES_NS="elasticsearch-${e}"
  ZK_NS="zookeeper-${e}"
  OBS_NS="observability-${e}"
  REDIS_NS="redis-${e}"

  # 1. Delete Helm releases
  echo "Deleting Helm releases..."
  helm uninstall "postgres-${e}"             --namespace "${PG_NS}"    2>/dev/null || true
  helm uninstall "pgadmin-${e}"              --namespace "${PG_NS}"    2>/dev/null || true
  helm uninstall "kafka-cluster-${e}"        --namespace "${KAFKA_NS}" 2>/dev/null || true
  helm uninstall "akhq-${e}"                 --namespace "${KAFKA_NS}" 2>/dev/null || true
  helm uninstall "elasticsearch-cluster-${e}" --namespace "${ES_NS}"   2>/dev/null || true
  helm uninstall "zookeeper-${e}"            --namespace "${ZK_NS}"    2>/dev/null || true
  helm uninstall "loki-${e}"                 --namespace "${OBS_NS}"   2>/dev/null || true
  helm uninstall "tempo-${e}"                --namespace "${OBS_NS}"   2>/dev/null || true
  helm uninstall "promtail-${e}"             --namespace "${OBS_NS}"   2>/dev/null || true
  helm uninstall "opentelemetry-collector-${e}" --namespace "${OBS_NS}" 2>/dev/null || true
  helm uninstall "redis-${e}"                --namespace "${REDIS_NS}" 2>/dev/null || true
  helm uninstall "prometheus-${e}"           --namespace "${OBS_NS}"   2>/dev/null || true

  # 2. Force-delete PVCs
  echo "Force-deleting PVCs..."
  for ns in "${PG_NS}" "${KAFKA_NS}" "${ES_NS}" "${ZK_NS}" "${OBS_NS}" "${REDIS_NS}"; do
    kubectl patch pvc -n "${ns}" --all --type='json' -p='[{"op": "remove", "path": "/metadata/finalizers"}]' 2>/dev/null || true
    kubectl delete pvc -n "${ns}" --all --ignore-not-found --timeout=30s 2>/dev/null || true
  done

  # 3. Delete CRs
  echo "Deleting CRs..."
  kubectl delete postgresql postgresql -n "${PG_NS}" --ignore-not-found --timeout=60s 2>/dev/null || true
  kubectl delete kafka kafka-cluster -n "${KAFKA_NS}" --ignore-not-found --timeout=60s 2>/dev/null || true
  kubectl delete elasticsearch elasticsearch -n "${ES_NS}" --ignore-not-found --timeout=60s 2>/dev/null || true

  # 4. Delete namespaces
  echo "Deleting namespaces..."
  for ns in "${PG_NS}" "${KAFKA_NS}" "${ES_NS}" "${ZK_NS}" "${OBS_NS}" "${REDIS_NS}"; do
    kubectl delete namespace "${ns}" --ignore-not-found --timeout=120s 2>/dev/null || true
  done

  echo "Environment ${e} teardown complete."
done

echo ""
echo "============================================"
echo " TEARDOWN COMPLETE"
echo "============================================"
echo ""
echo "To redeploy infrastructure:"
echo "  ./setup-all.sh <env>"
