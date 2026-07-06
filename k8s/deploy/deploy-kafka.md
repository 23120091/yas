# Clone/pull repo latest
cd k8s/deploy

# CHỈ deploy Kafka (ko chạy full setup-cluster.sh vì nó xóa PostgreSQL)

```bash
ENV=staging  # hoặc production
KAFKA_NS="kafka-${ENV}"

helm upgrade --install "kafka-cluster-${ENV}" ./kafka/kafka-cluster \
  --create-namespace --namespace "${KAFKA_NS}" \
  --set kafka.replicas=1 \
  --set postgresql.username="yasadminuser" \
  --set postgresql.password="" \
  --set postgresql.namespace="postgres-${ENV}" \
  --values "./infra-${ENV}-affinity.yaml"
```

Sau deploy:
1. Verify pod chạy: kubectl get pods -n kafka-${ENV}
2. Register CDC connector như bên dev (thay PostgreSQL password thực từ secret yasadminuser.postgresql.credentials.postgresql.acid.zalan.do trong namespace postgres-${ENV})
setup-cluster.sh vẫn dùng được nhưng sẽ xóa PostgreSQL, nên chỉ chạy phần Kafka.
Cần helm chạy ở đâu? Trên máy này không có helm.