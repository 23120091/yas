#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# deploy-kafka.sh — Deploy Kafka cluster + Debezium Connect + AKHQ
# Usage: ./deploy-kafka.sh <env>
#   env: dev | staging | production
# ==============================================================================

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEPLOY_DIR="${REPO_ROOT}/k8s/deploy"
CHART_DIR="${DEPLOY_DIR}/kafka/kafka-cluster"
CONFIG_FILE="${DEPLOY_DIR}/cluster-config-${1}.yaml"

if [ ! -f "$CONFIG_FILE" ]; then
  echo "Usage: $0 <env>"
  echo "  env: dev | staging | production"
  echo ""
  echo "Run from anywhere in the repo, e.g.:"
  echo "  bash k8s/deploy/deploy-kafka.sh dev"
  echo "  cp k8s/deploy/deploy-kafka.sh /tmp && bash /tmp/deploy-kafka.sh dev"
  exit 1
fi

ENV="$1"
KAFKA_NS="kafka-${ENV}"
KAFKA_REPLICAS=$(yq -r '.kafka.replicas' "$CONFIG_FILE")
PG_USERNAME=$(yq -r '.postgresql.username // "yasadminuser"' "$CONFIG_FILE")
PG_PASSWORD=$(yq -r '.postgresql.password // ""' "$CONFIG_FILE")
PG_NS=$(yq -r '.postgresql.namespace // "postgres-'${ENV}'"' "$CONFIG_FILE")

echo "Deploying Kafka cluster to namespace '${KAFKA_NS}'..."
echo "  Replicas:       ${KAFKA_REPLICAS}"
echo "  Postgres NS:    ${PG_NS}"
echo "  Postgres User:  ${PG_USERNAME}"
echo ""

helm upgrade --install "kafka-cluster-${ENV}" "${CHART_DIR}" \
  --create-namespace --namespace "${KAFKA_NS}" \
  --set kafka.replicas="${KAFKA_REPLICAS}" \
  --set postgresql.username="${PG_USERNAME}" \
  --set postgresql.password="${PG_PASSWORD}" \
  --set postgresql.namespace="${PG_NS}" \
  --values "${DEPLOY_DIR}/infra-${ENV}-affinity.yaml"

echo ""
echo "Kafka cluster '${KAFKA_NS}' deployed successfully."
echo "  Brokers:      kafka-cluster-kafka-brokers.${KAFKA_NS}:9092"
echo "  Bootstrap:    kafka-cluster-kafka-bootstrap.${KAFKA_NS}:9092"
echo "  AKHQ:        akhq-${ENV}.<domain>"
echo ""
echo "Note: postgresql.credentials secret is managed by SealedSecret (ArgoCD wave -3)."
echo "      Ensure sealed-secrets are synced BEFORE running this script."