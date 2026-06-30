#!/bin/bash
# ============================================================================
# DEPLOY YAS CONFIGURATION — Multi-Environment
# ============================================================================
# ⚠️  DEPRECATED: This script is kept for EMERGENCY USE ONLY.
#    yas-configuration is now managed by ArgoCD ApplicationSet.
#    Normal workflow: edit values.yaml → git push → ArgoCD auto-syncs.
#    Only use this script if ArgoCD is down or for disaster recovery.
# ============================================================================
# Deploys shared ConfigMaps and Secrets (yas-configuration Helm chart)
# for a specific environment.
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

echo "⚠️  WARNING: yas-configuration is managed by ArgoCD."
echo "    This script should only be used in emergencies (ArgoCD down)."
echo "    Normal workflow: edit values.yaml → git push → ArgoCD auto-syncs."
echo ""
read -p "Continue anyway? (yes/no): " confirm
if [ "$confirm" != "yes" ]; then
    echo "Aborted. Use ArgoCD for normal deployments."
    exit 0
fi

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
    IDENTITY_HOST="identity.${DOMAIN}"
    STOREFRONT_HOST="storefront.${DOMAIN}"
    BACKOFFICE_HOST="backoffice.${DOMAIN}"
else
    HOST_PREFIX="${ENV_SUBDOMAIN}-"
    IDENTITY_HOST="identity-${ENV_SUBDOMAIN}.${DOMAIN}"
    STOREFRONT_HOST="storefront-${ENV_SUBDOMAIN}.${DOMAIN}"
    BACKOFFICE_HOST="backoffice-${ENV_SUBDOMAIN}.${DOMAIN}"
fi

YAS_NS="${ENV}"
KAFKA_NS="kafka-${ENV}"
REDIS_NS="redis-${ENV}"
PG_NS="postgres-${ENV}"
ES_NS="elasticsearch-${ENV}"

echo "YAS namespace:   ${YAS_NS}"
echo "Kafka namespace: ${KAFKA_NS}"
echo "Redis namespace: ${REDIS_NS}"
echo "Identity host:   ${IDENTITY_HOST}"

# Read ECK-generated elastic password (operator auto-creates this secret)
ES_PASSWORD=$(kubectl get secret elasticsearch-es-elastic-user -n "${ES_NS}" -o jsonpath="{.data.elastic}" 2>/dev/null | base64 -d || echo "")
if [ -n "$ES_PASSWORD" ]; then
  echo "Elasticsearch password: read from ECK secret"
else
  echo "WARNING: Could not read ECK password, using default 'changeme'"
  ES_PASSWORD="changeme"
fi

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
  --set "backofficeBffExtraConfig.spring.security.oauth2.client.provider.keycloak.issuer-uri=http://${IDENTITY_HOST}/realms/Yas" \
  --set "storefrontBffExtraConfig.spring.data.redis.host=redis-master.${REDIS_NS}" \
  --set "storefrontBffExtraConfig.spring.security.oauth2.client.provider.keycloak.issuer-uri=http://${IDENTITY_HOST}/realms/Yas" \
  --set "customerApplicationConfig.keycloak.auth-server-url=http://${IDENTITY_HOST}" \
  --set "searchApplicationConfig.elasticsearch.url=elasticsearch-es-http.${ES_NS}:9200" \
  --set "credentials.elasticsearch.username=elastic" \
  --set "credentials.elasticsearch.password=${ES_PASSWORD}" \
  --set "paymentPaypalApplicationConfig.yas.public.url=http://${STOREFRONT_HOST}/complete-payment" \
  --set "mediaApplicationConfig.yas.publicUrl=http://${STOREFRONT_HOST}/api/media" \
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
