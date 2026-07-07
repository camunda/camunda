# Code Review: PR #56803 — Migrate UserTask and Job domains to CSL AuthorizationCheckPort (Inc 4d)

**Repo:** camunda/camunda · **Target:** `main` · **Focus:** behavioral parity with the pre-migration
`AuthorizationCheckBehavior` (same authorization decisions, rejection reasons/types, tenant filtering,
authorized-scope results).

Reviewed the diff against `main` and cross-referenced the old `AuthorizationCheckBehavior`,
`TenantResolver`, `AuthorizationScopeResolver`, `RejectionAggregator`, and the CSL classes
(`AuthorizationService`, `AuthorizationChecker`, `LazyTokenClaimsConverter`, `AuthorizedTenantsAdapter`,
`AnonymouslyAuthorizedTenants`).

---

## Findings (most severe first)

### 1. CRASH regression — `JobBatchActivateProcessor` returns ANONYMOUS when both flags disabled

- **File:** `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/job/JobBatchActivateProcessor.java`
- **Line:** 118 (`determineAuthorizedTenants`), triggered at line 138 (`determineTenantIds`)
- **Bug:** When `authorizationsEnabled=false AND multiTenancyChecksEnabled=false`, the new code returns
  `AuthorizedTenants.ANONYMOUS`. `AnonymouslyAuthorizedTenants.getAuthorizedTenantIds()` throws
  `UnsupportedOperationException`. For `TenantFilter.ASSIGNED`, `determineTenantIds()` calls
  `authorizedTenantIds.getAuthorizedTenantIds()` and crashes.
- **Old behavior:** `authorizationCheckBehavior.getAuthorizedTenantIds(record)` → `TenantResolver.getAuthorizedTenants`
  returned `DEFAULT_TENANTS` when `!multiTenancyEnabled` → `getAuthorizedTenantIds()` returned `['<default>']`
  (works fine).
- **Failure scenario:** Single-tenant / auth-disabled deployment (a common config). Worker sends
  `ActivateJobs` with `tenantFilter=ASSIGNED`. New code throws `UnsupportedOperationException` where the
  old code succeeded. Regression from a working path to an unhandled exception.

### 2. Inconsistent tenant resolution between Job and UserTask domains

- **File:** `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/usertask/processors/UserTaskCommandPreconditionValidator.java`
- **Line:** 102 (`determineAuthorizedTenants`)
- **Bug:** This method is missing the `!authEnabled && !multiTenancy → ANONYMOUS` first guard that
  `JobCommandPreconditionValidator` (line 133) and `JobBatchActivateProcessor` (line 118) both have. It
  falls through to `DEFAULT_TENANTS`.
- **Note:** The UserTask path actually matches the OLD behavior (`TenantResolver` also returned
  `DEFAULT_TENANTS` for `!multiTenancy`). The divergence was introduced by the *job-side* change. The
  two domains now disagree in the same PR — one of them is wrong.
- **Failure scenario:** Both flags disabled, authenticated non-anonymous user, resource with a non-default
  tenant. UserTask commands (Assign/Claim/Complete/Update) resolve `DEFAULT_TENANTS` → `getUserTask` returns
  null → `NOT_FOUND`, while job commands on the same tenant now succeed.

### 3. Job domain silently more permissive when both flags disabled

- **File:** `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/job/JobCommandPreconditionValidator.java`
- **Line:** 133 (`determineAuthorizedTenants`)
- **Bug:** With both flags disabled the job validator returns `ANONYMOUS` (no tenant restriction) where the
  old `TenantResolver.getAuthorizedTenants` returned `DEFAULT_TENANTS`.
- **Failure scenario:** Both flags disabled; a job exists with a non-default tenantId. Old:
  `getJob(key, DEFAULT_TENANTS)` filtered it out (`NOT_FOUND`). New: `authorizedTenants.isAnonymous()` is
  true → `getJob(key)` with no tenant filter → Fail/Complete/ThrowError/Update now operate on jobs of any
  tenant. Behavioral change vs `main` for the job domain.

### 4. Rejection reason changes for the no-principal case

- **Files (all migrated processors):**
  `job/JobCompleteProcessor.java` (~line 566), `job/JobFailProcessor.java`, `job/JobThrowErrorProcessor.java`,
  `job/behaviour/JobUpdateBehaviour.java`, and all four `usertask/processors/UserTask*Processor.java`
- **Bug:** When `AUTHORIZED_USERNAME` and `AUTHORIZED_CLIENT_ID` are both absent with
  `authorizationsEnabled=true`, the code returns `AuthorizationRejectionMapper.noPrincipal()`:
  `FORBIDDEN "No authenticated user or client could be determined for the request."`
- **Old behavior:** `AuthorizationCheckBehavior` aggregated a Permission rejection →
  `FORBIDDEN "Insufficient permissions to perform operation UPDATE_PROCESS_INSTANCE on resource PROCESS_DEFINITION"`.
- **Failure scenario:** Same `RejectionType` (FORBIDDEN) but an entirely different reason string. Clients or
  tests asserting on the old message text break. May be intentional — confirm with author / updated tests.

---

## Investigated and cleared (NOT bugs)

- **Wildcard `*` grants in the new per-job predicate (`JobBatchCollector`)** — Correct.
  `AuthorizationScopeStateAdapter.matchesCslScopeByResourceId` maps `ANY → true`, and
  `AuthorizationChecker.isAuthorized` always queries `[WILDCARD, specificId]`, so wildcard grants still
  authorize specific resource IDs.
- **`isAuthorizedByPropertyForPermType` querying scopes with `authorizedByAssignee()` for all three properties**
  — Not dead code. `retrieveAuthorizedAuthorizationScopes` → `findAuthorizedScopes` filters only by
  resourceType + permissionType and ignores `resourcePropertyNames`, so all PROPERTY scopes
  (assignee/candidateUsers/candidateGroups) are returned.
- **Non-wildcard rejection message `[*, <id>]` suffix (`AuthorizationRejectionMapper`)** — Matches the old
  `AuthorizationCheckBehavior` format (`FORBIDDEN_ERROR_MESSAGE_WITH_RESOURCE_IDS`), which always added
  resource IDs for PROCESS_DEFINITION/USER_TASK checks. The `p.resourceId().equals("*")` branch is
  effectively unreachable for the migrated processors (they always pass a concrete resourceId).
- **Empty-principal skip in `JobBatchCollector.buildAuthzPredicate` (`job -> false`)** — Matches old behavior
  (empty authorized-scope set skipped all jobs).
- **`authorizationsEnabled=false, multiTenancy=true` → `job -> true` in `JobBatchCollector`** — Matches old
  behavior: `AuthorizationScopeResolver.getAllAuthorizedScopes` returns `WILDCARD` when `!authorizationsEnabled`.
- **Tenant resolution via `LazyTokenClaimsConverter`** — Includes mapping-rule/group/role-derived tenant IDs,
  equivalent to the old `TenantResolver`.
- **Self-unassign secondary rejection message (`UserTaskAssignProcessor`)** — Returns only the secondary
  (CLAIM) failures, hard-coded `[assignee]` property suffix matches the old
  `UserTaskAuthorizationProperties.builder().assignee(...)` request.

---

## Notes

- Review-only; no build/tests were run. Findings 3 and 4 may be intentional and covered by updated tests —
  worth confirming with the author.
- Finding 1 is the hard blocker (unhandled exception on a previously working path).
- Finding 2 is an internal inconsistency introduced by this PR (job vs user-task tenant resolution disagree).

