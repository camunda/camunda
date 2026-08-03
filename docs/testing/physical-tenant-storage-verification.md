# camunda-docs#9484 — the four scenarios

Manual verification of the per-physical-tenant storage documentation against `camunda/camunda` `main` @
`11a33739c02` (2026-07-28/30). Four scenarios: the two secondary-storage types × the two authentication
modes. Each section gives a brief description, how to replicate it, and what was found.

**Overall: no documentation defects.** Every YAML block in the documentation works as written, apart from
hostnames. One improvement is suggested (§2). Two product issues unrelated to the documentation are listed
at the end.

## Common setup

Build the distribution once and boot each scenario with its own YAML file:

```bash
./mvnw -pl dist package -DskipTests -DskipChecks=true
DIST=dist/target/camunda-zeebe

$DIST/bin/camunda --spring.config.additional-location=file:/abs/path/to/config.yaml
```

Three things to know before the first boot:

- **Do not set `SPRING_PROFILES_ACTIVE`.** The default is `operate,tasklist,broker,admin,consolidated-auth`;
  overriding it drops beans and startup fails.
- **Use the distribution launcher, not a hand-built classpath.** `java -cp …` fails on agrona
  (`--add-opens java.base/jdk.internal.misc=ALL-UNNAMED`) and on a missing `camunda-cluster`, which
  `zeebe-gateway-rest` needs for `io.camunda.cluster.SecondaryStorageReadiness`.
- **Check for duplicate jars** in `$DIST/lib/`. If a Maven proxy serves timestamped snapshots, every locally
  built artifact can appear twice (`-SNAPSHOT.jar` plus `-8.10.0-2026….jar`), and MyBatis then parses each
  mapper XML twice and dies with
  `Result Maps collection already contains key io.camunda.db.rdbms.sql.AgentHistoryMapper.searchResultMap`.
  Delete the timestamped copy wherever a `-SNAPSHOT.jar` sibling exists.

The REST API is on `:8080`, actuator/health on `:9600` (health on 8080 is a 404). Every call goes through
the physical-tenant prefix `/physical-tenants/<id>/v2/...`.

All four configs declare two tenants, `default` and `tenanta`, and each tenant needs its own
`security.initialization` block. That block is identical in all four scenarios, so it is given once here and
omitted from the per-scenario YAML below:

```yaml
camunda:
  data:
    primary-storage:
      directory: /tmp/pt-manual-data
  security:
    authentication:
      method: BASIC
    authorization:
      enabled: true
    initialization:
      users:
        - username: demo
          password: demo
          name: Demo
          email: demo@example.com
      default-roles:
        admin:
          users: [demo]
  physical-tenants:
    tenanta:
      security:
        initialization:
          users:
            - username: tenanta-admin
              password: tenanta-secret
              name: Tenant A Admin
              email: tenanta@example.com
          default-roles:
            admin:
              users: [tenanta-admin]
```

REST credentials follow from it: `demo:demo` for the `default` prefix, `tenanta-admin:tenanta-secret` for
`tenanta`. The §3 config was booted verbatim from YAML with an empty environment (`env -i`); the other three
are the same shape, translated from the environment variables those runs used.

### What each scenario exercises

Every scenario runs the same sequence: deploy a process to `tenanta` and start an instance of it, then query
both tenants' `process-definitions` search and get-by-key endpoints, and finally try each tenant's
credentials against the other tenant's prefix. Results are in each section's findings table.

---

## 1. OpenSearch with basic authentication

Each physical tenant authenticates to Amazon OpenSearch Service with its own internal-database user and
writes under its own index prefix. Two variants: tenants sharing one instance, and a tenant on its own
dedicated instance. Fine-grained access control (FGAC) is administered on the AWS side; Camunda's part is
only to present the right credentials per tenant.

### Replicate

Two OpenSearch containers with the security plugin off (host ports avoid 9200 in case something else holds
it):

```bash
docker run -d --name os-default -p 19200:9200 -e discovery.type=single-node \
  -e DISABLE_SECURITY_PLUGIN=true -e DISABLE_INSTALL_DEMO_CONFIG=true \
  -e "OPENSEARCH_JAVA_OPTS=-Xms512m -Xmx512m" opensearchproject/opensearch:2.19.0
docker run -d --name os-tenanta -p 19201:9200 -e discovery.type=single-node \
  -e DISABLE_SECURITY_PLUGIN=true -e DISABLE_INSTALL_DEMO_CONFIG=true \
  -e "OPENSEARCH_JAVA_OPTS=-Xms512m -Xmx512m" opensearchproject/opensearch:2.19.0
```

