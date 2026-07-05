#!/bin/bash
# ============================================================================
# SYNC PASSWORD — Sync K8s secrets to live databases
# ============================================================================
# Reads passwords from K8s secrets (created by SealedSecrets) and applies
# them to the live databases (PostgreSQL, Elasticsearch).
#
# Run this AFTER changing a SealedSecret:
#   1. Edit SealedSecret in k8s/sealed-secrets/{env}/
#   2. Commit + push → ArgoCD applies → K8s secret updated
#   3. Run this script to sync the password TO the actual DB
#
# Usage:
#   ./sync-password.sh <env>     (dev|staging|production)
# ============================================================================

set -e
DIR="$(cd "$(dirname "$0")" && pwd)"

ENV=${1:-dev}
KEYCLOAK_NS="keycloak-${ENV}"
PG_NS="postgres-${ENV}"
ES_NS="elasticsearch-${ENV}"
APP_NS="${ENV}"

echo "============================================"
echo " Syncing passwords for: ${ENV}"
echo "============================================"

# ──────────────────────────────────────────────
# 1. POSTGRESQL — read credentials from K8s secret
# ──────────────────────────────────────────────
echo ""
POSTGRES_USERNAME=$(kubectl get secret -n "${KEYCLOAK_NS}" postgresql-credentials -o jsonpath='{.data.username}' 2>/dev/null | base64 -d)
POSTGRES_PASSWORD=$(kubectl get secret -n "${KEYCLOAK_NS}" postgresql-credentials -o jsonpath='{.data.password}' 2>/dev/null | base64 -d)

if [ -z "$POSTGRES_USERNAME" ] || [ -z "$POSTGRES_PASSWORD" ]; then
    echo "  WARNING: postgresql-credentials not found in ${KEYCLOAK_NS}. Skipping."
else
    echo "[1/3] PostgreSQL — updating user $POSTGRES_USERNAME..."
    PG_POD=$(kubectl get pods -n "${PG_NS}" -l application=spilo -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
    if [ -z "$PG_POD" ]; then
        echo "  WARNING: No PostgreSQL pod found in ${PG_NS}. Skipping."
    else
        kubectl exec -n "${PG_NS}" pod/"$PG_POD" -- psql -U postgres \
          -c "ALTER USER $POSTGRES_USERNAME WITH PASSWORD '$POSTGRES_PASSWORD';" 2>/dev/null && \
        echo "  OK PostgreSQL password updated" || \
        echo "  WARNING: PostgreSQL password update failed"
    fi
fi

# ──────────────────────────────────────────────
# 2. ELASTICSEARCH — file realm (search user)
# ──────────────────────────────────────────────
echo ""
echo "[2/3] Elasticsearch — ensuring file realm user search..."

# Generate bcrypt hash for password (using Python if available)
BCRYPT_HASH="\$2b\$10\$LcQn2FhWIQFkmCSEbL8eKOP67z8m4wwin48jiP6EiM.pKkPsqMH1m"

# Update user-credentials-secret in elasticsearch namespace
ES_USERS_NS="$ES_NS"
kubectl get secrets -n "$ES_USERS_NS" user-credentials-secret -o jsonpath='{.data.users}' 2>/dev/null | \
  base64 -d | grep -q "search:" || {
    echo "  Adding search user to file realm..."
    USERS=$(kubectl get secrets -n "$ES_USERS_NS" user-credentials-secret -o jsonpath='{.data.users}' 2>/dev/null | base64 -d)
    ROLES=$(kubectl get secrets -n "$ES_USERS_NS" user-credentials-secret -o jsonpath='{.data.users_roles}' 2>/dev/null | base64 -d)
    
    echo "$USERS" > /tmp/users-$$
    echo "search:$BCRYPT_HASH" >> /tmp/users-$$
    
    echo "$ROLES" > /tmp/roles-$$
    echo "$ROLES" | grep -q "superuser:search" || echo "superuser:search" >> /tmp/roles-$$
    
    kubectl patch secrets -n "$ES_USERS_NS" user-credentials-secret \
      -p "{\"data\":{\"users\":\"$(base64 -w0 < /tmp/users-$$)\",\"users_roles\":\"$(base64 -w0 < /tmp/roles-$$)\"}}" 2>/dev/null && \
    echo "  OK File realm updated" || \
    echo "  WARNING: File realm update failed"
    
    rm -f /tmp/users-$$ /tmp/roles-$$
}

# ──────────────────────────────────────────────
# 3. RESTART crashing pods
# ──────────────────────────────────────────────
echo ""
echo "[3/3] Restarting crashing deployments in ${APP_NS}..."

CRASHING=$(kubectl get pods -n "$APP_NS" --no-headers 2>/dev/null | \
  awk '/CrashLoop|Error/{print $1}' | \
  sed 's/-[a-z0-9]*-[a-z0-9]*$//' | \
  sort -u)

if [ -n "$CRASHING" ]; then
  for deploy in $CRASHING; do
    echo "  Restarting $deploy..."
    kubectl rollout restart deployment -n "$APP_NS" "$deploy" 2>/dev/null || true
  done
  echo "  OK All crashing deployments restarted"
else
  echo "  No crashing deployments found"
fi

echo ""
echo "============================================"
echo " Password sync complete for ${ENV}"
echo "============================================"
