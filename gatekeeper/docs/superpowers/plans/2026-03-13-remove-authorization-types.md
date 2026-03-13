# Remove Authorization Types from Gatekeeper

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Scope gatekeeper to authentication only by removing all authorization types, keeping authorization for a future dedicated effort.

**Architecture:** Gatekeeper's domain layer currently mixes authentication (identity, sessions, OIDC) with authorization (resource access, tenant access, authorization conditions). We remove authorization types, simplify `SecurityContext` to authentication-only, update filters/auto-config, clean up docs/ADRs, and rework the commit history so the branch tells a clean story.

**Tech Stack:** Java 21, Maven multi-module, Spring Boot 4.0.3, JUnit 5, ArchUnit, Google Java Format (Spotless)

---

## File Structure

### Files to DELETE

**Domain model (10 files):**
- `gatekeeper-domain/src/main/java/io/camunda/gatekeeper/model/authorization/Authorization.java`
- `gatekeeper-domain/src/main/java/io/camunda/gatekeeper/model/authorization/AuthorizationCheck.java`
- `gatekeeper-domain/src/main/java/io/camunda/gatekeeper/model/authorization/AuthorizationCondition.java`
- `gatekeeper-domain/src/main/java/io/camunda/gatekeeper/model/authorization/AuthorizationConditions.java`
- `gatekeeper-domain/src/main/java/io/camunda/gatekeeper/model/authorization/SingleAuthorizationCondition.java`
- `gatekeeper-domain/src/main/java/io/camunda/gatekeeper/model/authorization/AnyOfAuthorizationCondition.java`
- `gatekeeper-domain/src/main/java/io/camunda/gatekeeper/model/authorization/ResourceAccess.java`
- `gatekeeper-domain/src/main/java/io/camunda/gatekeeper/model/authorization/ResourceAccessChecks.java`
- `gatekeeper-domain/src/main/java/io/camunda/gatekeeper/model/authorization/TenantAccess.java`
- `gatekeeper-domain/src/main/java/io/camunda/gatekeeper/model/authorization/TenantCheck.java`

**Domain SPI (5 files):**
- `gatekeeper-domain/src/main/java/io/camunda/gatekeeper/spi/ResourceAccessController.java`
- `gatekeeper-domain/src/main/java/io/camunda/gatekeeper/spi/ResourceAccessProvider.java`
- `gatekeeper-domain/src/main/java/io/camunda/gatekeeper/spi/TenantAccessProvider.java`
- `gatekeeper-domain/src/main/java/io/camunda/gatekeeper/spi/AdminUserCheckProvider.java`
- `gatekeeper-domain/src/main/java/io/camunda/gatekeeper/spi/WebComponentAccessProvider.java`

**Domain config (2 files):**
- `gatekeeper-domain/src/main/java/io/camunda/gatekeeper/config/AuthorizationConfig.java`
- `gatekeeper-domain/src/main/java/io/camunda/gatekeeper/config/MultiTenancyConfig.java`

**Domain tests (6 files):**
- `gatekeeper-domain/src/test/java/io/camunda/gatekeeper/unit/model/authorization/AuthorizationTest.java`
- `gatekeeper-domain/src/test/java/io/camunda/gatekeeper/unit/model/authorization/AuthorizationConditionTest.java`
- `gatekeeper-domain/src/test/java/io/camunda/gatekeeper/unit/model/authorization/AuthorizationCheckTest.java`
- `gatekeeper-domain/src/test/java/io/camunda/gatekeeper/unit/model/authorization/ResourceAccessTest.java`
- `gatekeeper-domain/src/test/java/io/camunda/gatekeeper/unit/model/authorization/TenantCheckTest.java`
- `gatekeeper-domain/src/test/java/io/camunda/gatekeeper/unit/model/identity/SecurityContextTest.java`

**Spring Boot starter filters (2 files):**
- `gatekeeper-spring-boot-starter/src/main/java/io/camunda/gatekeeper/spring/filter/WebComponentAuthorizationCheckFilter.java`
- `gatekeeper-spring-boot-starter/src/main/java/io/camunda/gatekeeper/spring/filter/AdminUserCheckFilter.java`