Shared instance — tenant A overrides only its credentials and prefix:

```yaml
camunda:
  data:
    secondary-storage:
      type: opensearch
      opensearch:
        url: http://127.0.0.1:19200
        username: camunda-default
        password: default-secret
        index-prefix: default
  physical-tenants:
    tenanta:
      data:
        secondary-storage:
          opensearch:
            username: tenant-a-user
            password: tenant-a-secret
            index-prefix: tenant-a
```

Dedicated instance — tenant A additionally overrides `url`:

```yaml
camunda:
  physical-tenants:
    tenanta:
      data:
        secondary-storage:
          opensearch:
            url: http://127.0.0.1:19201
            username: tenant-a-user
            password: tenant-a-secret
            index-prefix: tenant-a
```

### Findings — works as documented

| Check | Shared instance | Dedicated instance |
|---|---|---|
| Boot | both partitions `UP` | both partitions `UP` |
| Indices created | 40 `default-*` and 40 `tenant-a-*` on the one cluster | `tenant-a-*` on the tenant cluster only |
| Search after deploying to tenant A | `totalItems=1` in tenanta, `0` in default | same |
| Get-by-key in tenanta / default | `HTTP 200` / `HTTP 404` | same |
| Cross-tenant credentials, both directions | `HTTP 401` / `HTTP 401` | same |
| Credentials each tenant presents | tenant A's requests authenticate as `tenant-a-user`, the default tenant's as `camunda-default` (494 requests, 252/242) | same split, but per endpoint |
| Cross-prefix access | none: `tenant-a-user` never touched `default-*`, `camunda-default` never touched `tenant-a-*` | no tenant A request reached the default instance, and none from default reached the tenant instance |

Confirms that `url`, `username`, `password` and `index-prefix` are each per-tenant overridable, that a
tenant moves to its own instance by overriding `url` alone, and that Camunda keeps every tenant inside its
own index prefix.

FGAC *enforcement* is AWS-side by the documentation's own wording, so it cannot be reproduced locally. What
can be checked is the half Camunda owns — that it presents each tenant's credentials and never crosses the
prefix boundary — and that holds.

---

## 2. OpenSearch with IAM (request signing)

