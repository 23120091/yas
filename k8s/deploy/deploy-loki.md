```bash
helm upgrade --install "loki-dev" grafana/loki \
  --create-namespace --namespace "observability-dev" \
  -f k8s/deploy/observability/loki.values.yaml \
  --set loki.useTestSchema=true \
  --values "k8s/deploy/infra-dev-affinity.yaml"
```