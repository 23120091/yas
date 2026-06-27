+ cat setup-keycloak.sh
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
DOMAIN=$(yq -r '.domain' "$CONFIG_FILE")
ENV_SUBDOMAIN=$(yq -r '.envSubdomain' "$CONFIG_FILE")
PG_USERNAME=$(yq -r '.postgresql.username' "$CONFIG_FILE")
PG_PASSWORD=$(yq -r '.postgresql.password' "$CONFIG_FILE")
BOOTSTRAP_ADMIN_USERNAME=$(yq -r '.keycloak.bootstrapAdmin.username' "$CONFIG_FILE")
BOOTSTRAP_ADMIN_PASSWORD=$(yq -r '.keycloak.bootstrapAdmin.password' "$CONFIG_FILE")
KEYCLOAK_BACKOFFICE_REDIRECT_URL=$(yq -r '.keycloak.backofficeRedirectUrl' "$CONFIG_FILE")
KEYCLOAK_STOREFRONT_REDIRECT_URL=$(yq -r '.keycloak.storefrontRedirectUrl' "$CONFIG_FILE")
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

echo "Detected Postgres Host: ${PG_HOST}"
echo "Keycloak namespace: ${KEYCLOAK_NS}"
echo "Keycloak hostname:  ${KEYCLOAK_HOSTNAME}"

# --------------------------------------------------------------------------
# Install Keycloak CRDs (cluster-scoped, idempotent)
# --------------------------------------------------------------------------
kubectl create namespace "${KEYCLOAK_NS}" --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f https://raw.githubusercontent.com/keycloak/keycloak-k8s-resources/26.0.2/kubernetes/keycloaks.k8s.keycloak.org-v1.yml
kubectl apply -f https://raw.githubusercontent.com/keycloak/keycloak-k8s-resources/26.0.2/kubernetes/keycloakrealmimports.k8s.keycloak.org-v1.yml
kubectl apply -f https://raw.githubusercontent.com/keycloak/keycloak-k8s-resources/26.0.2/kubernetes/kubernetes.yml -n "${KEYCLOAK_NS}"


echo "--- Đang đồng bộ thông tin Database từ Operator ---"
PG_SECRET_NAME="${PG_USERNAME}.postgresql.credentials.postgresql.acid.zalan.do"

until kubectl get secret "$PG_SECRET_NAME" -n "${PG_NS}" > /dev/null 2>&1; do
  echo "Chưa tìm thấy secret '$PG_SECRET_NAME' trong namespace ${PG_NS}, đợi 5 giây..."
  sleep 5
done

# Lấy dữ liệu thực tế từ Secret của Operator
DB_USER=$(kubectl get secret "$PG_SECRET_NAME" -n "${PG_NS}" -o jsonpath='{.data.username}' | base64 -d)
DB_PASS=$(kubectl get secret "$PG_SECRET_NAME" -n "${PG_NS}" -o jsonpath='{.data.password}' | base64 -d)

# 2. Tạo Secret trung gian trong namespace Keycloak
kubectl create secret generic keycloak-db-secret -n "${KEYCLOAK_NS}" \
  --from-literal=username="$DB_USER" \
  --from-literal=password="$DB_PASS" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "--- Đồng bộ Secret hoàn tất. Tiến hành cài đặt Keycloak ---"

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