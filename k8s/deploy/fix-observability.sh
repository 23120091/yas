#!/bin/bash
# Fix observability pod placement — redeploy Loki, Tempo, OTel with correct affinity
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
source "$DIR/.env"

ENVS="dev staging production"

for ENV in $ENVS; do
  OBS_NS="observability-${ENV}"

  echo "=== Fixing ${ENV} ==="

  # 1. Uninstall old releases
  helm uninstall "loki-${ENV}" -n "${OBS_NS}" 2>/dev/null || true
  helm uninstall "tempo-${ENV}" -n "${OBS_NS}" 2>/dev/null || true
  helm uninstall "opentelemetry-collector-${ENV}" -n "${OBS_NS}" 2>/dev/null || true

  # 2. Force-delete PVCs (data on wrong nodes, need to recreate on correct ones)
  echo "  Cleaning PVCs..."
  kubectl patch pvc -n "${OBS_NS}" --all --type=json -p='[{"op": "remove", "path": "/metadata/finalizers"}]' 2>/dev/null || true
  kubectl delete pvc -n "${OBS_NS}" --all --ignore-not-found --timeout=60s 2>/dev/null || true
  sleep 3

  sleep 5

  # 3. Redeploy Loki with per-component affinity
  echo "  Deploying Loki..."
  AFFINITY="{\"nodeAffinity\":{\"requiredDuringSchedulingIgnoredDuringExecution\":{\"nodeSelectorTerms\":[{\"matchExpressions\":[{\"key\":\"env\",\"operator\":\"In\",\"values\":[\"${ENV}\"]}]}]}}}"
  helm upgrade --install "loki-${ENV}" grafana/loki \
    --create-namespace --namespace "${OBS_NS}" \
    -f ./observability/loki.values.yaml \
    --set-json "write.affinity=${AFFINITY}" \
    --set-json "read.affinity=${AFFINITY}" \
    --set-json "backend.affinity=${AFFINITY}" \
    --set-json "gateway.affinity=${AFFINITY}" \
    --set-json "minio.affinity=${AFFINITY}" \
    --set-json "chunksCache.affinity=${AFFINITY}" \
    --set-json "resultsCache.affinity=${AFFINITY}"

  # 4. Redeploy Tempo (single binary — chart ignores affinity, patch STS after)
  echo "  Deploying Tempo..."
  helm upgrade --install "tempo-${ENV}" grafana/tempo \
    --create-namespace --namespace "${OBS_NS}" \
    -f ./observability/tempo.values.yaml \
    -f "./observability/tempo-${ENV}.values.yaml"
  # Tempo chart ignores --set-json for affinity in single-binary mode, patch STS
  kubectl patch sts "tempo-${ENV}" -n "${OBS_NS}" \
    -p "{\"spec\":{\"template\":{\"spec\":{\"affinity\":{\"nodeAffinity\":{\"requiredDuringSchedulingIgnoredDuringExecution\":{\"nodeSelectorTerms\":[{\"matchExpressions\":[{\"key\":\"env\",\"operator\":\"In\",\"values\":[\"${ENV}\"]}]}]}}}}}}}" \
    2>/dev/null || true

  # 5. Redeploy OTel Collector
  echo "  Deploying OTel Collector..."
  helm upgrade --install "opentelemetry-collector-${ENV}" ./observability/opentelemetry \
    --create-namespace --namespace "${OBS_NS}" \
    --set "lokiEndpoint=http://loki-${ENV}-gateway.${OBS_NS}.svc.cluster.local/loki/api/v1/push" \
    --set "tempoEndpoint=http://tempo-${ENV}.${OBS_NS}.svc.cluster.local:4318" \
    --values "./infra-${ENV}-affinity.yaml"

  echo "  ${ENV} done."
done

echo ""
echo "Done. Verify: kubectl get pods -n observability-dev -o wide"
