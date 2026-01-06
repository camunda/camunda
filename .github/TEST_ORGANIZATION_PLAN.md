# Test Organization Plan Based on CODEOWNERS

## Executive Summary

This document outlines options and plans for reorganizing the CI test structure to align with the CODEOWNERS file, providing clear ownership, meaningful test names, and flexibility for future adjustments.

## Current State Analysis

### Current Test Organization (Detailed)

### Current Test Organization (Detailed)

#### 0. **Workflow Architecture Overview**

**Main Orchestrator**: `ci.yml`
- Central workflow that coordinates all CI testing
- Calls specialized workflows using `workflow_call`
- Makes decisions based on change detection

**Specialized Product Workflows** (called by ci.yml):
```yaml
# In ci.yml:
zeebe-ci:
  uses: ./.github/workflows/ci-zeebe.yml

operate-ci:
  uses: ./.github/workflows/ci-operate.yml

tasklist-ci:
  uses: ./.github/workflows/ci-tasklist.yml

optimize-ci:
  uses: ./.github/workflows/ci-optimize.yml

client-components:
  uses: ./.github/workflows/ci-client-components.yml
```

**Why this architecture?**
- ✅ Separation of concerns: Each product has its own specialized tests
- ✅ Conditional execution: Only run tests for changed components
- ✅ Reusability: Workflows can be called from ci.yml OR manually via workflow_dispatch
- ⚠️ Creates fragmentation: Ownership scattered across multiple files

#### Detailed Analysis by Workflow Type

### A. Main CI Workflow (`ci.yml`)

**Contains**:
- General unit tests (non-product-specific modules)
- Zeebe unit tests (organized by team)
- Integration tests (for all products)
- RDBMS integration tests
- ArchUnit tests
- Docker tests
- Linting/formatting checks
- Snapshot deployment

**Organization**:
- ✅ Well-structured unit tests with team-based matrix
- ❌ Integration tests use manual owner field
- ⚠️ Mixes product-agnostic and product-specific tests

### B. Product-Specific CI Workflows

#### `ci-zeebe.yml` (owner: @camunda/core-features)

**Contains**:
```yaml
Jobs:
- smoke-tests: Cross-platform tests (Windows, Linux, macOS, ARM)
- property-tests: Property-based randomized testing
- performance-tests: JMH benchmarks
- strace-tests: Tests requiring Linux strace
- docker-checks: Docker build verification + Hadolint
- deploy-docker-snapshot: Pushes Zeebe Docker image
- deploy-benchmark-images: GCR benchmark images
- deploy-snyk-projects: Security scanning
- auto-merge: Automated backport PR merging
```

**Ownership in metadata**:
```yaml
env:
  TEST_OWNER: Core Features  # Manual field
  TEST_PRODUCT: Zeebe
```

**Problems**:
- ✅ Good: Specialized Zeebe-only tests (smoke, property, strace)
- ❌ Owner in file header says `@camunda/core-features` but many tests owned by Distributed Platform
  - `smoke-tests` → user_description: "team-distributed-systems"
  - `strace-tests` → user_description: "team-distributed-systems"
  - `docker-checks` → user_description: "team-distributed-systems"
- ❌ Inconsistent: `TEST_OWNER: Core Features` vs `user_description: "team-distributed-systems"`
- ❌ No clear way to determine from CODEOWNERS which team owns these specialized tests

#### `ci-operate.yml` (owner: @camunda/core-features)

**Contains**:
```yaml
Jobs:
- operate-backend-unit-tests: Uses test suites (DataLayer, CoreFeatures)
- build-operate-backend: Build verification
- build-operate-frontend: Frontend build
- operate-backup-restore-tests-es: Backup/restore on Elasticsearch
- operate-backup-restore-tests-os: Backup/restore on OpenSearch
- operate-importer-tests-es: Importer tests on ES
- operate-importer-tests-os: Importer tests on OS
- operate-frontend-tests: Frontend unit/integration
- docker-tests: Docker verification
```

**Organization**:
- ✅ Already team-based! Unit tests split by suite (DataLayer, CoreFeatures)
- ✅ Job names clearly indicate ownership: `"Operate / Unit - {suiteName}"`
- ⚠️ Some jobs have unclear ownership:
  - `build-operate-backend` → owner: "Core Features"
  - `operate-importer-tests` → Should be Data Layer (importers are their domain)
  - `docker-tests` → Unclear (DevOps? Core Features?)

#### `ci-tasklist.yml` (owner: @camunda/core-features)

**Contains**: Similar structure to Operate
```yaml
Jobs:
- tasklist-backend-unit-tests: Test suites (DataLayer, CoreFeatures)
- build-tasklist-backend
- build-tasklist-frontend
- tasklist-backup-restore-tests-es/os
- tasklist-integration-tests-es/os
- tasklist-frontend-tests
- docker-tests
```

**Organization**: ✅ Same as Operate, already team-based for unit tests

#### `ci-optimize.yml` (owner: @camunda/core-features)

**Contains**: Similar structure
```yaml
Jobs:
- optimize-backend-unit-tests: Test suites (DataLayer, CoreFeatures)
- build-optimize-backend
- optimize-frontend-tests
- (separate workflows for E2E tests)
```

**Organization**: ✅ Consistent with Operate/Tasklist

#### `ci-client-components.yml`

**Contains**: Frontend component library tests
**Owner**: Likely CamundaEx team (clients)

### C. Specialized Workflow Files (Not Called by ci.yml)

These are SEPARATE workflows with their own triggers:

#### E2E Test Workflows:
- `operate-e2e-tests.yml` → @camunda/core-features (currently disabled)
- `tasklist-e2e-tests.yml` → @camunda/core-features
- `identity-e2e-tests.yml` → @camunda/identity
- `optimize-e2e-tests-sm.yml` → @camunda/core-features
- `c8-orchestration-cluster-e2e-tests-*.yml` → Various teams

#### Load/Performance Test Workflows:
- `camunda-daily-load-tests.yml` → @camunda/zeebe-distributed-platform
- `zeebe-weekly-e2e.yml` → @camunda/zeebe-distributed-platform
- `zeebe-medic-benchmarks.yml` → @camunda/zeebe-distributed-platform
- `zeebe-pr-benchmark.yaml` → @camunda/zeebe-distributed-platform

#### Nightly/Scheduled Workflows:
- `zeebe-daily-qa.yml`
- `zeebe-rdbms-integration-tests.yml`
- `zeebe-version-compatibility.yml`
- `camunda-scheduled-release-migration-test.yml`

**Problems with Separate Workflows**:
- ❌ Ownership only in file header comments
- ❌ Not visible in PR checks (unless explicitly triggered)
- ❌ No standardized naming convention
- ❌ Harder to track ownership changes
- ⚠️ Some should perhaps be integrated into ci.yml for better visibility

### Current Ownership Documentation Methods

#### Method 1: File Header Comments (Most Common)
```yaml
# owner: @camunda/core-features
name: Operate CI
```
**Used in**: All ci-*.yml files, E2E tests, specialized workflows
**Problems**:
- Not programmatically accessible
- Can drift from CODEOWNERS
- Not visible in job names

#### Method 2: Job-level Env Vars
```yaml
env:
  TEST_OWNER: Core Features
  TEST_PRODUCT: Zeebe
  TEST_TYPE: Unit
```
**Used in**: ci-zeebe.yml, ci-operate.yml, ci-tasklist.yml
**Problems**:
- Manual maintenance
- Inconsistent values (team names vs display names)
- Not always used in observe-build-status

#### Method 3: observe-build-status user_description
```yaml
- name: Observe build status
  with:
    user_description: "team-distributed-systems"
```
**Used in**: Inconsistently across workflows
**Problems**:
- Different from TEST_OWNER in same job!
- Sometimes uses team name, sometimes display name
- Ad-hoc, not standardized

#### Method 4: Test Suite Classes (Code-based)
```java
@Suite
@SelectClasses({...})
class OperateDataLayerTestSuite {}
```
**Used in**: Operate, Tasklist, Optimize
**Works well**: ✅ Clear ownership at test level

#### Method 5: Matrix owner Field
```yaml
matrix:
  include:
    - group: zeebe-engine
      owner: "Core Features"
```
**Used in**: ci.yml integration tests
**Problems**: Manual, can drift

### Architecture Problems

1. **Ownership Fragmentation**:
   - File header: `@camunda/core-features`
   - TEST_OWNER: `"Core Features"`
   - user_description: `"team-distributed-systems"`
   - observe-build-status: Sometimes no owner specified
   **All in the SAME workflow!**

2. **No Single Source of Truth**:
   - CODEOWNERS has module ownership
   - ci-*.yml files have workflow ownership (in header)
   - Individual jobs have owner metadata (in env/matrix)
   - observe-build-status has user_description
   - Test suites have ownership in Java code
   **Which one is correct?**

3. **Specialized Tests Lack Clear Ownership**:
   - Who owns `smoke-tests`? File says Core Features, observe says Distributed Platform
   - Who owns `docker-checks`? Is it DevOps, Distributed Platform, or Core Features?
   - Who owns `strace-tests`? Property tests? Performance tests?
   - CODEOWNERS can't express "smoke test ownership" - it's about modules

4. **Separate Workflows Invisible in PRs**:
   - E2E tests run separately, not in PR checks
   - Load tests are scheduled/manual
   - Nightly tests not visible until they fail
   - Hard to see complete test coverage for a PR

5. **Conditional Workflow Calls Create Gaps**:
   ```yaml
   zeebe-ci:
     if: needs.detect-changes.outputs.zeebe-changes == 'true'
   ```
   - If change detection misses a dependency, tests don't run
   - Ownership doesn't help if tests are skipped
   - No "safety net" for unexpected changes

#### 1. **Unit Tests** (ci.yml & separate CI workflows)

**General Unit Tests** (`general-unit-tests` job):
- Runs all modules NOT in Zeebe, Operate, Tasklist, or Optimize
- Includes: Identity, Authentication, Security, Schema Manager, Configuration, etc.
- **Owner**: Not explicitly specified (falls back to "General")
- **Problem**: New modules auto-included without clear ownership

**Zeebe Unit Tests** (`zeebe-unit-tests` job) - **Already well-organized by ownership!**:
```yaml
Matrix includes:
- Component: General      → Suite: CoreFeatures     → Modules: bpmn-model, dmn, feel, protocol, etc.
- Component: Protocol     → Suite: CoreFeatures     → Modules: protocol-*
- Component: Gateway      → Suite: CoreFeatures     → Modules: gateway-*
- Component: Exporter     → Suite: DataLayer        → Modules: exporters, exporter-api
- Component: Backup       → Suite: DistributedSystems → Modules: backup-*
- Component: Distributed  → Suite: DistributedSystems → Modules: atomix, broker, journal, etc.
- Component: Client       → Suite: CamundaEx        → Modules: zeebe-client-java
```
**Naming format**: `"Zeebe / Unit - ${{matrix.component}}"` + Suite in metadata
**Ownership**: Clear via `suite` field (CoreFeatures, DataLayer, DistributedSystems, CamundaEx)
**✅ This is good!** Already follows team-based organization

**Operate Unit Tests** (ci-operate.yml):
```yaml
Matrix:
- suite: DataLayer → suiteName: Data Layer
- suite: CoreFeatures → suiteName: Core Features
```
**Naming format**: `"Operate / Unit - {suiteName}"`
**Ownership**: Clear via `TEST_OWNER` env var
**✅ Also well-organized!** Uses test suites defined in code

**Tasklist/Optimize Unit Tests**: Similar pattern to Operate
**✅ Web apps already follow team-based split!**

**ArchUnit Tests** (`archunit-tests` job):
- **Owner**: "General"
- **Problem**: Generic ownership, should be shared or assigned

#### 2. **Integration Tests** (ci.yml - `integration-tests` job)

