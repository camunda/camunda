# Extract Security-Core Authentication Types to Gatekeeper

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace duplicated authentication classes in `security-core` with their gatekeeper equivalents, making gatekeeper the single source of truth for authentication types.

**Architecture:** Three classes in `security-core` have exact equivalents in gatekeeper: `OidcPrincipalLoader`, `OidcGroupsLoader`, and `AuthenticationMethod`. Consumers will have their imports swapped to gatekeeper packages, then the security-core originals will be deleted. The `security-core` module will gain a dependency on `gatekeeper-domain`.

**Tech Stack:** Java 21, Maven multi-module, Spring Boot 4.0.3

---

## Scope and Non-Scope

### In scope (exact duplicates with gatekeeper equivalents)

|                Security-core class                |                    Gatekeeper equivalent                    |                 External consumers                  |
|---------------------------------------------------|-------------------------------------------------------------|-----------------------------------------------------|
| `io.camunda.security.auth.OidcPrincipalLoader`    | `io.camunda.gatekeeper.auth.OidcPrincipalLoader`            | 1 file (zeebe gateway-grpc)                         |
| `io.camunda.security.auth.OidcGroupsLoader`       | `io.camunda.gatekeeper.auth.OidcGroupsLoader`               | 1 file (zeebe gateway-grpc)                         |
| `io.camunda.security.entity.AuthenticationMethod` | `io.camunda.gatekeeper.model.identity.AuthenticationMethod` | ~38 files across zeebe, operate, tasklist, dist, qa |

### Out of scope (cannot be replaced yet)

|                               Class                               |                                                                                                             Reason                                                                                                             |
|-------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `SecurityContext`                                                 | Security-core version carries `AuthorizationCondition`; gatekeeper version is auth-only. 41 consumers need the authorization data.                                                                                             |
| `SecurityConfiguration`                                           | Bundles auth + authz + multi-tenancy + CSRF + headers. 81 consumers. Not decomposable without a larger refactor.                                                                                                               |
| `AuthenticationConfiguration` / `OidcAuthenticationConfiguration` | Mutable `@ConfigurationProperties` binding classes. Gatekeeper equivalents are immutable records. They serve different purposes (Spring property binding vs domain config). Bridge already exists via authentication adapters. |
| `Authorization<T>` / `AuthorizationCondition`                     | Authorization types coupled to zeebe protocol enums. Gatekeeper is scoped to authentication only (ADR-0005).                                                                                                                   |
| `MappingRuleMatcher`                                              | No gatekeeper equivalent.                                                                                                                                                                                                      |
| `BrokerRequestAuthorizationConverter`                             | Zeebe-specific authorization logic.                                                                                                                                                                                            |
| `AssertionConfiguration` / `KeystoreConfiguration`                | No gatekeeper equivalent yet.                                                                                                                                                                                                  |

### Pre-existing state on this branch

These classes were already migrated to gatekeeper in earlier commits:
- `CamundaAuthentication` (record) -> `gatekeeper.model.identity.CamundaAuthentication`
- `CamundaAuthenticationConverter` (SPI) -> `gatekeeper.spi.CamundaAuthenticationConverter`
- `CamundaAuthenticationHolder` (SPI) -> `gatekeeper.spi.CamundaAuthenticationHolder`
- `CamundaAuthenticationProvider` (SPI) -> `gatekeeper.spi.CamundaAuthenticationProvider`

Consumer imports for these were already swapped in the `refactor: migrate consumers to gatekeeper canonical types` commit.

---

## Chunk 1: Add gatekeeper-domain dependency to security-core

### Task 1: Add gatekeeper-domain dependency to security-core POM

**Files:**
- Modify: `security/security-core/pom.xml`

- [ ] **Step 1: Check if dependency already exists**

The `refactor: migrate consumers to gatekeeper canonical types` commit may have already added this dependency (since `SecurityContext` already imports `gatekeeper.model.identity.CamundaAuthentication`).

Run: `grep -A2 'gatekeeper' security/security-core/pom.xml`

If already present, skip to Task 2.

- [ ] **Step 2: Add gatekeeper-domain dependency (if not present)**

