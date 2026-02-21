# Phase 2: Build Artifact Sharing (Week 2-8)

> Back to [CI/CD Improvement Plan](./README.md)

**Goal**: Eliminate redundant compilation across CI jobs.

> **Note**: Phase 2 starts in parallel with Phase 1 (Week 2), not after it. Reducing the ~75
> redundant builds per PR also reduces runner resource contention, which itself contributes to
> flakiness. The shared build investigation should begin immediately after Phase 0 quick wins land.

---

## 5.1 Implement a Shared Build Job

**Impact**: Eliminate ~75 redundant Maven builds across 22 workflow files (~200-600 runner-minutes saved per PR)
**Risk**: Medium-High (introduces single point of failure — see risks below)
**Effort**: High

This is the **single highest-impact change**. Currently every test job independently runs `./.github/actions/build-zeebe` which does a full `./mvnw -B -T1C -DskipTests -DskipChecks install`.

**Target architecture**:
```
build-artifacts (single job, gcp-perf-core-16)
  |  ./mvnw install -DskipTests -DskipChecks -T1C
  |  share via cache (primary) or upload-artifact (fallback)
  |
  +-- general-unit-tests (restore cache -> run tests only)
  +-- zeebe-unit-tests[7] (restore cache -> run tests only)
  +-- integration-tests[12] (restore cache -> run tests only)
  +-- database-integration-tests[7] (restore cache -> run tests only)
  +-- operate-ci (restore cache -> run tests only)
  +-- tasklist-ci (restore cache -> run tests only)
  +-- optimize-ci (restore cache -> run tests only)
```

### Approach Comparison

| Approach | Pros | Cons | Recommendation |
|----------|------|------|----------------|
| **`actions/cache@v5`** (SHA-keyed) | Handles concurrent access well, no storage limits per run | 10 GB per-repo limit, LRU eviction | **Primary approach** |
| **Remote Maven Build Cache** (S3/GCS-backed) | Content-addressable, cross-job reuse, incremental | Requires infrastructure (S3 bucket), cache extension already installed | **Evaluate for Phase 2.5** |
| **`actions/upload-artifact@v4`** | Simple, per-run isolation | 10 GB per-run limit, thundering-herd downloads, single point of failure | **Fallback only** for non-Maven outputs |
| **Develocity (Gradle Enterprise for Maven)** | Full build cache + build scan analytics | Commercial license, infrastructure | **Future evaluation** |

### Scaling Risks

- **Single point of failure**: The build-artifacts job becomes a critical path bottleneck. If it fails, everything waits. Today's redundant builds are embarrassingly parallel.
- **Thundering herd**: 40+ downstream jobs simultaneously downloading a multi-GB artifact can cause download failures — a new source of CI flakiness.
- **Storage limits**: `~/.m2/repository/io/camunda` for 144 modules may be several hundred MB to multiple GB. Measure the actual size before committing.

### Implementation Steps

1. **Measure first**: Run a full build and measure the size of `~/.m2/repository/io/camunda`
2. **Primary**: Use `actions/cache@v5` keyed by `hashFiles('**/pom.xml')-${{ github.sha }}` — better concurrent access handling than artifacts
3. **Selective sharing**: Share only `~/.m2/repository/io/camunda`, not the entire workspace
4. **Graceful fallback**: If cache restore fails, the job should fall back to building locally (like today) rather than failing the entire pipeline
5. **Cleanup**: Set `retention-days: 1` on any artifacts to prevent storage bloat
6. Phase in: start with unit test jobs, then integration tests
7. **Evaluate remote cache**: Investigate S3/GCS-backed Maven Build Cache (the extension is already installed at `.mvn/maven-build-cache-config.xml`) as a more robust long-term solution

---

## 5.2 Share Frontend Build Outputs

**Impact**: Save ~18-48 min in deploy jobs
**Risk**: Low
**Effort**: Medium

The `build-platform-frontend` job already builds all 4 frontends. Upload the output:

```yaml
build-platform-frontend:
  steps:
    # ... existing build steps ...
    - uses: actions/upload-artifact@v4
      with:
        name: frontend-builds
        path: |
          operate/client/build/
          tasklist/client/build/
          identity/client/build/

deploy-snapshots:
  needs: [check-results, build-platform-frontend]
  steps:
    - uses: actions/download-artifact@v4
      with:
        name: frontend-builds
    # Skip frontend rebuild, go straight to Maven deploy
```

---

## 5.3 Add Docker Layer Caching

**Impact**: Faster Docker builds across all workflows
**Risk**: Low
**Effort**: Medium

For all `docker/build-push-action` steps, add GHA cache backend:
```yaml
- uses: docker/build-push-action@v6
  with:
    cache-from: type=gha
    cache-to: type=gha,mode=max
```

Applies to:
- `ci.yml` -> `docker-checks` job
- `ci-zeebe.yml` -> `docker-checks` job
- `deploy-camunda-docker-snapshot` job
- All integration test jobs that build Docker images

---

## 5.4 Consolidate Maven Cache Modifiers

**Impact**: Better cache reuse across jobs
**Risk**: Low
**Effort**: Low

Reduce from ~20+ unique cache key modifiers to 4:

| Modifier | Used By |
|----------|---------|
| `build` | Build artifact job |
| `test-ut` | All unit test jobs |
| `test-it` | All integration test jobs |
| `deploy` | Deploy jobs |
