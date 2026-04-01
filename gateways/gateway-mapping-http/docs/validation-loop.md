# Validation Loop

Three-stage validation loop for compile-time data contract changes.

## 1. Unit test / build

```bash
# Build the dist module and all dependencies
./mvnw install -pl dist -am -Dquickly -T1C

# Run gateway-rest unit tests
./mvnw verify -pl zeebe/gateway-rest -DskipTests=false -Dquickly -T1C

# Run gateway-mapping-http unit tests
./mvnw verify -pl gateways/gateway-mapping-http -DskipTests=false -Dquickly -T1C
```

All tests must pass before proceeding. Fix compilation errors and test failures at this stage —
they are cheaper to resolve here than downstream.

## 2. Start server and Elasticsearch with clean volumes

```bash
# Remove existing volumes to ensure a clean state
docker compose down -v
docker compose up -d elasticsearch

# Wait for Elasticsearch to be healthy, then start Camunda with correct configuration for the tests
./start-server.sh
```

A clean-volume start ensures no stale index mappings or data from prior schema versions mask
regressions.

## 3. Run e2e test suite

Run the end-to-end test suite against the running server to validate request/response shape,
constraint enforcement, and data flow through the full stack.

PLAYWRIGHT_HTML_OPEN=never npx playwright test --project=api-tests --project=api-tests-subset
