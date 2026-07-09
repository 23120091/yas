#!/bin/bash
# ============================================================================
# SETUP ALL — Deploy Infrastructure for a Single Environment
# ============================================================================
# Deploys ALL infrastructure: operators (cluster-scoped) + instances (per-env).
#
# WHAT THIS DEPLOYS:
#   OPERATORS (cluster-scoped, idempotent):
#     - Zalando PostgreSQL Operator
#     - Strimzi Kafka Operator
#     - ECK Elasticsearch Operator
#     - Cert Manager
#     - OpenTelemetry Operator
#     - Sealed Secrets
#   INSTANCES (per-env, namespace-isolated):
#     - PostgreSQL cluster + pgAdmin
#     - Kafka cluster + AKHQ
#     - Elasticsearch cluster + Kibana
#     - Zookeeper
#     - Redis
#     - Loki + Tempo + Promtail + OTel Collector
#
# All infra pods are pinned to the correct node via nodeSelector (label: env={env}).
#
# USAGE:
#   ./setup-all.sh <env>     (dev|staging|production, default: dev)
# ============================================================================

set -x
DIR="$(cd "$(dirname "$0")" && pwd)"

source "$DIR/.env"

ENV=${1:-dev}
CONFIG_FILE="$DIR/cluster-config-${ENV}.yaml"

if [ ! -f "$CONFIG_FILE" ]; then
    echo "ERROR: Config file '$CONFIG_FILE' not found."
    echo "Available: cluster-config-dev.yaml, cluster-config-staging.yaml, cluster-config-production.yaml"
    exit 1
fi

echo "============================================"
echo " Deploying infrastructure for: ${ENV}"
echo " Config file: ${CONFIG_FILE}"
echo "============================================"

# --------------------------------------------------------------------------
# Add chart repos and update
# --------------------------------------------------------------------------
helm repo add postgres-operator-charts https://opensource.zalando.com/postgres-operator/charts/postgres-operator
helm repo add strimzi https://strimzi.io/charts/
helm repo add akhq https://akhq.io/
helm repo add elastic https://helm.elastic.co
helm repo add grafana https://grafana.github.io/helm-charts
helm repo add open-telemetry https://open-telemetry.github.io/opentelemetry-helm-charts
helm repo add jetstack https://charts.jetstack.io
helm repo add sealed-secrets https://bitnami.github.io/sealed-secrets
helm repo update

# --------------------------------------------------------------------------
# Read configuration from cluster-config
# --------------------------------------------------------------------------
DOMAIN=$(yq -r '.domain' "$CONFIG_FILE")
ENV_SUBDOMAIN=$(yq -r '.envSubdomain // ""' "$CONFIG_FILE")
PG_REPLICAS=$(yq -r '.postgresql.replicas' "$CONFIG_FILE")
PG_USERNAME=$(yq -r '.postgresql.username' "$CONFIG_FILE")
PG_VOLUME_SIZE=$(yq -r '.postgresql.volumeSize' "$CONFIG_FILE")
KAFKA_REPLICAS=$(yq -r '.kafka.replicas' "$CONFIG_FILE")
ZK_REPLICAS=$(yq -r '.zookeeper.replicas' "$CONFIG_FILE")
ES_REPLICAS=$(yq -r '.elasticsearch.replicas' "$CONFIG_FILE")

PG_PASSWORD_VAR="POSTGRES_PASSWORD_$(echo ${ENV} | tr '[:lower:]' '[:upper:]')"
PG_PASSWORD="${!PG_PASSWORD_VAR:-$POSTGRES_PASSWORD}"

# --------------------------------------------------------------------------
# Build env-specific namespaces and hostnames
# --------------------------------------------------------------------------
if [ -z "$ENV_SUBDOMAIN" ] || [ "$ENV_SUBDOMAIN" = "null" ]; then
    HOST_PREFIX=""
else
    HOST_PREFIX="${ENV_SUBDOMAIN}."
fi

PG_NS="postgres-${ENV}"
KAFKA_NS="kafka-${ENV}"
ES_NS="elasticsearch-${ENV}"
OBS_NS="observability-${ENV}"
ZK_NS="zookeeper-${ENV}"
REDIS_NS="redis-${ENV}"

