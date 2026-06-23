#!/bin/bash
# ============================================================================
# DEPLOY YAS APPLICATIONS — Multi-Environment
# ============================================================================
# Deploys all YAS microservices for a specific environment via Helm.
#
# Usage:
#   ./deploy-yas-applications.sh <env>     (dev|staging|production, default: dev)
#
# Prerequisites:
#   - Infrastructure must be running (setup-cluster.sh, setup-keycloak.sh,
#     setup-redis.sh must have been executed for this env first)
#   - yas-configuration must be deployed (deploy-yas-configuration.sh)
#
# This script is for MANUAL deployment. In the GitOps model (PLAN.md),
# ArgoCD ApplicationSets replace this step for the application layer.
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
echo " Deploying YAS applications for: ${ENV}"
echo "============================================"

# --------------------------------------------------------------------------
# Read domain from config
# --------------------------------------------------------------------------
read -rd '' DOMAIN ENV_SUBDOMAIN < <(yq -r '.domain, .envSubdomain' "$CONFIG_FILE")

if [ -z "$ENV_SUBDOMAIN" ] || [ "$ENV_SUBDOMAIN" = "null" ]; then
    HOST_PREFIX=""
else
    HOST_PREFIX="${ENV_SUBDOMAIN}."
fi

YAS_NS="${ENV}"

echo "YAS namespace: ${YAS_NS}"
echo "Domain:        ${HOST_PREFIX}${DOMAIN}"

# --------------------------------------------------------------------------
# Reloader (auto-restart pods on ConfigMap/Secret change)
# --------------------------------------------------------------------------
helm repo add stakater https://stakater.github.io/stakater-charts
helm repo update

# ============================================================================
# BFF + UI — Deployed first (gateway + frontend)
# ============================================================================

# --------------------------------------------------------------------------
# Backoffice BFF (API Gateway for admin)
# --------------------------------------------------------------------------
helm dependency build ../charts/backoffice-bff
helm upgrade --install "backoffice-bff-${ENV}" ../charts/backoffice-bff \
  --namespace "${YAS_NS}" --create-namespace \
  --set backend.ingress.host="backoffice.${HOST_PREFIX}${DOMAIN}"

# --------------------------------------------------------------------------
# Backoffice UI (Next.js admin frontend)
# --------------------------------------------------------------------------
helm dependency build ../charts/backoffice-ui
helm upgrade --install "backoffice-ui-${ENV}" ../charts/backoffice-ui \
  --namespace "${YAS_NS}" --create-namespace

sleep 60

# --------------------------------------------------------------------------
# Storefront BFF (API Gateway for customers)
# --------------------------------------------------------------------------
helm dependency build ../charts/storefront-bff
helm upgrade --install "storefront-bff-${ENV}" ../charts/storefront-bff \
  --namespace "${YAS_NS}" --create-namespace \
  --set backend.ingress.host="storefront.${HOST_PREFIX}${DOMAIN}"

# --------------------------------------------------------------------------
# Storefront UI (Next.js customer frontend)
# --------------------------------------------------------------------------
helm dependency build ../charts/storefront-ui
helm upgrade --install "storefront-ui-${ENV}" ../charts/storefront-ui \
  --namespace "${YAS_NS}" --create-namespace

sleep 60

# --------------------------------------------------------------------------
# Swagger UI
# --------------------------------------------------------------------------
helm upgrade --install "swagger-ui-${ENV}" ../charts/swagger-ui \
  --namespace "${YAS_NS}" --create-namespace \
  --set ingress.host="api.${HOST_PREFIX}${DOMAIN}"

sleep 20

# ============================================================================
# BACKEND MICROSERVICES
# ============================================================================

for chart in {"cart","customer","inventory","location","media","order","payment","payment-paypal","product","promotion","rating","search","tax","recommendation","webhook","sampledata"} ; do
    helm dependency build ../charts/"$chart"
    helm upgrade --install "${chart}-${ENV}" ../charts/"$chart" \
      --namespace "${YAS_NS}" --create-namespace \
      --set backend.ingress.host="api.${HOST_PREFIX}${DOMAIN}"
    sleep 60
done

echo ""
echo "============================================"
echo " YAS applications deployed for: ${ENV}"
echo " Namespace:  ${YAS_NS}"
echo " Storefront: http://storefront.${HOST_PREFIX}${DOMAIN}"
echo " Backoffice: http://backoffice.${HOST_PREFIX}${DOMAIN}"
echo " Swagger:    http://api.${HOST_PREFIX}${DOMAIN}/swagger-ui/"
echo "============================================"
