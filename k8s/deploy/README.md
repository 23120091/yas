# YAS K8S Deployment

## Resource cluster installation reference
- **Postgresql:** https://github.com/zalando/postgres-operator
- **Elasticsearch:** https://github.com/elastic/cloud-on-k8s
- **Kafka:** https://github.com/strimzi/strimzi-kafka-operator
- **Debezium Connect:** https://debezium.io/documentation/reference/stable/operations/kubernetes.html
- **Keycloak:** https://www.keycloak.org/operator/installation
- **Redis:** https://artifacthub.io/packages/helm/bitnami/redis
- **Reloader:** https://github.com/stakater/Reloader
- **Loki:** https://github.com/grafana/loki/tree/main/production/helm/loki
- **Tempo:** https://github.com/grafana/helm-charts/tree/main/charts/tempo
- **Promtail:** https://github.com/grafana/helm-charts/tree/main/charts/promtail
- **Opentelemetry:** https://github.com/open-telemetry/opentelemetry-operator

> **Grafana** and **Prometheus** are NOT deployed — the project does not use them.
> The observability stack is: Promtail → OpenTelemetry Collector → Loki (logs) + Tempo (traces).

## Multi-Environment Architecture

Each environment (`dev`, `staging`, `production`) gets **isolated infrastructure** in its own namespaces:

| Resource | Namespace Pattern | Example (dev) |
|---|---|---|
| PostgreSQL | `postgres-{env}` | `postgres-dev` |
| Kafka + AKHQ | `kafka-{env}` | `kafka-dev` |
| Elasticsearch + Kibana | `elasticsearch-{env}` | `elasticsearch-dev` |
| Keycloak | `keycloak-{env}` | `keycloak-dev` |
| Redis | `redis-{env}` | `redis-dev` |
| Observability (Loki, Tempo, Promtail, OTel) | `observability-{env}` | `observability-dev` |
| Zookeeper | `zookeeper-{env}` | `zookeeper-dev` |
| YAS Applications | `yas-{env}` | `yas-dev` |

Operators (postgres-operator, strimzi, eck, cert-manager, opentelemetry-operator, keycloak CRDs) are **cluster-scoped** and installed once. The scripts handle this via idempotent `helm upgrade --install` — running for multiple envs is safe.

## Configuration

Per-environment config files:
- `cluster-config-dev.yaml`
- `cluster-config-staging.yaml`
- `cluster-config-production.yaml`

Each defines replica counts, volume sizes, passwords, and domain/redirect URLs for that environment.

## Local installation steps

- Require a minikube node minimum 16G memory and 40G disk space and run on Ubuntu operator
```shell
minikube start --disk-size='40000mb' --memory='16g'
```
- Enable ingress addon
```shell
minikube addons enable ingress
```
- Install helm
  https://helm.sh/
- Install yq (the tool read, update yaml file)
  https://github.com/mikefarah/yq
- Goto `k8s/deploy` folder

### Deploy DEV environment (default)

```shell
./setup-keycloak.sh dev
./setup-redis.sh dev
./setup-cluster.sh dev
```

- Verify all servers running in namespaces: `postgres-dev`, `elasticsearch-dev`, `kafka-dev`, `keycloak-dev`, `redis-dev`
- Deploy YAS configuration and applications:

```shell
./deploy-yas-configuration.sh dev
./deploy-yas-applications.sh dev
```

### Deploy STAGING environment

```shell
./setup-keycloak.sh staging
./setup-redis.sh staging
./setup-cluster.sh staging
./deploy-yas-configuration.sh staging
./deploy-yas-applications.sh staging
```

### Deploy PRODUCTION environment

```shell
./setup-keycloak.sh production
./setup-redis.sh production
./setup-cluster.sh production
./deploy-yas-configuration.sh production
./deploy-yas-applications.sh production
```

## Hosts file

Add to `/etc/hosts` for each environment. Example for dev:

```
192.168.49.2 pgadmin.dev.yas.local.com
192.168.49.2 akhq.dev.yas.local.com
192.168.49.2 kibana.dev.yas.local.com
192.168.49.2 identity.dev.yas.local.com
192.168.49.2 backoffice.dev.yas.local.com
192.168.49.2 storefront.dev.yas.local.com
```

Replace `dev` with `staging` for staging. Production uses clean URLs (no subdomain prefix):

```
192.168.49.2 pgadmin.yas.local.com
192.168.49.2 akhq.yas.local.com
192.168.49.2 kibana.yas.local.com
192.168.49.2 identity.yas.local.com
192.168.49.2 backoffice.yas.local.com
192.168.49.2 storefront.yas.local.com
```

`192.168.49.2` is the IP of the minikube node. Get it with:
```shell
minikube ip
```

## Keycloak bootstrap admin credentials

The username and password of Keycloak admin user are stored in the `keycloak-credentials` secret in the `keycloak-{env}` namespace:

```shell
kubectl get secret keycloak-credentials -n keycloak-dev -o jsonpath="{.data.password}" | base64 --decode
```

Bootstrap admin is a temporary admin user. To harden security, create a permanent admin account and delete the temporary one.

## YAS Helm Charts

All charts of YAS application are located in the `charts` folder.

To install the YAS helm charts access to [https://nashtech-garage.github.io/yas/](https://nashtech-garage.github.io/yas/)

## Observability

The YAS observability follows the Open Telemetry standard.
Promtail collects logs from all applications and sends to Open Telemetry Collector, which distributes to Loki server.
YAS applications also send metric data to Open Telemetry Collector, which sends metric data to Tempo server.

View details configuration of Open Telemetry Collector at [opentelemetry](./observability/opentelemetry/values.yaml)

### How to view log on the Grafana

> NOTE: Grafana is NOT deployed by this project. Use Loki's HTTP API or an external Grafana instance.

On the left menu select `Explore` -> select `Loki` datasource -> select Label filters:
- namespace
- container (Application)

On Loki also supports track by traceId. On Tempo you can select the Node graph to view the tracing of request.