**Current Matrix Structure**:
```yaml
- group: root
  name: "Root Acceptance Tests"
  modules: "'qa/acceptance-tests'"
  owner: "QA"

- group: zeebe-ds-modules
  name: "Zeebe Distributed Platform Modules"
  modules: "atomix,backup,journal,logstreams,snapshot,..."  # Excludes engine
  owner: "Distributed Platform"

- group: zeebe-engine-modules
  name: "Zeebe Engine Modules"
  modules: "!atomix,!backup,!journal,...' -f zeebe"  # Everything NOT in DS
  owner: "Core Features"

- group: schema-manager
  name: "Schema Manager"
  owner: "Data Layer"

- group: zeebe-engine-integration
  name: "Zeebe Engine QA"
  modules: "zeebe/qa/integration-tests"
  test-filter: "io.camunda.zeebe.it.engine.**"
  owner: "Core Features"

- group: zeebe-ds-integration
  name: "Zeebe Cluster QA"
  modules: "zeebe/qa/integration-tests"
  test-filter: "io.camunda.zeebe.it.cluster.**"
  owner: "Distributed Platform"

- group: zeebe-shared-integration
  name: "Zeebe General QA"
  modules: "zeebe/qa/integration-tests"
  test-filter: "!io.camunda.zeebe.it.engine.**,!io.camunda.zeebe.it.cluster.**"
  owner: "General"

- group: qa-update
  name: "Zeebe QA - Update"
  owner: "Core Features"

- group: qa-camunda-process-test
  name: "Camunda Process"
  owner: "CamundaEx"
```

**Naming format**: `"${{ matrix.module }} / Integration / ${{ matrix.name }} ITs"`
**Problems**:
- ❌ `matrix.module` often empty or generic
- ❌ Job names show as "Zeebe / Integration / Zeebe Distributed Platform Modules ITs" (redundant)
- ❌ Owner is manual and can drift from CODEOWNERS
- ⚠️ `zeebe-engine-modules` uses negative excludes (fragile - adding new module breaks it)

#### 3. **E2E Tests** (separate workflow files)

**Operate E2E**: `operate-e2e-tests.yml`
- **Owner**: `@camunda/core-features` (in file header)
- **Naming**: `"[Legacy] Operate / E2E Tests"`
- **Status**: Currently disabled (`if: false`)

**Tasklist E2E**: `tasklist-e2e-tests.yml`
- **Owner**: `@camunda/core-features` (in file header)
- **Naming**: Similar pattern

**Identity E2E**: `identity-e2e-tests.yml`
- **Owner**: `@camunda/identity` (in file header)

**C8 Orchestration E2E**: Multiple files (nightly, on-demand, release)
- **Owner**: Various (likely monorepo-devops-team)

**Problems**:
- ❌ Ownership only in file header comments, not in job names
- ❌ No consistent naming convention across E2E tests
- ❌ Different trigger mechanisms (some in ci.yml, some separate)

#### 4. **RDBMS Integration Tests** (ci.yml - separate job)

```yaml
rdbms-integration-tests:
  name: "[IT] RDBMS"
  owner: Not specified (but should be Data Layer)
```

### Current CODEOWNERS Coverage Analysis

#### ✅ **Well-defined module-level ownership**:
- `/zeebe/atomix`, `/zeebe/backup`, `/zeebe/journal`, etc. → `@camunda/zeebe-distributed-platform`
- `/operate/*`, `/tasklist/*`, `/optimize/*` → `@camunda/core-features` (split by test suite)
- `/identity/*`, `/authentication/*`, `/security/*` → `@camunda/identity`
- `/clients/*`, `/testing/*` → `@camunda/camundaex`
- `/c8run/` → `@camunda/distribution`
- `.github/workflows/` → `@camunda/monorepo-devops-team` (for workflow files themselves)

#### ⚠️ **Package-level ownership** (more granular than module):
```
# Zeebe broker - ownership at PACKAGE level, not module level
/zeebe/broker/src/test/java/io/camunda/zeebe/broker/backup → @camunda/zeebe-distributed-platform
/zeebe/broker/src/main/java/io/camunda/zeebe/broker/clustering → @camunda/zeebe-distributed-platform
/zeebe/broker/src/main/java/io/camunda/zeebe/broker/logstreams → @camunda/zeebe-distributed-platform
/zeebe/broker/src/main/java/io/camunda/zeebe/broker/partitioning → @camunda/zeebe-distributed-platform
/zeebe/broker/src/main/java/io/camunda/zeebe/broker/raft → @camunda/zeebe-distributed-platform
/zeebe/broker/src/main/java/io/camunda/zeebe/broker/transport → @camunda/zeebe-distributed-platform
```
**Problem**: `/zeebe/broker/` module as a whole has NO owner, only specific packages!
**Implication**: Tests for the broker module need to be split or assigned to shared ownership

#### ⚠️ **Unit test suite-level ownership** (in code, not in CODEOWNERS):
```java
// Operate has test suites defined in code:
/operate/common/src/test/java/io/camunda/operate/OperateDataLayerTestSuite.java → @camunda/data-layer
/operate/webapp/src/test/java/io/camunda/operate/OperateCoreFeaturesTestSuite.java → @camunda/core-features
```
**This works because**: Operate/Tasklist/Optimize have explicit test suite classes
**Zeebe doesn't have this**: Ownership determined by module grouping in CI matrix

#### ❌ **Gaps/Unclear ownership** (modules without CODEOWNERS entry):
- `/zeebe/engine/` - Not listed (should be core-features)
- `/zeebe/gateway/` - Not listed (should be core-features or distributed-platform?)
- `/zeebe/broker/` (main module) - Only sub-packages listed, no module-level owner
- `/zeebe/bpmn-model/` - Not listed (should be core-features)
- `/zeebe/dmn/` - Not listed (should be core-features)
- `/zeebe/feel/` - Not listed (should be core-features)
- `/zeebe/protocol/` - Not listed (should be core-features)
- `/zeebe/util/`, `/zeebe/test-util/` - Not listed (utilities - shared?)
- `/zeebe/exporters/` - Not listed at module level (should be data-layer)
- `/zeebe/exporter-api/` - Not listed (should be data-layer)
- `/schema-manager/` - Not listed (should be data-layer)
- `/configuration/` - Not listed (should be shared or specific team)
- `/db/` - Not listed (should be data-layer)
- `/search/` - Not listed (should be data-layer)

### Problems with Current Approach

1. **Inconsistent organization across test types**:
   - ✅ Unit tests: Well-organized by team (Zeebe, Operate, Tasklist, Optimize)
   - ❌ Integration tests: Manual owner field, no clear convention
   - ❌ E2E tests: Owner only in file headers, inconsistent naming
   - ❌ RDBMS tests: No explicit owner

2. **Job naming inconsistency**:
   - Unit tests: `"Zeebe / Unit - {Component}"` ✅
   - Integration tests: `"{Module} / Integration / {Name} ITs"` ❌ (Module often empty)
   - E2E tests: Various formats, no standard ❌

3. **Package-level vs Module-level ownership conflict**:
   - CODEOWNERS has package-level entries for `/zeebe/broker/`
   - Tests run at module level
   - No clear way to map package ownership to test jobs

4. **Manual maintenance of owner field**:
   - Integration test matrix has manual `owner:` field
   - Can drift from CODEOWNERS
   - Not auto-updated when ownership changes

5. **Negative module excludes are fragile**:
   ```yaml
   # This breaks when you add a new module!
   maven-modules: "'!qa/integration-tests,!qa/update-tests,!atomix,!backup,..."
   ```

6. **General/Shared ownership is unclear**:
   - `"General"` owner used as catch-all
   - No clear process for shared modules
   - `general-unit-tests` auto-includes new modules without owner review

7. **CODEOWNERS gaps mean tests have no owner**:
   - Many zeebe modules not in CODEOWNERS
   - Fall into "General" or "Core Features" by default
   - May not be correct ownership

8. **Test suite ownership defined in code, not CODEOWNERS**:
   - Operate/Tasklist have `@Suite` annotations for ownership
   - Works but duplicates ownership information
   - CODEOWNERS and test suites can drift

---

## Options for Test Organization

### Comparison with Existing CI Setup

Before diving into options, let's compare what we have vs what we need:

| Aspect | Current State | Desired State |
|--------|---------------|---------------|
| **Unit Tests** | ✅ Already team-based (Zeebe, Operate, Tasklist) | Keep as-is, ensure consistency |
| **Integration Tests** | ❌ Mix of module groups and manual owner field | Auto-derive from CODEOWNERS |
| **E2E Tests** | ⚠️ Owner in file header only | Owner in job name |
| **Job Naming** | ⚠️ Inconsistent formats | Standardized format across all test types |
| **CODEOWNERS Coverage** | ⚠️ ~60% coverage, missing key modules | 95%+ coverage |
| **Package Ownership** | ❌ Not handled in test organization | Clear strategy for package-level splits |
| **Shared Modules** | ❌ No clear pattern | Explicit "Shared" category or smart splits |
| **New Module Handling** | ❌ Falls into "General" catch-all | Explicit warning + assigned to Unowned |

**Key Insight**: Unit tests are already well-organized! Integration and E2E tests need the most work.

### Option 1: Team-Based Test Jobs (Recommended)

**Approach**: Organize test jobs by owning team, auto-derived from CODEOWNERS.

**Pros**:
- Clear ownership: Team name in job name
- Aligns with organizational structure
- Easy to see "my team's tests" in CI
- Natural aggregation of related modules
- Better for alerting/notifications (team Slack channels)

**Cons**:
- Large teams may have long-running jobs
- Cross-team dependencies less visible
- Requires good CODEOWNERS coverage

**Example Job Names**:
```
Integration / Zeebe Distributed Platform
Integration / Zeebe Core Features
Integration / Data Layer
Integration / Identity
Integration / CamundaEx SDK & Testing
Integration / Shared (Unowned Modules)
```

### Option 2: Module-Based with Owner Metadata

**Approach**: Keep current module-based organization but enrich with owner metadata.

**Pros**:
- Minimal changes to existing structure
- Module granularity preserved
- Easy to understand what's being tested
- Can still route alerts by owner

**Cons**:
- Many jobs in the CI list
- Ownership still requires manual maintenance
- Harder to see "all my team's tests"

**Example Job Names**:
```
Integration / Zeebe Atomix [@zeebe-distributed-platform]
Integration / Zeebe Engine [@core-features]
Integration / Schema Manager [@data-layer]
```

### Option 3: Hybrid Team + Critical Path

**Approach**: Organize by team but separate critical/frequently-failing tests.

**Pros**:
- Fast feedback on critical paths
- Team ownership clear
- Can optimize critical tests differently

**Cons**:
- More complex to maintain
- What's "critical" may change
- Potential duplication

**Example Job Names**:
```
Integration / Zeebe Distributed Platform / Critical
Integration / Zeebe Distributed Platform / Extended
Integration / Zeebe Core Features / Critical
Integration / Zeebe Core Features / Extended
```

### Option 4: Domain-Based (Functional Areas)

**Approach**: Organize by functional domain regardless of team.

**Pros**:
- Domain experts can follow specific areas
- Natural for cross-functional work
- Good for feature teams

**Cons**:
- Ownership less clear
- May not align with team structure
- Domain boundaries can be fuzzy

**Example Job Names**:
```
Integration / Process Execution
Integration / Clustering & Distribution
Integration / Data Import & Export
Integration / Authentication & Authorization
```

---

## Recommended Solution: Option 1 with Enhancements

**Team-Based Test Jobs with Auto-Detection and Safety Nets**

### Implementation Plan

#### Phase 1: CODEOWNERS Enhancement

1. **Complete CODEOWNERS coverage at MODULE level**
   ```
   # Add missing module-level entries
   /zeebe/engine/ @camunda/zeebe-core-features
   /zeebe/gateway/ @camunda/zeebe-core-features
   /zeebe/bpmn-model/ @camunda/zeebe-core-features
   /zeebe/dmn/ @camunda/zeebe-core-features
   /zeebe/feel/ @camunda/zeebe-core-features
   /zeebe/protocol/ @camunda/zeebe-core-features
   /zeebe/exporters/ @camunda/data-layer
   /zeebe/exporter-api/ @camunda/data-layer
   /zeebe/exporter-common/ @camunda/data-layer
   /schema-manager/ @camunda/data-layer
   /db/ @camunda/data-layer
   /search/ @camunda/data-layer

   # Shared ownership explicitly marked
   /zeebe/broker/ @camunda/zeebe-distributed-platform @camunda/zeebe-core-features
   /zeebe/exporter-api/ @camunda/data-layer @camunda/zeebe-distributed-platform
   ```

