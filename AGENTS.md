# AGENTS.md — YAS (Yet Another Shop)

## Architecture

- **Monorepo**: root `pom.xml` declares 20 Maven modules — 18 Java Spring Boot 4.0 services + `common-library/` (shared entities, Kafka CDC helpers, CSV, mappers) + `sampledata/` (seed data).
- **BFF modules** (`storefront-bff/`, `backoffice-bff/`) are Spring Cloud Gateway apps that proxy to backend microservices and handle OAuth2 token relay.
- **2 standalone Next.js 14 frontends** — `storefront/` and `backoffice/`.
- Authentication: **Keycloak** (realm export at `identity/realm-export.json`).
- Event-driven: **Kafka** + Debezium CDC source connectors (`kafka/connects/`).
- Search: **Elasticsearch** (separate `docker-compose.search.yml`).
- Observability: **OpenTelemetry** Java agent → **Grafana/Loki/Tempo/Prometheus** (separate `docker-compose.o11y.yml`).
- Modules `webhook`, `recommendation`, and `delivery` exist in `pom.xml` but are **commented out** in `docker-compose.yml`.

## Developer Commands

### Java services (Maven wrapper required — no global `mvn`)

```bash
# Build + test a single service and its dependencies (parallel)
./mvnw clean verify -pl <service> -am -T 1C

# Unit tests only (Maven surefire)
./mvnw test -pl <service> -am

# Integration tests only (Maven failsafe, *IT.java convention)
./mvnw failsafe:integration-test failsafe:verify -pl <service> -am

# Checkstyle (violations are warnings only, do NOT fail the build)
./mvnw compile checkstyle:checkstyle -pl <service> -am -DskipTests
```

### Containerized Java (no local JDK 25)

```bash
docker compose -f docker-compose.test.yml up -d java-env
docker exec -it yas-java-25 bash
./mvnw test -pl tax -am
```

### Frontend (Next.js)

```bash
# In storefront/ or backoffice/
npm run dev        # dev server
npm test -- --coverage
npx prettier -w .  # format before committing
```

### Docker Compose

```bash
direnv allow                           # load .env variables
docker compose up                      # all services (needs 16GB+ RAM)
docker compose -f docker-compose.yml up  # core services only
./start-source-connectors.sh           # register Debezium CDC connectors
```

**Prerequisite:** Add these to your hosts file:
```
127.0.0.1 identity
127.0.0.1 api.yas.local
127.0.0.1 storefront
127.0.0.1 backoffice
```

## Running a Backend Service Locally (outside Docker)

1. `docker compose up` to start infrastructure (postgres, kafka, keycloak, etc.).
2. Run the target Java service in your IDE (default port, e.g. product on 8092).
3. In the BFF's `application.yaml`, add a route pointing to your local service as the **first** gateway route (see `docs/developer-guidelines.md`).
4. Run the matching frontend via `npm run dev` in `storefront/` or `backoffice/`.

## Testing Conventions

- **Unit tests**: `src/test/java/`, run by **maven-surefire**.
- **Integration tests**: `src/it/java/`, naming `**/*IT.java`, run by **maven-failsafe**. Use **Testcontainers** (PostgreSQL, Keycloak). Test Keycloak realm: `test-realm.json` per module.
- **Checkstyle**: bound to `validate` phase in POM; `failOnViolation=false` — violations are **warnings only** (never fail the build).
- **JaCoCo** coverage report runs in `verify` phase. CI enforces **70% minimum**.
- Coverage excludes: `*Application.class`, `config/**`, `exception/**`, `constants/**`.
- Test libraries in use: **Instancio** (random test data), **RestAssured** (API assertions).

## Code Conventions

- **Google Java Style Guide** enforced via `/checkstyle/checkstyle.xml`. JavaDoc checks suppressed.
- **Lombok** + **MapStruct** as annotation processors (configured in parent POM).
- **Liquibase** for DB migrations: DDL in `db/changelog/ddl/`, data in `db/changelog/data/`. **Never modify an already-run changeset** — always add a new one.
- **Entities**: primitives for non-nullable fields, wrappers for nullable. Always override `equals` and `hashCode`.
- **No soft-delete**, **no cascade-delete** (except many-to-many). Show a user-facing message on DB constraint violations.
- Controllers can call repositories directly for simple CRUD. Business logic in service classes.

## CI