echo "Namespaces: PG=${PG_NS}, Kafka=${KAFKA_NS}, ES=${ES_NS}, OBS=${OBS_NS}, ZK=${ZK_NS}, Redis=${REDIS_NS}"

# ============================================================================
# OPERATORS — Cluster-scoped, idempotent (safe to re-run per env)
# ============================================================================

echo ""
echo ">>> DEPLOYING OPERATORS"
echo ""

helm upgrade --install postgres-operator postgres-operator-charts/postgres-operator \
  --create-namespace --namespace postgres \
  --values "./infra-master-affinity.yaml"

helm upgrade --install kafka-operator strimzi/strimzi-kafka-operator \
  --create-namespace --namespace kafka \
  --set watchAnyNamespace=true \
  --values "./infra-master-affinity.yaml"

echo "Waiting for Strimzi CRDs..."
kubectl wait --for=condition=established crd/kafkas.kafka.strimzi.io --timeout=120s 2>/dev/null || sleep 30

helm upgrade --install elastic-operator elastic/eck-operator \
  --create-namespace --namespace elasticsearch \
  --values "./infra-master-affinity.yaml"

helm upgrade --install cert-manager jetstack/cert-manager \
  --namespace cert-manager --create-namespace \
  --version v1.12.0 \
  --set installCRDs=true \
  --set prometheus.enabled=false \
  --set webhook.timeoutSeconds=30 \
  --set admissionWebhooks.certManager.create=true \
  --set startupapicheck.enabled=false \
  --values "./infra-master-affinity.yaml"

echo "Waiting for cert-manager webhook..."
kubectl wait --for=condition=ready pod -l app.kubernetes.io/instance=cert-manager -n cert-manager --timeout=120s 2>/dev/null || sleep 30

helm upgrade --install opentelemetry-operator open-telemetry/opentelemetry-operator \
  --create-namespace --namespace observability \
  --values "./infra-master-affinity.yaml"

kubectl create namespace kube-system --dry-run=client -o yaml | kubectl apply -f -
helm upgrade --install sealed-secrets sealed-secrets/sealed-secrets \
  --namespace kube-system \
  --set keyRenewPeriod=0

echo "Waiting for Sealed Secrets..."
kubectl rollout status deployment/sealed-secrets -n kube-system --timeout=150s || sleep 30

# Install kubeseal CLI if not present
if ! command -v kubeseal &> /dev/null; then
  echo "Installing kubeseal CLI..."
  KUBESEAL_VERSION="0.26.2"
  curl -OL "https://github.com/bitnami-labs/sealed-secrets/releases/download/v${KUBESEAL_VERSION}/kubeseal-${KUBESEAL_VERSION}-linux-amd64.tar.gz"
  tar -xzf "kubeseal-${KUBESEAL_VERSION}-linux-amd64.tar.gz" kubeseal
  sudo install -m 755 kubeseal /usr/local/bin/kubeseal
  rm -f "kubeseal-${KUBESEAL_VERSION}-linux-amd64.tar.gz" kubeseal
fi

# ============================================================================
# INSTANCES — Per-environment isolated resources
# ============================================================================

echo ""
echo ">>> DEPLOYING INSTANCES for: ${ENV}"
echo ""

# --------------------------------------------------------------------------
# PostgreSQL
# --------------------------------------------------------------------------
echo "Deploying PostgreSQL..."
kubectl delete postgresql postgresql -n "${PG_NS}" --ignore-not-found --timeout=120s 2>/dev/null || true
sleep 10
kubectl patch pvc -n "${PG_NS}" --all --type merge -p '{"metadata":{"finalizers":[]}}' 2>/dev/null || true
kubectl delete pvc -n "${PG_NS}" --all --ignore-not-found --timeout=60s 2>/dev/null || true

kubectl create secret generic "yasadminuser.postgresql.credentials.postgresql.acid.zalan.do" \
  -n "${PG_NS}" \
  --from-literal=username="${PG_USERNAME}" \
  --from-literal=password="${PG_PASSWORD}" \
  --dry-run=client -o yaml | kubectl apply -f -

helm upgrade --install "postgres-${ENV}" ./postgres/postgresql \
  --create-namespace --namespace "${PG_NS}" \
  --set replicas="$PG_REPLICAS" \
  --set username="$PG_USERNAME" \
  --set password="$PG_PASSWORD" \
  --set volumeSize="$PG_VOLUME_SIZE" \
  --values "./infra-${ENV}-affinity.yaml"