2. **Handling package-level ownership**

   **Problem**: Some modules (like `/zeebe/broker/`) have package-level ownership:
   ```
   /zeebe/broker/src/main/java/io/camunda/zeebe/broker/clustering → @camunda/zeebe-distributed-platform
   /zeebe/broker/src/main/java/io/camunda/zeebe/broker/engine → @camunda/zeebe-core-features
   ```

   **Solutions** (choose based on team preference):

   **Option A: Module-level ownership takes precedence for tests**
   - Add `/zeebe/broker/ @camunda/zeebe-distributed-platform @camunda/zeebe-core-features`
   - Tests run at module level with both teams as owners
   - Package-level entries still used for PR reviews
   ```yaml
   # Result:
   - name: "Integration / Shared / Broker"
     owner: "@camunda/zeebe-distributed-platform,@camunda/zeebe-core-features"
   ```

   **Option B: Split tests by package ownership** (More granular)
   - Don't add module-level entry
   - Create test jobs based on package paths
   - Use test filters to match package ownership
   ```yaml
   # Result:
   - name: "Integration / Zeebe Distributed Platform / Broker Clustering"
     test-filter: "io.camunda.zeebe.broker.clustering.**"
     owner: "@camunda/zeebe-distributed-platform"

   - name: "Integration / Zeebe Core Features / Broker Engine"
     test-filter: "io.camunda.zeebe.broker.engine.**"
     owner: "@camunda/zeebe-core-features"
   ```

   **Option C: Hybrid approach** (Recommended for broker)
   - Add module-level "shared" ownership
   - Keep package-level for PR reviews
   - Split tests ONLY if duration becomes problem
   ```yaml
   # Default:
   - name: "Integration / Shared / Broker"
     owner: "@camunda/zeebe-distributed-platform,@camunda/zeebe-core-features"

   # If tests take >90 minutes, then split:
   - name: "Integration / Zeebe Distributed Platform / Broker Clustering & Raft"
     modules: "broker"
     test-filter: "**/broker/clustering/**,**/broker/raft/**,**/broker/transport/**"

   - name: "Integration / Zeebe Core Features / Broker Engine & Processing"
     modules: "broker"
     test-filter: "**/broker/engine/**,**/broker/exporter/**"
   ```

3. **Handling test suite-based ownership** (Operate, Tasklist, Optimize)

   **Current approach** (works well, keep it!):
   - Test suites defined in code with `@Suite` annotations:
     ```java
     @Suite
     @SelectClasses({...})
     class OperateDataLayerTestSuite {}

     @Suite
     @SelectClasses({...})
     class OperateCoreFeaturesTestSuite {}
     ```
   - CI matrix references these suites
   - Ownership determined by suite name

   **Recommendation**: Keep this approach for web apps (Operate, Tasklist, Optimize)
   - ✅ Works well for cross-cutting concerns
   - ✅ Allows fine-grained ownership within a module
   - ✅ Tests are already organized this way

   **For CODEOWNERS completeness**, add entries pointing to test suites:
   ```
   # Test suite ownership (informational - CI uses suite name)
   /operate/common/src/test/java/io/camunda/operate/OperateDataLayerTestSuite.java @camunda/data-layer
   /operate/webapp/src/test/java/io/camunda/operate/OperateCoreFeaturesTestSuite.java @camunda/core-features
   ```

4. **Add ownership metadata to key modules**
   - Add comments in pom.xml: `<!-- owner: @camunda/team-name -->`
   - Only for modules not clearly covered by CODEOWNERS or as clarification
   - Used as fallback if CODEOWNERS lookup fails

#### Phase 2: Create Test Organization Script

Create `.github/scripts/organize-tests-by-owner.sh`:

```bash
#!/bin/bash
# Reads CODEOWNERS and generates test job matrix
# Outputs: JSON matrix for GitHub Actions

# Features:
# - Groups modules by primary owner
# - Handles multi-owner modules (creates shared job)
# - Falls back to "Unowned" category for safety
# - Allows manual overrides via config file
```

#### Phase 3: Implement Team-Based Jobs

**Job Naming Convention**:
```
Integration / {Team Name} / {Module Group}
```

**Examples**:
```yaml
- name: "Integration / Zeebe Distributed Platform / Clustering"
  owner: "@camunda/zeebe-distributed-platform"
  modules: "atomix,dynamic-config,journal,logstreams,snapshot,stream-platform,transport"

- name: "Integration / Zeebe Distributed Platform / Backup & Restore"
  owner: "@camunda/zeebe-distributed-platform"
  modules: "backup,backup-stores,restore"

- name: "Integration / Zeebe Core Features / Engine"
  owner: "@camunda/zeebe-core-features"
  modules: "engine,bpmn-model,dmn,feel,protocol"

- name: "Integration / Zeebe Core Features / Gateway"
  owner: "@camunda/zeebe-core-features"
  modules: "gateway,gateway-grpc,gateway-rest,gateway-protocol"

- name: "Integration / Data Layer / Exporters"
  owner: "@camunda/data-layer"
  modules: "exporters/elasticsearch-exporter,exporters/opensearch-exporter"

- name: "Integration / Data Layer / Schema"
  owner: "@camunda/data-layer"
  modules: "schema-manager"

- name: "Integration / Identity / Auth"
  owner: "@camunda/identity"
  modules: "identity,authentication,security"

- name: "Integration / CamundaEx / SDK & Testing"
  owner: "@camunda/camundaex"
  modules: "clients,testing"

- name: "Integration / Shared / Multi-Owner Modules"
  owner: "@camunda/zeebe-distributed-platform,@camunda/zeebe-core-features"
  modules: "broker"

- name: "Integration / Unowned / Safety Net"
  owner: "TBD"
  modules: "util,test-util,protocol-test-util"
```

#### Phase 4: Handle Shared Ownership

**Strategy for multi-owner modules**:

1. **Option A: Primary + Secondary Owner**
   - List primary owner first in CODEOWNERS
   - Job named after primary owner
   - Both teams notified on failure
   ```
   /zeebe/broker/ @camunda/zeebe-distributed-platform @camunda/zeebe-core-features
   ```

2. **Option B: Dedicated Shared Job**
   - Create explicit "Shared" category
   - Both teams in owner field
   - Clear visibility that this is cross-team
   ```yaml
   - name: "Integration / Shared / Broker Core"
     owner: "@camunda/zeebe-distributed-platform,@camunda/zeebe-core-features"
   ```

3. **Option C: Split by Sub-packages** (Most Granular)
   - Use CODEOWNERS sub-package entries
   - Create separate jobs for each owned part
   ```yaml
   - name: "Integration / Zeebe Distributed Platform / Broker Clustering"
     modules: "broker/src/main/java/io/camunda/zeebe/broker/clustering"

   - name: "Integration / Zeebe Core Features / Broker Engine"
     modules: "broker/src/main/java/io/camunda/zeebe/broker/engine"
   ```

**Recommendation**: Start with Option B (Dedicated Shared Job), migrate to Option C as teams prefer more granular ownership.

#### Phase 5: Safety Net for Unowned Modules

**Default behavior when no owner found**:

1. **Create "Unowned" job category**
   ```yaml
   - name: "Integration / Unowned / Safety Net"
     owner: "TBD"
     timeout-minutes: 60
     slack-channel: "zeebe-general-alerts"
   ```

2. **Alert on unowned modules**
   - Weekly report to devops team
   - PR checks warn if new module without owner

3. **Fallback rules**
   ```bash
   # If no owner in CODEOWNERS:
   # 1. Check parent directory
   # 2. Check pom.xml comment
   # 3. Fall back to "Unowned" category
   # 4. Still run tests (never skip!)
   ```

#### Phase 6: Flexible Splits

**Allow manual split configuration** via `.github/test-splits.yml`:

```yaml
# Manual overrides for test splits
# Overrides auto-detection from CODEOWNERS

splits:
  zeebe-distributed-platform:
    - name: "Clustering"
      modules: ["atomix", "dynamic-config", "journal"]
      timeout: 90
    - name: "Backup & Recovery"
      modules: ["backup", "backup-stores", "restore"]
      timeout: 60

  zeebe-core-features:
    - name: "Engine Core"
      modules: ["engine", "bpmn-model"]
      timeout: 120
    - name: "Decision Engine"
      modules: ["dmn", "feel"]
      timeout: 45

# Special handling for QA tests
qa-tests:
  - name: "Engine QA"
    owner: "@camunda/zeebe-core-features"
    filter: "io.camunda.zeebe.it.engine.**"
  - name: "Cluster QA"
    owner: "@camunda/zeebe-distributed-platform"
    filter: "io.camunda.zeebe.it.cluster.**"
```

---

### Phase 7: Handle ci-*.yml Workflow Files

#### The ci-*.yml Challenge

**Current Architecture**:
- Main `ci.yml` coordinates all tests
- Calls specialized workflows: `ci-zeebe.yml`, `ci-operate.yml`, `ci-tasklist.yml`, `ci-optimize.yml`
- Each workflow has different job structures and ownership patterns

**Key Problem**: Ownership is documented inconsistently:
- File header: `@camunda/core-features`
- Job TEST_OWNER: `"Core Features"`
- observe-build-status user_description: `"team-distributed-systems"`
- Sometimes all three are DIFFERENT in the same workflow!

#### Strategy: Hybrid Approach

**Keep Separate Workflows For**:

1. **Product-specific test suites** (Operate, Tasklist, Optimize, Identity)
   - ✅ Reason: Different change detection triggers
   - ✅ Reason: Different frontend/backend test paths
   - ✅ Reason: Can be toggled independently
   - **Action**: Standardize owner metadata across all jobs

2. **Specialized test types with unique requirements**:
   - `smoke-tests` (cross-platform: Windows, macOS, Linux, ARM)
   - `strace-tests` (requires special Linux setup)
   - `property-tests` (long-running randomized tests)
   - `performance-tests` (JMH benchmarks, special runners)
   - **Action**: Create explicit test-type-to-owner mapping

3. **Scheduled/On-demand workflows**:
   - Daily/weekly E2E tests
   - Load tests, benchmarks, migration tests
   - **Action**: Standardize naming and ownership metadata

**Consolidate Into Main ci.yml**:
- Docker checks (should be with integration tests)
- ArchUnit tests (already there, ensure proper owner)

#### Establish Ownership Hierarchy

**Level 1: Workflow File Ownership** (for workflow infrastructure)
- Who maintains the workflow file itself?
- Who approves changes to the workflow?

**Stored in CODEOWNERS**:
```
/.github/workflows/ci-zeebe.yml @camunda/zeebe-distributed-platform @camunda/zeebe-core-features
/.github/workflows/ci-operate.yml @camunda/core-features @camunda/data-layer
/.github/workflows/operate-e2e-tests.yml @camunda/core-features
```

**Level 2: Job Ownership** (for test failures)
- Who investigates when a job fails?
- Who fixes flaky tests?

**Stored in job metadata**:
```yaml
smoke-tests:
  name: "Zeebe / Smoke / Distributed Platform - ${{ matrix.os }}"
  env:
    TEST_OWNER: Distributed Platform  # Auto-derived from test type mapping
    TEST_PRODUCT: Zeebe
    TEST_TYPE: Smoke
```

**Level 3: Test Content Ownership** (for test maintenance)
- Determined by CODEOWNERS based on what's being tested
- Example: Smoke tests for broker module → follows broker ownership

#### Create Test Type Ownership Mapping

**New configuration file**: `.github/test-type-owners.yml`

```yaml
# Maps specialized test types to owning teams
# Used when CODEOWNERS doesn't apply (functional tests, not module tests)

test-types:
  # Zeebe specialized tests
  smoke:
    owner: "@camunda/zeebe-distributed-platform"
    description: "Cross-platform smoke tests"
    reason: "Infrastructure-focused, not module-specific"
    fallback-to-module-owner: false

  strace:
    owner: "@camunda/zeebe-distributed-platform"
    description: "Linux strace-based tests"
    reason: "Low-level system interaction testing"
    fallback-to-module-owner: false

  property:
    owner: "@camunda/zeebe-core-features"
    description: "Property-based randomized tests"
    reason: "Engine logic testing"
    fallback-to-module-owner: false

  performance:
    owner: "@camunda/zeebe-core-features"
    description: "JMH performance benchmarks"
    reason: "Engine performance characteristics"
    fallback-to-module-owner: false

  # Infrastructure tests
  docker:
    owner: "@camunda/zeebe-distributed-platform"
    description: "Docker build and runtime tests"
    reason: "Deployment infrastructure"
    fallback-to-module-owner: false

  # Product tests (fallback to module owner)
  unit:
    fallback-to-module-owner: true
    description: "Standard unit tests"

  integration:
    fallback-to-module-owner: true
    description: "Integration tests"

  e2e:
    fallback-to-module-owner: true
    description: "End-to-end tests"

  load:
    owner: "@camunda/zeebe-distributed-platform"
    description: "Load and stress testing"
    fallback-to-module-owner: false

  benchmark:
    owner: "@camunda/zeebe-distributed-platform"
    description: "Performance benchmarking"
    fallback-to-module-owner: false
```

