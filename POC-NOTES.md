# Physical Tenants at Scale: integrated proof of concept

Branch `pt-poc-integration`, on top of `pt-poc-lifecycle-api`. Local only, not for merge as is.

This branch combines the proof-of-concept branches of the initiative into one tree. Each branch
keeps its own notes under [`poc-notes/`](poc-notes):

|                       Branch                       |                                                  Notes                                                  |
|----------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `pt-poc-lifecycle-tenant-set` (lifecycle slice 0)  | [lifecycle-tenant-set.md](poc-notes/lifecycle-tenant-set.md)                                            |
| `pt-poc-lifecycle-api` (lifecycle slice 1)         | [lifecycle-api.md](poc-notes/lifecycle-api.md)                                                          |
| `pt-poc-dynamic-secrets-refresh` (incl. PR #67248) | [dynamic-secrets-refresh.md](poc-notes/dynamic-secrets-refresh.md)                                      |
| `pt-poc-lazy-tenant-resources`                     | [lazy-tenant-resources.md](poc-notes/lazy-tenant-resources.md)                                          |
| `pt-poc-tenant-disk-limit`                         | [tenant-disk-limit.md](poc-notes/tenant-disk-limit.md)                                                  |
| `pt-poc-tenant-restart-handover`                   | [Leadership handover before a tenant restart](#leadership-handover-before-a-tenant-restart-us-17) below |

## What the integration proves

|              Step               |                                                                                                                                                                                                                                 What it proves                                                                                                                                                                                                                                  |                                                                             Proven by                                                                              |
|---------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Merges                          | The four branches build and test together; text conflicts only in the notes, `RdbmsDataSources` and `MyBatisConfigurationUnreachableDatabaseTest`.                                                                                                                                                                                                                                                                                                                              | full `./mvnw install -Dquickly` after each merge                                                                                                                   |
| RDBMS pool design               | The lazy holder sits inside the refreshable wrapper (`LazyPool` is the client of `RefreshableClient`). A rotation before first use builds nothing, and the pool is built with the latest settings on first use; after first use a password is applied in place and a URL change builds, verifies, swaps and drains as before. Close works never built, built, failed, closed twice and after a URL swap. The unpooled schema data source follows rotations.                     | `LazyPoolTest` (6), `RdbmsDataSourcesRefreshTest` (13)                                                                                                             |
| Key classification              | `used-space.processing/replication` are `RESTART` (read by the tenant's partition manager, rebuilt on every module restart). `rdbms.lazy-initialization` is `REJECT outside-module` (built once by Spring). `secrets.refresh-interval` was `REJECT outside-module` until the refreshers followed the lifecycle, now `RESTART`.                                                                                                                                                  | `PhysicalTenantKeyClassificationTest`, `PhysicalTenantDiskLimitUpdateIT`                                                                                           |
| Credential guard                | `PhysicalTenantCredentialGuard` runs as a `PhysicalTenantConfigValidator` (`PhysicalTenantCredentialValidator`) on API create, API update and the file registration of a new tenant, on the receiving node before anything is sent to the coordinator. A literal password reaches neither the data directory (tenant config blobs, `.topology.meta`, logs of Raft and RocksDB), the cluster configuration, the logs (all loggers, rendered with stack traces) nor the response. | `PhysicalTenantCredentialValidatorTest` (5), `PhysicalTenantCredentialGuardIT` (3)                                                                                 |
| Refreshers follow the lifecycle | `PhysicalTenantResources` hook in `PhysicalTenantLifecycleService`. An added tenant gets a refresher on its own internal-config store; a replaced tenant's refresher is re-pointed (new store, new interval, same consumers); a removed tenant's refresher is cancelled and closes its consumers, which for RDBMS closes the `RefreshableClient`.                                                                                                                               | `InternalConfigRefresherTest` (11), `RdbmsDataSourcesRefreshTest#shouldCloseThePoolWhenTheTenantIsRemoved`, `PhysicalTenantSecretRefreshLifecycleIT` (4)           |
| One health model                | `SecretRefreshHealth` and its indicator are gone; the refresher reports `DEGRADED` through `PhysicalTenantRegistry#reportHealth`, with a detail naming the consumer and error, never a value, and only clears a degradation it reported. It shows under `/actuator/health/physicalTenants/<id>`; liveness and readiness stay `UP`.                                                                                                                                              | `InternalConfigRefresherTest`, `PhysicalTenantSecretRefreshLifecycleIT#shouldReportAFailedRefreshOnlyInTheTenantsOwnHealth`, `PhysicalTenantRdbmsSecretRotationIT` |
| Secret stores closed            | A runtime context gets job secret stores built from its own configuration when its module starts, and the lifecycle's close helper releases them through #67248's `SecretStoreResources`. The leak test now configures a file job store and an internal-config store for the restarted tenant.                                                                                                                                                                                  | `PhysicalTenantRestartLeakIT`                                                                                                                                      |
| Disk limit at runtime           | A `PUT` through the actuator lowers a tenant's limit below its usage: only that tenant is paused (`RESOURCE_EXHAUSTED`), `default` keeps accepting; raising it resumes the tenant without compaction.                                                                                                                                                                                                                                                                           | `PhysicalTenantDiskLimitUpdateIT`                                                                                                                                  |

A bug found on the way: building the new job stores before closing the old ones on a replace
removed the new stores' cache meters (both publish the same meter ids), and so did validating a
change of a running tenant. The stores are now built only when the module starts, after the old
module released its own, and validation only checks the store configuration.

## How to run

```bash
./mvnw install -Dquickly -T3
./mvnw verify -pl zeebe/qa/integration-tests -Dquickly -DskipTests=false -DskipUTs \
  -Dit.test='PhysicalTenantRuntimeLifecycleIT,PhysicalTenantRestartLeakIT,PhysicalTenantRestartWithDataIT,PhysicalTenantConfigRolloutIT,PhysicalTenantDiskUsageIsolationIT,PhysicalTenantDiskUsageLimitIT,PhysicalTenantDiskReplicationLimitIT,PhysicalTenantCredentialGuardIT,PhysicalTenantSecretRefreshLifecycleIT,PhysicalTenantDiskLimitUpdateIT'
# PostgreSQL in Docker; the rdbms profile includes @Tag("rdbms") tests
./mvnw verify -pl qa/acceptance-tests -Prdbms -Dquickly -DskipTests=false -DskipUTs \
  -Dit.test=PhysicalTenantRdbmsSecretRotationIT
# not tagged rdbms, so run without the profile
./mvnw verify -pl qa/acceptance-tests -Dquickly -DskipTests=false -DskipUTs \
  -Dit.test=PhysicalTenantLazyRdbmsResourcesIT
./mvnw verify -pl configuration -Dquickly -DskipTests=false -DskipITs
./mvnw clean verify -pl dist -Dquickly -DskipTests=false -DskipITs \
  -Dtest='io.camunda.application.commons.secrets.**.*Test,io.camunda.application.commons.rdbms.**.*Test,io.camunda.application.commons.pt.**.*Test,io.camunda.zeebe.broker.**.*Test,io.camunda.zeebe.shared.management.**.*Test'
```

## Results on the combined tree (2026-10-29, Apple-silicon laptop)

|                             Suite                             |                                                                   Result                                                                   |
|---------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| zeebe/qa/integration-tests, 10 classes above                  | 31 tests: 30/31 on the first run (`PhysicalTenantRestartLeakIT`: 114 threads after vs 113 before), 31/31 on the re-run                     |
| `PhysicalTenantRdbmsSecretRotationIT` (`-Prdbms`, PostgreSQL) | 3/3                                                                                                                                        |
| `PhysicalTenantLazyRdbmsResourcesIT`                          | 4/4                                                                                                                                        |
| `configuration` unit suite                                    | 1442/1442 (after the `fix:` that renames `ConfigurationSecretReference.PREFIX`, which `CodeQualityTest` rejected since the secrets branch) |
| dist touched packages (51 classes)                            | 1493 passed, 1 skipped (`MyBatisStatementSplitCostTest`, opt-in)                                                                           |
| zeebe/broker disk limit unit tests                            | 23/23                                                                                                                                      |

`PhysicalTenantRestartLeakIT` over 20 restarts: tenant meters 179 → 179 (7 of them secret cache
meters), actors, RocksDB instances and file descriptors constant, restarts 75–365 ms.

## What remains

- **RDBMS for a tenant added at runtime (step 8) is not done.** The lazy holder makes the pool and
  MyBatis cheap to add, but everything that would use them is built once from the boot tenants:

  - `RdbmsExporterConfiguration` (and the whole RDBMS Spring context) is loaded only when the root
    `secondary-storage.type` is `rdbms`, so a node whose boot tenants use none or a search engine
    has no RDBMS exporter factory at all;
  - `RdbmsExporterFactory`/`RdbmsExporterWrapper` (zeebe/exporters/rdbms-exporter) take a
    boot map of exporter configurations and throw for an unknown tenant in `configure`;
  - `RdbmsServiceFactory` (db/rdbms) takes `Map.copyOf` maps of mapper bundles and readers
    (`MyBatisConfiguration#rdbmsMapperBundles`, `RdbmsConfiguration#rdbmsTenantReaders`);
  - `RdbmsDataSources` and `PhysicalTenantMyBatisResources` hold fixed per-tenant maps;
  - schema initialisation (`physicalTenantSchemaConfigs`, `RdbmsSchemaManagerRegistry`,
    `PerTenantSchemaInitialization`) and the migration status provider iterate the boot resolver;
  - the exported-position suppliers are copied into `BrokerStartupContextImpl` at boot.

  Making these lookups live (functions or registries with add/remove instead of maps), adding a
  `PhysicalTenantResources` for RDBMS that registers the tenant in each and its pool with the
  refresher, and loading the RDBMS context whenever any tenant may use it, is the work; it crosses
  db/rdbms, the RDBMS exporter and the schema initialisation, so it was left out. Runtime-added
  tenants still need `secondary-storage.type=none`, which is documented but not enforced.

- A replaced **boot** tenant now runs on job stores of its own, but the boot stores stay open until
  shutdown and share meter ids with the new ones; removing it later removes those meters for both.
  The REST side keeps the boot stores, as it keeps the boot security chain.

- Removing a boot tenant with RDBMS closes its pool through the refresher; its Spring-side readers
  and row-count gauges stay and fail closed.

- A rotation before first use is accepted without connecting, so a wrong secret only shows when the
  tenant is first used.

- `PhysicalTenantRestartLeakIT` counted one extra thread once when it ran right after other classes
  in the same JVM; it passed alone and on the batch re-run. Not investigated further.

- The secret store configuration of a tenant is still `REJECT outside-module`, because the REST
  side keeps the boot stores; only `secrets.refresh-interval` follows a replace.

- Elasticsearch/OpenSearch clients, the CSL dispatcher and leadership transfer before a restart are
  unchanged from the branches' notes.

# Rollout safety: per-tenant changes, readiness gate and cancel

Branch `pt-poc-rollout-safety`, written on top of `pt-poc-csl-runtime-security` (ae1a3b56a1a) and
carried here without the Camunda Security Library commits. The scenarios that ran in
`PhysicalTenantRuntimeSecurityIT` (an unreachable identity provider holding a tenant's change) are
not on this branch; the table names the tests that remain. Not for merge as is.

Two problems measured on the branch below: one global change lock (a tenant change that cannot
complete blocks every other tenant's change cluster-wide), and a rolling restart that moves on to
the next member before the restarted replica is healthy again.

## What is proven, and by which test

|                                  Claim                                   |                                                                                                                        Evidence                                                                                                                         |                                                                      Test                                                                       |
|--------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| Changes of different tenants roll out independently                      | While one tenant's change cannot apply on the first member, other tenants' changes roll out to every member                                                                                                                                             | `PhysicalTenantConfigRolloutTest#shouldRollOutChangesOfOtherTenantsWhileOneTenantsChangeCannotApply` (3 members, real messaging, fake executor) |
| A second change of the same tenant is rejected (US-8)                    | `409 IN_PROGRESS` "A change of physical tenant '…' is in progress" from the coordinator (a delete while the update rolls out)                                                                                                                           | `PhysicalTenantConfigRolloutIT#shouldRejectASecondChangeOfATenantWhileItsChangeRollsOut`; `PhysicalTenantConfigChangesTest`                     |
| Another tenant's change is accepted while one rolls out                  | `202`, both roll out                                                                                                                                                                                                                                    | `PhysicalTenantConfigRolloutIT#shouldRollOutAChangeOfAnotherTenantWhileOneRollsOut`                                                             |
| Cluster plans stay serialised against tenant rollouts                    | A cluster-wide plan (member leave) and a plan for the rolling tenant's group are rejected (`ConcurrentModificationException` naming the tenant); a plan for another group is admitted. Without the guard the member leave was admitted (mutation check) | `NewModelConfigurationChangeCoordinatorTest` (3 new tests)                                                                                      |
| Cancel rolls a stuck update back                                         | `POST /actuator/physicalTenants/<id>/cancel` → `202`; version 3 with the hash of version 1 rolled out on every broker; old tokens 200; no module restarted (same module contexts); member 0 `HEALTHY` again; a second cancel `400` "No change"          | not on this branch (needed the security IT)                                                                                                     |
| Cancel restarts only members that applied the cancelled version          | member 0 applied version 2, member 1 could not, member 2 was not reached: after the cancel member 0 ran 1, 2, 3 and member 2 ran 1, 3; every member runs version 1's configuration                                                                      | `PhysicalTenantConfigRolloutTest#shouldRollACancelledUpdateBackOnEveryMember`; rules in `PhysicalTenantConfigChangesTest` (6 tests)             |
| Quorum is never lost under load with the readiness gate                  | 3 brokers, RF 3, continuous `publishMessage` through all three gateways, healthy replicas sampled every 10 ms on every broker: minimum 2 healthy replicas, 0 ms below quorum, in 6 of 6 rollouts (2 runs)                                               | `PhysicalTenantRolloutReadinessIT#shouldKeepAQuorumOfTheChangedTenantsPartitionUnderLoad`                                                       |
| Without a wait the quorum is lost                                        | readiness `NONE`: minimum 0 healthy replicas, ~2.8 s below quorum per rollout                                                                                                                                                                           | `PhysicalTenantRolloutReadinessIT#shouldLoseTheQuorumWhenTheRolloutDoesNotWait`                                                                 |
| A rollout that would lose a quorum is held                               | broker 2 down: the update waits on member 0 before restarting ("before the restart: partition 1 has 1 of the 2 healthy replicas …"), no replica restarts, and completes once broker 2 is back                                                           | `PhysicalTenantConfigRolloutIT#shouldHoldARolloutThatWouldLoseAQuorumUntilTheDownBrokerReturns`                                                 |
| A never-ready member stops the rollout, shows in health, no restart loop | readiness timeout → tenant `DEGRADED` with the reason, step fails, the retry waits again without a second restart, then `HEALTHY`                                                                                                                       | `PhysicalTenantConfigExecutorTest` (3, incl. another tenant applying on the same member while one waits)                                        |

A replica counts as healthy in the IT when its Raft role is leader or follower and its
`ZeebePartition` reports `HEALTHY`.

## How it works

|              Piece               |     Where      |                                                                                                                                                                                             What                                                                                                                                                                                             |
|----------------------------------|----------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Per-tenant lock                  | dynamic-config | `PhysicalTenantConfigChanges` no longer rejects a change because another tenant's group has a pending rollout. dynamic-config already drives each group with its own `GraphScopeReconciler` and per-operation retries, so nothing else had to change for rollouts to run side by side                                                                                                        |
| Cluster plans vs rollouts        | dynamic-config | `ConfigurationChangeCoordinatorImpl` rejects a phased plan whose scope covers a group that is rolling out a tenant config (`PartitionGroupConfiguration#isChangingPhysicalTenantConfig`): a `Global` plan (scaling, member join/leave) while any tenant rolls out, a `Groups` plan while its own group does. Tenant changes were already rejected while a conflicting phased plan is pending |
| Cancel                           | dynamic-config | `PhysicalTenantConfigRequest.Change.CANCEL` (proto value 4). The coordinator reads version v-1 from its blob store, `cancelPendingChanges()` on the group (recorded as `CANCELLED`), and starts version v+1 with v-1's hash on every live member. Only an update can be cancelled                                                                                                            |
| Per-tenant execution on a member | dist           | `PhysicalTenantConfigExecutor` chains each tenant's changes on virtual threads; different tenants run concurrently; module start/stop is serialised by a member-wide lock (the lifecycle's own lock stays as a guard)                                                                                                                                                                        |
| Readiness gate                   | dist           | `PhysicalTenantRolloutReadiness`: before the restart, every local partition of the tenant must keep `min(⌊RF/2⌋+1, RF-1)` healthy replicas on other members (gossiped topology of the embedded broker client); after it, installed + role leader/follower + leader known + partition `HEALTHY`. Bounded, polled every 50 ms                                                                  |
| No restart loop                  | dist           | the executor records the new configuration as running but not ready; a retry of the same version only waits again                                                                                                                                                                                                                                                                            |
| Health                           | dist           | a readiness timeout reports `DEGRADED` "applied version N …, but is not ready: …"; a degradation from a rejected change (`INVALID`) or a readiness timeout is cleared once the member runs a version again (e.g. the rollback)                                                                                                                                                               |
| Observability (US-9)             | dist           | existing: `appliedVersions`, `rolledOut`, `changing`, gauge `zeebe.physical.tenant.config.applied.version`. Added: `pendingMembers` (in order; the first is the member the rollout runs on or waits for) and `rollout` (what it waits for on the answering member, version, since)                                                                                                           |
| Configuration                    | dist           | `physical-tenant-rollout.readiness` = `NONE` / `INSTALLED` (the previous behaviour) / `SERVING` (default); `physical-tenant-rollout.readiness-timeout` (default 2 min). PoC knobs outside the unified configuration                                                                                                                                                                          |

Cancel semantics: the tenant goes back to the configuration before the update, as a new version, so
versions only grow and every member records the rollback. A member that applied the cancelled
version restarts the tenant on the previous configuration; a member that did not runs it already
(same hash) and restarts nothing; a member whose apply was rejected (`KEPT_PREVIOUS`) becomes
healthy again. Cancelling a rollback goes back to the version before it, i.e. the cancelled one.

## Measurements

Apple-silicon laptop, three brokers in one JVM, warm JVM, other agents' builds running on the
machine. Load: one gRPC client per broker gateway publishing messages in a loop (5 s timeout).
"Longest gap" is the longest time in which no publish succeeded through any gateway.

|  Readiness  | Rollouts | Rollout to 3 brokers | Longest gap |  Failed / accepted   | Min healthy replicas | Below quorum |
|-------------|----------|----------------------|-------------|----------------------|----------------------|--------------|
| `NONE`      | 3        | 0.24–0.70 s          | 5.0 s (×3)  | 21–82 / 350–522      | 0                    | 2.74–2.90 s  |
| `INSTALLED` | 3        | 5.4–8.1 s            | 2.8–3.9 s   | 113–165 / 3352–10021 | 1                    | 23–92 ms     |
| `SERVING`   | 6        | 5.6–8.4 s            | 2.9–3.4 s   | 80–274 / 5502–8857   | 2                    | 0            |

`NONE`'s 5.0 s gaps are bounded below by the client's 5 s request timeout: requests sent while no
replica could commit hung until it. The ~3 s gap that remains with `SERVING` is the election after
the leader's member restarts (as on the earlier branches); leadership transfer before the restart
(US-17) is what would shorten it.

Other numbers:

|                             What                             |              Result              |
|--------------------------------------------------------------|----------------------------------|
| Two other tenants' updates rolled out while a third is stuck | 3.9–4.0 s for both (2 runs)      |
| Cancel of a stuck update, rolled back on every broker        | 129–130 ms (2 runs; no restarts) |
| Rollout IT update without load (`SERVING`)                   | 3.3–3.4 s; longest gap 2.7–2.8 s |

## Results on this tree (2026-10-29)

|                                                                        Suite                                                                        |                                                                                   Result                                                                                    |
|-----------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| dynamic-config impl: `PhysicalTenantConfig*Test`, `NewModelConfigurationChangeCoordinatorTest`, `ClusterConfigurationManagementRequestsHandlerTest` | 51/51; 49/49 on a re-run without the handler test                                                                                                                           |
| dist `PhysicalTenantConfigExecutorTest`                                                                                                             | 3/3                                                                                                                                                                         |
| `PhysicalTenantRolloutReadinessIT`                                                                                                                  | 3/3 (plus 1/1 SERVING alone)                                                                                                                                                |
| `PhysicalTenantConfigRolloutIT`                                                                                                                     | 9/9 (first run 6/9: the down-broker test expected the old behaviour and the next two tests timed out behind the stopped broker; the test was rewritten for the quorum hold) |
| `PhysicalTenantRuntimeLifecycleIT`                                                                                                                  | 8/8                                                                                                                                                                         |

## Not done, or limits

- **Cancel of a create or a delete** is rejected. A stuck create would need a rollback to "not
  present" (the delete path) and a stuck delete has nothing to go back to.
- **Cancel needs version v-1 on the coordinator**: it reads its own blob store; a coordinator that
  does not hold it rejects the cancel (no fetch from peers).
- **Cancel through the file input** is not possible: a `FILE`-owned tenant's change is cancelled
  only through its owner, and the file would send its version again on the next coordinator start.
- **The cancel of a member's in-flight apply is not interrupted**: dynamic-config forgets the
  operation, but an apply already running completes and is then rolled back by the next version,
  because a tenant's changes run one after the other on the member.
- **The quorum hold makes a rollout wait while a replica of the tenant is down** (e.g. a broker is
  down), up to the timeout and then with dynamic-config's retries. This is the intended trade (no
  restart that loses a quorum), but it changes slice 1's "members that are up apply the change"
  behaviour; an operator can cancel. A forced mode is not implemented.
- **The pre-restart check reads the gossiped topology**, so it can be a gossip round behind; the
  post-restart check is local. Replicas that are healthy but lagging are not detected beyond what
  Raft's "ready after the first commit" and partition health show.
- **The INSTALLED vs SERVING difference** is in the minimum healthy replicas, not in the measured
  unavailability: three rollouts each are too few to tell their gaps apart, and the dip with
  `INSTALLED` was 23–92 ms.
- **Readiness knobs are Spring properties outside the unified configuration**, not validated and
  not per tenant.
- **No metric for "rollout waiting"** or per-member cluster-wide wait reasons: `rollout` shows the
  answering member only; `pendingMembers` shows which member to ask.
- **The end-to-end cancel of a stuck change** ran in the security IT, which is not on this branch.
  The same behaviour is shown by `PhysicalTenantConfigRolloutTest` with a failing fake executor.
- The coordinator-side unit tests of the handler were not extended for cancel; the cancel path is
  covered by the rollout test (real messaging).

## How to run

```bash
./mvnw install -Dquickly -T2 -Dmaven.build.cache.enabled=false
./mvnw verify -pl zeebe/dynamic-config/impl -Dquickly -DskipTests=false -DskipITs \
  -Dtest='PhysicalTenantConfig*Test,NewModelConfigurationChangeCoordinatorTest'
./mvnw verify -pl dist -Dquickly -DskipTests=false -DskipITs -Dtest=PhysicalTenantConfigExecutorTest
./mvnw verify -pl zeebe/qa/integration-tests -Dquickly -DskipTests=false -DskipUTs \
  -Dit.test='PhysicalTenantRolloutReadinessIT,PhysicalTenantConfigRolloutIT,PhysicalTenantRuntimeLifecycleIT'
# measurements: grep "Rollout under load" zeebe/qa/integration-tests/target/failsafe-reports/*Readiness*-output.txt
```

# Leadership handover before a tenant restart (US-17)

Branch `pt-poc-tenant-restart-handover`, written on top of `pt-poc-csl-runtime-security` and carried
here without the Camunda Security Library commits. Not for merge as is.

Before a member restarts a physical tenant's module for a configuration change, it hands the
leadership of every partition of that tenant it leads to another replica through coordinated
leadership transfer (CLT), then stops the module. The tenant's partitions keep a leader through the
whole rolling apply.

## How it works

|                              Piece                               |     Where     |                                                                                                                                                      What                                                                                                                                                       |
|------------------------------------------------------------------|---------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `RaftPartition#transferLeadership(MemberId)`                     | atomix        | A CLT transfer that the leader starts itself: same admission (caught up, acked, reachable, lag under `replication-lag-threshold`, no transfer or reconfiguration running), same attempt (freeze writes, catch up within `replication-timeout`, TimeoutNow); no coordinator check, the result completes a future |
| `LeadershipTransferAttempt` (refactor)                           | atomix        | Reports through a consumer; the runner keeps the report to the rebalance coordinator                                                                                                                                                                                                                            |
| `PartitionLeadershipHandover`                                    | zeebe/broker  | Every led partition at once; replicas in the caller's order; next replica on `NOT_MEMBER`, `NOT_REPLICATING`, `UNREACHABLE`, `LAG_TOO_HIGH`, `REPLICATION_TIMED_OUT`, `TIMEOUT_NOW_EXHAUSTED`; stops on a refusal no other replica changes; bounded; a report per partition                                     |
| `PhysicalTenantLifecycleService#replace`                         | dist          | Before the module stops: hand over (bound `rebalance.replication-timeout` + 5 s), then stop. Never fails the change: a partition not handed over restarts as before and the reason is logged                                                                                                                    |
| `PhysicalTenantConfigExecutor#handoverPreference`                | dist          | Members that already run the new version first (the leadership does not move again), then the others in reverse rollout order (the one restarting last first)                                                                                                                                                   |
| `camunda.cluster.raft.rebalance.hand-over-before-tenant-restart` | configuration | `true` by default; cluster-wide (non-overridable per tenant)                                                                                                                                                                                                                                                    |
| `TenantRestart`, actuator `local.lastRestart`                    | dist          | The last restart of a tenant on the member: when, the outcome per partition it led (`TRANSFERRED to 2 in 1 ms`, `TURNED_OFF`, `NO_REPLICA_TOOK_OVER`, …), how long the module was down                                                                                                                          |

Remove is unchanged: a delete disables the tenant cluster-wide in the same configuration update that
starts the stops, so it serves no requests while members stop it and there is nothing to keep led.

## What is proven, and by which test

|                                                                                            Claim                                                                                            |                                                                                                Test                                                                                                 |
|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| A leader hands over on its own initiative, also when the coordinator check would refuse; a follower answers `LEADER_CHANGED`; an unreachable target is refused and the leader keeps leading | `RaftOwnInitiativeLeadershipTransferTest` (4)                                                                                                                                                       |
| CLT still works for the coordinator after the refactor                                                                                                                                      | `LeadershipTransferAttemptTest` (2), `RaftLeadershipTransferInitiateTest` (12), `RaftCoordinatedLeadershipTransferTest` (11), `RaftLeadershipTransferConfigurationTest` (6), `-Pinclude-slow-tests` |
| Preference order, next replica on a lagging one, fallback reasons (no other replica, none took over, refused, timed out), only led partitions, all led partitions                           | `PartitionLeadershipHandoverTest` (8)                                                                                                                                                               |
| Rollout-aware preference                                                                                                                                                                    | `PhysicalTenantConfigExecutorTest` (1)                                                                                                                                                              |
| Flag mapping and golden file                                                                                                                                                                | `ClusterRaftTest`, `PhysicalTenantOverridablePropertiesGoldenTest`                                                                                                                                  |
| End to end, with vs without                                                                                                                                                                 | `PhysicalTenantRestartHandoverIT` (below)                                                                                                                                                           |
| Nothing else in the lifecycle broke                                                                                                                                                         | `PhysicalTenantConfigRolloutIT` (8), `PhysicalTenantRuntimeLifecycleIT` (8)                                                                                                                         |

`PhysicalTenantRestartHandoverIT`: two clusters of three brokers in one JVM, RF 3, each with the
flag on or off (handover cluster first, so a warmer JVM cannot favour it). A tenant with three
partitions is created at runtime. Before each rollout every broker is given the leadership of one
partition (through `transferLeadership`), so every member restart is a leader restart in both
clusters. Load: `PublishMessage` to each partition through each broker's gateway, one request in
flight per (gateway, partition), on a 10 ms schedule; and to a second tenant through each gateway.
One unmeasured rollout, then four measured rollouts (12 member restarts per cluster). Per restart
(window = this member's restart start to the next one's): the longest time without an accepted
request on any partition through the *other* members' gateways ("unavailable"), the longest request,
failed and timed-out (10 s) requests, and the longest leaderless period from a 5 ms sampler of the
Raft roles. Asserts: median unavailable with < without; every led partition `TRANSFERRED`; every
partition has a leader after each rollout; 0 failed requests on the other tenant.

## Measurements

Apple-silicon laptop shared with other builds, 2026-10-29, leader restarts only (all were).

| Run (12 restarts per arm) | Unavailable median, with / without | Worst, with / without | Leaderless median (worst), with / without | Max latency median, with / without |
|---------------------------|------------------------------------|-----------------------|-------------------------------------------|------------------------------------|
| 1                         | 486 / 2869 ms                      | 10018 / 10020 ms      | 0 (4) / 2484 (2564) ms                    | 313 / 2810 ms                      |
| 2                         | 681 / 2930 ms                      | 10019 / 10021 ms      | 0 (5) / 2485 (2555) ms                    | 291 / 2701 ms                      |
| 3                         | 302 / 2773 ms                      | 10021 / 10020 ms      | 0 (0) / 2490 (2550) ms                    | 208 / 2666 ms                      |
| 4 (gap counted once)      | 302 / 2800 ms                      | 10021 / 10020 ms      | 0 (109) / 2490 (2579) ms                  | 212 / 2792 ms                      |

- Transfer per partition: median 2 ms, max 115 ms (64 handovers over the four runs).
- Without handover the interruption is the election: leaderless ~2.5 s each time, matching the
  ~2.7 s of the slice 1 notes.
- With handover the partition never lacks a leader (one 109 ms blip in 48 restarts); what remains,
  100–700 ms, is the other gateways sending to the old leader until they learn the new one
  (`UNAVAILABLE: Processing paused for partition`), so more requests fail fast (median 3–14 per
  restart vs 0) instead of waiting ~2.7 s.
- Worst case is the same in both arms: a request that is never answered and runs into the client's
  10 s timeout (`DEADLINE_EXCEEDED`). Every run had at least one in each arm; run 4, the only one
  that counted them, had 5 with and 2 without. The handover does not fix it; see below.
- Other tenant during the measured rollouts: 0 failed of 3194–11239 requests per arm, max latency
  64–159 ms (60–138 ms between rollouts).

## Not done, and why

- **Lost requests during a restart.** A request in flight to the member whose module stops is never
  answered and waits for the full client timeout, with or without the handover. Not investigated:
  candidates are the stopping command API dropping requests instead of rejecting them, and gateways
  routing to the old leader until gossip arrives. A short drain after the handover (until the local
  topology shows the new leaders) might cut it; not tried.
- **The residual 100–700 ms** is topology propagation to the gateways, not Raft; the handover
  cannot shorten it on its own. A gateway that retried `Processing paused` on the new leader at
  once would.
- **Fallback paths are unit-tested only** (`PartitionLeadershipHandoverTest`): no IT with one
  replica, a lagging follower or a timeout. RF 1 has no other replica and restarts as before.
- **Order of preference assumes the current rollout order** (live members by id). If dynamic-config
  changes the order, `handoverPreference` must follow.
- **Leadership skews after a rollout**: the first members' partitions end up on the members that
  restarted earlier (in the IT, member 0 leads all three after a rollout). Nothing rebalances
  afterwards; a rebalance after the rollout would be the natural follow-up.
- **The handover bypasses the rebalance coordinator.** The leader admits one transfer at a time, so a
  running rebalance and a handover cannot both move a partition, but the coordinator does not know
  about the handover and may report `TRANSFER_IN_PROGRESS` for that partition.
- **Remove** gets no handover (the tenant is disabled first, see above).

## How to run

```bash
./mvnw install -Dquickly -T2 -Dmaven.build.cache.enabled=false
./mvnw verify -pl zeebe/qa/integration-tests -Dquickly -DskipTests=false -DskipUTs \
  -Dmaven.build.cache.enabled=false \
  -Dit.test='PhysicalTenantRestartHandoverIT,PhysicalTenantConfigRolloutIT,PhysicalTenantRuntimeLifecycleIT'
./mvnw verify -pl zeebe/atomix/cluster -Pinclude-slow-tests -Dquickly -DskipTests=false -DskipITs \
  -Dtest='RaftOwnInitiativeLeadershipTransferTest,LeadershipTransferAttemptTest,RaftLeadershipTransferInitiateTest,RaftCoordinatedLeadershipTransferTest,RaftLeadershipTransferConfigurationTest'
./mvnw verify -pl zeebe/broker -Dquickly -DskipTests=false -DskipITs -Dtest=PartitionLeadershipHandoverTest
./mvnw verify -pl dist -Dquickly -DskipTests=false -DskipITs -Dtest=PhysicalTenantConfigExecutorTest
```

The IT logs a table per arm (`with handover: 12 member restarts`) in its output file.