**Spring Boot starter tests (2 files):**
- `gatekeeper-spring-boot-starter/src/test/java/io/camunda/gatekeeper/spring/unit/filter/WebComponentAuthorizationCheckFilterTest.java`
- `gatekeeper-spring-boot-starter/src/test/java/io/camunda/gatekeeper/spring/unit/filter/AdminUserCheckFilterTest.java`

**Test adapters (1 file):**
- `gatekeeper-spring-boot-starter/src/test/java/io/camunda/gatekeeper/spring/integration/app/adapter/StubWebComponentAccessProvider.java`

**Obsolete plan/ADR files (2 files):**
- `docs/adr/0005-consolidate-security-core-types.md`
- `docs/superpowers/plans/2026-03-13-consolidate-security-core-types.md`

### Files to MODIFY

- `gatekeeper-domain/src/main/java/io/camunda/gatekeeper/model/identity/SecurityContext.java` — remove `authorizationCondition` field and all authorization builder methods
- `gatekeeper-spring-boot-starter/src/main/java/io/camunda/gatekeeper/spring/config/GatekeeperProperties.java` — remove authorization/multi-tenancy inner classes and config converters
- `gatekeeper-spring-boot-starter/src/test/java/io/camunda/gatekeeper/spring/unit/config/GatekeeperPropertiesTest.java` — remove authorization/multi-tenancy test methods
- `gatekeeper-spring-boot-starter/src/main/java/io/camunda/gatekeeper/spring/autoconfigure/GatekeeperSecurityFilterChainAutoConfiguration.java` — remove WebComponentAuthorizationCheckFilter references
- `gatekeeper-spring-boot-starter/src/main/java/io/camunda/gatekeeper/spring/autoconfigure/GatekeeperWebappFiltersAutoConfiguration.java` — remove AdminUserCheckFilter bean
- `gatekeeper-domain/src/test/java/io/camunda/gatekeeper/archunit/DomainArchTest.java` — remove AuthorizationConditions exemption
- `docs/adr/0001-hexagonal-auth-library.md` — remove `Authorization`, `SecurityContext` from canonical types list
- `docs/architecture.md` — remove authorization sections
- `docs/integration-guide.md` — remove authorization SPI sections

---

## Chunk 1: Remove authorization types from domain

### Task 1: Delete authorization model types and their tests

Remove the entire `model/authorization/` package and all corresponding tests.

**Files:**
- Delete: all 10 files in `gatekeeper-domain/src/main/java/io/camunda/gatekeeper/model/authorization/`
- Delete: all 5 test files in `gatekeeper-domain/src/test/java/io/camunda/gatekeeper/unit/model/authorization/`

- [ ] **Step 1: Delete authorization model source files**

```bash
rm -r gatekeeper/gatekeeper-domain/src/main/java/io/camunda/gatekeeper/model/authorization/
```

- [ ] **Step 2: Delete authorization model test files**

```bash
rm -r gatekeeper/gatekeeper-domain/src/test/java/io/camunda/gatekeeper/unit/model/authorization/
```

- [ ] **Step 3: Continue to next task (compilation will fail until SecurityContext is updated)**

---

### Task 2: Delete authorization SPI interfaces

Remove the 5 authorization-focused SPI interfaces.

**Files:**
- Delete: `gatekeeper-domain/src/main/java/io/camunda/gatekeeper/spi/ResourceAccessController.java`
- Delete: `gatekeeper-domain/src/main/java/io/camunda/gatekeeper/spi/ResourceAccessProvider.java`
- Delete: `gatekeeper-domain/src/main/java/io/camunda/gatekeeper/spi/TenantAccessProvider.java`
- Delete: `gatekeeper-domain/src/main/java/io/camunda/gatekeeper/spi/AdminUserCheckProvider.java`
- Delete: `gatekeeper-domain/src/main/java/io/camunda/gatekeeper/spi/WebComponentAccessProvider.java`

- [ ] **Step 1: Delete authorization SPI files**

