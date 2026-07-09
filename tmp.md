Plan (repeat per env)
Step 1 — Deploy image -10 (health fix)
# For dev
kubectl patch kafkaconnect debezium-connect-cluster -n kafka-dev --type=merge \
  -p '{"spec":{"image":"thanhthong2005/debezium-connect-postgresql:2.7.3.Final-10"}}'
kubectl delete pod -n kafka-dev -l strimzi.io/kind=KafkaConnect

# For staging
kubectl patch kafkaconnect debezium-connect-cluster -n kafka-staging --type=merge \
  -p '{"spec":{"image":"thanhthong2005/debezium-connect-postgresql:2.7.3.Final-10"}}'
kubectl delete pod -n kafka-staging -l strimzi.io/kind=KafkaConnect

# For production
kubectl patch kafkaconnect debezium-connect-cluster -n kafka-production --type=merge \
  -p '{"spec":{"image":"thanhthong2005/debezium-connect-postgresql:2.7.3.Final-10"}}'
kubectl delete pod -n kafka-production -l strimzi.io/kind=KafkaConnect
Step 2 — Fix PostgreSQL credentials per env
Each env has its own PostgreSQL with a different generated password. The SealedSecret k8s/sealed-secrets/kafka-{env}/postgresql-credentials.yaml currently has admin/admin (wrong). For each env:
# Get actual password
POSTGRES_NS=postgres-{env}
ACTUAL_PW=$(kubectl get secret yasadminuser.postgresql.credentials.postgresql.acid.zalan.do \
  -n $POSTGRES_NS -o jsonpath='{.data.password}' | base64 -d)

# Re-seal with correct password
kubectl create secret generic postgresql.credentials -n kafka-{env} --dry-run=client -o yaml \
  --from-literal=username=yasadminuser \
  --from-literal=password=$ACTUAL_PW \
  | kubeseal --format=yaml > k8s/sealed-secrets/kafka-{env}/postgresql-credentials.yaml

# Apply
kubectl apply -f k8s/sealed-secrets/kafka-{env}/postgresql-credentials.yaml

# Restart Connect to re-resolve credentials
kubectl delete pod -n kafka-{env} -l strimzi.io/kind=KafkaConnect
Step 3 — Verify
# Pod should show 1/1 Ready (health endpoint working)
kubectl get pod -n kafka-{env} -l strimzi.io/kind=KafkaConnect

# Connector should auto-start via Strimzi operator
kubectl get kafkaconnector -n kafka-{env}

# Check Elasticsearch document count
kubectl exec elasticsearch-es-node-0 -n elasticsearch-{env} -- curl -s -u elastic:<pw> \
  http://localhost:9200/product/_count
Step 4 — Commit the updated SealedSecret YAMLs
git add k8s/sealed-secrets/kafka-{env}/postgresql-credentials.yaml
git commit -m "fix: update postgresql credentials sealed secret for {env}"
git push
Want me to run the dev steps first?