Add to `security/security-core/pom.xml` in the `<dependencies>` section:

```xml
<dependency>
  <groupId>io.camunda</groupId>
  <artifactId>gatekeeper-domain</artifactId>
</dependency>
```

- [ ] **Step 3: Verify compilation**

Run from repo root: `mvn compile -pl security/security-core -q`

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add security/security-core/pom.xml
git commit -m "build: add gatekeeper-domain dependency to security-core"
```

---

## Chunk 2: Swap OidcPrincipalLoader and OidcGroupsLoader

These two classes have only 1 external consumer each (same file), making this a minimal-risk change.

### Task 2: Verify gatekeeper equivalents are API-compatible

**Files:**
- Read: `security/security-core/src/main/java/io/camunda/security/auth/OidcPrincipalLoader.java`
- Read: `gatekeeper/gatekeeper-domain/src/main/java/io/camunda/gatekeeper/auth/OidcPrincipalLoader.java`
- Read: `security/security-core/src/main/java/io/camunda/security/auth/OidcGroupsLoader.java`
- Read: `gatekeeper/gatekeeper-domain/src/main/java/io/camunda/gatekeeper/auth/OidcGroupsLoader.java`

- [ ] **Step 1: Compare OidcPrincipalLoader signatures**

Verify the gatekeeper version has the same public API:
- Constructor: `OidcPrincipalLoader(String usernameClaim, String clientIdClaim)`
- Method: `loadPrincipals(Map<String, Object> claims)` returning `OidcPrincipals`
- Inner record: `OidcPrincipals(String username, String clientId)`

If signatures differ, document the differences and adjust the consumer call sites in Task 3.

- [ ] **Step 2: Compare OidcGroupsLoader signatures**

Verify the gatekeeper version has the same public API:
- Constructor: `OidcGroupsLoader(String groupsClaim)`
- Method: `loadGroups(Map<String, Object> claims)` returning `List<String>`

### Task 3: Swap imports in the zeebe gateway-grpc consumer

**Files:**
- Modify: `zeebe/gateway-grpc/src/main/java/io/camunda/zeebe/gateway/interceptors/impl/AuthenticationHandler.java`

- [ ] **Step 1: Replace OidcPrincipalLoader import**

Change:

```java
import io.camunda.security.auth.OidcPrincipalLoader;
```

To:

```java
import io.camunda.gatekeeper.auth.OidcPrincipalLoader;
```

- [ ] **Step 2: Replace OidcGroupsLoader import**

Change:

```java
import io.camunda.security.auth.OidcGroupsLoader;
```

To:

```java
import io.camunda.gatekeeper.auth.OidcGroupsLoader;
```

- [ ] **Step 3: Check for OidcPrincipals inner record usage**

If `AuthenticationHandler.java` references `OidcPrincipalLoader.OidcPrincipals`, also update that import/reference to use the gatekeeper version.

- [ ] **Step 4: Update OidcAuthenticationConfiguration internal reference**

`security/security-core/src/main/java/io/camunda/security/configuration/OidcAuthenticationConfiguration.java` references `OidcGroupsLoader`. Since this file is IN security-core, it should now import from gatekeeper:

Change:

```java
import io.camunda.security.auth.OidcGroupsLoader;
```

To:

```java
import io.camunda.gatekeeper.auth.OidcGroupsLoader;
```

- [ ] **Step 5: Check zeebe/gateway-grpc POM for gatekeeper-domain dependency**

The `zeebe/gateway-grpc` module needs `gatekeeper-domain` on its classpath. Check if it's already present (it may be transitively via `security-core`). If not, add it.

Run: `grep -r 'gatekeeper' zeebe/gateway-grpc/pom.xml`

- [ ] **Step 6: Compile and verify**

Run: `mvn compile -pl zeebe/gateway-grpc -q`

Expected: BUILD SUCCESS

### Task 4: Delete OidcPrincipalLoader and OidcGroupsLoader from security-core

**Files:**
- Delete: `security/security-core/src/main/java/io/camunda/security/auth/OidcPrincipalLoader.java`
- Delete: `security/security-core/src/main/java/io/camunda/security/auth/OidcGroupsLoader.java`
- Delete: `security/security-core/src/test/java/io/camunda/security/auth/OidcPrincipalLoaderTest.java` (if exists)
- Delete: `security/security-core/src/test/java/io/camunda/security/auth/OidcGroupsLoaderTest.java` (if exists)

- [ ] **Step 1: Verify no remaining references in security-core**

Run: `grep -r "security.auth.OidcPrincipalLoader\|security.auth.OidcGroupsLoader" security/`

All hits should be in the files we're about to delete (plus `OidcAuthenticationConfiguration` which was updated in Task 3 Step 4). If there are other references, update them first.

- [ ] **Step 2: Delete the source files**

```bash
rm security/security-core/src/main/java/io/camunda/security/auth/OidcPrincipalLoader.java
rm security/security-core/src/main/java/io/camunda/security/auth/OidcGroupsLoader.java
```

- [ ] **Step 3: Delete corresponding test files (if they exist)**

```bash
rm -f security/security-core/src/test/java/io/camunda/security/auth/OidcPrincipalLoaderTest.java
rm -f security/security-core/src/test/java/io/camunda/security/auth/OidcGroupsLoaderTest.java
```

- [ ] **Step 4: Compile security-core**

Run: `mvn compile -pl security/security-core -q`

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add -A security/ zeebe/gateway-grpc/
git commit -m "refactor: replace security-core OidcPrincipalLoader/OidcGroupsLoader with gatekeeper equivalents"
```