`aws-enabled: true` makes Camunda sign requests with AWS Signature V4 instead of using basic auth.
Credentials come from the AWS SDK default provider chain (on EKS, the pod's IAM role via IRSA) and the
region from the environment. All tenants share that single identity; separation comes from per-tenant index
prefixes. The setting is inherited, so a tenant overrides only its `url` and prefix.

### Replicate

One obstacle has to be cleared first. With `aws-enabled: true` Camunda addresses **`https://<host>:443`**,
discarding the URL's scheme and port: the connector passes only the hostname to `AwsSdk2Transport`, whose
every constructor takes a bare host. The certificate must also be publicly trusted, because the AWS CRT
client's trust store is not configurable on this path. Pointing it at a local cluster on port 19200
therefore fails with `awssdk.crt.http.HttpException: socket connection refused`.

Three pieces solve it with no patch and no `sudo`:

- **DNS** — `127-0-0-1.local-ip.sh` resolves to `127.0.0.1`.
- **Certificate** — `local-ip.sh` publishes a Let's Encrypt wildcard certificate for `*.local-ip.sh`
  together with its private key, deliberately, for this purpose. It is already trusted, so nothing needs
  installing. (A published private key is fine for a local harness and never for anything real.)
- **Port 443** — bind it by running a TLS terminator as a container under **Docker Desktop**; a `colima`
  daemon cannot bind privileged host ports.

```bash
curl -sS -o server.pem https://local-ip.sh/server.pem
curl -sS -o server.key https://local-ip.sh/server.key
openssl x509 -in server.pem -noout -subject -enddate   # CN=*.local-ip.sh, still valid?
```

#### What the TLS terminator does

A reverse proxy sits on 443 holding the trusted certificate and relays to the plain-HTTP OpenSearch
container. Camunda believes it is talking to an HTTPS endpoint on the standard port, which is the only shape
the `aws-enabled` path can express.

- Presents `server.pem`/`server.key` and terminates TLS on the port it is given.
- Relays each request to the upstream unchanged except for the target host, stripping only hop-by-hop
  headers, and copies the status, headers and body straight back — so what OpenSearch receives is what
  Camunda sent, signature included.
- Prints one line per request with the method, path, authentication scheme and SigV4 credential scope. That
  line is the evidence behind the findings below: it is how the signature, its region and its service were
  read off the wire.
- Threads each connection and sets an explicit `Content-Length` on every response, so concurrent clients and
  HTTP/1.1 keep-alive do not stall.


```python
# tlsfront.py <listen_port> <upstream_host> <upstream_port> <certfile> <keyfile>
import http.server, re, socketserver, ssl, sys, urllib.error, urllib.request

PORT, UP_HOST, UP_PORT, CERT, KEY = sys.argv[1:6]
UPSTREAM = f"http://{UP_HOST}:{UP_PORT}"
HOP = {"connection", "keep-alive", "transfer-encoding", "upgrade", "host", "content-length"}

class H(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    def log_message(self, *a): pass
    def _do(self):
        auth = self.headers.get("Authorization", "")
        scope = re.search(r"Credential=([^,]+)", auth)
        print(f"{self.command} {self.path.split('?')[0]} auth="
              f"{auth.split(' ')[0] or 'none'} scope={scope.group(1) if scope else '-'}", flush=True)
        n = int(self.headers.get("Content-Length") or 0)
        req = urllib.request.Request(UPSTREAM + self.path,
                                     data=self.rfile.read(n) if n else None, method=self.command)
        for k, v in self.headers.items():
            if k.lower() not in HOP: req.add_header(k, v)
        try:
            with urllib.request.urlopen(req) as r: body, code, hdrs = r.read(), r.status, r.headers
        except urllib.error.HTTPError as e: body, code, hdrs = e.read(), e.code, e.headers
        self.send_response(code)
        for k, v in hdrs.items():
            if k.lower() not in HOP: self.send_header(k, v)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers(); self.wfile.write(body)
    do_GET = do_POST = do_PUT = do_DELETE = do_HEAD = _do

class S(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True; allow_reuse_address = True

srv = S(("", int(PORT)), H)
ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER); ctx.load_cert_chain(CERT, KEY)
srv.socket = ctx.wrap_socket(srv.socket, server_side=True)
print(f"tlsfront :{PORT} -> {UPSTREAM}", flush=True); srv.serve_forever()
```

```bash
docker --context desktop-linux run -d --name os-tls -p 443:443 -v "$PWD:/work" -w /work \
  python:3.12-alpine python3 tlsfront.py 443 host.docker.internal 19200 server.pem server.key
```

The AWS identity is supplied as environment variables — these are inputs to the SDK provider chain, not
Camunda configuration:

```bash
export AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE
export AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
export AWS_REGION=eu-central-1
```

```yaml
camunda:
  data:
    secondary-storage:
      type: opensearch
      opensearch:
        url: https://127-0-0-1.local-ip.sh
        aws-enabled: true
        index-prefix: default
  physical-tenants:
    tenanta:
      data:
        secondary-storage:
          opensearch:
            # aws-enabled deliberately not repeated — the documentation says it is inherited
            index-prefix: tenant-a
```

### Findings — works as documented

| Check | Observed |
|---|---|
| Boot | both partitions `UP` |
| Every request signed | 304 requests, **all** `AWS4-HMAC-SHA256`; none basic, none unauthenticated |
| Credential scope | exactly one distinct value: `AKIAIOSFODNN7EXAMPLE/20260729/eu-central-1/es/aws4_request` |
| Region source | the endpoint hostname contains **no region**, yet every signature is scoped `eu-central-1` — so it comes purely from `AWS_REGION` |
| Signing service | `es`, matching the `es:ESHttp*` permissions the documentation describes |
| One identity across tenants | a single access key serves both tenants' traffic |
| `aws-enabled` inheritance | a tenant overriding only `index-prefix` used the AWS path; the log reports a client per tenant: `Initializing search engine schema for 2 physical tenant(s): [tenanta, default]` |
| Per-tenant prefixes over the signed path | 354 request lines touching `default-*`, 165 touching `tenant-a-*` |
| Search / get-by-key / cross-tenant credentials | `totalItems=1` vs `0`; `HTTP 200` / `HTTP 404`; `401` both directions |

The region result is stronger evidence than a cross-region test would have been: the endpoint carries no
region at all, so `eu-central-1` in the signature can only have come from the environment.

**Suggested documentation improvement.** Nothing warns that `aws-enabled: true` ignores the URL's scheme and
port and always uses `https://<host>:443`. The examples themselves are correct, since they use real
`https://….es.amazonaws.com` endpoints, but a reader who points this at anything else gets only an opaque
`socket connection refused`.

Not reproducible locally: AWS's *authorization* of a signed request — the domain access policy or FGAC role
mapping — because a local cluster has no notion of it. LocalStack does not help either; it enforces neither
SigV4 nor FGAC and listens on 4566 rather than 443.

---

## 3. Aurora with basic authentication

Each physical tenant connects to Aurora PostgreSQL through the AWS JDBC wrapper as its own database user,
into its own schema. Permissions are administered in PostgreSQL, so isolation is enforced by the database
rather than by the application.

### Replicate

```bash
docker run -d --name pg-basic -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=camunda \
  -p 55432:5432 postgres:16
```

```sql
CREATE SCHEMA default_schema;  CREATE SCHEMA tenant_a_schema;
CREATE USER camunda       WITH PASSWORD 'camunda';
CREATE USER tenant_a_user WITH PASSWORD 'tenant-a-secret';
GRANT ALL ON SCHEMA default_schema  TO camunda;
GRANT ALL ON SCHEMA tenant_a_schema TO tenant_a_user;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
```

```yaml
camunda:
  data:
    secondary-storage:
      type: rdbms
      rdbms:
        url: jdbc:aws-wrapper:postgresql://localhost:55432/camunda?currentSchema=default_schema
        username: camunda
        password: camunda
  physical-tenants:
    tenanta:
      data:
        secondary-storage:
          rdbms:
            url: jdbc:aws-wrapper:postgresql://localhost:55432/camunda?currentSchema=tenant_a_schema
            username: tenant_a_user
            password: tenant-a-secret
```

### Findings — works as documented

| Check | Observed |
|---|---|
| Boot | `Partition-default-1` and `Partition-tenanta-1` `UP`; health `UP` on `:9600` |
| Liquibase, run as each tenant's own user | 52 tables in `default_schema`, 52 in `tenant_a_schema` |
| Topology per tenant | `HTTP 200` on both prefixes |
| Deploy + create instance in tenant A | succeeds |
| Search: tenanta / default | `totalItems=1` / `totalItems=0` |
| Get-by-key: tenanta / default | `HTTP 200` / `HTTP 404` |
| Cross-tenant credentials, both directions | `HTTP 401` / `HTTP 401` |
| Where rows physically landed | `tenant_a_schema`: 1 process definition + 1 instance; `default_schema`: 0 and 0 |
| Database-enforced isolation | PostgreSQL answers `permission denied` when tenant A's user reaches for `default_schema` |
| Connections per database user | `camunda` ×2, `tenant_a_user` ×2 |

Isolation holds at both layers and in both directions: the application routes correctly, and the database
refuses the crossing independently of it. The AWS JDBC wrapper needs no separate installation — it ships in
the distribution (`dist/pom.xml`) — and `jdbc:aws-wrapper:` URLs resolve `software.amazon.jdbc.Driver`
through Hikari with no extra configuration.

---

## 4. Aurora with IAM authentication

`wrapperPlugins=iam` with **no password configured**: the wrapper generates a short-lived RDS authentication
token per database user, signed with the application's single AWS identity from the default provider chain.
`rds-db:connect` is granted per database user, so one identity reaches many tenant-scoped users.

### Replicate

The token is produced by **local signing only** — no call to RDS or STS — so no AWS service and no
LocalStack is involved. But a local PostgreSQL cannot accept a rotating token as a password, so the
connection needs a shim: PostgreSQL runs with cleartext password authentication, and a small pgwire proxy
replaces the token with the real password before forwarding. It also prints the token, which is where the
findings below come from.

```bash
docker run -d --name pg-iam -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=camunda \
  -e POSTGRES_HOST_AUTH_METHOD=password -p 55433:5432 postgres:16
```

```sql
CREATE SCHEMA default_schema;  CREATE SCHEMA tenant_a_schema;
CREATE USER camunda       WITH PASSWORD 'harness-secret';
CREATE USER tenant_a_user WITH PASSWORD 'harness-secret';
GRANT ALL ON SCHEMA default_schema  TO camunda;
GRANT ALL ON SCHEMA tenant_a_schema TO tenant_a_user;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
```

Both database users share one password purely so a single substitution serves both; the tokens the driver
generates stay per-user.

#### What the pgwire shim does

It speaks just enough of the PostgreSQL wire protocol to swap one field, letting an IAM connection complete
against a database that knows nothing about IAM.

- Accepts a client connection and opens one to PostgreSQL, then relays between them.
- Handles the startup phase, which is untyped: a 4-byte length followed by the payload. If that payload is an
  `SSLRequest` (code `80877103`) it answers `N` to decline encryption, so the exchange stays readable, then
  forwards the real `StartupMessage`.
- After startup every client message is typed — a 1-byte tag, a 4-byte length, then the body — and all are
  forwarded untouched except `PasswordMessage` (`p`). For that one it prints the token the wrapper generated
  and substitutes the real password before forwarding, so authentication succeeds.
- Copies the server-to-client direction blindly on its own thread, and handles each connection on a thread of
  its own.

This works only because PostgreSQL is started with cleartext `password` authentication. Under the default
scram the token would be hashed by the client, leaving nothing to read and nothing to substitute.


```python
# pgshim.py <listen_port> <upstream_host> <upstream_port> <real_password>
import socket, struct, sys, threading

PORT, UP_HOST, UP_PORT, REPLACEMENT = int(sys.argv[1]), sys.argv[2], int(sys.argv[3]), sys.argv[4]
SSL_REQUEST, PASSWORD_MSG = 80877103, b"p"

def read_exactly(sock, n):
    buf = b""
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk: return None
        buf += chunk
    return buf

def pipe(src, dst):
    try:
        while True:
            data = src.recv(8192)
            if not data: break
            dst.sendall(data)
    except OSError: pass

def handle(client):
    up = socket.create_connection((UP_HOST, UP_PORT))
    try:
        while True:                                     # startup: untyped; decline SSLRequest in place
            head = read_exactly(client, 4)
            if head is None: return
            (length,) = struct.unpack("!I", head)
            body = read_exactly(client, length - 4)
            if body is None: return
            if len(body) >= 4 and struct.unpack("!I", body[:4])[0] == SSL_REQUEST:
                client.sendall(b"N"); continue
            up.sendall(head + body); break
        threading.Thread(target=pipe, args=(up, client), daemon=True).start()
        while True:                                     # typed messages; swap the password
            t = read_exactly(client, 1)
            if t is None: return
            head = read_exactly(client, 4)
            (length,) = struct.unpack("!I", head)
            body = read_exactly(client, length - 4) if length > 4 else b""
            if body is None: return
            if t == PASSWORD_MSG:
                print("token:", body.rstrip(b"\x00").decode("utf-8", "replace"), flush=True)
                new = REPLACEMENT.encode() + b"\x00"
                up.sendall(PASSWORD_MSG + struct.pack("!I", 4 + len(new)) + new)
            else:
                up.sendall(t + head + body)
    except OSError: pass
    finally:
        client.close(); up.close()

srv = socket.socket(); srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
srv.bind(("127.0.0.1", PORT)); srv.listen(64)
print(f"pgshim :{PORT} -> {UP_HOST}:{UP_PORT}", flush=True)
while True:
    c, _ = srv.accept(); threading.Thread(target=handle, args=(c,), daemon=True).start()
```

```bash
python3 pgshim.py 55533 localhost 55433 harness-secret &
export AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE
export AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
export AWS_REGION=eu-central-1
```

```yaml
camunda:
  data:
    secondary-storage:
      type: rdbms
      rdbms:
        url: jdbc:aws-wrapper:postgresql://localhost:55533/camunda?wrapperPlugins=iam&currentSchema=default_schema&iamRegion=eu-central-1&sslmode=disable
        username: camunda
        # no password
  physical-tenants:
    tenanta:
      data:
        secondary-storage:
          rdbms:
            url: jdbc:aws-wrapper:postgresql://localhost:55533/camunda?wrapperPlugins=iam&currentSchema=tenant_a_schema&iamRegion=eu-central-1&sslmode=disable
            username: tenant_a_user
            # no password
```

`iamRegion` is needed only because `localhost` is not an RDS hostname; real Aurora endpoints derive the
region themselves.

### Findings — works as documented

| Check | Observed |
|---|---|
| Boot with no password for either tenant | both partitions `UP` |
| Liquibase over IAM connections | 52 tables in `default_schema`, 52 in `tenant_a_schema` |
| Tokens generated | 4: two for `DBUser=camunda`, two for `DBUser=tenant_a_user` |
| Credential scope | all `AKIAIOSFODNN7EXAMPLE/…/eu-central-1/rds-db/aws4_request` — service `rds-db`, matching `rds-db:connect` |
| One identity, many database users | a single access key across both tenants' tokens |
| Search / get-by-key / cross-tenant credentials | `totalItems=1` vs `0`; `HTTP 200` / `HTTP 404`; `401` both directions |
| Row placement | `tenant_a_schema` 1+1, `default_schema` 0+0 |
| Connections per database user | `camunda` ×2, `tenant_a_user` ×2 |

A sample token (the key is AWS's published example key):

```
localhost:65412/?DBUser=tenant_a_user&Action=connect&X-Amz-Algorithm=AWS4-HMAC-SHA256
&X-Amz-Date=20260728T192315Z&X-Amz-SignedHeaders=host&X-Amz-Expires=900
&X-Amz-Credential=AKIAIOSFODNN7EXAMPLE%2F20260728%2Feu-central-1%2Frds-db%2Faws4_request
&X-Amz-Signature=2f80034b…
```

Four tokens, two distinct `DBUser` values, one access key — "one AWS identity, many tenant-scoped database
users", confirmed on the wire. Only AWS's evaluation of the `rds-db:connect` grant is beyond a local setup.

### A tenant cannot override *only* `username` — by design

Worth knowing, because it is the natural thing to try. With both tenants on one URL and schema, differing
only by database user, boot fails fast:

```
UnifiedConfigurationException: Physical tenants must not share a secondary-storage location, or they would
write into the same database. Use a distinct connection, or a distinct index/table prefix per tenant. To
isolate Oracle physical tenants by schema-per-user (distinct DB users on a shared jdbc url), set
data.secondary-storage.rdbms.database-vendor-id: oracle on each tenant. Conflicts: tenants
[tenanta, default] share the same secondary-storage location [type=rdbms, connection=…, namespace='']
```

`StorageIdentity` treats schema-per-user as Oracle-only, deliberately, "so distinct users never falsely
isolate tenants on, e.g., PostgreSQL/MySQL (where the user does not partition storage)". On Aurora
PostgreSQL a tenant must differentiate its `url` as well — which is exactly what the documented example
does.

---

## Product issues found along the way

Neither is a documentation defect.

1. **Per-tenant RDBMS table prefixes are broken on PostgreSQL** — one of the two escapes the isolation
   validator above recommends. With `data.secondary-storage.rdbms.prefix` set per tenant (`dflt` / `tnta`),
   the isolation check passes and Liquibase then fails: it creates quoted mixed-case tables
   (`tntaGROUP_MEMBER`, `tntaJOB_METRICS_BATCH`, …) but changeset
   `db/changelog/rdbms-exporter/changesets/8.9.0.xml::create_process_instance_state_active_index`
   references the prefixed table unquoted, so PostgreSQL folds it to lower case:
   `ERROR: relation "tntaprocess_instance" does not exist [Failed SQL: CREATE INDEX
   tntaIDX_PROCESS_INSTANCE_STATE_ACTIVE …]`. 44 tables are created before it dies. Deserves its own issue.
2. **`RdbmsConfigurationPerTenantReadersIT` is red on `main`** under `mvn test -pl dist`:
   `NoSuchBeanDefinitionException: RdbmsWriterFactory`, and no such bean exists anywhere in
   `dist/src/main`. It fails in isolation too; recent physical-tenant commits (`5aa0db80c66`,
   `1dbb6c99bfd`) touched it. Separately, `RdbmsDataSources`' javadoc still claims the class is wired with a
   single hardcoded `default` physical tenant, which its only caller contradicts.
