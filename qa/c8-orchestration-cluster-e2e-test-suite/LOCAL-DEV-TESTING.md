# Running E2E Tests Against a Locally-Built Server

This guide covers building Camunda from source and running the Playwright E2E test suite against it. This is useful when testing changes in feature branches before merging.

For the docker-compose approach using pre-built images, see [README.md](README.md).

---

## Prerequisites

- Java 21+
- Docker running (for Elasticsearch backend only)
- Node.js (see `.nvmrc` for version)

## 1. Build the Server

From the repository root:

```bash
./mvnw install -pl dist -am -Dquickly -T2
```

This builds the `dist` module and all its dependencies, skipping tests and checks. The server binary lands at `dist/target/camunda-zeebe/bin/camunda`.

> **Tip:** Use `-T1C` (one thread per CPU core) if you're not running other resource-intensive processes alongside the build.

## 2. Start the Server

Choose one of the two backend options below. Both configure BASIC auth with a `demo`/`demo` user that has admin privileges.

### Option A: Elasticsearch Backend

#### Start Elasticsearch

```bash
docker run -d --name elasticsearch \
  -e "discovery.type=single-node" \
  -e "cluster.name=elasticsearch" \
  -e "bootstrap.memory_lock=true" \
  -e "xpack.security.enabled=false" \
  -e "ES_JAVA_OPTS=-Xms1024m -Xmx1024m" \
  --ulimit memlock=-1:-1 \
  -p 9200:9200 -p 9300:9300 \
  docker.elastic.co/elasticsearch/elasticsearch:8.19.12
```

Wait for the cluster to be green:

```bash
curl -s http://localhost:9200/_cluster/health | python3 -m json.tool
```

#### Start Camunda

```bash
SPRING_PROFILES_ACTIVE=elasticsearch,consolidated-auth,broker,operate,tasklist,admin \
CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI=false \
CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED=true \
CAMUNDA_SECURITY_AUTHENTICATION_METHOD=BASIC \
CAMUNDA_SECURITY_MULTITENANCY_CHECKSENABLED=false \
CAMUNDA_SECURITY_INITIALIZATION_USERS_0_USERNAME=demo \
CAMUNDA_SECURITY_INITIALIZATION_USERS_0_PASSWORD=demo \
CAMUNDA_SECURITY_INITIALIZATION_USERS_0_NAME=Demo \
CAMUNDA_SECURITY_INITIALIZATION_USERS_0_EMAIL=demo@example.com \
CAMUNDA_SECURITY_INITIALIZATION_DEFAULTROLES_ADMIN_USERS_0=demo \
CAMUNDA_DATA_SECONDARY_STORAGE_TYPE=elasticsearch \
CAMUNDA_DATA_SECONDARY_STORAGE_ELASTICSEARCH_URL=http://localhost:9200 \
CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_WAITFORIMPORTERS=false \
dist/target/camunda-zeebe/bin/camunda
```

### Option B: H2 (In-Memory RDBMS) Backend

No external database needed. H2 runs embedded in the server process.

```bash
SPRING_PROFILES_ACTIVE=rdbmsH2,consolidated-auth,broker,operate,tasklist,admin \
CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI=false \
CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED=true \
CAMUNDA_SECURITY_AUTHENTICATION_METHOD=BASIC \
CAMUNDA_SECURITY_MULTITENANCY_CHECKSENABLED=false \
CAMUNDA_SECURITY_INITIALIZATION_USERS_0_USERNAME=demo \
CAMUNDA_SECURITY_INITIALIZATION_USERS_0_PASSWORD=demo \
CAMUNDA_SECURITY_INITIALIZATION_USERS_0_NAME=Demo \
CAMUNDA_SECURITY_INITIALIZATION_USERS_0_EMAIL=demo@example.com \
CAMUNDA_SECURITY_INITIALIZATION_DEFAULTROLES_ADMIN_USERS_0=demo \
dist/target/camunda-zeebe/bin/camunda
```

### Verify the Server Is Running

```bash
curl -s -u demo:demo http://localhost:8080/v2/topology | python3 -m json.tool
```

You should see a JSON response with cluster info and `"brokers"` containing a partition in `LEADER` role.

## 3. Configure the Test Suite

The `.env` file in this directory should contain:

```env
CAMUNDA_AUTH_STRATEGY=BASIC
CAMUNDA_BASIC_AUTH_USERNAME=demo
CAMUNDA_BASIC_AUTH_PASSWORD=demo
ZEEBE_REST_ADDRESS=http://localhost:8080
ZEEBE_GRPC_ADDRESS=grpc://localhost:26500
```

## 4. Install Test Dependencies

```bash
cd qa/c8-orchestration-cluster-e2e-test-suite
npm install
npx playwright install chromium
```

## 5. Run the Tests

### API tests (headless, no browser report popup)

```bash
PLAYWRIGHT_HTML_OPEN=never npx playwright test \
  --project=api-tests --project=api-tests-subset
```

### Interactive mode

```bash
npm run test:local
```

### View the HTML report afterwards

```bash
npx playwright show-report html-report
```

## Useful Playwright Options

|            Option            |                                              Effect                                               |
|------------------------------|---------------------------------------------------------------------------------------------------|
| `PLAYWRIGHT_HTML_OPEN=never` | Prevents the HTML report from opening a browser at the end of the run (which blocks the terminal) |
| `--reporter=list,json`       | Override reporters entirely (skip HTML report generation)                                         |
| `--project=api-tests`        | Run only the main API test project                                                                |
| `--project=api-tests-subset` | Run the serial subset (clock, usage-metrics)                                                      |
| `--grep "keyword"`           | Filter tests by name                                                                              |
| `--workers=1`                | Run tests serially (useful for debugging)                                                         |

## Cleanup

```bash
# Stop the Camunda server (Ctrl+C in its terminal, or):
lsof -i :8080 -t | xargs kill

# If using Elasticsearch:
docker stop elasticsearch && docker rm elasticsearch
```

## Ports Used

| Port  |                  Service                  |
|-------|-------------------------------------------|
| 8080  | Camunda REST API (HTTP)                   |
| 9200  | Elasticsearch HTTP (ES backend only)      |
| 9300  | Elasticsearch transport (ES backend only) |
| 9600  | Camunda management/metrics                |
| 26500 | Zeebe gRPC gateway                        |
| 26501 | Zeebe internal command API                |
| 26502 | Zeebe internal cluster                    |