```bash
rm gatekeeper/gatekeeper-domain/src/main/java/io/camunda/gatekeeper/spi/ResourceAccessController.java
rm gatekeeper/gatekeeper-domain/src/main/java/io/camunda/gatekeeper/spi/ResourceAccessProvider.java
rm gatekeeper/gatekeeper-domain/src/main/java/io/camunda/gatekeeper/spi/TenantAccessProvider.java
rm gatekeeper/gatekeeper-domain/src/main/java/io/camunda/gatekeeper/spi/AdminUserCheckProvider.java
rm gatekeeper/gatekeeper-domain/src/main/java/io/camunda/gatekeeper/spi/WebComponentAccessProvider.java
```

---

### Task 3: Delete authorization config records

**Files:**
- Delete: `gatekeeper-domain/src/main/java/io/camunda/gatekeeper/config/AuthorizationConfig.java`
- Delete: `gatekeeper-domain/src/main/java/io/camunda/gatekeeper/config/MultiTenancyConfig.java`

- [ ] **Step 1: Delete authorization config files**

```bash
rm gatekeeper/gatekeeper-domain/src/main/java/io/camunda/gatekeeper/config/AuthorizationConfig.java
rm gatekeeper/gatekeeper-domain/src/main/java/io/camunda/gatekeeper/config/MultiTenancyConfig.java
```

---

### Task 4: Simplify SecurityContext to authentication-only

Remove the `authorizationCondition` field and all authorization-related builder methods from SecurityContext. After this change, SecurityContext carries only `CamundaAuthentication`.

**Files:**
- Modify: `gatekeeper-domain/src/main/java/io/camunda/gatekeeper/model/identity/SecurityContext.java`
- Delete: `gatekeeper-domain/src/test/java/io/camunda/gatekeeper/unit/model/identity/SecurityContextTest.java`

- [ ] **Step 1: Rewrite SecurityContext**

Replace the entire file content with:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.model.identity;

/**
 * Represents the security context for the current operation, containing the authenticated
 * identity. Consumers that need authorization data should compose this with their own
 * authorization context.
 */
public record SecurityContext(CamundaAuthentication authentication) {

  public static SecurityContext of(final CamundaAuthentication authentication) {
    return new SecurityContext(authentication);
  }
}
```

- [ ] **Step 2: Delete the SecurityContext test**

The existing test exercises authorization builder methods that no longer exist.

```bash
rm gatekeeper/gatekeeper-domain/src/test/java/io/camunda/gatekeeper/unit/model/identity/SecurityContextTest.java
```

- [ ] **Step 3: Update DomainArchTest**

Remove the `AuthorizationConditions` exemption from the records-only rule.

Open `gatekeeper-domain/src/test/java/io/camunda/gatekeeper/archunit/DomainArchTest.java` and remove the line:

```
.and().doNotHaveSimpleName("AuthorizationConditions")
```

- [ ] **Step 4: Verify domain module compiles**

```bash
cd /Users/ben.sheppard/code/camunda && ./mvnw compile -pl gatekeeper/gatekeeper-domain -q
```

- [ ] **Step 5: Run domain tests**

```bash
cd /Users/ben.sheppard/code/camunda && ./mvnw test -pl gatekeeper/gatekeeper-domain -q
```

- [ ] **Step 6: Commit**

```bash
git add gatekeeper/gatekeeper-domain/
git commit -m "refactor(gatekeeper): remove authorization types from domain

