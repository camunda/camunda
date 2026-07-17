# Setup Maven Cache Action

## Intro

Configures the shared GitHub Actions cache for Maven's local repository (`~/.m2/repository`),
and forces `--update-snapshots` / conservative resolver settings so a cache hit never masks a
stale artifact.

The cache key is not a hash of raw file bytes. It is a **content-based dependency fingerprint**
computed by [`dep-fingerprint.py`](../../scripts/dep-fingerprint.py) over the dependency-relevant
XML (`<dependencies>`, `<dependencyManagement>`, `<properties>`, `<build><plugins>`) of every
`pom.xml` in the repo, plus `.mvn/extensions.xml` and the Maven wrapper properties. This exists
because the previous key — `hashFiles('**/pom.xml')` — hashed the raw bytes of all ~150 poms, so
any pom edit anywhere (even one with zero dependency impact) rotated the key and forced an
unnecessary cold Maven resolve on `build-distball` (see camunda/camunda#56950, INC-5949). A
curated static file subset (e.g. only root/BOM/parent poms) was considered and rejected: several
leaf poms in this repo declare dependency versions directly, and `actions/cache` never re-saves
on an exact key hit — so an undetected leaf-pom version bump under a static file set would
silently download the new artifact on every run without ever persisting it to the cache.

Saving only happens on `main` and `stable/*` branches. Other branches (PRs) only **restore** —
they never write back, to avoid every PR run polluting/evicting the shared cache.

## Prerequisites

- The repository must be checked out first (e.g. `actions/checkout`), since this action is
  referenced by local path.
- The Maven wrapper (`./mvnw`) must be present and executable — used to detect the Maven version
  and pick the cache path/key layout accordingly.
- `python3` must be available on the runner (used to compute the dependency fingerprint).

## Usage

### Inputs

|          Input           |                                           Description                                           | Required | Default  |
|--------------------------|-------------------------------------------------------------------------------------------------|----------|----------|
| maven-cache-key-modifier | A modifier key used for the maven cache, can be used to create isolated caches for certain jobs | false    | `shared` |
| maven-wagon-http-pool    | Whether to use a connection pool for HTTP connections                                           | false    | `"true"` |

### Outputs

None. Configures `.mvn/maven.config`, sets `MAVEN_CACHE_PATH`/`MAVEN_CACHE_KEY`/
`MAVEN_CACHE_KEY_PREFIX` env vars, and restores (and, on `main`/`stable/*`, saves) the Maven
local repository cache.

## Notes

- Cache path depends on the detected Maven version: 3.9+ uses
  `~/.m2/repository/cached/releases/` (only release artifacts, via
  `aether.enhancedLocalRepository.split`); older Maven falls back to caching the whole
  `~/.m2/repository/`.
- The dependency fingerprint is deterministic regardless of filesystem iteration order — poms are
  enumerated via `git ls-files` and sorted before hashing. See
  [`test_dep_fingerprint.py`](../../scripts/test_dep_fingerprint.py) for the full behavior
  contract (unrelated edits don't rotate the key, dependency-relevant edits do), verified both
  with synthetic fixtures and against every real pom on the branch under test.
- Respects the `is-cache-enabled` check (`camunda/infra-global-github-actions/is-cache-enabled`),
  which lets a PR label disable the cache entirely for debugging.
- This action is normally consumed indirectly via
  [`setup-build`](../setup-build) rather than used standalone.

## Example

```yaml
steps:
  - uses: actions/checkout@v6
  - uses: ./.github/actions/setup-maven-cache
    with:
      maven-cache-key-modifier: build-shared
```

