#!/bin/bash
# ============================================================================
# SETUP KEYCLOAK — Multi-Environment
# ============================================================================
# Deploys Keycloak for Identity and Access Management with per-env isolation.
#
# Usage:
#   ./setup-keycloak.sh <env>     (dev|staging|production, default: dev)
#
# Each environment gets its own Keycloak instance in keycloak-{env} namespace
# with env-specific hostname (e.g., identity.dev.yas.local.com).
# ============================================================================

set -x
DIR="$(cd "$(dirname "$0")" && pwd)"

# --------------------------------------------------------------------------
# Load common passwords from .env
# --------------------------------------------------------------------------
source "$DIR/.env"

# --------------------------------------------------------------------------
# Environment selection
# --------------------------------------------------------------------------
ENV=${1:-dev}
CONFIG_FILE="$DIR/cluster-config-${ENV}.yaml"

if [ ! -f "$CONFIG_FILE" ]; then
    echo "ERROR: Config file '$CONFIG_FILE' not found."
    exit 1
fi

echo "============================================"
echo " Deploying Keycloak for: ${ENV}"
echo "============================================"

# --------------------------------------------------------------------------
# Read configuration individually (not read -rd '' which breaks on empty values)
# --------------------------------------------------------------------------
DOMAIN=$(yq -r '.domain' "$CONFIG_FILE")
ENV_SUBDOMAIN=$(yq -r '.envSubdomain // ""' "$CONFIG_FILE")
PG_USERNAME=$(yq -r '.postgresql.username' "$CONFIG_FILE")
KEYCLOAK_BACKOFFICE_REDIRECT_URL_0=$(yq -r '.keycloak.backofficeRedirectUrls[0]' "$CONFIG_FILE")
KEYCLOAK_BACKOFFICE_REDIRECT_URL_1=$(yq -r '.keycloak.backofficeRedirectUrls[1] // ""' "$CONFIG_FILE")
KEYCLOAK_STOREFRONT_REDIRECT_URL_0=$(yq -r '.keycloak.storefrontRedirectUrls[0]' "$CONFIG_FILE")
KEYCLOAK_STOREFRONT_REDIRECT_URL_1=$(yq -r '.keycloak.storefrontRedirectUrls[1] // ""' "$CONFIG_FILE")

# Passwords come from .env.txt (sourced above):
#   POSTGRES_PASSWORD, KEYCLOAK_ADMIN_USERNAME, KEYCLOAK_ADMIN_PASSWORD

# Build env-specific hostname and namespace
if [ -z "$ENV_SUBDOMAIN" ] || [ "$ENV_SUBDOMAIN" = "null" ]; then
    KEYCLOAK_HOSTNAME="identity.${DOMAIN}"
else
    KEYCLOAK_HOSTNAME="identity-${ENV_SUBDOMAIN}.${DOMAIN}"
fi

KEYCLOAK_NS="keycloak-${ENV}"
PG_NS="postgres-${ENV}"
PG_HOST="postgresql.${PG_NS}.svc.cluster.local"

echo "Keycloak namespace: ${KEYCLOAK_NS}"
echo "Keycloak hostname:  ${KEYCLOAK_HOSTNAME}"
echo "PostgreSQL host:    ${PG_HOST}"

# --------------------------------------------------------------------------
# Install Keycloak CRDs (cluster-scoped, idempotent)
# --------------------------------------------------------------------------
kubectl create namespace "${KEYCLOAK_NS}" --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f https://raw.githubusercontent.com/keycloak/keycloak-k8s-resources/26.0.2/kubernetes/keycloaks.k8s.keycloak.org-v1.yml
kubectl apply -f https://raw.githubusercontent.com/keycloak/keycloak-k8s-resources/26.0.2/kubernetes/keycloakrealmimports.k8s.keycloak.org-v1.yml
kubectl apply -f https://raw.githubusercontent.com/keycloak/keycloak-k8s-resources/26.0.2/kubernetes/kubernetes.yml -n "${KEYCLOAK_NS}"

# --------------------------------------------------------------------------
# Install Keycloak instance
# --------------------------------------------------------------------------
helm upgrade --install "keycloak-${ENV}" ./keycloak/keycloak \
  --namespace "${KEYCLOAK_NS}" \
  --set hostname="${KEYCLOAK_HOSTNAME}" \
  --set backchannelDynamic=false \
  --set postgresql.username="$PG_USERNAME" \
  --set postgresql.password="$POSTGRES_PASSWORD" \
  --set postgresql.host="$PG_HOST" \
  --set bootstrapAdmin.username="$KEYCLOAK_ADMIN_USERNAME" \
  --set bootstrapAdmin.password="$KEYCLOAK_ADMIN_PASSWORD" \
  --set "backofficeRedirectUrls[0]=$KEYCLOAK_BACKOFFICE_REDIRECT_URL_0" \
  --set "backofficeRedirectUrls[1]=$KEYCLOAK_BACKOFFICE_REDIRECT_URL_1" \
  --set "storefrontRedirectUrls[0]=$KEYCLOAK_STOREFRONT_REDIRECT_URL_0" \
  --set "storefrontRedirectUrls[1]=$KEYCLOAK_STOREFRONT_REDIRECT_URL_1" \
  --values "./infra-${ENV}-affinity.yaml"

echo ""
echo "============================================"
echo " Keycloak deployed for: ${ENV}"
echo " Namespace: ${KEYCLOAK_NS}"
echo " URL:       http://${KEYCLOAK_HOSTNAME}"
echo " Admin:     ${KEYCLOAK_ADMIN_USERNAME} / ${KEYCLOAK_ADMIN_PASSWORD}"
echo "============================================"

# --- CoreDNS rewrite: route identity.{env}.yas.local.com to Traefik ingress ---
# BFF pods call Keycloak's OIDC discovery endpoint via the external hostname.
# Rewrite it to the Traefik service so traffic stays cluster-internal.
echo "Patching CoreDNS to resolve ${KEYCLOAK_HOSTNAME}..."
kubectl get configmap -n kube-system coredns -o yaml > /tmp/coredns-${ENV}.yaml
if ! grep -q "rewrite stop name ${KEYCLOAK_HOSTNAME} traefik" /tmp/coredns-${ENV}.yaml; then
  sed -i "/^\s*kubernetes cluster.local/i\        rewrite stop name ${KEYCLOAK_HOSTNAME} traefik.kube-system.svc.cluster.local" /tmp/coredns-${ENV}.yaml
  kubectl apply -f /tmp/coredns-${ENV}.yaml
  kubectl rollout restart deployment -n kube-system coredns
  echo "CoreDNS patched. DNS will be active in ~30s."
else
  echo "CoreDNS already patched."
fi