**Usage**: Scripts/actions can query this file to determine owner for specialized tests

#### Standardize Job Naming Across ALL Workflows

**Current Inconsistencies**:
```yaml
# ci-zeebe.yml - ALL DIFFERENT FORMATS
name: "Smoke - ${{ matrix.os }} with ${{ matrix.arch }}"       # Missing product & owner
name: "Unit - Property Tests"                                  # Missing owner & product
name: "Performance"                                            # Missing everything

# ci-operate.yml - INCONSISTENT
name: "Unit"                                                   # Missing owner & product
name: "Operate / Build Back End"                              # Different format
```

**Standardized Format** (same as main ci.yml):
```yaml
# Format: {Product} / {Test Type} / {Team} - {Details}

# ci-zeebe.yml
name: "Zeebe / Smoke / Distributed Platform - ${{ matrix.os }}"
name: "Zeebe / Property / Core Features"
name: "Zeebe / Performance / Core Features"
name: "Zeebe / Docker / Distributed Platform"

# ci-operate.yml
name: "Operate / Unit / Data Layer"
name: "Operate / Unit / Core Features"
name: "Operate / Build / Core Features"
name: "Operate / Integration / Data Layer - Importer ES"

# ci-tasklist.yml
name: "Tasklist / Unit / Data Layer"
name: "Tasklist / Integration / Core Features - ES"
```

**Benefits**:
- ✅ Consistent with ci.yml jobs
- ✅ Scannable in PR checks
- ✅ Clear owner in every job name
- ✅ Filterable in dashboards

#### Reconcile Conflicting Ownership

**Example Problem**: `ci-zeebe.yml`
- File header: `owner: @camunda/core-features`
- Smoke tests: Actually owned by `@camunda/zeebe-distributed-platform`
- Property tests: Actually owned by `@camunda/zeebe-core-features`

**Solution**:

1. **File ownership** (workflow infrastructure)
   ```
   # Both teams share workflow maintenance
   /.github/workflows/ci-zeebe.yml @camunda/zeebe-distributed-platform @camunda/zeebe-core-features
   ```

2. **Job ownership** (test failures)
   - Derived from test-type-owners.yml + CODEOWNERS
   - Shown in job name
   - Used in observe-build-status consistently

3. **Updated file header**:
   ```yaml
   # workflow-owner: @camunda/zeebe-distributed-platform @camunda/zeebe-core-features
   # job-owners: See individual job metadata and names
   # description: Specialized Zeebe tests (smoke, property, performance, docker)
   ```

#### Standardize Separate E2E/Scheduled Workflows

**Problem**: E2E and scheduled workflows lack standard metadata

**Solution**: Workflow header template

**Required metadata for ALL workflow files**:
```yaml
# description: {What this workflow tests}
# test-location: {Where the tests are: modules or paths}
# test-type: {E2E, Load, Benchmark, Migration, etc.}
# workflow-owner: {@team who maintains workflow file}
# test-owner: {@team who owns test failures} (can be different)
# called-by: {ci.yml | scheduled | manual | other}
# schedule: {cron if scheduled}
# visibility: {PR checks | main-only | scheduled-only}
```

**Examples**:
```yaml
# File: operate-e2e-tests.yml
# description: Playwright E2E tests for Operate UI flows
# test-location: /operate/client/e2e-playwright/tests
# test-type: E2E
# workflow-owner: @camunda/core-features
# test-owner: @camunda/core-features
# called-by: ci.yml (on operate changes)
# visibility: PR checks

# File: zeebe-medic-benchmarks.yml
# description: Performance benchmarks using Medic framework
# test-location: /zeebe/benchmarks
# test-type: Benchmark
# workflow-owner: @camunda/zeebe-distributed-platform
# test-owner: @camunda/zeebe-distributed-platform
# called-by: scheduled
# schedule: 0 2 * * * (nightly at 2am)
# visibility: main-only

# File: zeebe-daily-qa.yml
# description: Extended QA suite for nightly validation
# test-location: /zeebe/qa
# test-type: Integration
# workflow-owner: @camunda/zeebe-core-features
# test-owner: @camunda/zeebe-core-features @camunda/zeebe-distributed-platform
# called-by: scheduled
# schedule: 0 6 * * * (daily at 6am)
# visibility: scheduled-only
```

**Benefits**:
- Clear documentation of all workflows
- Easy to audit coverage
- Can generate workflow → owner mapping
- Supports dashboards/reports

#### Validation: Workflow Ownership Consistency Check

**New CI check**: `.github/workflows/validate-workflow-ownership.yml`

**Validates**:
1. ✅ All workflow files have owner metadata in header
2. ✅ All jobs in ci-*.yml have TEST_OWNER env var
3. ✅ All observe-build-status calls have consistent owner
4. ✅ Workflow file ownership in CODEOWNERS matches header
5. ✅ No conflicts between different ownership declarations in same job
6. ✅ Job names follow standard format
7. ✅ Specialized tests reference test-type-owners.yml

**Runs on**: PRs that modify workflow files

**Example validation output**:
```
❌ ci-zeebe.yml: Line 38
   Job 'smoke-tests' has conflicting ownership:
   - TEST_OWNER env var: "Core Features"
   - observe-build-status user_description: "team-distributed-systems"
   - test-type-owners.yml smoke: "@camunda/zeebe-distributed-platform"
   → Fix: Use consistent owner from test-type-owners.yml

❌ ci-operate.yml: Line 52
   Job name 'Unit' doesn't follow format '{Product} / {Type} / {Team}'
   → Fix: Rename to 'Operate / Unit / Data Layer'

✅ ci-tasklist.yml: All jobs have consistent ownership
✅ operate-e2e-tests.yml: Has required metadata headers
```

#### Integration with Main ci.yml

**Current**:
```yaml
zeebe-ci:
  if: needs.detect-changes.outputs.zeebe-changes == 'true'
  uses: ./.github/workflows/ci-zeebe.yml
```

**Enhanced** (future):
```yaml
zeebe-ci:
  if: needs.detect-changes.outputs.zeebe-changes == 'true'
  uses: ./.github/workflows/ci-zeebe.yml
  with:
    # Pass detected changes for smart test selection
    changed-modules: ${{ needs.detect-changes.outputs.zeebe-modules }}
    # Could enable dynamic test type selection
    # Example: Only run smoke if atomix changed
```

---

## Naming Convention Standards

### Critical Question: What Should Come First in Job Names?

**Two Options**:
- **Option A** (Test Type First): `{Test Type} / {Owner Team} / {Module Group}`
- **Option B** (Owner First): `{Owner Team} / {Test Type} / {Module Group}`

#### Detailed Comparison

| Aspect | **Option A: Test Type First** | **Option B: Owner First** |
|--------|-------------------------------|---------------------------|
| **Example** | `Integration / Data Layer / Exporters` | `Data Layer / Integration / Exporters` |
| **Primary Grouping** | By test type (all Integration tests together) | By team (all team tests together) |
| **Scanning Pattern** | Find test type → find owner | Find owner → find test type |
| **GitHub PR Checks** | Groups by test phase | Groups by team responsibility |
| **Mental Model** | "What failed?" → "Who owns it?" | "Who's responsible?" → "What failed?" |
| **Filtering/Search** | Easy to find "all integration tests" | Easy to find "all my team's tests" |
| **Test Pipeline View** | ✅ Clear test phases (Unit → Integration → E2E) | ⚠️ Test phases scattered across teams |
| **Team Ownership View** | ⚠️ Team tests scattered across phases | ✅ All team tests grouped together |

#### Pros of Option A: Test Type First

**1. Pipeline Mental Model** ⭐⭐⭐⭐⭐
- ✅ Tests group by CI pipeline stage (Unit → Integration → E2E)
- ✅ Easy to see test phase progression
- ✅ Matches typical CI pipeline thinking
- **Example PR checks view**:
  ```
  ✅ Unit / Data Layer
  ✅ Unit / Core Features
  ✅ Unit / Distributed Platform
  ⏳ Integration / Data Layer (running)
  ⏳ Integration / Core Features (running)
  ⏳ Integration / Distributed Platform (running)
  ⬜ E2E / Operate (queued)
  ```
  **Benefit**: Clear progression through test phases

**2. Find Failures by Type** ⭐⭐⭐⭐
- ✅ "All integration tests failed" is immediately visible
- ✅ Infrastructure issues affect test type, not teams
- ✅ Easier to spot patterns (all E2E tests failing)
- **Example**: If all Integration tests fail → likely infra issue, not code

**3. Consistent with Industry Norms** ⭐⭐⭐
- ✅ Most projects group by test type first
- ✅ Jenkins, CircleCI, Travis patterns: "Integration Tests / Module"
- ✅ Lower learning curve for new contributors
- **Example**: GitHub Actions docs use test type first

**4. Test Lifecycle Clarity** ⭐⭐⭐⭐
- ✅ See which stage of testing you're in
- ✅ Unit tests should pass before Integration runs
- ✅ Matches "shift left" testing philosophy
- **Example**: Unit failures block Integration attempts

**5. Cross-Team Infrastructure Issues** ⭐⭐⭐⭐
- ✅ "All Integration tests down" is immediately clear
- ✅ Platform issues (Docker, DB, runners) group by test type
- ✅ Easier for DevOps to spot infrastructure problems
- **Example**: If all Integration tests timing out → runner issue

#### Cons of Option A: Test Type First

**1. Team Ownership Less Visible** ⭐⭐⭐⭐⭐
- ❌ Team's tests scattered across different groups
- ❌ Hard to see "all my team's work" at a glance
- ❌ Need to scan multiple sections to find your tests
- **Example**: Distributed Platform team needs to check Unit, Integration, Smoke, Property sections

**2. Scanning Overhead for Teams** ⭐⭐⭐⭐
- ❌ Teams must scan all test types to find their tests
- ❌ More cognitive load to find relevant failures
- ❌ Can miss failures if scanning quickly
- **Example**: Developer skims PR checks, misses their Integration test failure

**3. Team Metrics Harder** ⭐⭐⭐
- ❌ Can't easily see team health at a glance
- ❌ Metrics dashboards need to aggregate across types
- ❌ Team-specific filtering requires more work
- **Example**: "How are my team's tests doing?" requires checking multiple sections

**4. Accountability Less Clear** ⭐⭐⭐
- ❌ Owner is second piece of info, can be overlooked
- ❌ "Who do I ask?" requires reading further
- ❌ Notification routing more complex
- **Example**: Integration test fails → need to read further to know who to ping

#### Pros of Option B: Owner First

**1. Team Ownership Crystal Clear** ⭐⭐⭐⭐⭐
- ✅ All team tests grouped together
- ✅ Immediately see who's responsible
- ✅ Teams can quickly scan for their tests
- **Example PR checks view**:
  ```
  ✅ Data Layer / Unit
  ✅ Data Layer / Integration / Exporters
  ⏳ Data Layer / Integration / Schema (running)
  ✅ Core Features / Unit
  ⏳ Core Features / Integration (running)
  ✅ Distributed Platform / Unit
  ✅ Distributed Platform / Integration / Clustering
  ✅ Distributed Platform / Smoke
  ```
  **Benefit**: Each team's section is contiguous

**2. Fast Scanning for Teams** ⭐⭐⭐⭐⭐
- ✅ "Show me MY tests" is one scan
- ✅ Developers find their work instantly
- ✅ No need to check multiple sections
- **Example**: Distributed Platform dev looks for "Distributed Platform" prefix only

**3. Clear Accountability** ⭐⭐⭐⭐⭐
- ✅ Owner is FIRST thing you see
- ✅ No ambiguity about who to contact
- ✅ Supports ownership culture
- **Example**: Test fails → owner is in the first segment, immediately actionable

**4. Team Metrics Natural** ⭐⭐⭐⭐
- ✅ Easy to filter/group by team
- ✅ Dashboards show team health clearly
- ✅ Team-specific views are natural
- **Example**: "Data Layer" filter shows all their tests

**5. Matches Split-by-Owner Architecture** ⭐⭐⭐⭐
- ✅ If splitting ci.yml by owner, names already match
- ✅ Natural grouping for team workflow files
- ✅ Easier migration path
- **Example**: `ci-team-data-layer.yml` matches "Data Layer / ..." names

**6. Aligns with Ownership Focus** ⭐⭐⭐⭐⭐
- ✅ Whole plan is about ownership → names should reflect it
- ✅ Primary organization is by owner → names should be too
- ✅ Reinforces ownership culture
- **Example**: CODEOWNERS-driven approach → owner-first names

#### Cons of Option B: Owner First

