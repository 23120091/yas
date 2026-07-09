# AGENTS.md — YAS (Yet Another Shop)

## Architecture

- **Monorepo**: root `pom.xml` declares 20 Maven modules — 18 Java Spring Boot 4.0.2 services + `common-library/` (shared entities, Kafka CDC helpers, CSV, mappers) + `sampledata/` (seed data).
- **BFF modules** (`storefront-bff/`, `backoffice-bff/`) are Spring Cloud Gateway apps that proxy to backend microservices and handle OAuth2 token relay.
- **2 standalone Next.js 14 frontends** — `storefront/` and `backoffice/`.
- Authentication: **Keycloak** (realm export at `identity/realm-export.json`).
- Event-driven: **Kafka** + Debezium CDC source connectors (`kafka/connects/`).
- Search: **Elasticsearch** (separate `docker-compose.search.yml`).
- Observability: **OpenTelemetry** Java agent → **Grafana/Loki/Tempo/Prometheus** (separate `docker-compose.o11y.yml`).
- `webhook` and `recommendation` exist in `pom.xml` but are **commented out** in `docker-compose.yml`. `delivery` exists in `pom.xml` but is entirely absent from `docker-compose.yml`.

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
./mvnw clean verify -pl <service> -am
```

### Frontend (Next.js)

```bash
# In storefront/ or backoffice/
npm run dev        # dev server
npm test -- --coverage --passWithNoTests
npm ci --legacy-peer-deps  # install deps (peer dep conflicts)
npx prettier -w .  # format before committing
```

- **Jest versions differ**: storefront uses `jest@30.3.0`, backoffice uses `jest@29.7.0`.
- CI uses `npm ci --legacy-peer-deps` (not `npm install`) because of peer dependency conflicts.

### Docker Compose

```bash
# Copy .env.example to .env first (gitignored), then:
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
3. In the BFF's `application-dev.yaml`, add a route pointing to your local service as the **first** gateway route. Use `path: gateway.server.webflux.routes` — **not** `gateway.routes` (see K8s Gotcha below).
4. Run the matching frontend via `npm run dev` in `storefront/` or `backoffice/`.

**Note:** `docs/developer-guidelines.md` references the old `spring.cloud.gateway.routes` property path which is **silently ignored** in this version. The correct path is shown in `application-dev.yaml`.

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

- Path-filtered: **only changed services** build (see `ci.yml` filter-changes job). Triggers on push/PR to `main`.
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

## Architecture Notes

- **Nginx** (port 80) is the front proxy — it routes incoming requests to BFFs, backend services, Keycloak, and pgAdmin via hostnames (`api.yas.local`, `backoffice`, `storefront`, `identity`, etc.).
- **Virtual threads** enabled in both BFF modules (`spring.threads.virtual.enabled: true`).
- **Spring profiles**: `dev` (default) for local/IDE runs, `prod` in Docker containers via `SPRING_PROFILES_ACTIVE=prod`.
- **automation-ui/** is a separate Java 21 + Spring Boot 3.3.2 + Cucumber/Selenium test project (not part of the main Java 25 build).

## Known Gotchas

- On first `docker compose up`, the storefront/backoffice containers may need a restart (Ctrl+C then up again).
- If limited to 16GB RAM, set `COMPOSE_FILE=docker-compose.yml` and `OTEL_JAVAAGENT_ENABLED=false` in `.env`, and comment out unused services from `docker-compose.yml`.
- Search service running locally outside Docker: change `spring.kafka.consumer.bootstrap-servers` from `kafka:9092` to `localhost:29092`.
- BFFs use `TokenRelay=` filter for OAuth2 token propagation; do not remove it.
- **Liquibase lock stuck?** Manually drop the lock:
  ```sql
  UPDATE <schema>.DATABASECHANGELOGLOCK SET LOCKED=false, LOCKEDBY=null, LOCKGRANTED=null;
  ```

## K8s Deployment

ArgoCD-managed GitOps (push to `main` → auto-sync). Infrastructure (PostgreSQL, Kafka, ES, Redis, etc.) is set up once per env via `./setup-cluster.sh <env>` and `./setup-redis.sh <env>`.

### K8s config gotchas

**Spring Cloud Gateway property path (Boot 4 / Gateway 5):** BFF routes must use `spring.cloud.gateway.server.webflux.routes` — the old `spring.cloud.gateway.routes` is **silently ignored** (404s everything). Docs at `docs/developer-guidelines.md` reference the wrong path.

**`backofficeBffExtraConfig` / `storefrontBffExtraConfig`** = OAuth2 + Redis only. Gateway routes go in `gatewayRoutesConfig` (Helm chart at `k8s/charts/yas-configuration/`).

**Host predicate wildcard:** `Host` uses single `*` (dot-separated segment). `**` does NOT work in `Host`.

**Media `publicUrl` per environment:** `mediaApplicationConfig.yas.publicUrl` defaults to `http://api.yas.local.com/media`. In K8s override per env:
```bash
--set "mediaApplicationConfig.yas.publicUrl=http://${STOREFRONT_HOST}/api/media"
```

**Why backoffice "works" but storefront 404s:** Backoffice intercepts unauthenticated requests **before** gateway routing (redirects to Keycloak login). Storefront allows anonymous access, so broken gateway routes produce 404 immediately.

**Node selectors:** `debug-tools`, `swagger-ui`, and `yas-reloader` use env-specific node selectors from `cluster-config-{env}.yaml`.

**`k8s/deploy/check-connections.sh`** — diagnostic script to test connectivity between services from a temporary debug pod.

**Developer Build workflow** (`.github/workflows/developer-build.yml`) — deploy/destroy services to a sandbox namespace for isolated testing. Creates ExternalName CNAMEs pointing back to the source env for non-deployed services.