- Path-filtered: **only changed services** build (see `ci.yml` filter-changes job). Triggers on push/PR to `main` or `dev`.
- **Java CI**: Gitleaks → Snyk SCA → `mvn clean verify -pl <svc> -am -T 1C` → Checkstyle → SonarQube → JaCoCo → Docker build & push (main only).
- **Node CI**: ESLint → Gitleaks → Snyk SCA → `npm test -- --coverage` → `npm run build` → Docker build & push (main only).
- Docker images pushed to **ghcr.io** only on `main` branch.
- Reusable workflows: `shared-java-ci.yml`, `shared-node-ci.yml` in `.github/workflows/`.

## Environment & Config

- Copy `.env.example` to `.env` (gitignored). Sets service URLs, OTEL config, Kafka addresses.
- `COMPOSE_FILE` in `.env` controls which compose files are merged (`docker-compose.yml:docker-compose.search.yml:docker-compose.o11y.yml`).
- Keycloak admin: `admin/admin`. PostgreSQL: `admin/admin`. PGAdmin: `admin@yas.com/admin`.
- Backoffice login: `admin/password`.
- Swagger UI at `http://api.yas.local/swagger-ui/` when containers are running.

## Known Gotchas

- On first `docker compose up`, the storefront/backoffice containers may need a restart (Ctrl+C then up again).
- If limited to 16GB RAM, set `COMPOSE_FILE=docker-compose.yml` and `OTEL_JAVAAGENT_ENABLED=false` in `.env`, and comment out unused services from `docker-compose.yml`.
- Search service running locally outside Docker: change `spring.kafka.consumer.bootstrap-servers` from `kafka:9092` to `localhost:29092`.
- BFFs use `TokenRelay=` filter for OAuth2 token propagation; do not remove it.

## K8s Deployment Gotchas

### `yas-configuration` IS managed by ArgoCD (sync-wave: -1)

The `yas-configuration` Helm chart (shared ConfigMaps + Secrets) is deployed by the `yas-configuration` ApplicationSet, with **sync-wave: -1** so it deploys BEFORE all application services.

**How it works:**
1. Edit `k8s/charts/yas-configuration/values.yaml`
2. Push to git
3. ArgoCD auto-syncs ConfigMaps in all environments
4. **Stakater Reloader** auto-restarts pods that reference updated ConfigMaps

**No manual deploy needed.** The old `./deploy-yas-configuration.sh` script is kept for emergency use only (e.g., if ArgoCD is down).

### Infrastructure NOT managed by ArgoCD (run scripts once per env)

These are deployed via scripts because they use custom CRDs and need one-time setup:

```bash
./setup-cluster.sh <env>     # PostgreSQL, Kafka, ES, Loki, Tempo, Promtail, OTel, Zookeeper
./setup-redis.sh <env>       # Redis
./setup-keycloak.sh <env>    # Keycloak
```

After initial setup, infrastructure rarely changes. Day-to-day changes only need `git push`.

### Spring Cloud Gateway property path (Boot 4 / Gateway 5)

BFF POM uses `spring-cloud-starter-gateway-server-webflux` (new path). Gateway routes must be under:

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: ...
```

The old path `spring.cloud.gateway.routes` is **silently ignored** — routes will not load and every request returns 404.

**Where this lives:**
- Values: `k8s/charts/yas-configuration/values.yaml` → key `gatewayRoutesConfig`
- Template: `k8s/charts/yas-configuration/templates/yas-configurations.configmap.yaml` wraps the value in the correct property path

### BFF extra configs are for auth/redis only

`backofficeBffExtraConfig` and `storefrontBffExtraConfig` should contain **only** OAuth2 client registration and Redis connection. Do **not** put gateway routes here — they go in `gatewayRoutesConfig` which renders to a separate ConfigMap mounted at `/opt/yas/gateway-routes-config`.

### Host predicate wildcard

Spring Cloud Gateway `Host` predicate uses single `*` (matches characters within one dot-separated segment). `**` is a path-pattern wildcard (multi-segment, `/` separator) and does **not** work in `Host` predicates.

```yaml
# Correct
- Host=storefront*.tthong.dev

# Wrong — route will never match
- Host=storefront**.tthong.dev
```

### Media `publicUrl` must be overridden per environment

`mediaApplicationConfig.yas.publicUrl` defaults to `http://api.yas.local.com/media` (docker-compose domain). In K8s this must be set per environment in `deploy-yas-configuration.sh`:

```bash
--set "mediaApplicationConfig.yas.publicUrl=http://${STOREFRONT_HOST}/api/media"
```

Without this, storefront product images will try to load from the non-resolvable docker-compose domain.

### Why backoffice "works" but storefront 404s

Backoffice has Spring Security OAuth2 which intercepts unauthenticated requests **before** gateway routing (redirects to Keycloak login). Storefront allows anonymous access, so requests hit gateway routing immediately. If gateway routes are broken, storefront shows 404 while backoffice appears to work (shows login page).