**1. Pipeline View Less Clear** ⭐⭐⭐⭐
- ❌ Test phases scattered across teams
- ❌ Harder to see "all Integration tests" status
- ❌ CI pipeline progression less visible
- **Example**: Can't quickly see if Integration phase is complete

**2. Infrastructure Issues Harder to Spot** ⭐⭐⭐⭐
- ❌ "All Integration tests failing" not immediately visible
- ❌ Need to scan multiple team sections
- ❌ Platform-wide issues look like team issues
- **Example**: Runner timeout affects all teams → looks like 5 separate issues

**3. Less Familiar Pattern** ⭐⭐
- ❌ Unusual compared to most projects
- ❌ New contributors may be confused
- ❌ Doesn't match typical CI naming
- **Example**: Contributors used to "Integration / Module" pattern

**4. Test Type Requires Extra Scanning** ⭐⭐⭐
- ❌ "What type of test failed?" requires reading further
- ❌ Can't quickly see test phase at a glance
- ❌ More cognitive load to understand test context
- **Example**: Need to read past owner to know if it's Unit or Integration

#### Real-World Scanning Scenarios

**Scenario 1: Developer Looking at Their PR**
- **Test Type First**:
  - ❌ Scan "Unit" section → find their team → ❌ Scan "Integration" section → find their team → ❌ Scan "E2E" section → find their team
  - **Time**: ~15 seconds, high cognitive load

- **Owner First**:
  - ✅ Find their team name → see all their tests
  - **Time**: ~5 seconds, low cognitive load

**Winner**: **Owner First** (significantly faster for most common use case)

**Scenario 2: DevOps Investigating Infrastructure Issue**
- **Test Type First**:
  - ✅ See "Integration" section → all failing → identify infrastructure issue
  - **Time**: ~5 seconds

- **Owner First**:
  - ❌ Notice multiple teams → all Integration tests failing → identify infrastructure issue
  - **Time**: ~10 seconds, requires pattern recognition

**Winner**: **Test Type First** (slightly faster, more obvious)

**Scenario 3: Team Lead Checking Team Health**
- **Test Type First**:
  - ❌ Check Unit section for team → ❌ Check Integration section for team → ❌ Check E2E section for team → aggregate
  - **Time**: ~20 seconds

- **Owner First**:
  - ✅ Find team section → see all test types
  - **Time**: ~5 seconds

**Winner**: **Owner First** (much faster)

**Scenario 4: PR Reviewer Checking Test Status**
- **Test Type First**:
  - ✅ See test progression (Unit → Integration → E2E)
  - ✅ Understand test phase

- **Owner First**:
  - ⚠️ Need to parse test types within each team section
  - ⚠️ Less clear test progression

**Winner**: **Test Type First** (clearer context)

**Scenario 5: Contributor Looking for Test to Modify**
- **Test Type First**:
  - Know test type → Find type section → Find owner → Find specific test

- **Owner First**:
  - Know owner → Find owner section → Find test type → Find specific test

**Winner**: **Tie** (depends on what you know first)

#### Frequency Analysis

**How often does each scenario occur?**

| Scenario | Frequency | Winner |
|----------|-----------|--------|
| Developer checking their PR | ⭐⭐⭐⭐⭐ (dozens/day) | Owner First |
| DevOps checking infrastructure | ⭐⭐ (few/week) | Test Type First |
| Team lead checking team health | ⭐⭐⭐⭐ (daily) | Owner First |
| PR reviewer checking status | ⭐⭐⭐⭐ (daily) | Test Type First |
| Looking for specific test | ⭐⭐ (few/week) | Tie |

**Weighted Score**:
- **Owner First**: 5 + 4 = **9 points** (most frequent use cases)
- **Test Type First**: 2 + 4 = **6 points**

**Winner**: **Owner First** (better for most common use cases)

### Recommendation: Owner First (Option B)

**Format**: `{Owner Team} / {Test Type} / {Module Group}`

**Rationale**:
1. ✅ **Primary organization is by owner** → names should reflect it
2. ✅ **Most common use case** (developer checking their tests) is much faster
3. ✅ **Ownership culture** is reinforced every time someone looks at CI
4. ✅ **Team autonomy** is emphasized (team name first = team owns it)
5. ✅ **Better for split-by-owner** architecture (if we go that route)
6. ⚠️ Infrastructure issue detection is slightly slower, but still doable

**Mitigation for Infrastructure Issues**:
- Add observability dashboard that groups by test type
- Use GitHub Actions job matrix grouping
- DevOps can filter by test type in logs

### Alternative: Hybrid Approach

**For most tests**: `{Owner Team} / {Test Type} / {Module Group}`
**For shared infrastructure tests**: `{Test Type} / {Component}`

**Examples**:
```
✅ Data Layer / Unit
✅ Data Layer / Integration / Exporters
✅ Core Features / Unit / Engine
✅ Distributed Platform / Smoke / Linux

❌ Docker Checks / Build (no team owns, it's infrastructure)
❌ Linting / Code Format (no team owns, it's quality gates)
```

**Benefit**: Most tests (owned by teams) use owner-first, infrastructure tests use test-type-first

### Updated Format Specification

**Recommended Job Name Format**:
```
{Owner Team} / {Test Type} / {Module Group} [{Special Tag}]
```

**Components**:
- **Owner Team** (FIRST):
  - `Distributed Platform`
  - `Core Features`
  - `Data Layer`
  - `Identity`
  - `CamundaEx`
  - `Shared` (multi-owner)
  - `Unowned` (safety net)
- **Test Type** (SECOND):
  - `Unit`, `Integration`, `E2E`, `Performance`, `Smoke`, `Property`, `Strace`
- **Module Group** (THIRD):
  - Short descriptive name (max 25 chars)
- **Special Tag** (OPTIONAL):
  - `[Critical]`, `[Long-Running]`, `[Flaky]`

### Examples

**Good Names** (Clear, Scannable, Owner Visible):
```
✅ Distributed Platform / Integration / Clustering
✅ Core Features / Integration / Process Engine
✅ Data Layer / Integration / Exporters
✅ Shared / Integration / Broker Core
✅ Core Features / Integration / Engine QA [Critical]
✅ Identity / Unit / Authentication
✅ Distributed Platform / Smoke / Linux
✅ Core Features / Property
✅ CamundaEx / Unit / SDK
```

**Bad Names** (Avoid):
```
❌ Zeebe Module (which module? who owns it? what test type?)
❌ Integration Tests Group 1 (meaningless grouping)
❌ zeebe-ds-modules (technical, not human-readable)
❌ Tests for atomix,backup,journal (too detailed, wrong format)
❌ Unit Tests (no owner specified)
```

### GitHub PR Summary Display

In PR checks, jobs will appear as:
```
✅ Distributed Platform / Unit (5m 12s)
✅ Distributed Platform / Integration / Clustering (12m 34s)
✅ Distributed Platform / Smoke / Linux (8m 45s)
✅ Core Features / Unit (6m 23s)
✅ Core Features / Integration / Process Engine (18m 12s)
❌ Data Layer / Integration / Exporters (8m 45s) [FAILED]
⏳ Shared / Integration / Broker Core (running)
```

**Benefits of Owner-First Naming**:
- ✅ **Scannable**: Find your team by looking for team name at start
- ✅ **Grouped**: All team tests appear together in alphabetical sort
- ✅ **Informative**: Know what failed AND who owns it immediately
- ✅ **Actionable**: Clear who should investigate
- ✅ **Consistent**: Predictable pattern, team name always first

In PR checks, jobs will appear as:
```
✅ Integration / Zeebe Distributed Platform / Clustering (12m 34s)
✅ Integration / Zeebe Core Features / Process Engine (18m 12s)
❌ Integration / Data Layer / Exporters (8m 45s) [FAILED]
⏳ Integration / Shared / Broker Core (running)
```

Benefits:
- **Scannable**: Easy to find your team's tests
- **Informative**: Know what failed without clicking
- **Actionable**: Clear who should investigate
- **Consistent**: Predictable pattern

---

## Migration Strategy

### Step 1: Enhance CODEOWNERS (Week 1)
- [ ] Audit current CODEOWNERS coverage
- [ ] Add missing module ownership
- [ ] Mark shared ownership explicitly
- [ ] Validate with teams

### Step 2: Create Automation (Week 1-2)
- [ ] Implement `organize-tests-by-owner.sh`
- [ ] Create GitHub Action for auto-detection
- [ ] Add validation in PR checks
- [ ] Test with dry-run mode

### Step 3: Pilot with One Team (Week 2)
- [ ] Choose pilot team (suggest: Zeebe Distributed Platform)
- [ ] Migrate their tests to new structure
- [ ] Gather feedback
- [ ] Adjust based on learnings

### Step 4: Gradual Rollout (Week 3-4)
- [ ] Migrate remaining teams one at a time
- [ ] Keep old jobs running in parallel initially
- [ ] Monitor for issues
- [ ] Document any manual overrides needed

### Step 5: Cleanup (Week 5)
- [ ] Remove old job definitions
- [ ] Update documentation
- [ ] Train teams on new structure
- [ ] Set up monitoring/alerting

---

## Team-Specific Defaults

Based on historical data and team preferences, set defaults per team:

### Zeebe Distributed Platform
```yaml
timeout_minutes: 90
slack_channel: "zeebe-distributed-platform-alerts"
test_suite_priority: "high"
maven_fork_count: 7
runner: gcp-perf-core-16-longrunning
```

### Zeebe Core Features
```yaml
timeout_minutes: 120
slack_channel: "zeebe-core-features-alerts"
test_suite_priority: "critical"
maven_fork_count: 10
runner: gcp-perf-core-16-default
```

### Data Layer
```yaml
timeout_minutes: 60
slack_channel: "data-layer-alerts"
test_suite_priority: "high"
maven_fork_count: 5
runner: gcp-perf-core-8-default
```

### Identity
```yaml
timeout_minutes: 45
slack_channel: "identity-alerts"
test_suite_priority: "medium"
maven_fork_count: 3
runner: gcp-perf-core-8-default
```

### CamundaEx
```yaml
timeout_minutes: 30
slack_channel: "camundaex-alerts"
test_suite_priority: "medium"
maven_fork_count: 3
runner: gcp-perf-core-8-default
```

---

## Handling Edge Cases

### Case 1: Module with No Clear Owner
**Solution**: Falls into "Unowned" safety net
- Tests still run (never skip)
- Weekly report to devops team
- PR warning: "Module lacks ownership"

### Case 2: Module with 3+ Owners
**Solution**: Treat as shared
- Create dedicated shared job
- All owners notified
- Consider splitting module if too complex

### Case 3: Owner Team Doesn't Exist Anymore
**Solution**: CODEOWNERS validation in CI
- PR check fails if @mention doesn't resolve
- Forces ownership update
- Temporary fallback to "Unowned"

### Case 4: Tests Too Slow for One Job
**Solution**: Manual split in config
- Use `.github/test-splits.yml`
- Split by sub-module or test package
- Maintain same owner for all splits

### Case 5: Flaky Tests
**Solution**: Tag and track
- Add `[Flaky]` tag to job name
- Separate job for known flaky tests
- Track flaky tests to ownership team
- Don't block PR but alert owner

---

## Metrics & Observability

### Key Metrics by Owner
1. **Test Duration**: Track per team over time
2. **Failure Rate**: % of failures by team
3. **Flaky Test Count**: Per team
4. **Unowned Modules**: Count and list
5. **Shared Module Failures**: Track which team responds

### Dashboards
- **Team Health**: Each team sees their test metrics
- **Overall Health**: All teams, compare reliability
- **Ownership Coverage**: % modules with clear owner

### Alerts
- **Slack**: Team channel on failure
- **Email**: Team lead on repeated failures
- **Weekly Report**: Unowned modules, flaky tests

---

## Benefits of This Approach

### For Development Teams
✅ **Clear ownership**: Know which tests you own
✅ **Faster feedback**: See your team's tests first in CI
✅ **Better alerts**: Routed to right team/channel
✅ **Autonomy**: Teams can adjust their test splits
✅ **Accountability**: Ownership drives quality

### For Platform/DevOps
✅ **Less maintenance**: Auto-derived from CODEOWNERS
✅ **Consistency**: Enforced naming conventions
✅ **Safety net**: Unowned modules still tested
✅ **Flexibility**: Easy to add overrides
✅ **Visibility**: Metrics per team

### For Engineering Leadership
✅ **Ownership tracking**: See test coverage per team
✅ **Quality metrics**: Compare team test reliability
✅ **Resource planning**: Understand team test load
✅ **Accountability**: Clear responsibility