Gatekeeper is scoped to authentication only. Authorization types
(Authorization, AuthorizationCondition, ResourceAccess, TenantAccess,
and related SPIs) are removed. SecurityContext is simplified to carry
only CamundaAuthentication. Authorization will be handled separately."
```

---

## Chunk 2: Update Spring Boot starter

### Task 5: Remove authorization filters and their tests

**Files:**
- Delete: `gatekeeper-spring-boot-starter/src/main/java/io/camunda/gatekeeper/spring/filter/WebComponentAuthorizationCheckFilter.java`
- Delete: `gatekeeper-spring-boot-starter/src/main/java/io/camunda/gatekeeper/spring/filter/AdminUserCheckFilter.java`
- Delete: `gatekeeper-spring-boot-starter/src/test/java/io/camunda/gatekeeper/spring/unit/filter/WebComponentAuthorizationCheckFilterTest.java`
- Delete: `gatekeeper-spring-boot-starter/src/test/java/io/camunda/gatekeeper/spring/unit/filter/AdminUserCheckFilterTest.java`
- Delete: `gatekeeper-spring-boot-starter/src/test/java/io/camunda/gatekeeper/spring/integration/app/adapter/StubWebComponentAccessProvider.java`

- [ ] **Step 1: Delete authorization filter files and tests**

```bash
rm gatekeeper/gatekeeper-spring-boot-starter/src/main/java/io/camunda/gatekeeper/spring/filter/WebComponentAuthorizationCheckFilter.java
rm gatekeeper/gatekeeper-spring-boot-starter/src/main/java/io/camunda/gatekeeper/spring/filter/AdminUserCheckFilter.java
rm gatekeeper/gatekeeper-spring-boot-starter/src/test/java/io/camunda/gatekeeper/spring/unit/filter/WebComponentAuthorizationCheckFilterTest.java
rm gatekeeper/gatekeeper-spring-boot-starter/src/test/java/io/camunda/gatekeeper/spring/unit/filter/AdminUserCheckFilterTest.java
rm gatekeeper/gatekeeper-spring-boot-starter/src/test/java/io/camunda/gatekeeper/spring/integration/app/adapter/StubWebComponentAccessProvider.java
```

---

### Task 6: Update auto-configuration classes

Remove references to deleted authorization filters and SPIs from the auto-configuration.

**Files:**
- Modify: `gatekeeper-spring-boot-starter/src/main/java/io/camunda/gatekeeper/spring/autoconfigure/GatekeeperSecurityFilterChainAutoConfiguration.java`
- Modify: `gatekeeper-spring-boot-starter/src/main/java/io/camunda/gatekeeper/spring/autoconfigure/GatekeeperWebappFiltersAutoConfiguration.java`

- [ ] **Step 1: Update GatekeeperSecurityFilterChainAutoConfiguration**

Read the file and remove:
- Import of `WebComponentAuthorizationCheckFilter`
- Import of `WebComponentAccessProvider` (if present)
- Any lines that instantiate or register `WebComponentAuthorizationCheckFilter` in filter chains
- Any TODO comments about `AdminUserCheckFilter`

- [ ] **Step 2: Update GatekeeperWebappFiltersAutoConfiguration**

Read the file and remove:
- Import of `AdminUserCheckProvider`
- Import of `AdminUserCheckFilter`
- The `adminUserCheckFilter` bean definition method

---

### Task 7: Update GatekeeperProperties

Remove authorization and multi-tenancy property bindings.

**Files:**
- Modify: `gatekeeper-spring-boot-starter/src/main/java/io/camunda/gatekeeper/spring/config/GatekeeperProperties.java`
- Modify: `gatekeeper-spring-boot-starter/src/test/java/io/camunda/gatekeeper/spring/unit/config/GatekeeperPropertiesTest.java`

- [ ] **Step 1: Update GatekeeperProperties**

Read the file and remove:
- Imports of `AuthorizationConfig` and `MultiTenancyConfig`
- The `authorizations` field and its getter/setter
- The `multiTenancy` field and its getter/setter
- The `AuthorizationsProperties` inner class
- The `MultiTenancyProperties` inner class
- The `toAuthorizationConfig()` method
- The `toMultiTenancyConfig()` method

- [ ] **Step 2: Update GatekeeperPropertiesTest**

Read the test file and remove:
- Imports of `AuthorizationConfig` and `MultiTenancyConfig`
- `shouldConvertToAuthorizationConfig()` test method
- `shouldConvertToMultiTenancyConfig()` test method
- Any assertions about authorization/tenancy defaults in other test methods

- [ ] **Step 3: Verify starter compiles**

```bash
cd /Users/ben.sheppard/code/camunda && ./mvnw compile -pl gatekeeper/gatekeeper-spring-boot-starter -q
```

- [ ] **Step 4: Run starter tests**

```bash
cd /Users/ben.sheppard/code/camunda && ./mvnw test -pl gatekeeper/gatekeeper-spring-boot-starter -q
```

- [ ] **Step 5: Commit**

```bash
git add gatekeeper/gatekeeper-spring-boot-starter/
git commit -m "refactor(gatekeeper): remove authorization filters and properties from starter

