# AGENTS.md — YAS (Yet Another Shop)

## Architecture

- **Monorepo** with a **Maven multi-module root** (`pom.xml` packages 18 Java Spring Boot 4.0 services) + 2 standalone **Next.js 14** frontends (`storefront/`, `backoffice/`).
- All Java services share `common-library/` (shared entities, exceptions, Kafka CDC helpers, CSV tools, mappers).
- BFF modules (`storefront-bff/`, `backoffice-bff/`) are **Spring Cloud Gateway** apps that proxy to backend microservices and handle OAuth2 token relay.
- Authentication: **Keycloak** (realm export at `identity/realm-export.json`).
- Event-driven: **Kafka** + Debezium CDC source connectors (see `kafka/connects/`).
- Search: **Elasticsearch** (separate compose file `docker-compose.search.yml`).
- Observability stack: **OpenTelemetry** (Java agent) → **Grafana/Loki/Tempo/Prometheus** (separate compose file `docker-compose.o11y.yml`).
- The webhook, recommendation, and delivery modules exist in `pom.xml` but are commented out in `docker-compose.yml`.

## Developer Commands

### Java services (Maven 1C parallel build)

```bash
# Build + test a single service and its dependencies
./mvnw clean verify -pl <service> -am -T 1C

# Run only unit tests for a service (skip integration tests)
./mvnw test -pl <service> -am

# Checkstyle for a single service
./mvnw compile checkstyle:checkstyle -pl <service> -am -DskipTests

# Run integration tests only (naming convention *IT.java)
./mvnw verify -pl <service> -am -DskipUnitTests
```

### Containerized Java (if no local JDK 25)

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

1. `docker compose up` to start all infrastructure (postgres, kafka, keycloak, etc.).
2. Run the target Java service in your IDE on its default port (e.g. product on 8092).
3. In the BFF's `application.yaml`, add a route pointing to your local service as the **first** gateway route (see `docs/developer-guidelines.md` for example).
4. Run the matching frontend via `npm run dev` in `storefront/` or `backoffice/`.

## Testing Conventions

- **Unit tests**: `src/test/java/`, run by **maven-surefire**.
- **Integration tests**: `src/it/java/`, file naming `**/*IT.java`, run by **maven-failsafe**. Use **Testcontainers** (PostgreSQL, Keycloak).
- **Checkstyle** runs on `mvn compile` phase; violations are **warnings only** (not fail-build) in the POM config.
- **JaCoCo** coverage report runs in the `verify` phase. Minimum 70% coverage enforced in CI.
- Coverage excludes: `*Application.class`, `config/**`, `exception/**`, `constants/**`.

## Code Conventions (Java)

- **Google Java Style Guide** enforced via `/checkstyle/checkstyle.xml`. JavaDoc checks are suppressed.
- **Lombok** + **MapStruct** as annotation processors (see parent POM plugin config).
- **Liquibase** for DB migrations: DDL changesets in `db/changelog/ddl/`, data changesets in `db/changelog/data/`. **Never modify an already-run changeset** — always add new ones.
- **Entities**: Use primitives for non-nullable fields, wrappers for nullable. Always override `equals` and `hashCode`.
- **No soft-delete** and **no cascade-delete** (except many-to-many). Show a user-facing message on DB constraint violations.
- Controllers can call repositories directly for simple CRUD. Business logic goes in service classes.

## CI / Release

- CI is path-filtered: only changed services build. All workflows in `.github/workflows/`.
- **Java CI** (`shared-java-ci.yml`): Gitleaks → Snyk SCA → `mvn clean verify -pl <svc> -am -T 1C` → Checkstyle → SonarQube → JaCoCo → Docker build & push.
- **Node CI** (`shared-node-ci.yml`): ESLint → Gitleaks → Snyk SCA → `npm test -- --coverage` → `npm run build` → Docker build & push.
- Docker images pushed to **ghcr.io** only on `main` branch.
- **Release**: Push a tag in format `<service_path>-<version>` (e.g. `product-1.0.0`) to trigger `release-docker.yml`.

## Environment & Config

- Copy `.env.example` to `.env` (gitignored). It sets service URLs, OTEL config, Kafka addresses, etc.
- Keycloak admin: `admin/admin`. PGAdmin: `admin@yas.com/admin`. PostgreSQL: `admin/admin`.
- Backoffice login: `admin/password`.
- Swagger UI at `http://api.yas.local/swagger-ui/` when containers are running.

## Known Gotchas

- On first `docker compose up`, the storefront/backoffice may need a restart (Ctrl+C then up again).
- If limited to 16GB RAM, set `COMPOSE_FILE=docker-compose.yml` and `OTEL_JAVAAGENT_ENABLED=false` in `.env`, and comment out unused services from `docker-compose.yml`.
- Search service: when running locally outside Docker, change `spring.kafka.consumer.bootstrap-servers` from `kafka:9092` to `localhost:29092`.
- The `test.ps1` is a PowerShell helper; the repo uses `./mvnw` (Maven wrapper — committed) not a global `mvn`.
