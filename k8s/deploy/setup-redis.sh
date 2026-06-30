#!/bin/bash
# ============================================================================
# SETUP REDIS — Multi-Environment
# ============================================================================
# Deploys Redis for session storage with per-env namespace isolation.
#
# Usage:
#   ./setup-redis.sh <env>     (dev|staging|production, default: dev)
#
# Each environment gets its own Redis instance in redis-{env} namespace.
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
echo " Deploying Redis for: ${ENV}"
echo "============================================"

# --------------------------------------------------------------------------
# Read configuration
# --------------------------------------------------------------------------
read -rd '' REDIS_PASSWORD < <(yq -r '.redis.password' "$CONFIG_FILE")

REDIS_NS="redis-${ENV}"

echo "Redis namespace: ${REDIS_NS}"

# --------------------------------------------------------------------------
# Install Redis (Bitnami chart)
# --------------------------------------------------------------------------
helm upgrade --install "redis-${ENV}" \
  --set auth.password="$REDIS_PASSWORD" \
  --values "./infra-${ENV}-affinity.yaml" \
  oci://registry-1.docker.io/bitnamicharts/redis \
  --namespace "${REDIS_NS}" --create-namespace

echo ""
echo "============================================"
echo " Redis deployed for: ${ENV}"
echo " Namespace: ${REDIS_NS}"
echo " Service:   redis-master.${REDIS_NS}:6379"
echo "============================================"