Remove WebComponentAuthorizationCheckFilter, AdminUserCheckFilter,
and authorization/multi-tenancy property bindings. Gatekeeper starter
now handles authentication only."
```

---

## Chunk 3: Update documentation and ADRs

### Task 8: Update ADR-0001 and remove obsolete ADR-0005

**Files:**
- Modify: `docs/adr/0001-hexagonal-auth-library.md`
- Delete: `docs/adr/0005-consolidate-security-core-types.md`

- [ ] **Step 1: Update ADR-0001**

Read the file and update:
- Line 24: Change the model records list from `CamundaAuthentication`, `CamundaUserInfo`, `Authorization`, `SecurityContext`, etc.` to `CamundaAuthentication`, `CamundaUserInfo`, `SecurityContext`, etc.)` — remove `Authorization`
- Line 25: Update SPI examples to remove authorization SPIs, keeping only authentication SPIs like `MembershipResolver`, `CamundaUserProvider`, `SecurityPathProvider`

- [ ] **Step 2: Delete obsolete ADR-0005**

```bash
rm gatekeeper/docs/adr/0005-consolidate-security-core-types.md
```

The ADR index (`docs/adr/README.md`) does not reference ADR-0005 so no index update is needed.

---

### Task 9: Create ADR-0005 documenting the scope decision

Create a new ADR documenting the decision to scope gatekeeper to authentication only.

**Files:**
- Create: `docs/adr/0005-scope-gatekeeper-to-authentication.md`
- Modify: `docs/adr/README.md`

- [ ] **Step 1: Write ADR-0005**

```markdown
# ADR-0005: Scope Gatekeeper to Authentication

**Date:** 2026-03-13
**Status:** Accepted

## Context

Gatekeeper-domain initially included both authentication types (CamundaAuthentication, session
management, OIDC utilities) and authorization types (Authorization, AuthorizationCondition,
ResourceAccessController, ResourceAccessProvider, TenantAccessProvider, and related models).

Attempting to consolidate security-core's authorization types into gatekeeper revealed a fundamental
incompatibility: security-core's `Authorization` record uses zeebe-protocol enums
(`AuthorizationResourceType`, `PermissionType`) while gatekeeper's uses `String`. This difference
cascades through `AuthorizationCondition`, `SecurityContext`, `ResourceAccessProvider`, and
`ResourceAccessController` — making a mechanical import swap impossible.

More importantly, authentication and authorization are distinct concerns with different consumers
and lifecycles. Mixing them in one module creates unnecessary coupling.

## Decision

Scope gatekeeper to **authentication only**:

- **Keep:** Identity model (`CamundaAuthentication`, `CamundaUserInfo`, `SecurityContext`), session
  management, OIDC integration, authentication SPIs (`MembershipResolver`, `CamundaUserProvider`,
  `SecurityPathProvider`), security filter chains
- **Remove:** All authorization types (`Authorization`, `AuthorizationCondition` hierarchy,
  `ResourceAccess`, `TenantAccess`, etc.), authorization SPIs (`ResourceAccessController`,
  `ResourceAccessProvider`, `TenantAccessProvider`, `AdminUserCheckProvider`,
  `WebComponentAccessProvider`), authorization filters, authorization/multi-tenancy config

Authorization will be addressed in a future dedicated effort, likely as interfaces in a separate
module that components implement to answer "can this identity do X?"

## Consequences

- Gatekeeper has a focused, coherent scope: establishing and managing identity
- The Authorization enum-vs-String incompatibility is no longer a blocker
- Authorization types remain in security-core for now; consumers continue using them as-is
- Future authorization work can design the right abstraction without being constrained by
  gatekeeper's domain model
