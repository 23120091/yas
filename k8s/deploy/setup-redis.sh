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
DIR="$(cd "$(dirname "$0")" && pwd)"

# --------------------------------------------------------------------------
# Load common passwords from .env
# --------------------------------------------------------------------------
source "$DIR/.env"

# --------------------------------------------------------------------------
# Environment selection
# --------------------------------------------------------------------------
ENV=${1:-dev}

REDIS_NS="redis-${ENV}"

echo "============================================"
echo " Deploying Redis for: ${ENV}"
echo "============================================"
echo "Redis namespace: ${REDIS_NS}"

# --------------------------------------------------------------------------
# Install Redis (Bitnami chart) — password from .env
# --------------------------------------------------------------------------
# Bitnami chart uses master.nodeSelector / replica.nodeSelector,
# not root-level .Values.affinity. We pass nodeSelector directly.
helm upgrade --install "redis-${ENV}" \
  --set auth.password="$REDIS_PASSWORD" \
  --set master.nodeSelector.env="${ENV}" \
  --set replica.nodeSelector.env="${ENV}" \
  --set sentinel.nodeSelector.env="${ENV}" \
  oci://registry-1.docker.io/bitnamicharts/redis \
  --namespace "${REDIS_NS}" --create-namespace

echo ""
echo "============================================"
echo " Redis deployed for: ${ENV}"
echo " Namespace: ${REDIS_NS}"
echo " Service:   redis-master.${REDIS_NS}:6379"
echo "============================================"
