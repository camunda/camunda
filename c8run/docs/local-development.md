# Local Development with C8Run

C8Run lets internal developers run the Orchestration Cluster locally for rapid development and testing — no Kubernetes required.

## Prerequisites

| Requirement | Notes |
|---|---|
| **JDK** | ≥ 21. `JAVA_HOME` must be set. (C8Run 8.8 and earlier had a known compatibility constraint with JDK > 23 — use JDK 21–23 for those versions.) |
| **Go** | 1.25+ (see `go.mod`) |
| **Maven** | Configured with Camunda Nexus access (required when testing local code changes) |
| **JFrog access** | Required for downloading artifacts |
| **Camunda repo** | `camunda/camunda` cloned locally |

### Nexus credentials

For the `packager package` command, export the following before running:

```bash
export JAVA_ARTIFACTS_USER=firstname.lastname
export JAVA_ARTIFACTS_PASSWORD=<your Okta password>
```

These go in `c8run/.env` for persistent local use. Never commit `.env`.

## Quick Start (Pre-built distribution)

Use this when you want to run the version pinned in `.env` without building Camunda from source.

From `c8run/`:

```bash
go build -o c8run ./cmd/c8run
go build -o packager ./cmd/packager
./packager package
./start.sh
```

Access your environment:

| Endpoint | URL |
|---|---|
| REST API | http://localhost:8080 |
| gRPC API | http://localhost:26500 |
| Management API | http://localhost:9600/actuator |

Default credentials: `demo` / `demo`

## Testing Your Code Changes

Use this when you want to run C8Run against a locally built Camunda distribution.

### 1. Build the Camunda distribution

From the repository root:

```bash
./mvnw -B -T1C -DskipTests -DskipChecks -Dflatten.skip=true \
  -Dskip.fe.build=false -DskipOptimize clean package
```

This produces: `dist/target/camunda-zeebe-<version>-SNAPSHOT.tar.gz`

### 2. Copy the distribution to C8Run and update `.env`

```bash
cp ./dist/target/camunda-zeebe-*.tar.gz ./c8run/
```

Edit `c8run/.env` and update `CAMUNDA_VERSION` to match your snapshot version.

### 3. Package and run

```bash
cd c8run
go build -o c8run ./cmd/c8run
go build -o packager ./cmd/packager
./packager package
./start.sh
```