- `SecurityContext` is simplified to carry only `CamundaAuthentication`
```

- [ ] **Step 2: Update ADR index**

Add to `docs/adr/README.md`:

```
| [0005](0005-scope-gatekeeper-to-authentication.md) | Scope Gatekeeper to Authentication | Accepted |
```

---

### Task 10: Update architecture.md and integration-guide.md

**Files:**
- Modify: `docs/architecture.md`
- Modify: `docs/integration-guide.md`

- [ ] **Step 1: Update architecture.md**

Read the file and:
- Remove the `authorization/` line from the module structure diagram
- Remove the "Authorization and AuthorizationCondition" section entirely
- Update the SecurityContext description to reflect it now only carries authentication
- Remove authorization SPIs (ResourceAccessProvider, ResourceAccessController, TenantAccessProvider, WebComponentAccessProvider) from the SPI overview table
- Update any ArchUnit rules that reference authorization types

- [ ] **Step 2: Update integration-guide.md**

Read the file and:
- Remove the WebComponentAccessProvider SPI section
- Remove the ResourceAccessProvider and TenantAccessProvider section
- Remove SecurityContext authorization usage examples
- Update the integration checklist to remove authorization items

- [ ] **Step 3: Delete obsolete consolidation plan**

```bash
rm gatekeeper/docs/superpowers/plans/2026-03-13-consolidate-security-core-types.md
```

- [ ] **Step 4: Commit**

```bash
git add gatekeeper/docs/
git commit -m "docs(gatekeeper): scope documentation to authentication only

Update ADR-0001, create ADR-0005 documenting scope decision, update
architecture and integration guides to remove authorization content.
Remove obsolete consolidation plan and ADR."
```

---

## Chunk 4: Final verification and commit history cleanup

### Task 11: Verify full build

- [ ] **Step 1: Format code**

```bash
cd /Users/ben.sheppard/code/camunda && ./mvnw spotless:apply -pl gatekeeper/gatekeeper-domain,gatekeeper/gatekeeper-spring-boot-starter -q
```

- [ ] **Step 2: Compile all gatekeeper modules**

```bash
cd /Users/ben.sheppard/code/camunda && ./mvnw compile -pl gatekeeper/gatekeeper-domain,gatekeeper/gatekeeper-spring-boot-starter -q
```

- [ ] **Step 3: Run all gatekeeper tests**

```bash
cd /Users/ben.sheppard/code/camunda && ./mvnw test -pl gatekeeper/gatekeeper-domain,gatekeeper/gatekeeper-spring-boot-starter -q
```

- [ ] **Step 4: Verify no stale references to deleted types**

```bash
grep -rl 'model\.authorization\.\|spi\.ResourceAccessController\|spi\.ResourceAccessProvider\|spi\.TenantAccessProvider\|spi\.AdminUserCheckProvider\|spi\.WebComponentAccessProvider\|config\.AuthorizationConfig\|config\.MultiTenancyConfig' gatekeeper/ --include='*.java' | grep -v '/test/' || echo "No stale references found"
```

- [ ] **Step 5: Commit any formatting changes**

```bash
git add gatekeeper/
git diff --cached --stat
# Only commit if there are changes
git commit -m "style: apply spotless formatting"
```

---

### Task 12: Rework commit history

Rewrite the branch's commit history so it tells a clean story without authorization types ever having been introduced. This should be done last, after all changes are verified.

**Note:** This task rewrites history on a feature branch (not main). The branch currently has 5 commits plus the new commits from this plan.

- [ ] **Step 1: Interactive rebase to squash everything into clean commits**

The target commit structure:

1. `feat: add gatekeeper domain module` — authentication-only domain types, SPIs, config, tests
2. `feat: add gatekeeper Spring Boot starter` — authentication-only auto-config, filters, tests
3. `docs: add gatekeeper architecture documentation and ADRs` — clean docs without authorization content
4. `refactor(auth): replace auth implementation with gatekeeper adapters` — adapter migration
5. `refactor: migrate consumers to gatekeeper canonical types` — consumer migration

Use `git reset main` then selectively stage and commit in the order above, ensuring no authorization types appear in any commit.

- [ ] **Step 2: Verify final commit history**

```bash
git log main..HEAD --oneline
```

- [ ] **Step 3: Verify no authorization types in any commit**

```bash
git diff main..HEAD --stat | grep -i authorization || echo "Clean"
```