echo "Waiting for PostgreSQL..."
kubectl wait --for=condition=ready pod -l application=spilo -n "${PG_NS}" --timeout=300s
sleep 30
kubectl wait --for=condition=ready pod -l application=spilo -n "${PG_NS}" --timeout=300s
sleep 30

# Overwrite operator-generated password with our .env password
# The operator syncs its secret -> DB, so we must patch the secret AND the DB
echo "Syncing PostgreSQL password..."
PG_POD=$(kubectl get pods -n "${PG_NS}" -l application=spilo -o jsonpath='{.items[0].metadata.name}')
kubectl exec -n "${PG_NS}" "$PG_POD" -- psql -U postgres \
  -c "ALTER USER ${PG_USERNAME} WITH PASSWORD '${PG_PASSWORD}';"
kubectl create secret generic "yasadminuser.postgresql.credentials.postgresql.acid.zalan.do" \
  -n "${PG_NS}" \
  --from-literal=username="${PG_USERNAME}" \
  --from-literal=password="${PG_PASSWORD}" \
  --dry-run=client -o yaml | kubectl apply -f -
echo "PostgreSQL password synced."

# --------------------------------------------------------------------------
# pgAdmin
# --------------------------------------------------------------------------
echo "Deploying pgAdmin..."
pg_admin_hostname="pgadmin.${HOST_PREFIX}${DOMAIN}" yq -i '.hostname=env(pg_admin_hostname)' ./postgres/pgadmin/values.yaml
helm upgrade --install "pgadmin-${ENV}" ./postgres/pgadmin \
  --create-namespace --namespace "${PG_NS}" \
  --values "./infra-${ENV}-affinity.yaml"

# --------------------------------------------------------------------------
# Kafka
# --------------------------------------------------------------------------
echo "Deploying Kafka..."
helm upgrade --install "kafka-cluster-${ENV}" ./kafka/kafka-cluster \
  --create-namespace --namespace "${KAFKA_NS}" \
  --set kafka.replicas="$KAFKA_REPLICAS" \
  --set postgresql.username="$PG_USERNAME" \
  --set postgresql.password="$PG_PASSWORD" \
  --set postgresql.namespace="$PG_NS" \
  --values "./infra-${ENV}-affinity.yaml"

# --------------------------------------------------------------------------
# AKHQ (Kafka UI)
# --------------------------------------------------------------------------
echo "Deploying AKHQ..."
akhq_hostname="akhq.${HOST_PREFIX}${DOMAIN}" yq -i '.hostname=env(akhq_hostname)' ./kafka/akhq.values.yaml
helm upgrade --install "akhq-${ENV}" akhq/akhq \
  --create-namespace --namespace "${KAFKA_NS}" \
  --values ./kafka/akhq.values.yaml \
  --values "./infra-${ENV}-affinity.yaml"

# --------------------------------------------------------------------------
# Elasticsearch
# --------------------------------------------------------------------------
echo "Deploying Elasticsearch..."
kubectl delete elasticsearch elasticsearch -n "${ES_NS}" --ignore-not-found --timeout=120s 2>/dev/null || true
kubectl patch pvc -n "${ES_NS}" --all --type merge -p '{"metadata":{"finalizers":[]}}' 2>/dev/null || true
kubectl delete pvc -n "${ES_NS}" --all --ignore-not-found --timeout=60s 2>/dev/null || true

kubectl create secret generic elasticsearch-es-elastic-user \
  -n "${ES_NS}" \
  --from-literal=elastic="${ES_PASSWORD}" \
  --dry-run=client -o yaml | kubectl apply -f -

helm upgrade --install "elasticsearch-cluster-${ENV}" ./elasticsearch/elasticsearch-cluster \
  --create-namespace --namespace "${ES_NS}" \
  --set elasticsearch.replicas="$ES_REPLICAS" \
  --set kibana.ingress.hostname="kibana.${HOST_PREFIX}${DOMAIN}" \
  --values "./infra-${ENV}-affinity.yaml"