---

## Open Questions & Decisions Needed

### Q1: Team Name Format
- Use GitHub team handle? `@camunda/zeebe-distributed-platform`
- Use display name? `Zeebe Distributed Platform`
- Use short code? `zeebe-ds`

**Recommendation**: Use display name for readability

### Q2: How to Handle QA Tests?
- Keep as separate owner (`QA` team)?
- Assign to functional owner (Engine QA → Core Features)?
- Hybrid (QA owns infrastructure, teams own content)?

**Recommendation**: Assign to functional owner, QA reviews/maintains

### Q3: When to Split Jobs?
- By module count? (>5 modules = split)
- By duration? (>60 min = split)
- By team request?

**Recommendation**: Combination - default by duration, allow team requests

### Q4: Shared Module Strategy
- Always create dedicated "Shared" job?
- Assign to primary owner only?
- Duplicate tests in both team's jobs?

**Recommendation**: Dedicated "Shared" job, both teams notified

### Q5: Migration Timeline
- Big bang (all at once)?
- Gradual (team by team)?
- Parallel run period?

**Recommendation**: Gradual with 1-week parallel run

---

## Success Criteria

After implementation, we should achieve:

1. ✅ **100% test coverage**: Every test has a job, no modules skipped
2. ✅ **>90% ownership clarity**: <10% tests in "Unowned" category
3. ✅ **Clear CI display**: Can scan PR and see test owners
4. ✅ **Reduced MTTR**: Faster identification of who fixes what
5. ✅ **Team satisfaction**: Teams find it easier to manage their tests
6. ✅ **Automation**: CODEOWNERS changes auto-update test organization
7. ✅ **Flexibility**: Teams can request splits/adjustments easily

---

## Next Steps

1. **Review this document** with stakeholders
2. **Decide on Option 1** (Team-Based) vs alternatives
3. **Complete CODEOWNERS audit** (gap analysis)
4. **Implement automation scripts** (.github/scripts/)
5. **Choose pilot team** for trial run
6. **Set migration timeline** (suggest 4-6 weeks)
7. **Communicate plan** to all teams

---

## Appendix: Example Generated Matrix

Based on CODEOWNERS, the auto-generation would produce:

```yaml
strategy:
  fail-fast: false
  matrix:
    include:
      # Zeebe Distributed Platform
      - name: "Integration / Zeebe Distributed Platform / Clustering"
        owner: "@camunda/zeebe-distributed-platform"
        modules: "atomix,dynamic-config,logstreams,stream-platform,transport"
        timeout: 90

      - name: "Integration / Zeebe Distributed Platform / Backup"
        owner: "@camunda/zeebe-distributed-platform"
        modules: "backup,backup-stores,restore"
        timeout: 60

      - name: "Integration / Zeebe Distributed Platform / Storage"
        owner: "@camunda/zeebe-distributed-platform"
        modules: "journal,snapshot,scheduler"
        timeout: 60

      # Zeebe Core Features
      - name: "Integration / Zeebe Core Features / Engine"
        owner: "@camunda/zeebe-core-features"
        modules: "engine,bpmn-model,protocol"
        timeout: 120

      - name: "Integration / Zeebe Core Features / Decision Engine"
        owner: "@camunda/zeebe-core-features"
        modules: "dmn,feel,feel-tagged-parameters"
        timeout: 60

      - name: "Integration / Zeebe Core Features / Gateway"
        owner: "@camunda/zeebe-core-features"
        modules: "gateway,gateway-grpc,gateway-rest,gateway-protocol"
        timeout: 60

      # Data Layer
      - name: "Integration / Data Layer / Exporters"
        owner: "@camunda/data-layer"
        modules: "exporters/elasticsearch-exporter,exporters/opensearch-exporter"
        timeout: 60

      - name: "Integration / Data Layer / Schema"
        owner: "@camunda/data-layer"
        modules: "schema-manager"
        timeout: 45

      # Identity
      - name: "Integration / Identity / Auth & Security"
        owner: "@camunda/identity"
        modules: "identity,authentication,security"
        timeout: 45

      # CamundaEx
      - name: "Integration / CamundaEx / SDK & Testing"
        owner: "@camunda/camundaex"
        modules: "clients,testing"
        timeout: 30

      # Shared
      - name: "Integration / Shared / Broker Core"
        owner: "@camunda/zeebe-distributed-platform,@camunda/zeebe-core-features"
        modules: "broker"
        timeout: 90

      # Unowned (Safety Net)
      - name: "Integration / Unowned / Utilities"
        owner: "TBD"
        modules: "util,test-util,protocol-test-util,protocol-jackson"
        timeout: 30
```

This provides clear ownership, meaningful names, and comprehensive coverage.

---

## Advanced Considerations

### Should ci.yml be Split by Owner?

**Question**: Instead of one monolithic `ci.yml`, should we create separate workflow files per team?

Example structure:
```
.github/workflows/
  ci-zeebe-distributed-platform.yml  # All Distributed Platform tests
  ci-zeebe-core-features.yml         # All Core Features tests
  ci-data-layer.yml                  # All Data Layer tests
  ci-identity.yml                    # All Identity tests
  ci-camundaex.yml                   # All CamundaEx tests
  ci-orchestrator.yml                # Coordinates all team workflows
```

#### Pros of Splitting ci.yml by Owner

**1. Clear Ownership Boundaries**
- ✅ Each team owns their entire workflow file
- ✅ Teams can modify their workflows without affecting others
- ✅ Easier to review PRs - changes stay within team scope
- ✅ No merge conflicts between teams modifying ci.yml

**2. Independent Evolution**
- ✅ Teams can experiment with new test strategies
- ✅ Different teams can use different runners/configurations
- ✅ Easier to add team-specific tooling or test types
- ✅ Teams can iterate faster without coordinating changes

**3. Better Performance Control**
- ✅ Teams can optimize their own test execution
- ✅ Easier to identify which team's tests are slow
- ✅ Can set team-specific parallelization strategies
- ✅ Clearer accountability for CI resource usage