---

## Chunk 3: Swap AuthenticationMethod enum

This has ~38 consumers across zeebe, operate, tasklist, dist, and qa modules.

### Task 5: Verify gatekeeper AuthenticationMethod is API-compatible

**Files:**
- Read: `security/security-core/src/main/java/io/camunda/security/entity/AuthenticationMethod.java`
- Read: `gatekeeper/gatekeeper-domain/src/main/java/io/camunda/gatekeeper/model/identity/AuthenticationMethod.java`

- [ ] **Step 1: Compare enum values and methods**

Security-core version has:
- Values: `BASIC`, `OIDC`
- Method: `parse(String)` returning `Optional<AuthenticationMethod>`

Verify gatekeeper version has identical values and `parse()` method. If the gatekeeper version lacks `parse()`, add it before proceeding.

### Task 6: Swap AuthenticationMethod imports in security-core internal references

**Files:**
- Modify: `security/security-core/src/main/java/io/camunda/security/configuration/AuthenticationConfiguration.java`
- Modify: Any other security-core files that import `io.camunda.security.entity.AuthenticationMethod`

- [ ] **Step 1: Find all references within security-core**

Run: `grep -rn "security.entity.AuthenticationMethod" security/`

- [ ] **Step 2: Replace each import**

Change:

```java
import io.camunda.security.entity.AuthenticationMethod;
```

To:

```java
import io.camunda.gatekeeper.model.identity.AuthenticationMethod;
```

- [ ] **Step 3: Compile security-core**

Run: `mvn compile -pl security/security-core -q`

Expected: BUILD SUCCESS

### Task 7: Swap AuthenticationMethod imports in zeebe modules

**Files:**
- Modify: All files in `zeebe/` that import `io.camunda.security.entity.AuthenticationMethod`

Consumer files (from exploration):
- `zeebe/gateway-grpc/src/main/java/.../AuthenticationMetrics.java`
- `zeebe/gateway-grpc/src/test/java/.../StubbedGateway.java`
- `zeebe/gateway-grpc/src/test/java/.../AuthenticationInterceptorTest.java`
- `zeebe/gateway-rest/src/main/java/.../SetupController.java`
- `zeebe/gateway-rest/src/test/java/.../SetupControllerTest.java`
- `zeebe/qa/util/src/main/java/.../TestSpringApplication.java`
- `zeebe/qa/util/src/main/java/.../TestStandaloneApplication.java`
- `zeebe/qa/util/src/main/java/.../TestStandaloneBroker.java`
- `zeebe/qa/integration-tests/src/test/java/...` (multiple test files)