# --------------------------------------------------------------------------
# Zookeeper
# --------------------------------------------------------------------------
echo "Deploying Zookeeper..."
helm upgrade --install "zookeeper-${ENV}" ./zookeeper \
  --namespace "${ZK_NS}" --create-namespace \
  --values "./infra-${ENV}-affinity.yaml"

# --------------------------------------------------------------------------
# Redis
# --------------------------------------------------------------------------
echo "Deploying Redis..."
helm upgrade --install "redis-${ENV}" \
  --set auth.password="$REDIS_PASSWORD" \
  --set master.nodeSelector.env="${ENV}" \
  --set replica.nodeSelector.env="${ENV}" \
  --set sentinel.nodeSelector.env="${ENV}" \
  --values "./infra-${ENV}-affinity.yaml" \
  oci://registry-1.docker.io/bitnamicharts/redis \
  --namespace "${REDIS_NS}" --create-namespace

# --------------------------------------------------------------------------
# Loki (log aggregation)
# --------------------------------------------------------------------------
echo "Deploying Loki..."
AFFINITY="{\"nodeAffinity\":{\"requiredDuringSchedulingIgnoredDuringExecution\":{\"nodeSelectorTerms\":[{\"matchExpressions\":[{\"key\":\"env\",\"operator\":\"In\",\"values\":[\"${ENV}\"]}]}]}}}"
helm upgrade --install "loki-${ENV}" grafana/loki \
  --create-namespace --namespace "${OBS_NS}" \
  -f ./observability/loki.values.yaml \
  --set-json "write.affinity=${AFFINITY}" \
  --set-json "read.affinity=${AFFINITY}" \
  --set-json "backend.affinity=${AFFINITY}" \
  --set-json "gateway.affinity=${AFFINITY}" \
  --set-json "minio.affinity=${AFFINITY}" \
  --set-json "chunksCache.affinity=${AFFINITY}" \
  --set-json "resultsCache.affinity=${AFFINITY}"

# --------------------------------------------------------------------------
# Tempo (trace storage — single binary mode, chart ignores affinity)
# --------------------------------------------------------------------------
echo "Deploying Tempo..."
helm upgrade --install "tempo-${ENV}" grafana/tempo \
  --create-namespace --namespace "${OBS_NS}" \
  -f ./observability/tempo.values.yaml \
  -f "./observability/tempo-${ENV}.values.yaml"
kubectl patch sts "tempo-${ENV}" -n "${OBS_NS}" \
  -p "{\"spec\":{\"template\":{\"spec\":{\"affinity\":{\"nodeAffinity\":{\"requiredDuringSchedulingIgnoredDuringExecution\":{\"nodeSelectorTerms\":[{\"matchExpressions\":[{\"key\":\"env\",\"operator\":\"In\",\"values\":[\"${ENV}\"]}]}]}}}}}}}" \
  2>/dev/null || true

# --------------------------------------------------------------------------
# Promtail (log collector)
# --------------------------------------------------------------------------
echo "Deploying Promtail..."
helm upgrade --install "promtail-${ENV}" grafana/promtail \
  --create-namespace --namespace "${OBS_NS}" \
  --values ./observability/promtail.values.yaml \
  --values "./infra-${ENV}-affinity.yaml"

# --------------------------------------------------------------------------
# OpenTelemetry Collector
# --------------------------------------------------------------------------
echo "Deploying OpenTelemetry Collector..."
kubectl wait --for=condition=available deployment/opentelemetry-operator-controller-manager -n observability --timeout=180s 2>/dev/null || sleep 30

helm upgrade --install "opentelemetry-collector-${ENV}" ./observability/opentelemetry \
  --create-namespace --namespace "${OBS_NS}" \
  --set lokiEndpoint="http://loki-${ENV}-gateway.${OBS_NS}.svc.cluster.local/loki/api/v1/push" \
  --set tempoEndpoint="http://tempo-${ENV}.${OBS_NS}.svc.cluster.local:4318" \
  --values "./infra-${ENV}-affinity.yaml"

echo ""
echo "============================================"
echo " Infrastructure deployed for: ${ENV}"
echo " Namespaces: ${PG_NS}, ${KAFKA_NS}, ${ES_NS}, ${OBS_NS}, ${ZK_NS}, ${REDIS_NS}"
echo "============================================"
