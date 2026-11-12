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
leadership transfer (CLT), optionally snapshots the tenant's partitions, and stops the module. With
the default (`TRANSFER_BACK`), it asks for the leadership back through CLT once each restarted
replica has replayed its log, and only then reports the change as applied on this member. The tenant's
partitions keep a leader through the whole rolling apply, and the leader distribution is the same
after it.

## How it works

|                             Piece                              |     Where     |                                                                                                                                         What                                                                                                                                         |
|----------------------------------------------------------------|---------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `RaftPartition#transferLeadership(MemberId)`                   | atomix        | A CLT transfer the leader starts itself: same admission and attempt as a coordinated one (caught up, lag under `replication-lag-threshold`, freeze, catch-up within `replication-timeout`, TimeoutNow)                                                                               |
| `RaftPartition#requestLeadership(Duration)`                    | atomix        | A replica asks the leader for the leadership for itself (coordinator = desired leader); answers the leader's refusal, or `TRANSFERRED` once it leads, `LEADER_CHANGED`, or fails after the bound                                                                                     |
| `PartitionLeadershipHandover`                                  | zeebe/broker  | Every led partition at once; replicas in the caller's order; next replica on a refusal another replica may not give; bounded; a report per partition                                                                                                                                 |
| `ZeebePartition#forceSnapshot`, `#replayProgress`              | zeebe/broker  | A snapshot on demand in either role, once the current role transition completed; the log end (from the log stream, as a follower's snapshot director does not follow commits), last replayed event, snapshot position                                                                |
| `PartitionRestartSnapshots`                                    | zeebe/broker  | Snapshots every partition within one bound; a skipped, failed or timed-out snapshot never fails the restart                                                                                                                                                                          |
| `PartitionLeadershipReclaim`                                   | zeebe/broker  | Per partition: wait until installed and within 100 positions of its log end, then `requestLeadership`; asks again on `LAG_TOO_HIGH`, `TRANSFER_IN_PROGRESS`, …; gives up once the replica has not caught up further for `rebalance.leader-wait-timeout`; or only follow the catch-up |
| `PhysicalTenantLifecycleService#replace`                       | dist          | snapshot (if on) → handover → stop → start → reclaim (`TRANSFER_BACK`, waited for) or catch-up watch (otherwise, not waited for); never fails the change; `local.lastRestart` reports every step                                                                                     |
| `camunda.cluster.raft.rebalance.tenant-restart-handover`       | configuration | `NONE`, `TRANSFER_AWAY`, `TRANSFER_BACK` (default); cluster-wide                                                                                                                                                                                                                     |
| `…tenant-restart-snapshot`, `…tenant-restart-snapshot-timeout` | configuration | `false` by default; 10 s                                                                                                                                                                                                                                                             |

Remove is unchanged: a delete disables the tenant cluster-wide in the same configuration update that
starts the stops, so it serves no requests while members stop it and there is nothing to keep led.

## What is proven, and by which test

|                                                                                         Claim                                                                                          |                                                                                                Test                                                                                                 |
|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| A leader hands over on its own initiative; a replica obtains the leadership by asking for it; a leader asking for its own leadership gets `ALREADY_LEADER`                             | `RaftOwnInitiativeLeadershipTransferTest` (8)                                                                                                                                                       |
| CLT still works for the coordinator                                                                                                                                                    | `LeadershipTransferAttemptTest` (2), `RaftLeadershipTransferInitiateTest` (12), `RaftCoordinatedLeadershipTransferTest` (11), `RaftLeadershipTransferConfigurationTest` (6), `-Pinclude-slow-tests` |
| Handover preference, fallbacks                                                                                                                                                         | `PartitionLeadershipHandoverTest` (8)                                                                                                                                                               |
| Reclaim asks only once caught up, waits for the install, asks again while the leader finds it behind, stops on a final refusal, gives up after the bound, can only follow the catch-up | `PartitionLeadershipReclaimTest` (6)                                                                                                                                                                |
| A failed, skipped, timed-out or unaskable snapshot never holds up or fails the restart                                                                                                 | `PartitionRestartSnapshotsTest` (4)                                                                                                                                                                 |
| Configuration mapping and golden file                                                                                                                                                  | `ClusterRaftTest`, `PhysicalTenantOverridablePropertiesGoldenTest` (24)                                                                                                                             |
| End to end, every variant against no handover, other tenant unaffected                                                                                                                 | `PhysicalTenantRestartHandoverIT` (below), passed three times in a row                                                                                                                              |
| Nothing else in the lifecycle broke                                                                                                                                                    | `PhysicalTenantConfigRolloutIT` (8), `PhysicalTenantRuntimeLifecycleIT` (8), dist `PhysicalTenant*Test` (52)                                                                                        |

`PhysicalTenantRestartHandoverIT`: one cluster per variant, three brokers, RF 3, all in one JVM, run
in this order: transfer back with and without snapshot, transfer away with and without snapshot,
transfer away with a rebalance through `POST /cluster/v2/rebalance` after each rollout, no handover
(last, so a warmer JVM cannot favour it). A tenant with three partitions is created at runtime.
Before each rollout every broker is given the leadership of one partition, so every member restart
is a leader restart. Load: `PublishMessage` to each partition through each broker's gateway, one
request in flight per gateway and partition, every 10 ms; and to a second tenant through each
gateway. One unmeasured rollout, then four measured ones (12 member restarts per variant).

Per member restart (window: this member's restart start to the next one's):

- *unavailable*: the longest time a partition accepted no request through any of the *other*
  members' gateways;
- *max latency*, *failed*, *timed out* (a request that waited the client's full 10 s);
- *leaderless*: the longest time a partition had no leader, from a 5 ms sampler of the Raft roles.

Per rollout: the sum of the restarts' unavailable (plus the rebalance's), the rollout's duration, and
the leaders per member afterwards.

The IT asserts:

- every handover variant has a lower median unavailable than no handover;
- no partition is leaderless for as long as the election timeout;
- every led partition was `TRANSFERRED`;
- transfer back took back every partition and leaves one leader per member;
- the rebalance does the same;
- every snapshot variant took snapshots;
- every partition has a leader after each rollout;
- the other tenant has no failed request.

## Measurements

Apple-silicon laptop shared with other agents' builds, 2026-10-29. Two runs (B and C) of the final
IT. Every one of the 12 restarts per variant was a leader restart.

|            Variant             | Unavailable per restart, median (worst), ms |           Rollout sum, median, ms           | Failed per restart, median | Leaderless worst, ms | Rollout duration, median, ms | Leaders per member after each rollout |
|--------------------------------|---------------------------------------------|---------------------------------------------|----------------------------|----------------------|------------------------------|---------------------------------------|
| transfer back                  | B 228 (323) · C 258 (429)                   | B 566 · C 791                               | 22 · 23                    | 0 · 6                | 4259 · 3432                  | 1/1/1 every time                      |
| transfer back + snapshot       | B 239 (9209) · C 258 (330)                  | B 778 · C 741                               | 74 · 87                    | 5 · 75               | 6648 · 9099                  | 1/1/1 every time                      |
| transfer away                  | B 259 (486) · C 243 (328)                   | B 926 · C 588                               | 6 · 6                      | 0 · 5                | 3002 · 2917                  | 3/0/0 every time                      |
| transfer away + snapshot       | B 279 (404) · C 179 (269)                   | B 789 · C 538                               | 54 · 34                    | 74 · 5               | 6260 · 6535                  | 3/0/0 every time                      |
| transfer away, rebalance after | B 380 (964) · C 277 (886)                   | B 1682 · C 1529 (rebalance worst 565 · 628) | 20 · 16                    | 0 · 6                | 775 · 1029 (+ rebalance)     | 3/0/0, 1/1/1 after the rebalance      |
| no handover                    | B 2731 (10018) · C 2726 (10018)             | B 8441 · C 8706                             | 0 · 0                      | 2555 · 2584          | 7800 · 7848                  | 1/1/1 (elected by priority)           |

- Requests that waited the full 10 s timeout: 1–5 per variant and run, in every variant, including
  no handover. In run B, one transfer-back-with-snapshot restart had a 9.2 s gap on one partition
  after the transfer back; cause not established.
- The other tenant during the measured rollouts: 0 failed requests in every variant and run (3792 to
  11640 requests per variant), max latency 58–124 ms.
- A handover takes 1–126 ms; a transfer back 200–900 ms after the module started (most of it is
  waiting for the replica to be installed and to catch up), up to ~2.9 s when the install is slow
  (see below).
- An earlier run (A), which counted a gap per client rather than per partition, gave the same medians
  (transfer back 229 ms, transfer away 239 ms, no handover 2687 ms). Its worst values were inflated
  by the 10 s timeouts, so it led to the per-partition measure.

What the variants cost:

- **Transfer away** interrupts a partition for ~250 ms per restart (gateways send to the old leader
  until they learn the new one; the requests fail fast with `Processing paused`). It leaves every
  leadership on the member that restarted first (3/0/0 after every rollout).
- **Transfer back** costs the same per restart (two handovers per leader restart, each ~200 ms
  interruption, at different times) and roughly twice the fast-failing requests. It keeps the
  distribution at 1/1/1. The rollout is longer only by the wait for each restarted replica to
  catch up, which the next member's restart should wait for anyway.
- **Rebalance after the rollout** restores 1/1/1 but adds a third interruption per moved partition
  (worst 565–628 ms for the rebalance). It can only run after the rollout, because the coordinator
  refuses while any cluster configuration change is pending, and the rollout is one. The
  priority-election rebalance of the actuator is election-based. So this variant always costs more
  than transfer back, and leaves the tenant unbalanced in between.
- **No handover**: ~2.7 s per leader restart (the election timeout), about 10x the others.

### Replay against log length

The restarted replica's catch-up (from installed to within 100 positions of its log end) against the
log since its latest snapshot, all variants, runs A and B (restarts with a slow install left out):

| Log since snapshot (positions) | Restarts | Catch-up, mean (max), ms |
|--------------------------------|----------|--------------------------|
| < 1 000                        | 68       | 9 (87)                   |
| 1 000 – 4 999                  | 34       | 21 (40)                  |
| 5 000 – 9 999                  | 37       | 74 (239)                 |
| 10 000 – 19 999                | 61       | 199 (388)                |
| 20 000 – 29 999                | 74       | 359 (614)                |
| 30 000 – 39 999                | 32       | 557 (930)                |
| 40 000 – 54 999                | 25       | ~670 (967)               |

Replay runs at roughly 55–60 k positions/s, ~17 ms per 1 000 positions, from the first replayed
record. The JVM stays up across a tenant restart, so the restarted module does not start cold:
the transfer back moves the leadership to a module that is seconds old, and its interruption and
latency (`back-ms`, max latency) are no worse than the transfer away's. A node restart would show
a JIT warm-up tail; a tenant restart does not, so warm-up does not need handling here.

Replay never interrupts the tenant here: with a handover the leader is elsewhere while the
restarted replica replays, and transfer back asks only once it caught up.

### Forced snapshot

- Cost: 180–245 ms per partition on average (max 582 ms), taken on the follower right after the
  handover. 280–330 KB per partition on average (max 716 KB); the RocksDB checkpoint is mostly hard
  links, so the size is the state, not extra copying.
- Effect on the leader and the other replicas: none measurable. The other members' max latency was
  lower with a snapshot than without (59–71 ms against 162–234 ms median).
- Bounds:
  - a snapshot already in progress makes it return null (`SKIPPED`, seen in early runs);
  - it covers up to the lowest of the processed, exported and backup positions, so an exporter
    lagging behind limits it;
  - on a follower it can fail with `NoEntryAtSnapshotPosition` if the entry has not arrived.
  - Taking it during the handover's leader-to-follower transition found no snapshot director, or a
    snapshot that never completed when the director closed under it. It now waits for the
    transition to complete.
- Failure or timeout: the restart goes ahead from the existing snapshot (`PartitionRestartSnapshotsTest`).
- Observed downside: with the snapshot, a third of the restarted replicas took ~2.5 s to be installed
  (41 of 144 in runs A and B; non-snapshot handover variants: 15 of 216). The replica waits for its
  leader's first append ("Found leader" 2.5 s after "Server join completed"), which looks like an
  append that was in flight to the stopped member running into the request timeout. Cause not
  established. It lengthens the rollout (6.3–9.1 s against 2.9–4.3 s), not the tenant's
  interruption.

### Recommended default

`tenant-restart-handover: TRANSFER_BACK`, `tenant-restart-snapshot: false`.

- Transfer back interrupts as little as transfer away (median 228–258 ms against 243–259 ms per
  restart). It keeps the leader distribution, with no rebalance afterwards, and never uses an
  election.
- At the log lengths seen here the snapshot saves less replay than it costs: it saves ~17 ms per
  1 000 positions, against ~200 ms plus the slow installs. Break-even is around 10 000–15 000
  positions since the last snapshot. Turn it on where partitions write that much between snapshots
  (high throughput with a long `snapshot-period`), where it shortens the rollout. It never shortens
  the interruption.
- These figures were measured with the snapshot taken after the handover. A replica that stops
  leading reloads its state from its latest snapshot, so the partitions the member had led were
  snapshotted at what they had replayed since, and replayed as much after the restart as without a
  snapshot (3–11k positions against 5–14k; followed partitions 0.2–2.5k). The snapshot is now
  taken before the handover.

### What the readiness wait between members (`pt-poc-rollout-safety`) needs

With `TRANSFER_BACK`, `replace` returns on a member only after the reclaim, so "the member applied the
change" already means "its partitions are installed, caught up and led as before".

- The readiness wait should treat the member's change as ready only once `replace` returned. Today
  that means the executor's step completion.
- It must not time out a member while its restarted replicas still catch up: the reclaim lasts as
  long as the replay, and gives up only after `rebalance.leader-wait-timeout` without progress.
- With `TRANSFER_AWAY` or `NONE`, ready should mean "every partition of the tenant on the member
  caught up". `local.lastRestart` reports that per partition (`CAUGHT_UP after … ms`) once known.

## Not done, and why

- **Lost requests.** 1–5 requests per variant and run wait the full 10 s client timeout, in every
  variant including no handover. Not investigated. Candidates: requests in flight to a stopping
  command API that are dropped, not rejected.
- **The ~250 ms per handover** is gateways learning the new leader from topology gossip. CLT cannot
  shorten it. A gateway that retried `Processing paused` on the new leader at once would.
- **Slow installs after a restart** (~2.5 s, more often with the forced snapshot), see above.
- **The 9.2 s gap after one transfer back** (one of 144 transfer-back restarts) is not explained.
- **Fallbacks are unit-tested only**: no IT with a lagging follower, a refused reclaim or a
  snapshot timeout. RF 1 has no other replica and restarts as before.
- **The handover order of preference** assumes the current rollout order (live members by id).
- **The handover and reclaim bypass the rebalance coordinator.** A leader admits one transfer at a
  time, but the coordinator may report `TRANSFER_IN_PROGRESS` for a partition being handed over.
- **Remove** gets no handover (the tenant is disabled first).
- **Measured on one laptop, with one load shape** (`PublishMessage`, ~300 commands/s per
  partition). Replay rates and snapshot costs depend on state size and hardware.

## How to run

```bash
./mvnw install -Dquickly -T2 -Dmaven.build.cache.enabled=false
./mvnw verify -pl zeebe/qa/integration-tests -Dquickly -DskipTests=false -DskipUTs \
  -Dmaven.build.cache.enabled=false \
  -Dit.test='PhysicalTenantRestartHandoverIT,PhysicalTenantConfigRolloutIT,PhysicalTenantRuntimeLifecycleIT'
./mvnw verify -pl zeebe/atomix/cluster -Pinclude-slow-tests -Dquickly -DskipTests=false -DskipITs \
  -Dtest='RaftOwnInitiativeLeadershipTransferTest,LeadershipTransferAttemptTest,RaftLeadershipTransferInitiateTest,RaftCoordinatedLeadershipTransferTest,RaftLeadershipTransferConfigurationTest'
./mvnw verify -pl zeebe/broker -Dquickly -DskipTests=false -DskipITs \
  -Dtest='PartitionLeadershipHandoverTest,PartitionLeadershipReclaimTest,PartitionRestartSnapshotsTest'
./mvnw verify -pl configuration -Dquickly -DskipTests=false -DskipITs \
  -Dtest='ClusterRaftTest,PhysicalTenantOverridablePropertiesGoldenTest'
```

The IT logs a table per variant (`transfer back: 12 member restarts`), then `variant comparison`,
the `replay |` rows (variant, log since snapshot, installed after, caught up after, outcome), and the
`snapshot |` rows (variant, outcome, took, bytes, failure).

# Self-handover under a realistic load: phases, fixes and what is open

Branch `mk-pt-tenant-restart-handover`, on top of `pt-poc-integration` (834b56d765e). It carries the
rollout-safety and leadership-handover branches and three fixes of the second integration round
(the reclaim outside the module lock, disk-paused replicas, the last restart of a removed tenant)
without the Camunda Security Library commits, so it builds from public and Camunda repositories
only. Dropped as CSL-only: the HTTP security status in the actuator's `local` view, the `SECURITY`
rejection reason, `PhysicalTenantRuntimeSecurityIT` and its leak-check commits, and the notes of
the second integration round. Not for merge as is.

The earlier evaluation of transfer back measured gaps in accepted message publications in a laptop
IT and attributed the remaining ~0.25 s to gossip without measuring it. This branch adds the
measurement, fixes what it found, and leaves the answer to a load-test cluster (plan in
`~/Documents/pt-self-handover-bench/PLAN.md`).

## Instrumentation

One INFO line per phase from the `io.camunda.zeebe.handover` logger:
`handover event=<event> group=<tenant> partition=<n> at=<epoch ms> key=value…`.

|               Where               |                                                                                                         Events                                                                                                         |
|-----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| old leader (atomix)               | `transfer.started`, `transfer.frozen`, `transfer.caughtUp`, `transfer.timeoutNowSent`, `transfer.timeoutNowAccepted`, `transfer.leaderObserved`, `transfer.finished` (result), `transfer.refused` (reason)             |
| target and every replica (atomix) | `transfer.requested` (a replica asks for the leadership), `raft.timeoutNowReceived`, `raft.role` (from, to, term), `raft.leaderReady` (initial entries committed)                                                      |
| partition (broker)                | `partition.transition` (role, term, prepare ms, steps ms, ms per step), `partition.leaderListenersNotified`, `topology.leaderPublished`                                                                                |
| stream processor                  | `replay.finished` (mode, from, to, positions, events, ms), `processing.started`, `processing.firstCommand` (age of the command), `processing.firstResponse`                                                            |
| command API                       | `commandApi.<state>` on frozen, unfrozen, paused, resumed, recovered, leading, not leading, with the requests rejected since the previous state (frozen, paused, not leading) and accepted; `commandApi.firstAccepted` |
| gateway                           | `gateway.leaderChanged` (leader, previous, term, which gateway)                                                                                                                                                        |
| snapshot director                 | `snapshot.finished` (forced, mode, outcome, processed position, transient ms, total ms)                                                                                                                                |

`POST /actuator/partitions/handOverLeadership?physicalTenant=<id>` moves the leadership of every
partition a broker leads to another replica, through the transfer a tenant restart uses, so that a
rebalance between warm brokers can be measured.

`PhysicalTenantHandoverPhasesIT` puts it together: 3 brokers, a tenant created at runtime, 60 PI/s
of one-task instances awaiting their result over gRPC, streaming workers with clients of their own,
then leader-initiated transfers with rebalances, 24 forced snapshots against control windows, and
two rollouts with transfer back. It writes the trace, client and job samples, and commit and
processed positions to `target/handover-phases/`.

## Measured locally (one laptop; medians, ms after the transfer started)

|                                         | n  | Raft leader | leader ready | transition done | replay positions / ms | gateways all route |
|-----------------------------------------|----|-------------|--------------|-----------------|-----------------------|--------------------|
| CLT, leader-initiated, before the fixes | 6  | 1           | 2            | 16              | 3 / 1                 | 229                |
| CLT rebalance, before                   | 12 | 2           | 3            | 68              | 3 / 1                 | 169                |
| tenant restart, away, before            | 6  | 1           | 2            | 44              | 31 / 4                | 195                |
| tenant restart, back, before            | 6  | 1           | 2            | 13              | 31 / 3                | 189                |
| CLT, leader-initiated, after            | 6  | 2           | 4            | 24              | 33 / 5                | 25                 |
| CLT rebalance, after                    | 14 | 4           | 5–6          | 27–31           | 33 / 4                | 69–73              |
| tenant restart, away, after             | 6  | 1           | 2            | 43–56           | 31 / 3–4              | 61                 |
| tenant restart, back, after             | 6  | 1–2         | 2            | 26–30           | 31 / 3–4              | 27–31              |

- Gateways learned a new leader 100–250 ms after it published its role, because a broker's role
  change was only noticed on the next 250 ms gossip round. Fixed: `fix: route requests to a new
  partition leader without waiting for gossip`.
- The freeze answered `Processing paused`, which reached clients as errors: 51–129 per run of 18–20
  handovers. Fixed: `fix: redirect requests to the new leader while leadership moves` (from the
  request-path round, adapted). After it, 0 such errors.
- Replay at takeover was 3–33 positions in 1–5 ms in every kind of handover. Transfer back asks for
  the leadership only once the replica is within 100 positions of its log end.
- Forced snapshot: 36–122 ms. Processing latency on the leader during the snapshot (77–148 commands
  over 12 snapshots per role) was not above that of equally long control windows, at the timer's
  1 ms resolution.
- Every role change forces a snapshot ~100 ms later on this tree: the post-migration snapshot step
  re-arms on every transition while `areMigrationsPerformed()` stays true. Not changed here.

## Open

- **Job delivery stops for ~15 s after a rollout begins** in the full IT sequence: 6 of 6 runs, 0 of
  3 runs with only some of the cycle kinds. No worker receives a job, by streaming or by polling;
  Raft commit and the leaders' processing keep going, and ~900 jobs arrive at once afterwards. It is
  not the shared gRPC connection of starter and worker (separate clients changed nothing). Not root
  caused. It must be checked on the cluster before the arms are compared.
- **Results lost on a leader change**: a few create-with-result requests per run time out
  (`DEADLINE_EXCEEDED`). The fixes on `rest-await-result-timeout` (replicated await metadata,
  response forwarding) are not on this branch.
- A tenant added at runtime has no REST security chain without CSL, so the IT and the plan use gRPC.

## How to run

```bash
./mvnw install -Dquickly -T2 -Dmaven.build.cache.enabled=false
./mvnw verify -pl zeebe/qa/integration-tests -Dquickly -DskipTests=false -DskipUTs \
  -Dmaven.build.cache.enabled=false -Dit.test=PhysicalTenantHandoverPhasesIT
# options: -DhandoverPhases.rate=60 -DhandoverPhases.cycles=6 -DhandoverPhases.kinds=clt,snapshot,restart
#          -DhandoverPhases.streamingWorkers=true -DhandoverPhases.snapshot=false
grep -A40 "handover phases, ms" zeebe/qa/integration-tests/target/failsafe-reports/*HandoverPhases*-output.txt
./mvnw verify -pl zeebe/atomix/cluster -Dquickly -DskipTests=false -DskipITs -Dtest=SwimProtocolTest
./mvnw verify -pl zeebe/broker -Dquickly -DskipTests=false -DskipITs \
  -Dtest='CommandApiRequestHandlerTest,ZeebePartitionTest,PartitionTransitionImplTest'
```

# Standalone gateway: physical tenants created, changed and removed at runtime

Branch `pt-poc-standalone-gateway-tenants`, on top of `mk-pt-tenant-restart-handover`
(3b042bda0df). Not for merge as is.

A standalone gateway built its gRPC module per tenant once, from its own static configuration. A
tenant created through the lifecycle API answered `NOT_FOUND` there until the gateway restarted with
a matching file, and a removed tenant stayed known. This branch makes a standalone gateway follow
the tenant set the brokers run, with no gateway restart, for gRPC.

## What it proves, and by which test

3 brokers without embedded gateways, 1 standalone gateway, changes through the brokers'
`physicalTenants` actuator. A client publishes messages to `default` through the gateway in a loop
during every test, and the test fails if a single one fails.

|                                      Claim                                      |                                                                                                            Test                                                                                                             |
|---------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| The gateway receives a tenant's `PhysicalTenantConfigReference` by gossip       | `PhysicalTenantStandaloneGatewayLifecycleIT#shouldServeATenantCreatedAtRuntimeThroughTheStandaloneGateway` (the gateway's `BrokerTopologyManager` holds version 1 of the new tenant's reference)                            |
| A tenant created at runtime is served over gRPC                                 | same test: deploy, a job pushed to a job stream and completed (instance with result), and a job activated by polling and completed                                                                                          |
| A tenant the brokers declare in their file, and the gateway does not, is served | `#shouldServeAFileTenantThatOnlyTheBrokersDeclare` (deploy, job stream, completion)                                                                                                                                         |
| A removed tenant is rejected as unknown                                         | `#shouldRejectARemovedTenantAsUnknownOnTheStandaloneGateway` (`NOT_FOUND`; gone from the gateway's registry and gRPC modules)                                                                                               |
| A changed tenant's gRPC-relevant config takes effect on the gateway             | `#shouldApplyAChangedTenantsGrpcSecurityOnTheStandaloneGateway`: `security.authorizations.enabled` false → true; the gateway's module and authorization converter switch, `default` keeps its own; the display name follows |
| Other tenants keep serving                                                      | every test above: 0 failed publishes to `default` through the gateway                                                                                                                                                       |
| A store without a data directory fetches once and keeps it                      | `PhysicalTenantConfigFetcherTest#shouldFetchIntoAStoreWithoutADataDirectoryAndAskNoPeerTheSecondTime`                                                                                                                       |
| One tenant binds over a root the cross-tenant checks would reject               | `PhysicalTenantResolverTest#shouldBindOneTenantOverTheRootWithoutTheChecksAcrossTenants`                                                                                                                                    |
| The PoC override makes a REJECT key restartable                                 | `PhysicalTenantKeyClassificationTest#shouldAcceptAChangeOfAPropertyOverriddenToRestart`                                                                                                                                     |

The change test asserts on the gateway's beans, not on a client-visible result: with an unprotected
API the claims a gateway sends do not depend on the tenant's security config. Every security key is
`REJECT security-chain` on this tree, so the test turns `security.authorizations.enabled` into
`RESTART` with the PoC property below. Without it, no change the API accepts reaches a gateway.

## How it works

|                 Piece                  |                                        Where                                         |                                                                                                                                                                 What                                                                                                                                                                  |
|----------------------------------------|--------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Cluster configuration by gossip        | existing                                                                             | A standalone gateway runs `GatewayClusterConfigurationServiceImpl` (`DynamicClusterServices`, `@Profile("!broker")`) and so receives the whole `CurrentClusterConfiguration`: each group's `TenantAvailability` and configuration reference (version, hash, owner, incarnation). Nothing new is gossiped.                             |
| `GatewayPhysicalTenantFollower`        | dist, `io.camunda.zeebe.gateway`                                                     | Listens to that configuration (bean in `GatewayModuleConfiguration`). Per group: disabled or removed → removes the gRPC module and the registry entry; a reference with a version newer than the one it serves → fetches, binds, `GatewayPhysicalTenants#put`, registry add or replace. Runs on its own thread; retries every second. |
| Fetch                                  | dynamic-config `PhysicalTenantConfigFetcher`, `PhysicalTenantConfigStore.inMemory()` | Same request as between brokers (topic `physical-tenant-config-fetch`): the coordinator first, then the members that applied the version. The gateway stores what it fetched in memory and fetches again after a restart. The brokers already answer any member, so they needed no change.                                            |
| Bind                                   | `PhysicalTenantResolver.bindOne`                                                     | Binds `name` and `security.*` of the blob over the gateway's root, with the map overlays (named OIDC providers) and provider narrowing of the full resolver, without the validations across tenants.                                                                                                                                  |
| Registry                               | `DefaultPhysicalTenantRegistry` (gateway's)                                          | The live `PhysicalTenantIds` on the gateway, so health aggregation and other `known()` consumers follow. Uses the incarnation from the reference.                                                                                                                                                                                     |
| `physical-tenant-rollout.restart-keys` | `BrokerModuleConfiguration`, `PhysicalTenantKeyClassification#withRestart`           | PoC property on brokers: keys classified `RESTART` regardless of the resource. Empty by default.                                                                                                                                                                                                                                      |

Decisions taken where nothing says otherwise (each easy to change):

- **Which tenants the gateway follows**: every non-`default` group with a reference, whatever its
  owner, unless the gateway's own file declares it. A gateway-declared tenant keeps its file
  configuration; only its disabling or removal applies.
- **When a new version takes effect**: when the gateway sees the reference's version rise, not when
  the brokers' rollout completes. During a rollout the gateway may already apply a security config
  that some brokers do not run yet (from reading the follower; not measured).
- **What the gateway binds**: `name` and `security.*`, the only keys its gRPC module reads.

Two problems found on the way, both from binding the whole blob through `PhysicalTenantResolver.of`:

1. The blob names exporters only the brokers declare (`data.exporters-assigned`), which the
   gateway's exporter assignment check rejects.
2. The checks across tenants compare the tenant with the gateway's root. The test gateway's root
   storage is Elasticsearch, the brokers' tenants use `none`: the storage type compatibility check
   rejects every runtime tenant. Keeping the tenant's storage keys does not help, and matching the
   brokers' root (`none`) does not start: a standalone gateway with BASIC authentication fails fast
   without secondary storage (`BasicAuthenticationNoDbConfiguration`).

Hence the gateway binds a subset, without those checks, and relies on the brokers having validated
the whole configuration.

## Call sites that froze the tenant set on a standalone gateway

From reading code (a survey of every gateway-active bean), not tested unless marked. Cost: S = a
lookup or a register/remove hook, M = per-tenant objects with a close path, L = a per-tenant
subsystem with resources and its own lifecycle.

|                                                                                               Call site                                                                                               |                                                Builds once                                                |                          Affects                           |            Cost            |                                                                              Status here                                                                              |
|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|------------------------------------------------------------|----------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `dist/.../gateway/GatewayModuleConfiguration.java:105`                                                                                                                                                | engine security config per boot tenant → `Gateway` → `GatewayPhysicalTenants`                             | gRPC                                                       | S                          | **Made live** by the follower (tested)                                                                                                                                |
| `zeebe/gateway-grpc/.../EndpointManager.java:155`                                                                                                                                                     | static `RequestMapper.setMultiTenancyEnabled` from `default`                                              | gRPC                                                       | M                          | Left: `security.multi-tenancy` is uniform by the override policy, so one flag is consistent; per tenant needs the tenant at every `RequestMapper` call                |
| `zeebe/gateway-grpc/.../Gateway.java:140`                                                                                                                                                             | `authEnabled` from `default`'s `unprotected-api`                                                          | gRPC                                                       | S                          | Left: uniform by the override policy                                                                                                                                  |
| `GatewayModuleConfiguration.java:141` → `PhysicalTenantHandlerFactory`                                                                                                                                | BASIC handler resolves `serviceRegistry.userServices(id)`                                                 | gRPC BASIC                                                 | M (after the registry row) | Left: `DefaultServiceRegistry.byTenant` throws for a tenant it was not built with, so `put` of a runtime tenant with BASIC would fail; the IT uses an unprotected API |
| `dist/.../commons/service/CamundaServicesConfiguration.java:143-600`                                                                                                                                  | `ServiceRegistry` (`Map.copyOf`) of every per-tenant service, cluster topology/status services per tenant | REST, MCP, gRPC BASIC, `/cluster/v2/topology` and `status` | L                          | Left (secondary storage, out of scope)                                                                                                                                |
| `dist/.../search/*`, `dist/.../rdbms/*` (search clients, readers, data sources, schema initialisers, health indicators)                                                                               | per-tenant clients and schema                                                                             | REST, MCP, readiness                                       | L                          | Left (secondary storage, out of scope); the same gap as on brokers                                                                                                    |
| `authentication/.../pt/PhysicalTenantSecurityConfiguration.java`, `PhysicalTenantOidcSettingsRegistry`, `OidcOverrideBeansConfiguration`                                                              | one CSL `SecurityFilterChain` and OIDC settings per tenant                                                | REST, MCP over HTTP                                        | L                          | Left (CSL, out of scope)                                                                                                                                              |
| `zeebe/gateway-rest/.../PhysicalTenantServedFilter.java:54`                                                                                                                                           | boot incarnations with a security chain                                                                   | REST, MCP                                                  | by design                  | Left: answers 404 for a runtime tenant, which is the fail-closed behaviour until the chains are live                                                                  |
| `dist/.../pt/SecondaryStorageReadinessMetrics.java:42`                                                                                                                                                | one gauge per boot tenant in `bindTo`                                                                     | metrics                                                    | S                          | Left                                                                                                                                                                  |
| `dist/.../rest/PhysicalTenantRestConfiguration.java`, `security/AuthorizationCheckerProviderConfiguration.java`, `pt/DatabaseTypeProviderConfiguration.java`, `secrets/SecretStoreConfiguration.java` | per-tenant REST config, authorization checkers, database type, secret stores                              | REST                                                       | S–M                        | Left                                                                                                                                                                  |
| `BrokerTopologyManagerImpl` (gateway bean), `JobStreamClient`, `clusterStatesByPhysicalTenant`, MCP tenant filter, `LongPollingActivateJobsHandler`                                                   | —                                                                                                         | gRPC, health                                               | live                       | Already live: new groups arrive by gossip and broker info, job stream handlers are created per tenant on the first broker of a group (job streaming tested)           |

For gRPC with an unprotected API or OIDC, the first row was the only blocker. BASIC on gRPC needs
the `ServiceRegistry` row, so it shares the cost of REST.

## The lifecycle API on a standalone gateway (question 5)

From reading code, not tested:

- **The `/cluster/v2` controllers run on a standalone gateway.** `RestApiConfiguration`
  (`@ComponentScan("io.camunda.zeebe.gateway.rest")`, `@ConditionalOnRestGatewayEnabled`) is in
  `io.camunda.application.commons`, which `CommonsModuleConfiguration` scans for the standalone
  gateway too; `GatewayModuleConfiguration`'s exclude filter only affects its own scan.
  `@ClusterScoped` only keeps them off the per-tenant path prefix.
- **They forward.** Exporting and recovery send through `ClusterConfigurationManagementRequestSender`
  to the coordinator, which the gateway finds from the gossiped configuration; rebalance through
  `RebalanceRequestSender` the same way. None acts on the gateway's own state.
- **The actuator `PhysicalTenantsEndpoint` is `@Profile("broker")`.** Its service validates on the
  receiving broker (key classification, credential guard, then the boot validations through
  `PhysicalTenantLifecycleService#validate`, which needs the broker's `SystemContext`), and only then
  sends `topology-physical-tenant-config` to the coordinator, which checks ownership, version and
  concurrency but runs no validation.

Recommendation: a call on a gateway should **forward to a broker, not refuse, and not go straight to
the coordinator**. The transport exists on the gateway, but sending there would skip the
validations, so an invalid change would be accepted and fail on every broker during the rollout
instead of answering 400. The smallest step is a new request topic answered by
`PhysicalTenantConfigService` on the coordinator (validate, then submit locally), with the gateway
as a thin forwarder. Reads (`GET`) can be answered on the gateway from the gossiped configuration
(owner, version, hash, applied versions, state), but not the per-member detail that the broker's
endpoint reads from local state (health, the rollout step it waits for, the last restart).

## Measurements

Apple-silicon laptop, 3 brokers and 1 gateway in one JVM (`TestCluster`), 1 partition, RF 3,
secondary storage `none`, warm JVM, another heavy build on the machine.

|                                          What                                           |                            Result                             |                                        Method                                         |
|-----------------------------------------------------------------------------------------|---------------------------------------------------------------|---------------------------------------------------------------------------------------|
| `202` of the create to the gateway serving version 1                                    | 150 ms and 152 ms (2 runs)                                    | `System.nanoTime` in the create test, until `followedVersion` is present              |
| Failed publishes to `default` through the gateway, per test (2 green runs)              | 0 in all 8 test runs; 787–3145 accepted, longest gap 12–75 ms | `PublishLoad` from `@BeforeEach` to `@AfterEach`; each window is only 1–3 s           |
| Same, in 2 earlier runs where every test waited 2 min and failed (see the two problems) | 0 failed of 288k–338k per test, longest gap 35–210 ms         | same; the brokers created tenants in these windows, but the gateway never served them |

## Results (2026-11-12)

|                                                 Suite                                                 |   Result   |
|-------------------------------------------------------------------------------------------------------|------------|
| `PhysicalTenantStandaloneGatewayLifecycleIT`                                                          | 4/4, twice |
| `PhysicalTenantStandaloneGatewayTopologyIT`, `PhysicalTenantConfigRolloutIT` (regression, same batch) | 1/1, 9/9   |
| `PhysicalTenantResolverTest`, `PhysicalTenantKeyClassificationTest` (configuration)                   | 30/30, 8/8 |
| `PhysicalTenantConfigFetcherTest` (zeebe/dynamic-config/impl)                                         | 5/5        |

## Not done, and why

- **REST and MCP for a runtime tenant on a gateway**: answers 404 (served filter). It needs the
  `ServiceRegistry`, secondary-storage clients and CSL chains to become per-tenant at runtime, the
  same work as on brokers.
- **BASIC on gRPC for a runtime tenant**: would fail in `put` (see the inventory); not tested.
- **OIDC per tenant on the gateway**, the gRPC config that matters most in practice (issuer,
  audiences, JWKS), is not tested: it needs an identity provider in the IT, and every
  `security.authentication.*` key is `REJECT security-chain` today.
- **A disabled tenant enabled again**: the gateway's registry keeps a removed id reserved, so the
  follower would serve its gRPC module but not re-add it to the registry (from reading code).
- **A version the gateway cannot fetch or bind** is retried every second with a WARN and no other
  signal; the gateway keeps the previous version, or does not serve a new tenant.
- **Meters**: `GatewayPhysicalTenants` registers `AuthenticationMetrics` per module when the API is
  protected; whether a removed or replaced module leaves them behind was not checked.

## Open questions for the team

1. Should a gateway switch a tenant's security when the version rises (here), when the rollout
   completes, or keep both and accept either during the rollout? Each option leaves a window where
   gateway and brokers disagree.
2. Fetch the configuration (here) or carry the gateway-relevant part in the cluster configuration?
   Fetching keeps one source and keeps secret references out of gossip; carrying would save the
   round trip and the dependency on a reachable broker.
3. When the gateway's file and the cluster disagree about a tenant, which wins? Here the gateway's
   file wins for tenants it declares.
4. Should the gateway validate what it binds (e.g. an OIDC provider assigned on the brokers but not
   declared on the gateway), and how does it report a version it cannot serve?
5. Question 5: forward to a broker that validates (recommended), or refuse on gateways at first?
6. Are `security.multi-tenancy` and `unprotected-api` meant to stay uniform across tenants? The
   gateway's static flags rely on it.

## How to run

```bash
./mvnw install -Dquickly -T2 -Dmaven.build.cache.enabled=false
./mvnw verify -pl zeebe/qa/integration-tests -Dquickly -DskipTests=false -DskipUTs \
  -Dmaven.build.cache.enabled=false -Dit.test=PhysicalTenantStandaloneGatewayLifecycleIT
grep -h "Load on the default\|standalone gateway served" \
  zeebe/qa/integration-tests/target/failsafe-reports/*StandaloneGatewayLifecycleIT*-output.txt
./mvnw verify -pl configuration -Dquickly -DskipTests=false -DskipITs \
  -Dtest='PhysicalTenantResolverTest,PhysicalTenantKeyClassificationTest'
./mvnw verify -pl zeebe/dynamic-config/impl -Dquickly -DskipTests=false -DskipITs \
  -Dtest=PhysicalTenantConfigFetcherTest
```

Brokers take `physical-tenant-rollout.restart-keys=security.authorizations.enabled` to let the API
change that key.

# No restart for any input: the configuration file at runtime, and `default` like any tenant

Branch `pt-poc-default-tenant-file-watch`, on top of `pt-poc-standalone-gateway-tenants`
(e6730e4da4c). Not for merge as is.

Two requirements confirmed by the product owner: a change of the configuration file takes effect
without restarting any node, as an API change does; and `default` is treated like every other
tenant. Before this branch the coordinator read its file only at start, a differing version of
`default` was reported `DEGRADED` and never applied, and the lifecycle refused to restart `default`.
"Changing `default`" means changing `camunda.physical-tenants.default.*`; root configuration still
applies only at node start. `default` stays file-owned (the API answers `409 NOT_OWNER`), and it
cannot be disabled or deleted.

## What it proves, and by which test

|                                                                                                Claim                                                                                                 |                                                                                     Test                                                                                      |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| A changed file tenant rolls out to every broker, as a normal rolling change, and reaches a standalone gateway                                                                                        | `PhysicalTenantConfigFileWatchIT#shouldRollOutAChangedTenantOnlyOnceTheFileIsStable` (snapshot period on every broker, version 2, gateway serves version 2 with the new name) |
| A file written in two steps (first truncated to `default` only, then in full, within the quiet period) is acted on once, with its final contents                                                     | same test: the availability version of every other tenant is unchanged afterwards                                                                                             |
| A tenant added to the file is created (owner `FILE`) and serves gRPC through the standalone gateway                                                                                                  | `#shouldCreateATenantAddedToTheFile`                                                                                                                                          |
| A tenant taken out of the file is disabled, not removed: stopped on every broker and the gateway (`NOT_FOUND`), its data directory kept                                                              | `#shouldDisableATenantTakenOutOfTheFileKeepItsDataAndEnableItWhenPutBack`                                                                                                     |
| Put back, it is enabled at the same version, runs on every broker and the gateway again, and its process deployed before still runs                                                                  | same test                                                                                                                                                                     |
| A change of `camunda.physical-tenants.default.*` rolls out like any tenant: new module context on every broker, readiness gate and handover, gateway follows                                         | `#shouldRollOutAChangeOfTheDefaultTenantFromTheFile` (0 failed publishes to `default` through the gateway during it, see measurements)                                        |
| Only the coordinator acts on its file                                                                                                                                                                | `#shouldActOnTheFileOfTheCoordinatorOnly`: a change in brokers 1 and 2's files stays at version 1 for 4 s; written to broker 0's file, it rolls out                           |
| A coordinator that was down acts on the file it finds when it starts; nobody acts while it is down                                                                                                   | `#shouldActOnTheFileOfACoordinatorThatChangedWhileItWasDown`                                                                                                                  |
| A cluster with no `camunda.physical-tenants` section: adding a `default` section rolls out (default stops being the root object), taking it out again rolls out and default is the root object again | `PhysicalTenantDefaultWithoutSectionIT` (3 brokers, processes run through every broker after each step)                                                                       |
| Every sentinel `RESTART` key changed on `default` reaches every consumer that a boot with the value gives it, and no component keeps the old value                                                   | `PhysicalTenantDefaultKeyEffectIT` (85 keys in 8 batches; the two consumers it found are fixed, see below)                                                                    |
| DISABLE/ENABLE changes, `default` cannot be disabled or deleted, `default` updates like any tenant                                                                                                   | `PhysicalTenantConfigChangesTest` (6 new)                                                                                                                                     |
| The availability initializer leaves file-owned tenants to the file input                                                                                                                             | `PhysicalTenantAvailabilityInitializerTest#shouldLeaveATenantOwnedByTheFileToTheFileInput`                                                                                    |
| Discovery of the files, re-read with boot precedence, quiet period, partial and repeated writes, unparseable and missing files                                                                       | `PhysicalTenantConfigFileWatcherTest` (8)                                                                                                                                     |

## How it works

|                  Piece                  |                                     Where                                     |                                                                                                                                                                                                                                                                                                                        What                                                                                                                                                                                                                                                                                                                        |
|-----------------------------------------|-------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `PhysicalTenantConfigFiles`             | dist                                                                          | Finds the files Spring Boot loaded: every property source named `Config resource 'file [<path>]' via location '…'` (one per YAML document), so `./config/application.yaml`, `spring.config.location` and `spring.config.additional-location` alike. Re-reading = load each file again with the same loader, put its documents where its old documents were (later documents first, inactive profiles dropped), keep every other source (env vars, system properties, command line, classpath files) as it is, then take `subtreeOf` for `default` and every id `PhysicalTenantConfigUtil.discover` finds. Root configuration is never bound again. |
| `PhysicalTenantConfigFileWatcher`       | dist                                                                          | Polled every `physical-tenant-rollout.file-watch.interval` (1 s). Reads the files once per poll; hands out their tenants once the bytes stayed the same for `…file-watch.quiet-period` (2 s) and parse, parsing exactly the bytes found stable. Unparseable contents are logged once and skipped until the files change; a missing file is waited for.                                                                                                                                                                                                                                                                                             |
| File reconciliation                     | `PhysicalTenantConfigService`                                                 | On every broker; only the coordinator acts: at start, when it becomes the coordinator (checked at every poll), and when the files changed. State-based against the cluster configuration: no group → create (owner `FILE`, partitions and exporters from the validated configuration); group without reference → register; disabled → `ENABLE`; different hash → update through the API's path (classification, validators, boot validations); a `FILE` group missing from the files, except `default` → `DISABLE`. `IN_PROGRESS`/`UNAVAILABLE` → retried after 1 s; a rejected hash is not sent again.                                            |
| `DISABLE`, `ENABLE`                     | dynamic-config `PhysicalTenantConfigChanges`, proto values 5 and 6            | `DISABLE`: group disabled + `StopPhysicalTenantOperation` per live member; members, routing, reference and data stay. `ENABLE`: group enabled, reference moves to the next version only if the hash differs, `ApplyPhysicalTenantConfigOperation` per live member. Both refused for `default`, as `DELETE` is.                                                                                                                                                                                                                                                                                                                                     |
| `PhysicalTenantAvailabilityInitializer` | dynamic-config                                                                | Leaves every group with a configuration reference alone; the file input disables and enables those with stops and starts. Groups without a reference keep the flag-only behaviour of `main`.                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| Re-enabled tenant on a member           | `DefaultPhysicalTenantRegistry#release`, `PhysicalTenantLifecycleService#add` | A stop reserves the id on the member, which made a re-enable fail ("stays reserved"). An add with a cluster-agreed incarnation releases it first; the cluster configuration already refuses to re-create a removed tenant.                                                                                                                                                                                                                                                                                                                                                                                                                         |
| `default`                               | `PhysicalTenantLifecycleService`, `PhysicalTenantConfigExecutor`              | The restart guard and the `DEGRADED` special case are gone; removal is still refused. A `default` subtree with overrides resolves to an object of its own and a converted `BrokerCfg`; an empty one resolves to the root object and the root `BrokerCfg`, as at boot.                                                                                                                                                                                                                                                                                                                                                                              |
| Standalone gateway                      | `GatewayPhysicalTenantFollower`                                               | Follows the cluster for every tenant, `default` and its own file's tenants included. Its own file serves a tenant until the gateway sees the tenant's reference; it then fetches the cluster's configuration and replaces the gRPC module unless the gateway part (`name`, `security.*`) equals its own. Keeps the registry's incarnation of a boot tenant; serves a re-enabled tenant again.                                                                                                                                                                                                                                                      |
| Test support                            | `TestSpringApplication#withAdditionalConfigLocation`                          | Passes `--spring.config.additional-location=file:…`, so a test broker reads a real file as a mounted `application.yaml`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |

A bug found on the way: the first version fingerprinted the file and then parsed it with a second
read. `Files.writeString` truncates before writing, so a write that started between the two reads
was parsed as an empty file, and the coordinator disabled every tenant, then enabled them again
once the full file was stable. The IT's log showed it while the test still passed on the final
state; the test now also checks that no other tenant's availability version moved. The watcher now
parses the bytes it fingerprinted. A second one: the IT deleted its files after the tests, and the
watcher took the missing files as files without tenants and disabled them all; a missing file is
now waited for. In Kubernetes a ConfigMap volume is swapped
atomically (`..data` symlink), so a reader there never sees a half-written file; the quiet period
covers editors and tools that write in place.

## Root-object consumers that keep `default` stale

The harness changed 85 sentinel `RESTART` keys on `default` (one broker boots with X, another boots
with Y and restarts `default`'s module with X) and also listed beans still holding a configuration
object with Y. Rows marked **H** were found by it; the others are from reading code. Cost as in the
sections above: S = a lookup or a hook, M = per-tenant objects with a close path, L = a subsystem.

|                                                                    Where                                                                    |                                  Keys                                   |                                                                                                         Symptom                                                                                                         |      Cost      |                                     Status                                     |
|---------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------|--------------------------------------------------------------------------------|
| `dist/.../commons/rest/PhysicalTenantRestConfiguration.java:22-35` (boot `resolver.mapValues`)                                              | `monitoring.metrics.job-metrics.*` (5 sentinel keys)                    | REST `/v2/system/configuration` and the job-metrics gate keep the boot values (**H**); the same for every replaced tenant                                                                                               | S              | Fixed: cherry-pick of `9d51833a1470` from `pt-poc-tenant-config-consumers`     |
| `zeebe/broker/.../bootstrap/BrokerContextImpl.java` (fields), `BrokerStartupProcess.java` (`createBrokerContext`)                           | all                                                                     | `getPartitionManager()`/`getBrokerAdminService()` returned the stopped boot instances and kept the old module (factory, disk monitor, `BrokerCfg`) reachable (**H**, for every key)                                     | S              | Fixed: looked up in the live maps                                              |
| `dist/.../zeebe/broker/RdbmsExporterConfiguration.java:46` → `RdbmsExporterWrapper.configure`                                               | `data.wait-states.enabled`                                              | RDBMS exporter keeps the boot value; not exercised (`secondary-storage: none`)                                                                                                                                          | S              | Left; in flight on `pt-poc-runtime-secondary-storage`                          |
| `BrokerBasedConfiguration.java:94` → `SchedulerConfiguration`; `ActorClockControlledPropertiesOverride.java:31`; `ConditionalOnJfr.java:35` | `monitoring.metrics.actor`, `system.clock-controlled`, `monitoring.jfr` | Node-wide beans built from the root; accepted for `default` and ignored, as for every tenant                                                                                                                            | S (reclassify) | Left: `REJECT root-only` on `pt-poc-tenant-config-consumers`, not on this base |
| `dist/.../zeebe/broker/SystemContextLoader.java:281-301`                                                                                    | legacy `zeebe.broker.*` without a unified twin                          | Not stale, but drift: `default` without overrides runs on the root `BrokerCfg`, which carries legacy properties; the first override switches it to a converted `BrokerCfg` without them, and back when the overrides go | M              | Left; whatever a restart with the same file does                               |
| `zeebe/broker/.../system/SystemContext.java:246-275` (`getPhysicalTenantContexts`, `getSecurityConfiguration`)                              | `default`'s boot security and `BrokerCfg`                               | Boot snapshot; no runtime caller found (the lifecycle seeds from it)                                                                                                                                                    | S              | Left, latent                                                                   |

Every other consumer of the 85 keys is rebuilt by the module restart, as for the other tenants:
the open journal segment keeps its size until the next one. Not covered: the 37 flags and choices
of the original harness (they need several tenants in one broker to be attributed), and `SECURITY`
keys (`REJECT security-chain`, another spike).

## A cluster without a `physical-tenants` section

The same path works (`PhysicalTenantDefaultWithoutSectionIT`). What differs:

- `default` is the root `Camunda` object itself, and its file subtree is empty. The coordinator
  registers it as `FILE` version 1 with the hash of the empty subtree.
- The first `camunda.physical-tenants.default.*` key turns it into an object of its own with a
  converted `BrokerCfg` (legacy `zeebe.broker.*` drift above); taking the section out turns it back
  into the root object. Both are what a restart with that file gives.
- **With a generic exporter in the root catalog the section cannot be added at runtime.** A
  declared `default` must list `data.exporters-assigned` (boot validation), and that key is
  `REJECT exporter-ids`, so the coordinator rejects the change ("'data.exporters-assigned[0]'
  cannot change at runtime"), although the exporter set is the same and a restart accepts it. Seen
  in a run with the recording exporter; the IT runs without it. Fix: compare the effective exporter
  sets of the resolved configurations instead of the key (S–M). The same rejection applies to a
  file tenant whose `exporters-assigned` line is dropped.

## Kubernetes delivery

No checkout of camunda-platform-helm exists in `~/git`. A rendered copy of chart `15.0.0-alpha3`
(appVersion 8.10.x) is in `~/git/camunda/load-tests/setup/c8-mk-render-check/charts/camunda-platform`
(untracked, in the main checkout), so this reads that copy:

- `templates/orchestration/statefulset.yaml` mounts the ConfigMap's `application.yaml` with
  `subPath` at `/usr/local/camunda/config/application.yaml`; `orchestration.extraConfiguration`
  files are mounted with `subPath` too (`camundaPlatform.extraConfigurationVolumeMounts`). A
  `subPath` mount is never updated in place, so a running pod never sees a ConfigMap change.
- The pod template carries `checksum/config` of the orchestration ConfigMap, so a `helm upgrade`
  that changes the configuration rolls every pod anyway.

So with today's chart the watcher never sees a change, and every change restarts the cluster. For
no-restart file changes the chart needs: the tenants' configuration in its own ConfigMap, mounted as
a directory (no `subPath`), added through `SPRING_CONFIG_ADDITIONALLOCATION`, and excluded from the
checksum annotation. The kubelet then swaps the files atomically within its sync period (about a
minute by default), and the brokers pick it up within the poll interval plus the quiet period.

## Measurements

Apple-silicon laptop, 3 brokers and 1 standalone gateway in one JVM, 1 partition, RF 3, secondary
storage `none`, poll interval 200 ms, quiet period 1 s, another heavy build on the machine.

|                                         What                                         |                                        Result                                         |                                  Method                                  |
|--------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------|--------------------------------------------------------------------------|
| Files written to a `default` change rolled out to 3 brokers (version applied by all) | 2161, 4844, 7064, 7404 ms (4 runs, the first before the half-read fix)                | `System.nanoTime` in the IT; includes the 1 s quiet period and the polls |
| Files written to a new tenant rolled out to 3 brokers                                | 1974, 2016, 1793, 1815 ms (4 runs)                                                    | same                                                                     |
| Publishes to `default` through the standalone gateway during its rollout             | 0 failed in 4 runs (3102 to 16531 accepted); longest gap 95–161 ms (3 runs logged it) | one client in a loop, 5 s timeout, during the whole `default` test       |
| Restart of `default`'s module (`lifecycle.replace`, single broker)                   | 175–405 ms (8 batches)                                                                | `PhysicalTenantDefaultKeyEffectIT` report header                         |
| Walk of the harness, per batch (2 brokers)                                           | whole class 49 s                                                                      | failsafe                                                                 |

Most of a `default` rollout is the per-member restart with handover and transfer back behind the
readiness gate, as for other tenants; why it took 2.2 s once and 7 s in the other runs was not
analysed.

## Not done, and why

- **A coordinator change through a membership change** is not tested: the coordinator is the lowest
  member id of the cluster configuration, so it changes only when that member leaves. The code path
  is the one tested at coordinator start (the poll notices "became coordinator" and reconciles).
- **A re-enabled boot tenant answers 404 on REST** (from reading): it comes back with the cluster's
  incarnation, and `PhysicalTenantServedFilter` serves only boot incarnations with a security chain.
  gRPC serves it (tested).
- **A member that restarts with a file listing a tenant the cluster disabled**: whether its boot
  starts the tenant was not checked.
- **Cancel through the file** is still not possible; writing the old contents back is the file's
  cancel, as a new version.
- **`spring.config.import`, config trees and `optional:` locations created after start** are not
  watched; only files that were loaded at boot. Environment variables and system properties cannot
  change at runtime and are kept.
- **Security keys** of `default` stay `REJECT security-chain`; another spike covers its HTTP security.
- **The exporter-assignment rejection** above is not fixed.

## Open questions

1. Should a file tenant taken out of the file be disabled at once, or only after a grace period (a
   bad ConfigMap edit stops a tenant cluster-wide within seconds)? `main` disabled it only at the
   next coordinator start.
2. Should non-coordinators warn when their own file differs from the cluster (they ignore it)?
3. Is the exporter-set comparison the right fix for `exporters-assigned`, or should a declared
   `default` default to the root catalog?
4. Should the chart move tenants to their own ConfigMap (needed for any of this to work in
   Kubernetes), and who owns that change?
5. A missing file is waited for here; a restart without an `optional:` file would not start at all,
   one with an `optional:` file would start without its tenants. Which should a runtime deletion mean?

## Results (2026-11-12)

|                                                                                                               Suite                                                                                                               |                                                                                                               Result                                                                                                                |
|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `PhysicalTenantConfigFileWatchIT`                                                                                                                                                                                                 | 6/6 in 2 runs after both fixes above; earlier: one run did not start (missing `default` exporter assignment in the test file), one passed with the half-read bug in its log, one passed with the deleted-files disabling in its log |
| `PhysicalTenantDefaultWithoutSectionIT`                                                                                                                                                                                           | 1/1, twice (first run, with the recording exporter, failed: the exporter-assignment rejection)                                                                                                                                      |
| `PhysicalTenantDefaultKeyEffectIT`                                                                                                                                                                                                | 8/8 after the two fixes; 2/8 before (REST job-metrics, broker context), 7/8 with the broker context fix only                                                                                                                        |
| Regression batch: `PhysicalTenantConfigRolloutIT` 9, `PhysicalTenantRuntimeLifecycleIT` 10, `PhysicalTenantStandaloneGatewayLifecycleIT` 4, `PhysicalTenantStandaloneGatewayTopologyIT` 1, plus the file-watch 6 and no-section 1 | 31/31, one run                                                                                                                                                                                                                      |
| dynamic-config impl: `PhysicalTenantConfig*Test`, `PhysicalTenantAvailabilityInitializerTest`, `ClusterConfigurationManagementRequestsHandlerTest`                                                                                | 45/45                                                                                                                                                                                                                               |
| dist: `PhysicalTenantConfigFileWatcherTest` 8, `PhysicalTenantConfigExecutorTest` 5, `PhysicalTenantRestConfigurationTest` 8                                                                                                      | 21/21                                                                                                                                                                                                                               |

## How to run

```bash
./mvnw install -Dquickly -T2 -Dmaven.build.cache.enabled=false
./mvnw verify -pl zeebe/qa/integration-tests -Dquickly -DskipTests=false -DskipUTs \
  -Dmaven.build.cache.enabled=false \
  -Dit.test='PhysicalTenantConfigFileWatchIT,PhysicalTenantDefaultWithoutSectionIT,PhysicalTenantDefaultKeyEffectIT'
grep -h "rolled out to 3 brokers\|Load on the default" \
  zeebe/qa/integration-tests/target/failsafe-reports/*FileWatchIT*-output.txt
cat zeebe/qa/integration-tests/target/physical-tenant-key-effect/default-*.txt
./mvnw verify -pl zeebe/dynamic-config/impl -Dquickly -DskipTests=false -DskipITs \
  -Dtest='PhysicalTenantConfig*Test,PhysicalTenantAvailabilityInitializerTest'
./mvnw verify -pl dist -Dquickly -DskipTests=false -DskipITs \
  -Dtest='PhysicalTenantConfigFileWatcherTest,PhysicalTenantConfigExecutorTest,PhysicalTenantRestConfigurationTest'
```

Brokers take `physical-tenant-rollout.file-watch.interval` and `…quiet-period` (Spring durations;
an interval of 0 turns the watcher off).
