#!/bin/bash
# ============================================================================
# CHECK CONNECTIONS — Verify credentials across all environments
# ============================================================================
# Reads credentials from K8s secrets and tests live connections to:
#   - PostgreSQL (using actual credentials from secrets)
#   - Redis (password auth)
#   - Keycloak (realm reachable)
#   - Elasticsearch (cluster health)
#
# Usage:
#   ./check-connections.sh           # Check all environments
#   ./check-connections.sh dev       # Check single environment
# ============================================================================

set -e
DIR="$(cd "$(dirname "$0")" && pwd)"

ENV=${1:-all}

if [ "$ENV" = "all" ]; then
  ENVS="dev staging production"
else
  ENVS="$ENV"
fi

echo "============================================"
echo " CHECKING CONNECTIONS"
echo "============================================"

for e in $ENVS; do
  echo ""
  echo "========== ${e} =========="
  
  PG_NS="postgres-${e}"
  REDIS_NS="redis-${e}"
  KEYCLOAK_NS="keycloak-${e}"
  ES_NS="elasticsearch-${e}"
  APP_NS="${e}"

  # ──────────────────────────────────────────────
  # 1. PostgreSQL
  # ──────────────────────────────────────────────
  echo ""
  echo "  [1] PostgreSQL..."

  PG_POD=$(kubectl get pods -n "${PG_NS}" -l application=spilo -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)
  if [ -z "$PG_POD" ]; then
    echo "       Pod:     NOT FOUND"
  else
    PG_HOST=$(kubectl get pod -n "${PG_NS}" "$PG_POD" -o jsonpath='{.status.podIP}' 2>/dev/null || echo "?")
    echo "       Pod:     $PG_POD ($PG_HOST)"

    # Check app credentials (yas-credentials-secret)
    APP_USER=$(kubectl get secret -n "${APP_NS}" yas-credentials-secret -o jsonpath='{.data.POSTGRESQL_USERNAME}' 2>/dev/null | base64 -d 2>/dev/null || echo "")
    APP_PASS=$(kubectl get secret -n "${APP_NS}" yas-credentials-secret -o jsonpath='{.data.POSTGRESQL_PASSWORD}' 2>/dev/null | base64 -d 2>/dev/null || echo "")
    if [ -n "$APP_USER" ] && [ -n "$APP_PASS" ]; then
      RESULT=$(kubectl exec -n "${PG_NS}" "$PG_POD" -- psql -U postgres -c "SELECT 1 FROM pg_roles WHERE rolname='$APP_USER'" 2>/dev/null | grep -c "1" || true)
      if [ "$RESULT" -ge 1 ]; then
        # Actually test password login
        AUTH=$(kubectl exec -n "${PG_NS}" "$PG_POD" -- env PGPASSWORD="$APP_PASS" psql -U "$APP_USER" -d postgres -c "SELECT 1" 2>/dev/null | grep -c "1" || true)
        if [ "$AUTH" -ge 1 ]; then
          echo "       App DB user '$APP_USER':  OK (password verified)"
        else
          echo "       App DB user '$APP_USER':  PASSWORD MISMATCH"
        fi
      else
        echo "       App DB user '$APP_USER':  MISSING ROLE"
      fi
    else
      echo "       App DB user:  NO CREDENTIALS SECRET"
    fi

    # Check Keycloak credentials (postgresql-credentials)
    KC_USER=$(kubectl get secret -n "${KEYCLOAK_NS}" postgresql-credentials -o jsonpath='{.data.username}' 2>/dev/null | base64 -d 2>/dev/null || echo "")
    KC_PASS=$(kubectl get secret -n "${KEYCLOAK_NS}" postgresql-credentials -o jsonpath='{.data.password}' 2>/dev/null | base64 -d 2>/dev/null || echo "")
    if [ -n "$KC_USER" ] && [ -n "$KC_PASS" ]; then
      RESULT=$(kubectl exec -n "${PG_NS}" "$PG_POD" -- psql -U postgres -c "SELECT 1 FROM pg_roles WHERE rolname='$KC_USER'" 2>/dev/null | grep -c "1" || true)
      if [ "$RESULT" -ge 1 ]; then
        echo "       Keycloak DB user '$KC_USER': OK"
      else
        echo "       Keycloak DB user '$KC_USER': MISSING ROLE"
      fi
    else
      echo "       Keycloak DB user: NO CREDENTIALS SECRET"
    fi

    # Check database exists
    for db in keycloak product media customer cart order payment promotion rating search inventory location tax; do
      EXISTS=$(kubectl exec -n "${PG_NS}" "$PG_POD" -- psql -U postgres -t -c "SELECT 1 FROM pg_database WHERE datname='$db'" 2>/dev/null | tr -d ' ' || echo "")
      if [ "$EXISTS" = "1" ]; then
        echo "       DB '$db':              OK"
      fi
    done
  fi

  # ──────────────────────────────────────────────
  # 2. Redis
  # ──────────────────────────────────────────────
  echo ""
  echo "  [2] Redis..."

  REDIS_POD=$(kubectl get pods -n "${REDIS_NS}" -l app.kubernetes.io/name=redis -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)
  if [ -z "$REDIS_POD" ]; then
    echo "       Pod:     NOT FOUND"
  else
    REDIS_HOST=$(kubectl get pod -n "${REDIS_NS}" "$REDIS_POD" -o jsonpath='{.status.podIP}' 2>/dev/null || echo "?")
    echo "       Pod:     $REDIS_POD ($REDIS_HOST)"
    REDIS_SECRET=$(kubectl get secrets -n "${REDIS_NS}" --no-headers -o name 2>/dev/null | grep -E "^secret/redis-" | head -1 | cut -d/ -f2 || echo "")
    REDIS_PASS=""
    if [ -n "$REDIS_SECRET" ]; then
      REDIS_PASS=$(kubectl get secret -n "${REDIS_NS}" "$REDIS_SECRET" -o jsonpath='{.data.redis-password}' 2>/dev/null | base64 -d 2>/dev/null || echo "")
    fi
    if [ -n "$REDIS_PASS" ]; then
      RESULT=$(kubectl exec -n "${REDIS_NS}" "$REDIS_POD" -- redis-cli -a "$REDIS_PASS" ping 2>/dev/null || echo "FAIL")
      if [ "$RESULT" = "PONG" ]; then
        echo "       Auth:    OK (PONG)"
      else
        echo "       Auth:    FAIL ($RESULT)"
      fi
    else
      echo "       Auth:    NO PASSWORD SECRET"
    fi
  fi

  # ──────────────────────────────────────────────
  # 3. Keycloak
  # ──────────────────────────────────────────────
  echo ""
  echo "  [3] Keycloak..."

    KC_POD=$(kubectl get pods -n "${KEYCLOAK_NS}" -l app=keycloak -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)
    if [ -z "$KC_POD" ]; then
      echo "       Pod:     NOT FOUND"
    else
      KC_READY=$(kubectl get pod -n "${KEYCLOAK_NS}" "$KC_POD" -o jsonpath='{.status.containerStatuses[0].ready}' 2>/dev/null || echo "false")
      echo "       Pod:     $KC_POD (ready=$KC_READY)"
      if [ "$KC_READY" = "true" ]; then
        # Try to reach the realm endpoint via the service DNS
        REALM_CODE=$(kubectl run -n "${KEYCLOAK_NS}" kc-check --image=curlimages/curl:latest --rm -i --restart=Never \
          -- curl -s -o /dev/null -w "%{http_code}" "http://keycloak-service.${KEYCLOAK_NS}:80/realms/Yas/" 2>/dev/null || echo "000")
        echo "       Realm:   HTTP $REALM_CODE"
        # Test admin credentials
        ADMIN_USER=$(kubectl get secret -n "${KEYCLOAK_NS}" keycloak-credentials -o jsonpath='{.data.username}' 2>/dev/null | base64 -d 2>/dev/null || echo "admin")
        ADMIN_PASS=$(kubectl get secret -n "${KEYCLOAK_NS}" keycloak-credentials -o jsonpath='{.data.password}' 2>/dev/null | base64 -d 2>/dev/null || echo "admin")
        KC_HOST="keycloak-service.${KEYCLOAK_NS}:80"
        TOKEN=$(kubectl run -n "${KEYCLOAK_NS}" kc-token --image=curlimages/curl:latest --rm -i --restart=Never \
          -- curl -s -X POST "http://${KC_HOST}/realms/master/protocol/openid-connect/token" \
          -d "client_id=admin-cli" -d "username=$ADMIN_USER" -d "password=$ADMIN_PASS" -d "grant_type=password" 2>/dev/null || echo "")
        if echo "$TOKEN" | grep -q "access_token"; then
          echo "       Admin:   OK (token received)"
        else
          echo "       Admin:   FAIL (bad credentials?)"
        fi
      fi
    fi

  # ──────────────────────────────────────────────
  # 4. Elasticsearch
  # ──────────────────────────────────────────────
  echo ""
  echo "  [4] Elasticsearch..."

  ES_POD=$(kubectl get pods -n "${ES_NS}" -l common.k8s.elastic.co/type=elasticsearch --no-headers -o name 2>/dev/null | head -1 | cut -d/ -f2 || true)
  if [ -z "$ES_POD" ]; then
    echo "       Pod:     NOT FOUND"
  else
    ES_HOST=$(kubectl get pod -n "${ES_NS}" "$ES_POD" -o jsonpath='{.status.podIP}' 2>/dev/null || echo "?")
    echo "       Pod:     $ES_POD ($ES_HOST)"
    ES_USER=$(kubectl get secret -n "${ES_NS}" elasticsearch-es-elastic-user -o jsonpath='{.data.elastic}' 2>/dev/null | base64 -d 2>/dev/null || echo "")
    if [ -n "$ES_USER" ]; then
      HEALTH=$(kubectl exec -n "${ES_NS}" "$ES_POD" -- curl -s -u "elastic:$ES_USER" "http://localhost:9200/_cluster/health" 2>/dev/null || echo "")
      STATUS=$(echo "$HEALTH" | grep -o '"status":"[^"]*"' | cut -d'"' -f4 || echo "unknown")
      echo "       Health:  $STATUS"
    else
      echo "       Auth:    NO ELASTIC USER"
    fi
  fi

  echo ""
  echo "  --- ${e} done ---"
done

echo ""
echo "============================================"
echo " CHECK COMPLETE"
echo "============================================"
