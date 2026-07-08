# Run Optimize from source against the camunda-hub docker stack

Goal: run **Optimize from source** (so it includes the `BusinessValueReportSeeder` / dashboard code)
side-by-side with the docker containers started by camunda-hub's `make env-up-self-managed-c8`, so the
Hub's Business Value Dashboard can call it.

The docker stack provides Elasticsearch, Keycloak, Identity and the orchestration cluster; **Optimize is
the only piece you run from source.** Verified working on 2026-07-06 (JDK 21).

## 1. Start the camunda-hub docker stack

From the **camunda-hub** repo:

```sh
make env-up-self-managed-c8
```

This brings up (host ports): Elasticsearch `9200`, Keycloak `18080`, Identity `8080`, the orchestration
cluster, plus a **bundled `camunda/optimize:SNAPSHOT` container**. That bundled image does **not** contain
your source changes — stop it so it doesn't collide with your source Optimize:

```sh
docker stop optimize 2>/dev/null || true
```

Sanity-check the dependencies are reachable from the host:

```sh
curl -s localhost:9200/_cluster/health | head -c 80          # ES: expect status yellow/green
curl -s http://localhost:18080/auth/realms/camunda-platform/.well-known/openid-configuration | head -c 80
```

Make sure ports **8090 / 8091 / 8092** are free (Optimize's http / https / actuator). If something else
holds them, either free it or override the ports (see the env block).

> **Port collision with the Hub restapi:** the Hub backend uses **8081** (http) and **8091** (management/
> actuator) — and Optimize's **https connector also defaults to 8091**. So Optimize and the Hub both want
> 8091, and whichever starts second fails with `Web server failed to start. Port 8091 was already in use`.
> Fix by moving the Hub's management port when you run it (see step 5), or move Optimize's https port here
> with `export CAMUNDA_OPTIMIZE_CONTAINER_PORTS_HTTPS=18091`.

## 2. Environment variables for source Optimize

These remap the docker hostnames used by the bundled container to their **host** equivalents, and enable
JWT bearer auth so the Hub's forwarded user token is accepted. **All are required.**

```sh
export SPRING_PROFILES_ACTIVE=ccsm
export OPTIMIZE_ELASTICSEARCH_HOST=localhost
export OPTIMIZE_ELASTICSEARCH_HTTP_PORT=9200
export CAMUNDA_OPTIMIZE_ZEEBE_ENABLED=true
export CAMUNDA_OPTIMIZE_ZEEBE_NAME=zeebe-record
export CAMUNDA_OPTIMIZE_ZEEBE_PARTITION_COUNT=1          # match the cluster's partition count
export CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_URL=http://localhost:18080/auth/realms/camunda-platform
export CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_BACKEND_URL=http://localhost:18080/auth/realms/camunda-platform
export SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI=http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/certs
export CAMUNDA_OPTIMIZE_IDENTITY_BASE_URL=http://localhost:8080
export CAMUNDA_OPTIMIZE_IDENTITY_CLIENTID=optimize
export CAMUNDA_OPTIMIZE_IDENTITY_CLIENTSECRET=XALaRPl5qwTEItdwCMiPS62nVpKs7dL7   # must match KEYCLOAK_INIT_OPTIMIZE_SECRET in docker-compose.sm-keycloak.yml
export CAMUNDA_OPTIMIZE_IDENTITY_AUDIENCE=optimize-api
export CAMUNDA_OPTIMIZE_API_JWT_AUTH_ENABLED=true
export CAMUNDA_OPTIMIZE_API_AUDIENCE=optimize-api
export CAMUNDA_OPTIMIZE_SECURITY_AUTH_COOKIE_SAME_SITE_ENABLED=false
# Optional: override ports only if 8091/8092 are taken on your machine
# export CAMUNDA_OPTIMIZE_CONTAINER_PORTS_HTTPS=18091
# export MANAGEMENT_SERVER_PORT=18092
```

Common mistakes (each produces a startup crash whose real cause is *above* the
`elasticsearch-rest-client ... failed to stop it` teardown warnings):
- **`api.jwtSetUri is not configured`** → you set `CAMUNDA_OPTIMIZE_API_JWT_AUTH_ENABLED=true` but omitted
`SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` (that env var *is* Optimize's `api.jwtSetUri`).
- **401 from Keycloak / can't get M2M token** → wrong `CAMUNDA_OPTIMIZE_IDENTITY_CLIENTSECRET`
(must be `XALaRPl5…`, not `demo-optimize-secret`).
- Using docker hostnames (`elasticsearch`, `keycloak:8080`, `identity:8080`) from a source run — they
don't resolve on the host; use `localhost` + the host ports above.

## 3. Run Optimize (main class `io.camunda.optimize.Main`), JDK 21

### Option A — IntelliJ (recommended)

Run configuration: **Application**, main class `io.camunda.optimize.Main`, module `optimize-backend`,
JDK **21**, and paste the env vars above into *Environment variables*. IntelliJ puts the full module
classpath on the run, so it "just works". (`SERVER_PORT` is ignored — Optimize's HTTP port is
`CAMUNDA_OPTIMIZE_CONTAINER_PORTS_HTTP`, default **8090**.)

### Option B — command line

`spring-boot:run` / `exec:java` drop a couple of transitive runtime jars, so run `Main` with a fully
resolved classpath:

```sh
cd <monorepo root>
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.11-tem; export PATH=$JAVA_HOME/bin:$PATH
./mvnw -o -pl optimize/backend dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=/tmp/optimize-cp.txt
java -cp "optimize/backend/target/classes:$(cat /tmp/optimize-cp.txt):$HOME/.m2/repository/com/vdurmont/semver4j/3.1.0/semver4j-3.1.0.jar" \
  io.camunda.optimize.Main
```

(Rebuild classes first if you changed Optimize code: `./mvnw -o -pl optimize/backend compile`.)

## 4. Verify Optimize came up + seeded the report

In the Optimize log, look for:

```
Tomcat started on port 8090 (http)
i.c.o.s.d.BusinessValueReportSeeder : Seeding Business Value Dashboard reports
i.c.o.s.d.BusinessValueReportSeeder : Finished seeding Business Value Dashboard reports
```

Confirm the seeded report exists in ES (auth-free):

```sh
CID=$(python3 -c "import hashlib,uuid;print(uuid.UUID(bytes=hashlib.md5(b'bv-cycle-time').digest(),version=3))")
curl -s "http://localhost:9200/optimize-single-process-report*/_search" -H 'Content-Type: application/json' \
  -d "{\"query\":{\"ids\":{\"values\":[\"$CID\"]}}}" | grep -o '"name":"businessValueCycleTimeName"'
```

`WARN ... No Zeebe index with alias zeebe-record-agent-instance found` is harmless — it just means no
agentic data has been exported yet. Cycle Time uses process-instance data; you need some **completed**
process instances in the cluster (exported to `zeebe-record_process-instance*`) for non-empty numbers.

## 5. Run the Hub and open the dashboard

The Hub's cluster config already points Optimize at `http://localhost:8090`
(`restapi/config/config-self-managed/src/main/resources/application-self-managed-local.yml`). From
camunda-hub (using the pinned JDK 25.0.1, and moving the Hub's management port off Optimize's https 8091):

```sh
export JAVA_HOME=~/.asdf/installs/java/openjdk-25.0.1 && export PATH="$JAVA_HOME/bin:$PATH"
export MANAGEMENT_SERVER_PORT=18091     # Hub mgmt port defaults to 8091 = Optimize https → collision
make local-self-managed
```

Open http://localhost:8088 (`demo` / `demo`) → **Business value dashboard** → pick a cluster + date range.
The evaluate call is authorized as the logged-in user (needs the **Optimize** role, which `demo` has).

## Notes

- Run **only one** Optimize against the ES instance (stop the bundled docker one in step 1) — two importers
  fight over import positions.
- Headless user tokens aren't available (Keycloak direct-access-grants are disabled on these clients), so
  the evaluate endpoint is exercised through the Hub browser login, not `curl`.
- **`localhost:8090` shows an "invalid redirect" error.** That's Optimize's *own* UI login redirecting to
  Keycloak, and the `optimize` client's redirect URIs must include the port Optimize serves on. The compose
  now sets `KEYCLOAK_INIT_OPTIMIZE_ROOT_URL=http://localhost:8090`, so a fresh `make env-up-self-managed-c8`
  registers `:8090` correctly. If you started the stack *before* that change, either recreate it, or add the
  URI live via the Keycloak admin API (admin/admin at `:18080`):

  ```sh
  KC=http://localhost:18080
  T=$(curl -s -X POST "$KC/auth/realms/master/protocol/openid-connect/token" -d grant_type=password \
       -d client_id=admin-cli -d username=admin -d password=admin | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")
  CID=$(curl -s -H "Authorization: Bearer $T" "$KC/auth/admin/realms/camunda-platform/clients?clientId=optimize" | python3 -c "import sys,json;print(json.load(sys.stdin)[0]['id'])")
  BODY=$(curl -s -H "Authorization: Bearer $T" "$KC/auth/admin/realms/camunda-platform/clients/$CID" | python3 -c "import sys,json;c=json.load(sys.stdin);ru=set(c.get('redirectUris') or []);ru.add('http://localhost:8090/*');c['redirectUris']=sorted(ru);print(json.dumps(c))")
  curl -s -o /dev/null -w "%{http_code}\n" -X PUT -H "Authorization: Bearer $T" -H 'Content-Type: application/json' \
       "$KC/auth/admin/realms/camunda-platform/clients/$CID" -d "$BODY"
  ```

  Note: this UI is **not needed** for the Business Value Dashboard — the Hub calls Optimize's API
  server-side with a bearer token (no redirect). Use it only if you want to browse Optimize directly.