- [ ] **Step 1: Bulk replace imports in zeebe**

For each file, change:

```java
import io.camunda.security.entity.AuthenticationMethod;
```

To:

```java
import io.camunda.gatekeeper.model.identity.AuthenticationMethod;
```

- [ ] **Step 2: Verify gatekeeper-domain is on classpath for zeebe modules**

Check if `gatekeeper-domain` is already a transitive dependency via `security-core`. If not, the affected zeebe submodules need it added to their POMs.

- [ ] **Step 3: Compile zeebe**

Run: `mvn compile -pl zeebe/gateway-grpc,zeebe/gateway-rest -q`

Expected: BUILD SUCCESS

### Task 8: Swap AuthenticationMethod imports in operate, tasklist, dist

**Files:**
- Modify: `operate/common/src/main/java/.../OperateProfileService.java`
- Modify: `tasklist/webapp/src/main/java/.../TasklistProfileService.java`
- Modify: `dist/src/main/java/.../WebappsConfigurationInitializer.java`
- Modify: `dist/src/main/java/.../AdminClientConfigController.java`
- Modify: `dist/src/test/java/.../DefaultConfigurationTest.java`
- Modify: `dist/src/test/java/.../AdminClientConfigControllerTest.java`

- [ ] **Step 1: Replace imports in each file**

Same pattern: `io.camunda.security.entity.AuthenticationMethod` -> `io.camunda.gatekeeper.model.identity.AuthenticationMethod`

- [ ] **Step 2: Verify gatekeeper-domain is on classpath**

These modules likely get `gatekeeper-domain` transitively through `security-core` or `gatekeeper-spring-boot-starter`. Verify with:

Run: `mvn dependency:tree -pl operate/common | grep gatekeeper`

- [ ] **Step 3: Compile affected modules**

Run: `mvn compile -pl operate/common,tasklist/webapp,dist -q`

Expected: BUILD SUCCESS

### Task 9: Swap AuthenticationMethod imports in qa modules

**Files:**
- Modify: `qa/util/src/main/java/.../TestCamundaApplication.java`
- Modify: `qa/acceptance-tests/src/test/java/...` (multiple test files, ~15 files)

- [ ] **Step 1: Bulk replace imports**

Same pattern as above.

- [ ] **Step 2: Compile qa modules**

Run: `mvn compile -pl qa/util -q`

Expected: BUILD SUCCESS

### Task 10: Delete AuthenticationMethod from security-core

**Files:**
- Delete: `security/security-core/src/main/java/io/camunda/security/entity/AuthenticationMethod.java`

- [ ] **Step 1: Verify no remaining references**

Run: `grep -rn "security.entity.AuthenticationMethod" --include="*.java" .`

Expected: No hits.

- [ ] **Step 2: Delete the file**

```bash
rm security/security-core/src/main/java/io/camunda/security/entity/AuthenticationMethod.java
```

- [ ] **Step 3: Full compile**

Run: `mvn compile -T1C -q`

Expected: BUILD SUCCESS

- [ ] **Step 4: Run spotless**

Run: `mvn spotless:apply`

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: replace security-core AuthenticationMethod with gatekeeper equivalent"
```

---

## Chunk 4: Rework commit history

### Task 11: Fold changes into existing branch commits

The import swaps and deletions should be folded into the existing commits on `spike/new-replacement-auth-lib` so the types never appear to be duplicated.

- [ ] **Step 1: Create fixup commits**

The OidcPrincipalLoader/OidcGroupsLoader swap + AuthenticationMethod swap should be fixup'd into `refactor: migrate consumers to gatekeeper canonical types` (the last commit on the branch).

- [ ] **Step 2: Autosquash rebase**

```bash
GIT_SEQUENCE_EDITOR=true git rebase -i --autosquash <base-commit>~1
```

- [ ] **Step 3: Verify final commit history**

```bash
git log --oneline spike/new-replacement-auth-lib ^main
```

Expected: Same 5 commits, no fixup remnants.

- [ ] **Step 4: Run full test suite for gatekeeper module**

```bash
cd gatekeeper && ../mvnw test
```

Expected: All tests pass.
