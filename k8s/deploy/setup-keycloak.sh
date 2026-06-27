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
echo " Deploying Keycloak for: ${ENV}"
echo "============================================"

# --------------------------------------------------------------------------
# Read configuration
# --------------------------------------------------------------------------
read -rd '' DOMAIN ENV_SUBDOMAIN \
PG_USERNAME PG_PASSWORD \
BOOTSTRAP_ADMIN_USERNAME BOOTSTRAP_ADMIN_PASSWORD \
KEYCLOAK_BACKOFFICE_REDIRECT_URL KEYCLOAK_STOREFRONT_REDIRECT_URL \
< <(yq -r '
  .domain,
  .envSubdomain,
  .postgresql.username,
  .postgresql.password,
  .keycloak.bootstrapAdmin.username,
  .keycloak.bootstrapAdmin.password,
  .keycloak.backofficeRedirectUrl,
  .keycloak.storefrontRedirectUrl
' "$CONFIG_FILE")

# Build env-specific hostname and namespace
if [ -z "$ENV_SUBDOMAIN" ] || [ "$ENV_SUBDOMAIN" = "null" ]; then
    HOST_PREFIX=""
else
    HOST_PREFIX="${ENV_SUBDOMAIN}."
fi

KEYCLOAK_NS="keycloak-${ENV}"
PG_NS="postgres-${ENV}"
KEYCLOAK_HOSTNAME="identity.${HOST_PREFIX}${DOMAIN}"
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
  --set postgresql.username="$PG_USERNAME" \
  --set postgresql.password="$PG_PASSWORD" \
  --set postgresql.host="$PG_HOST" \
  --set bootstrapAdmin.username="$BOOTSTRAP_ADMIN_USERNAME" \
  --set bootstrapAdmin.password="$BOOTSTRAP_ADMIN_PASSWORD" \
  --set backofficeRedirectUrl="$KEYCLOAK_BACKOFFICE_REDIRECT_URL" \
  --set storefrontRedirectUrl="$KEYCLOAK_STOREFRONT_REDIRECT_URL"

echo ""
echo "============================================"
echo " Keycloak deployed for: ${ENV}"
echo " Namespace: ${KEYCLOAK_NS}"
echo " URL:       http://${KEYCLOAK_HOSTNAME}"
echo " Admin:     ${BOOTSTRAP_ADMIN_USERNAME} / ${BOOTSTRAP_ADMIN_PASSWORD}"
echo "============================================"

# --- CoreDNS rewrite: resolve external Keycloak hostname to cluster-internal IPs ---
# Pods need to reach identity.{env}.yas.local.com for OIDC discovery
# Rewrite it to the nginx ingress controller, which routes via Keycloak's Ingress
echo "Patching CoreDNS to resolve ${KEYCLOAK_HOSTNAME}..."
kubectl get configmap -n kube-system coredns -o yaml > /tmp/coredns-${ENV}.yaml
if ! grep -q "rewrite stop name ${KEYCLOAK_HOSTNAME}" /tmp/coredns-${ENV}.yaml; then
  sed -i "/^\s*kubernetes cluster.local/i\        rewrite stop name ${KEYCLOAK_HOSTNAME} ingress-nginx-controller.ingress-nginx.svc.cluster.local" /tmp/coredns-${ENV}.yaml
  kubectl apply -f /tmp/coredns-${ENV}.yaml
  kubectl rollout restart deployment -n kube-system coredns
  echo "CoreDNS patched. DNS will be active in ~30s."
else
  echo "CoreDNS already patched."
fi
