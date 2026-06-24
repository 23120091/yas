#!/bin/bash
# ============================================================================
# DEPLOY YAS CONFIGURATION — Multi-Environment
# ============================================================================
# Deploys shared ConfigMaps and Secrets (yas-configuration Helm chart)
# for a specific environment. This must run BEFORE yas-applications.
#
# Usage:
#   ./deploy-yas-configuration.sh <env>     (dev|staging|production, default: dev)
#
# The yas-configuration chart contains:
#   - application.yaml (shared Spring Boot config)
#   - gateway-routes-config.yaml (BFF routes)
#   - Per-service ConfigMaps (payment, media, customer, search, etc.)
#   - Credential Secrets (postgresql, keycloak, redis, elasticsearch, openai)
#
# Env-specific overrides (--set):
#   - Kafka bootstrap servers (kafka-{env} namespace)
#   - Keycloak issuer URI (identity.{env}.yas.local.com)
#   - BFF Redis host (redis-{env} namespace)
# ============================================================================

set -x

# --------------------------------------------------------------------------
# Environment selection
# --------------------------------------------------------------------------
ENV=${1:-dev}
CONFIG_FILE="cluster-config-${ENV}.yaml"

if [ ! -f "$CONFIG_FILE" ]; then
    echo "ERROR: Config file '$CONFIG_FILE' not found."
    exit 1
fi

echo "============================================"
echo " Deploying YAS configuration for: ${ENV}"
echo "============================================"

# --------------------------------------------------------------------------
# Read domain and envSubdomain from config
# --------------------------------------------------------------------------
read -rd '' DOMAIN ENV_SUBDOMAIN < <(yq -r '.domain, .envSubdomain' "$CONFIG_FILE")

if [ -z "$ENV_SUBDOMAIN" ] || [ "$ENV_SUBDOMAIN" = "null" ]; then
    HOST_PREFIX=""
else
    HOST_PREFIX="${ENV_SUBDOMAIN}."
fi

YAS_NS="${ENV}"
KAFKA_NS="kafka-${ENV}"
REDIS_NS="redis-${ENV}"
PG_NS="postgres-${ENV}"
ES_NS="elasticsearch-${ENV}"

IDENTITY_HOST="identity.${HOST_PREFIX}${DOMAIN}"

echo "YAS namespace:   ${YAS_NS}"
echo "Kafka namespace: ${KAFKA_NS}"
echo "Redis namespace: ${REDIS_NS}"
echo "Identity host:   ${IDENTITY_HOST}"

# --------------------------------------------------------------------------
# Install Stakater Reloader (auto-restart pods on ConfigMap/Secret change)
# --------------------------------------------------------------------------
helm repo add stakater https://stakater.github.io/stakater-charts
helm repo update

# --------------------------------------------------------------------------
# Deploy yas-configuration chart with env-specific overrides
# --------------------------------------------------------------------------
helm dependency build ../charts/yas-configuration
helm upgrade --install "yas-configuration-${ENV}" ../charts/yas-configuration \
  --namespace "${YAS_NS}" --create-namespace \
  --set "applicationConfig.spring.kafka.bootstrap-servers=kafka-cluster-kafka-brokers.${KAFKA_NS}:9092" \
  --set "applicationConfig.spring.kafka.consumer.bootstrap-servers=kafka-cluster-kafka-brokers.${KAFKA_NS}:9092" \
  --set "applicationConfig.spring.security.oauth2.resourceserver.jwt.issuer-uri=http://${IDENTITY_HOST}/realms/Yas" \
  --set "applicationConfig.springdoc.oauthflow.authorization-url=http://${IDENTITY_HOST}/realms/Yas/protocol/openid-connect/auth" \
  --set "applicationConfig.springdoc.oauthflow.token-url=http://${IDENTITY_HOST}/realms/Yas/protocol/openid-connect/token" \
  --set "backofficeBffExtraConfig.spring.data.redis.host=redis-master.${REDIS_NS}" \
  --set "storefrontBffExtraConfig.spring.data.redis.host=redis-master.${REDIS_NS}" \
  --set "customerApplicationConfig.keycloak.auth-server-url=http://${IDENTITY_HOST}" \
  --set "searchApplicationConfig.elasticsearch.url=http://elasticsearch-es-http.${ES_NS}:9200" \
  --set "paymentPaypalApplicationConfig.yas.public.url=http://storefront.${HOST_PREFIX}${DOMAIN}/complete-payment" \
  --set "sampledataApplicationConfig.spring.datasource.product.url=jdbc:postgresql://postgresql.${PG_NS}:5432/product" \
  --set "sampledataApplicationConfig.spring.datasource.media.url=jdbc:postgresql://postgresql.${PG_NS}:5432/media"

echo ""
echo "============================================"
echo " YAS configuration deployed for: ${ENV}"
echo " Namespace:       ${YAS_NS}"
echo " Kafka brokers:   kafka-cluster-kafka-brokers.${KAFKA_NS}:9092"
echo " Identity:        http://${IDENTITY_HOST}/realms/Yas"
echo " Redis:           redis-master.${REDIS_NS}:6379"
echo "============================================"