**4. Simplified Troubleshooting**
- ✅ Teams debug their own workflow file
- ✅ Clear boundary when a workflow breaks
- ✅ Easier to understand what ran (or didn't run)
- ✅ Team-specific observability and metrics

**5. Granular Access Control**
- ✅ Can use CODEOWNERS for workflow files themselves
- ✅ Teams control their own CI infrastructure
- ✅ Reduce risk of accidental breakage across teams

#### Cons of Splitting ci.yml by Owner

**1. Coordination Overhead**
- ❌ Need orchestrator workflow to coordinate all team workflows
- ❌ Cross-team dependencies become explicit and complex
- ❌ Harder to ensure all teams run on every PR
- ❌ PR checks list becomes longer/noisier

**2. Duplication of Common Logic**
- ❌ Setup steps (Maven cache, build tools) duplicated
- ❌ Change detection logic duplicated across workflows
- ❌ Shared actions need to be compatible with all team workflows
- ❌ Updates to common logic need to propagate to all files

**3. Loss of Holistic View**
- ❌ No single file showing complete CI picture
- ❌ Harder to understand overall test coverage
- ❌ Can't easily see cross-team test organization
- ❌ New contributors need to understand multiple workflows

**4. Merge Queue Complexity**
- ❌ Merge queue needs to track all team workflows
- ❌ One team's slow tests block everyone
- ❌ Harder to configure required checks
- ❌ Complex dependency graph between workflows

**5. Inconsistency Risk**
- ❌ Teams may drift toward different conventions
- ❌ Harder to enforce standards across all workflows
- ❌ Naming conventions may diverge
- ❌ Observability/metrics may become inconsistent

**6. Change Detection Complexity**
- ❌ Each workflow needs to detect if it should run
- ❌ Cross-module dependencies harder to track
- ❌ Risk of skipping tests when dependencies change
- ❌ Harder to implement "run all tests" mode

#### Hybrid Recommendation

**Keep ci.yml as central orchestrator BUT make it thinner:**

```yaml
# ci.yml - Central orchestrator
jobs:
  detect-changes:
    # Centralized change detection

  zeebe-distributed-platform-tests:
    if: needs.detect-changes.outputs.zeebe-ds-changes == 'true'
    uses: ./.github/workflows/team-zeebe-distributed-platform.yml
    with:
      changed-modules: ${{ needs.detect-changes.outputs.zeebe-ds-modules }}

  zeebe-core-features-tests:
    if: needs.detect-changes.outputs.zeebe-cf-changes == 'true'
    uses: ./.github/workflows/team-zeebe-core-features.yml
    with:
      changed-modules: ${{ needs.detect-changes.outputs.zeebe-cf-modules }}

  data-layer-tests:
    if: needs.detect-changes.outputs.data-layer-changes == 'true'
    uses: ./.github/workflows/team-data-layer.yml
    with:
      changed-modules: ${{ needs.detect-changes.outputs.data-layer-modules }}

  # ... more teams

  check-results:
    needs: [zeebe-distributed-platform-tests, zeebe-core-features-tests, ...]
    # Aggregate results for merge queue
```

**Team workflow files contain actual test logic:**
```yaml
# .github/workflows/team-zeebe-distributed-platform.yml
# owner: @camunda/zeebe-distributed-platform
on:
  workflow_call:
    inputs:
      changed-modules:
        required: true
        type: string

jobs:
  unit-tests-clustering:
    # Distributed Platform unit tests

  integration-tests-clustering:
    # Distributed Platform integration tests

  smoke-tests:
    # Cross-platform smoke tests
```

**Benefits of Hybrid Approach:**
- ✅ Teams own their test implementations
- ✅ Central coordination for change detection
- ✅ Consistent PR check experience
- ✅ Easier to enforce standards via central orchestrator
- ⚠️ Best of both worlds, but requires discipline

---

### Dynamic Test Generation from CODEOWNERS

**Question**: Can we auto-generate test matrix from CODEOWNERS instead of manual maintenance?

**Answer**: Yes! This is highly recommended and aligns with the plan's goals.

#### How Dynamic Generation Would Work

**1. Pre-execution Script**

Create `.github/scripts/generate-test-matrix-from-codeowners.sh`:

```bash
#!/bin/bash
# Reads CODEOWNERS and generates test matrix JSON for GitHub Actions

# Input: CODEOWNERS file
# Output: JSON matrix for GitHub Actions

# Features:
# - Groups modules by primary owner
# - Handles shared ownership (multi-owner modules)
# - Applies overrides from .github/test-matrix-overrides.yml
# - Validates all modules have owners
# - Generates timeout/resource configs from team defaults
```

**2. GitHub Action Integration**

```yaml
jobs:
  generate-test-matrix:
    runs-on: ubuntu-latest
    outputs:
      integration-test-matrix: ${{ steps.generate.outputs.matrix }}
    steps:
      - uses: actions/checkout@v4
      - name: Generate test matrix from CODEOWNERS
        id: generate
        run: |
          MATRIX=$(.github/scripts/generate-test-matrix-from-codeowners.sh)
          echo "matrix=${MATRIX}" >> "$GITHUB_OUTPUT"

  integration-tests:
    needs: [generate-test-matrix]
    strategy:
      matrix: ${{ fromJson(needs.generate-test-matrix.outputs.integration-test-matrix) }}
    # Tests run with auto-generated matrix
```

**3. Override Configuration**

Allow manual overrides via `.github/test-matrix-overrides.yml`:

```yaml
# Manual overrides for auto-generated test matrix
# Takes precedence over CODEOWNERS-based generation

# Override specific module ownership for testing purposes
module-overrides:
  zeebe/broker:
    owner: "@camunda/zeebe-distributed-platform,@camunda/zeebe-core-features"
    split: true  # Split into separate jobs
    splits:
      - name: "Broker Clustering"
        test-filter: "**/broker/clustering/**,**/broker/raft/**"
        timeout: 90
      - name: "Broker Engine"
        test-filter: "**/broker/engine/**,**/broker/exporter/**"
        timeout: 120

# Override test configuration for specific teams
team-overrides:
  "@camunda/zeebe-distributed-platform":
    timeout-multiplier: 1.5  # Give 50% more time
    runner: gcp-perf-core-16-longrunning

  "@camunda/zeebe-core-features":
    fork-count: 10
    runner: gcp-perf-core-16-default

# Exclude specific modules from auto-generation
# (e.g., for modules with custom test setup)
exclude-modules:
  - "zeebe/qa/integration-tests"  # Has custom split by test package
  - "zeebe/benchmarks"             # Runs in separate workflow
  - "operate/qa/backup-restore-tests"  # Has custom ES/OS matrix

# Force include modules not detected by CODEOWNERS
force-include:
  - module: "zeebe/util"
    owner: "@camunda/zeebe-distributed-platform"
    reason: "Utility module without CODEOWNERS entry"
```

#### Pros of Dynamic Generation

**1. Single Source of Truth**
- ✅ CODEOWNERS drives everything
- ✅ No drift between CODEOWNERS and test matrix
- ✅ Update CODEOWNERS → tests automatically reorganize
- ✅ Ownership changes propagate automatically

**2. Reduced Maintenance**
- ✅ No manual matrix updates when adding modules
- ✅ No need to remember to update ci.yml
- ✅ Less prone to human error
- ✅ Self-documenting (code = documentation)

**3. Automatic Coverage**
- ✅ New modules automatically included
- ✅ No risk of forgetting to test something
- ✅ Safety net ensures all modules tested
- ✅ Orphaned modules clearly identified

**4. Flexibility via Overrides**
- ✅ Can still customize when needed
- ✅ Overrides are explicit and documented
- ✅ Easy to see what's custom vs auto-generated
- ✅ Gradual migration path

**5. Better Observability**
- ✅ Can generate reports: coverage by team
- ✅ Can track test duration by owner
- ✅ Can identify unowned modules
- ✅ Metrics tied to ownership

**6. Consistency**
- ✅ All tests follow same naming convention
- ✅ All tests have proper owner metadata
- ✅ Timeout/resource allocation consistent
- ✅ Reduces variance between teams

#### Cons of Dynamic Generation

**1. Complexity**
- ❌ Requires script maintenance
- ❌ Harder to debug when generation fails
- ❌ Need to understand generation logic
- ❌ More moving parts in CI

**2. Less Explicit**
- ❌ Test matrix not visible in ci.yml
- ❌ Harder to see what tests will run
- ❌ Need to run script locally to preview
- ❌ Can't grep ci.yml for test configuration

**3. Override Management**
- ❌ Overrides can become complex
- ❌ Need discipline to minimize overrides
- ❌ Overrides can drift from CODEOWNERS
- ❌ Harder to review override changes

**4. Change Detection Challenges**
- ❌ CI needs to re-run if CODEOWNERS changes
- ❌ Generation script changes affect all tests
- ❌ Hard to test generation logic changes
- ❌ Need validation to catch generation errors

**5. Learning Curve**
- ❌ New contributors need to understand system
- ❌ Debugging requires understanding generation
- ❌ Overrides add another layer to learn
- ❌ More docs needed to explain approach

**6. Performance Overhead**
- ❌ Generation adds time to CI start
- ❌ Need to cache/optimize generation
- ❌ Every CI run parses CODEOWNERS
- ❌ Complex logic slows down feedback

#### Recommended Approach: Hybrid Dynamic Generation

**Phase 1: Generate with Validation (Recommended Start)**
- Script generates test matrix from CODEOWNERS
- Compare against current ci.yml matrix
- **Fail if they don't match** (validation mode)
- Forces keeping ci.yml and CODEOWNERS in sync
- Low risk, high value

**Phase 2: Generate with Manual Review**
- Script generates test matrix
- Output as artifact for review
- Manual copy to ci.yml after review
- Keeps explicit matrix, gets auto-generation benefits

**Phase 3: Full Dynamic Generation**
- Script directly generates matrix at runtime
- Overrides for edge cases
- ci.yml becomes thin orchestrator
- Maximum automation, requires confidence

#### Implementation Roadmap

**Week 1-2: Proof of Concept**
- [ ] Build generation script (validation mode only)
- [ ] Run in CI to catch drift
- [ ] Document how it works
- [ ] Test with different scenarios

**Week 3-4: Validation Enforcement**
- [ ] Add as required CI check
- [ ] Fix any discrepancies found
- [ ] Add override config file
- [ ] Train teams on approach

**Week 5-6: Gradual Migration**
- [ ] Pilot with one team (e.g., Identity - smallest)
- [ ] Monitor for issues
- [ ] Gather feedback
- [ ] Adjust generation logic

**Week 7-8: Full Rollout**
- [ ] Enable for all teams
- [ ] Remove manual matrix from ci.yml
- [ ] Update documentation
- [ ] Set up monitoring/alerts

#### Key Success Factors

**1. Start Simple**
- Begin with validation, not generation
- Prove the concept before committing
- Get team buy-in early

**2. Make Overrides Easy**
- Clear documentation on when to override
- Examples for common scenarios
- Easy syntax in override file

**3. Excellent Error Messages**
- When generation fails, show why
- Suggest fixes for common issues
- Link to documentation

**4. Maintain Escape Hatches**
- Always allow manual matrix as fallback
- Can disable generation if needed
- Override file can turn off auto-generation

**5. Visibility**
- Generate human-readable summary
- Show generated matrix in PR
- Make it easy to preview changes

#### Example Generated Matrix

From CODEOWNERS:
```
/zeebe/atomix @camunda/zeebe-distributed-platform
/zeebe/engine @camunda/zeebe-core-features
/zeebe/exporters @camunda/data-layer
```

Generated output:
```json
{
  "include": [
    {
      "team": "Zeebe Distributed Platform",
      "owner": "@camunda/zeebe-distributed-platform",
      "modules": "atomix,backup,journal,logstreams,scheduler,snapshot,stream-platform,transport",
      "timeout": 90,
      "runner": "gcp-perf-core-16-longrunning",
      "fork-count": 7
    },
    {
      "team": "Zeebe Core Features",
      "owner": "@camunda/zeebe-core-features",
      "modules": "engine,bpmn-model,dmn,feel,protocol,gateway",
      "timeout": 120,
      "runner": "gcp-perf-core-16-default",
      "fork-count": 10
    },
    {
      "team": "Data Layer",
      "owner": "@camunda/data-layer",
      "modules": "exporters/elasticsearch-exporter,exporters/opensearch-exporter,schema-manager",
      "timeout": 60,
      "runner": "gcp-perf-core-8-default",
      "fork-count": 5
    }
  ]
}
```

---

## Detailed Comparison: Unified vs Split-by-Owner CI Architecture

### Three Architecture Options

**Option A: Unified ci.yml (Current Proposal)**
- Single ci.yml with team-based job matrix
- All teams' tests in one file
- Manual or dynamic matrix generation

**Option B: Split by Owner with Defaults**
- Each team has their own workflow file (e.g., `ci-team-distributed-platform.yml`)
- Team files have defaults loaded from CODEOWNERS
- Teams can override splits, timeouts, etc. in their file
- Orchestrator ci.yml calls team workflows

**Option C: Fully Dynamic with Team Configs**
- Single ci.yml orchestrator
- Each team has config file (e.g., `.github/teams/distributed-platform.yml`)
- Config allows overrides of CODEOWNERS-derived defaults
- Matrix generated dynamically from CODEOWNERS + team configs

### Detailed Comparison: Unified ci.yml vs Split-by-Owner

| Aspect | **Option A: Unified ci.yml** | **Option B: Split by Owner** |
|--------|------------------------------|------------------------------|
| **File Structure** | Single ci.yml (1 file) | ci.yml orchestrator + team workflows (6-8 files) |
| **Ownership** | CODEOWNERS for modules, shared ci.yml responsibility | Teams own their workflow files completely |
| **Change Velocity** | One team's changes can affect others | Teams change independently |
| **Merge Conflicts** | High risk when multiple teams update | Low risk - changes isolated to team files |
| **Discoverability** | ✅ All tests visible in one place | ❌ Must check multiple files |
| **Consistency** | ✅ Enforced by single file structure | ⚠️ Can drift unless validated |
| **Override Flexibility** | Medium - via matrix or config file | ✅ High - each team controls their file |
| **Learning Curve** | ✅ Low - one file to understand | ⚠️ Medium - multiple files + orchestration |
| **PR Review** | ❌ Hard to review - all teams in one file | ✅ Easy - only review team's file |
| **Blast Radius** | ❌ High - break ci.yml breaks everything | ✅ Low - break team workflow affects only that team |
| **Common Logic** | ✅ Shared naturally | ❌ Must be extracted to actions |
| **Team Autonomy** | ⚠️ Limited - need coordination | ✅ High - teams control their CI |
| **Debugging** | ⚠️ Medium complexity | ✅ Clear - debug own file |
| **New Module** | Must update ci.yml | Must update team's workflow file |
| **Cross-team Changes** | Easy - one file | Hard - update multiple files |
| **Observability** | Same metrics approach | Same metrics approach |

### Option B Deep Dive: Split-by-Owner with Defaults

#### Structure

```
.github/
  workflows/
    ci.yml                                    # Orchestrator only
    ci-team-distributed-platform.yml          # Distributed Platform tests
    ci-team-core-features.yml                 # Core Features tests
    ci-team-data-layer.yml                    # Data Layer tests
    ci-team-identity.yml                      # Identity tests
    ci-team-camundaex.yml                     # CamundaEx tests
  actions/
    load-team-defaults/                       # Loads defaults from CODEOWNERS
    validate-team-workflow/                   # Validates workflow against conventions
```

#### Example Team Workflow: `ci-team-distributed-platform.yml`

```yaml
# owner: @camunda/zeebe-distributed-platform
# description: All tests for Distributed Platform team
# This workflow is OWNED by the Distributed Platform team.
# Team can modify timeouts, splits, runners, etc. without coordinating with others.

name: CI / Distributed Platform

on:
  workflow_call:
    inputs:
      changed-modules:
        description: 'Modules that changed (from change detection)'
        required: true
        type: string

defaults:
  run:
    shell: bash

jobs:
  # Load defaults from CODEOWNERS for this team
  load-defaults:
    runs-on: ubuntu-latest
    outputs:
      # Auto-detected from CODEOWNERS
      modules: ${{ steps.defaults.outputs.modules }}
      timeout: ${{ steps.defaults.outputs.timeout }}
      runner: ${{ steps.defaults.outputs.runner }}
      fork-count: ${{ steps.defaults.outputs.fork-count }}
    steps:
      - uses: actions/checkout@v4
      - name: Load team defaults from CODEOWNERS
        id: defaults
        uses: ./.github/actions/load-team-defaults
        with:
          team: '@camunda/zeebe-distributed-platform'
          # Output: modules owned by this team
          # Output: default timeout, runner, fork-count from team config

  # Unit tests - using defaults
  unit-tests:
    needs: [load-defaults]
    name: "Distributed Platform / Unit"
    runs-on: ${{ needs.load-defaults.outputs.runner }}
    timeout-minutes: ${{ fromJson(needs.load-defaults.outputs.timeout) }}
    steps:
      - uses: actions/checkout@v4
      - uses: ./.github/actions/setup-build
      - name: Run unit tests
        run: |
          ./mvnw verify -pl ${{ needs.load-defaults.outputs.modules }}

  # Integration tests - CUSTOM SPLIT by team
  # Team decided to split their integration tests into 3 jobs
  integration-tests-clustering:
    needs: [load-defaults]
    name: "Distributed Platform / Integration / Clustering"
    runs-on: gcp-perf-core-16-longrunning
    timeout-minutes: 90  # Override default
    steps:
      - uses: actions/checkout@v4
      - name: Run clustering tests
        run: |
          # Team-specific split: atomix, dynamic-config, journal
          ./mvnw verify -pl atomix,dynamic-config,journal

  integration-tests-backup:
    needs: [load-defaults]
    name: "Distributed Platform / Integration / Backup & Recovery"
    runs-on: gcp-perf-core-8-default
    timeout-minutes: 60  # Override default
    steps:
      - uses: actions/checkout@v4
      - name: Run backup tests
        run: |
          # Team-specific split: backup modules
          ./mvnw verify -pl backup,backup-stores/*,restore

  integration-tests-streams:
    needs: [load-defaults]
    name: "Distributed Platform / Integration / Streams"
    runs-on: gcp-perf-core-16-default
    timeout-minutes: 75  # Override default
    steps:
      - uses: actions/checkout@v4
      - name: Run stream tests
        run: |
          # Team-specific split: logstreams, scheduler, snapshot
          ./mvnw verify -pl logstreams,scheduler,snapshot,stream-platform

  # Specialized tests unique to this team
  smoke-tests:
    name: "Distributed Platform / Smoke - ${{ matrix.os }}"
    runs-on: ${{ matrix.runner }}
    timeout-minutes: 20
    strategy:
      matrix:
        os: [linux, windows, macos]
        include:
          - os: linux
            runner: gcp-perf-core-8-default
          - os: windows
            runner: windows-latest
          - os: macos
            runner: macos-latest
    steps:
      - uses: actions/checkout@v4
      - name: Run smoke tests
        run: ./mvnw verify -P smoke-test
```

#### Orchestrator: `ci.yml`

```yaml
name: CI

on:
  pull_request: {}
  push:
    branches: [main, stable/*]

jobs:
  detect-changes:
    # Centralized change detection
    outputs:
      zeebe-distributed-platform-changes: ${{ steps.changes.outputs.distributed-platform }}
      zeebe-core-features-changes: ${{ steps.changes.outputs.core-features }}
      data-layer-changes: ${{ steps.changes.outputs.data-layer }}
      # ... more teams

  # Call team workflows
  distributed-platform-ci:
    if: needs.detect-changes.outputs.zeebe-distributed-platform-changes == 'true'
    needs: [detect-changes]
    uses: ./.github/workflows/ci-team-distributed-platform.yml
    with:
      changed-modules: ${{ needs.detect-changes.outputs.distributed-platform-modules }}

  core-features-ci:
    if: needs.detect-changes.outputs.zeebe-core-features-changes == 'true'
    needs: [detect-changes]
    uses: ./.github/workflows/ci-team-core-features.yml
    with:
      changed-modules: ${{ needs.detect-changes.outputs.core-features-modules }}

  data-layer-ci:
    if: needs.detect-changes.outputs.data-layer-changes == 'true'
    needs: [detect-changes]
    uses: ./.github/workflows/ci-team-data-layer.yml
    with:
      changed-modules: ${{ needs.detect-changes.outputs.data-layer-modules }}

  # Aggregate results for merge queue
  check-results:
    needs:
      - distributed-platform-ci
      - core-features-ci
      - data-layer-ci
    if: always()
    runs-on: ubuntu-latest
    steps:
      - name: Check all teams passed
        run: |
          # Fail if any team workflow failed
          if [[ "${{ contains(needs.*.result, 'failure') }}" == "true" ]]; then
            exit 1
          fi
```

### Pros of Split-by-Owner (Option B)

#### 1. **Team Autonomy** ⭐⭐⭐⭐⭐
- ✅ Teams control their own CI destiny
- ✅ Can experiment without approval from other teams
- ✅ Can optimize for their specific needs
- ✅ No coordination overhead for changes
- **Example**: Distributed Platform team wants to try new runner types → just update their file

#### 2. **Reduced Merge Conflicts** ⭐⭐⭐⭐⭐
- ✅ No more "someone else changed ci.yml while I was working"
- ✅ Multiple teams can update CI simultaneously
- ✅ PRs are cleaner - only touch team's file
- **Impact**: High for active projects with many teams

#### 3. **Clearer Ownership** ⭐⭐⭐⭐
- ✅ CODEOWNERS for workflow files themselves
- ✅ Clear who to ask about CI changes
- ✅ Team owns file = team owns CI decisions
- **Example**:
  ```
  /.github/workflows/ci-team-distributed-platform.yml @camunda/zeebe-distributed-platform
  ```

#### 4. **Isolated Blast Radius** ⭐⭐⭐⭐⭐
- ✅ Breaking team workflow only affects that team
- ✅ Other teams' PRs not blocked
- ✅ Easier to debug - clear boundary
- **Example**: If Distributed Platform breaks their workflow, Core Features can still merge

#### 5. **Easier PR Reviews** ⭐⭐⭐⭐
- ✅ Reviewers only look at one team's file
- ✅ No need to understand entire CI
- ✅ Less context needed to approve
- **Impact**: Faster review cycles

#### 6. **Flexible Splits** ⭐⭐⭐⭐⭐
- ✅ Teams can split jobs however they want
- ✅ Can optimize based on test characteristics
- ✅ Can experiment with different strategies
- **Example**: Distributed Platform splits into 3 jobs, Core Features keeps as 1

#### 7. **Independent Evolution** ⭐⭐⭐⭐
- ✅ Teams can adopt new GitHub Actions features
- ✅ Can upgrade runners independently
- ✅ Can try new test frameworks
- **Example**: One team tests matrix parallelization while others keep current approach

### Cons of Split-by-Owner (Option B)

#### 1. **Fragmentation** ⭐⭐⭐⭐⭐
- ❌ No single view of all tests
- ❌ Need to check 6-8 files to understand CI
- ❌ Harder for new contributors
- ❌ Documentation needs to cover multiple files
- **Mitigation**: Generate summary dashboard from all team files

#### 2. **Duplication** ⭐⭐⭐⭐
- ❌ Setup steps duplicated across team files
- ❌ Common actions need to work for all teams
- ❌ Changes to common logic need to propagate
- **Example**: Updating Maven cache logic requires updating 6 files
- **Mitigation**: Extract to composite actions, but still need updates

#### 3. **Inconsistency Risk** ⭐⭐⭐⭐
- ❌ Teams may diverge in conventions
- ❌ Job naming may become inconsistent
- ❌ Observability may differ between teams
- **Example**: One team uses "Unit Tests", another uses "Unit / Team"
- **Mitigation**: Validation action enforces standards, but requires discipline

#### 4. **Coordination Overhead** ⭐⭐⭐
- ❌ Cross-team changes need multiple PRs
- ❌ Shared modules (e.g., broker) need coordination
- ❌ Changes affecting multiple teams are harder
- **Example**: Adding new change detection logic needs updates to 6 files
- **Mitigation**: Centralize change detection in orchestrator

#### 5. **Orchestration Complexity** ⭐⭐⭐
- ❌ ci.yml needs to know about all team workflows
- ❌ Conditional execution logic more complex
- ❌ Merge queue needs to track all workflows
- **Example**: Adding new team requires updating orchestrator

#### 6. **Testing CI Changes** ⭐⭐⭐⭐
- ❌ Harder to test cross-team changes locally
- ❌ Need to run multiple workflows to validate
- ❌ Integration issues between workflows harder to catch
- **Mitigation**: CI validation workflow that checks all team workflows

#### 7. **Change Detection Complexity** ⭐⭐⭐⭐
- ❌ Each team file needs to know if it should run
- ❌ Cross-module dependencies harder to track
- ❌ Risk of skipping tests
- **Mitigation**: Centralize change detection in orchestrator (already in design)

### Head-to-Head: Critical Decision Factors

#### Factor 1: Team Size and Activity
- **If 2-3 teams, low change frequency** → Unified ci.yml is fine
- **If 5+ teams, high change frequency** → Split by owner significantly reduces friction
- **Camunda has 5-6 active teams** → Split by owner has clear benefits

#### Factor 2: Test Customization Needs
- **If all teams have similar test patterns** → Unified ci.yml works well
- **If teams need different strategies** → Split by owner enables experimentation
- **Camunda reality**: Distributed Platform has smoke/strace tests, Core Features has property tests → Different needs

#### Factor 3: CI Evolution Rate
- **If CI is stable** → Unified ci.yml maintenance overhead is manageable
- **If CI changes frequently** → Split by owner reduces coordination
- **Camunda reality**: Active development, frequent CI updates → Split helps

#### Factor 4: Blast Radius Tolerance
- **If blocking all teams is acceptable** → Unified ci.yml acceptable
- **If team independence is critical** → Split by owner mandatory
- **Camunda priority**: Unknown, but trend in industry is toward isolation

#### Factor 5: Maintenance Philosophy
- **If prefer centralized control** → Unified ci.yml
- **If prefer team autonomy** → Split by owner
- **Camunda**: Teams already own modules → Extending to CI ownership is natural

### Hybrid Recommendation for Camunda

Given Camunda's context, I recommend a **Phased Hybrid Approach**:

#### Phase 1 (Months 1-3): Unified ci.yml with Team-Based Matrix
- Implement the current proposal (Option A)
- Validate CODEOWNERS coverage
- Establish team-based job naming
- **Why**: Prove the concept, minimal disruption

#### Phase 2 (Months 4-6): Extract Common Logic to Actions
- Create composite actions for setup steps
- Standardize observability across teams
- Build validation framework
- **Why**: Prepare for potential split, valuable regardless

#### Phase 3 (Months 7-9): Pilot Split with One Team
- Choose a team (suggest: Distributed Platform - most specialized tests)
- Split their tests to separate workflow file
- Validate orchestration works
- **Why**: Test the architecture with real usage

#### Phase 4 (Months 10-12): Evaluate and Decide
- Gather metrics: merge conflicts, review time, team satisfaction
- Assess: Did split help or hurt?
- **Decision point**:
  - If helpful → Continue rolling out to other teams
  - If not helpful → Keep unified but with better organization

#### Success Criteria for Moving to Split

Split-by-owner is worth it IF:
- ✅ Merge conflicts on ci.yml happen >2x per week
- ✅ Teams blocked waiting for CI changes >1 day average
- ✅ Teams want to experiment with different test strategies
- ✅ CI review cycle time is >2 days due to complexity
- ✅ Team satisfaction surveys show desire for autonomy

Split is NOT worth it IF:
- ❌ Teams happy with current coordination
- ❌ CI changes are infrequent (<1 per week)
- ❌ Fragmentation causes more confusion than benefit
- ❌ Maintenance overhead of multiple files is high

### Final Architecture Recommendation

**Start with Option A (Unified ci.yml)**, but architect it so migration to **Option B (Split by Owner)** is easy:

**Design Principles**:
1. ✅ Extract all setup logic to composite actions NOW
2. ✅ Use team-based job organization NOW (practice for split)
3. ✅ Centralize change detection NOW (will stay central if split)
4. ✅ Standardize naming NOW (enforced across files if split)
5. ✅ Load team defaults dynamically NOW (same mechanism if split)

**Why This Works**:
- Low risk: Start with unified
- Easy migration: All the hard work (actions, conventions) done upfront
- Flexibility: Can split teams incrementally as needed
- Reversible: Can merge back if split doesn't work

**Migration Path** (if decided to split):
```bash
# For each team:
1. Extract their jobs from ci.yml to ci-team-{name}.yml
2. Add workflow_call trigger
3. Update orchestrator to call team workflow
4. Test thoroughly
5. Merge

# Result: Same tests, different files
# Effort: ~1 day per team
# Risk: Low (tests don't change, just file location)
```

This gives Camunda the benefits of the current proposal (unified, organized by team) while keeping the door open for split-by-owner if team autonomy becomes more important.

## Comparison: Static vs Dynamic Approach

| Aspect | Static (Current) | Dynamic (Proposed) |
|--------|-----------------|-------------------|
| **Maintenance** | Manual updates to ci.yml | Auto-updates from CODEOWNERS |
| **Drift Risk** | High - can diverge | Low - single source of truth |
| **Explicitness** | Very explicit | Requires running script to see |
| **Flexibility** | Fully flexible | Flexible via overrides |
| **Debugging** | Easy - just read ci.yml | Harder - need to understand generation |
| **New Modules** | Must remember to add | Automatically included |
| **Consistency** | Easy to be inconsistent | Enforced consistency |
| **Complexity** | Simple | More complex |
| **Learning Curve** | Low | Medium |
| **Long-term Scalability** | Poor (manual work grows) | Excellent (automated) |

## Final Recommendation

**Recommended Approach for Camunda Project:**

### Phase 1 (Months 1-2): Foundation
1. ✅ Complete CODEOWNERS coverage (100% modules)
2. ✅ Implement validation script (catches drift)
3. ✅ Keep ci.yml as single file (thin orchestrator)
4. ✅ Start with team-based workflow calls (hybrid approach)

### Phase 2 (Months 3-4): Validation
5. ✅ Run validation in CI as required check
6. ✅ Build override configuration mechanism
7. ✅ Create generation script (validation mode)
8. ✅ Document generation approach

### Phase 3 (Months 5-6): Pilot
9. ✅ Enable dynamic generation for ONE team
10. ✅ Gather feedback and iterate
11. ✅ Refine override syntax based on real needs
12. ✅ Build confidence in the approach

### Phase 4 (Months 7+): Rollout
13. ✅ Enable for all teams incrementally
14. ✅ Keep override file for edge cases
15. ✅ Monitor and optimize generation performance
16. ✅ Consider splitting ci.yml if it gets too large

**Why This Approach:**
- Gradual migration reduces risk
- Validation proves the concept before committing
- Hybrid keeps benefits of both approaches
- Overrides provide escape hatches
- Can roll back if needed

**Success Metrics:**
- 95%+ CODEOWNERS coverage maintained
- <5% of tests using overrides
- Zero unowned modules
- Test ownership changes propagate in <1 week
- Teams report easier maintenance

