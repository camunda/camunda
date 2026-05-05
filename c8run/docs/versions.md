# Version Branches

## Branch Layout

Each minor version lives on its own stable branch. The latest unreleased minor version develops on
`main` until its first stable release, at which point a new `stable/x.y` branch is cut and `main`
moves on to the next minor.

| Branch | Version | Status |
|---|---|---|
| `stable/8.7` | 8.7.x | Maintenance |
| `stable/8.8` | 8.8.x | Maintenance |
| `stable/8.9` | 8.9.x | Maintenance |
| `main` | 8.10.x (alpha) → 8.11 once `stable/8.10` is cut | Active development |

> [!IMPORTANT]
> Breaking changes must never be backported to stable branches.
> Bug fixes and security patches may be backported; new features and architectural changes must not.

---

## Feature Matrix

| Feature | 8.7 | 8.8 | 8.9 | main (8.10+) |
|---|---|---|---|---|
| Supported JDK range | 21–23 | 21–23 | 21–25 | 21–25 |
| Elasticsearch bundled | ✓ v8.13.4 | ✓ v8.17.3 | ✗ | ✗ |
| `--disable-elasticsearch` flag | ✓ | ✓ | ✗ | ✗ |
| Docker Compose bundled (`--docker` flag) | ✓ | ✓ | ✓ | ✗ |
| RDBMS secondary storage | ✗ | ✗ | ✓ | ✓ |
| H2 as default secondary storage | ✗ | ✗ | ✓ | ✓ |
| `--extra-driver` flag | ✗ | ✗ | ✓ | ✓ |

---

## Per-Version Notes

### 8.7 (`stable/8.7`)

**Supported JDK:** OpenJDK 21–23. JDK 24+ is not supported on this branch.

C8Run bundles Camunda, Elasticsearch, and Connectors into a single archive.

- Elasticsearch (v8.13.4) is started and managed as a child process.
- `--disable-elasticsearch` skips the bundled Elasticsearch so an external instance can be used.
- `--docker` starts the stack via the bundled Docker Compose files instead of the native process
  manager.
- No secondary storage abstraction — Elasticsearch is the only supported backend.

### 8.8 (`stable/8.8`)

**Supported JDK:** OpenJDK 21–23. JDK 24+ is not supported on this branch.

Elasticsearch (v8.17.3) is still bundled. `--disable-elasticsearch` is removed — the bundled
Elasticsearch is always started.

- `--username`, `--password`, and `--startup-url` flags added.
- `--docker` flag retained.
- No RDBMS support.

### 8.9 (`stable/8.9`)

**Supported JDK:** OpenJDK 21–25.

Major architectural shift: Elasticsearch is no longer bundled. H2 becomes the default secondary
storage, and RDBMS support is introduced.

- H2 runs file-based by default (`jdbc:h2:file:./camunda-data/h2db`) — data persists across
  restarts.
- `--extra-driver` flag added (repeatable) to supply custom JDBC driver JARs for external RDBMS.
- `--docker` flag is still present (Docker Compose variant ships alongside the Java-only archive).
- `--disable-elasticsearch` removed entirely.
- Go minimum bumped to 1.24.

### main (8.10 alpha / future 8.11)

**Supported JDK:** OpenJDK 21–25.

Docker Compose is fully decoupled — c8run is now a Java-only distribution. The Docker Compose
variant is published separately.

- `--docker` flag removed.
- RDBMS support and H2 defaults retained from 8.9.
- Go minimum is 1.25.

---

## Backport Policy

- **Never backport:** breaking API or flag changes, architectural changes, new dependencies.
- **May backport:** bug fixes, security patches, documentation corrections.

When fixing a bug that affects multiple versions, open separate PRs targeting each stable branch
rather than merging to `main` and cherry-picking — this avoids accidentally pulling in unrelated
`main` changes.
