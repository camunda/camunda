# Proof of concept: cluster-wide physical tenant lifecycle (lifecycle slice 1)

Branch `pt-poc-lifecycle-api`, on top of slice 0 (`pt-poc-lifecycle-tenant-set`, notes in
[POC-NOTES-slice-0.md](POC-NOTES-slice-0.md)). Not for merge as is.

## What it proves

On a cluster of three brokers with embedded gateways, a physical tenant is created, updated and
removed through a management endpoint on any broker, without a node restart. The change reaches
every member through dynamic-config, one member at a time, and its progress is visible per member.

- **Create** through a broker that is not the coordinator: the tenant serves gRPC on every broker.
- **Update**: only that tenant restarts, on one member at a time; the next member restarts only once
  the tenant's partitions are installed again on the previous one. Other tenants' modules are
  untouched (same module context object before and after).
- **Rejected update** (unclassified key, storage location, exporter ids): `400` with one violation
  per key, nothing changes.
- **Second change during a rollout**: `409 IN_PROGRESS`, not queued.
- **Member down during a change**: the others apply it; the member applies it when it returns.
- **File input**: the coordinator registers every tenant of its static configuration as `FILE`
  owned; each member records it applies it when it runs the same configuration (by hash).
- **Ownership**: the API cannot change a `FILE`-owned tenant (`409 NOT_OWNER`); the file ignores
  `API`-owned tenants, including the coordinator's availability rule.
- **Remove**: stopped on every member, tombstoned (`REMOVED`), data kept, id cannot be reused.
- **Observability**: `GET /actuator/physicalTenants` lists owner, version, hash, incarnation,
  state, the version each member applied and `rolledOut`; each broker exposes
  `zeebe.physical.tenant.config.applied.version{physicalTenant}`.

## How to run

```bash
./mvnw install -Dquickly -T3
# 3 brokers, end to end through the actuator (8 tests, ~30 s)
./mvnw verify -pl zeebe/qa/integration-tests -DskipTests=false -DskipUTs -Dquickly \
  -Dit.test=PhysicalTenantConfigRolloutIT
# 3 dynamic-config members with real messaging and a fake executor (5 tests, ~10 s)
./mvnw verify -pl zeebe/dynamic-config/impl -DskipTests=false -DskipITs -Dquickly \
  -Dtest='PhysicalTenantConfig*Test,ClusterConfigurationManagerImplTest'
./mvnw verify -pl configuration -DskipTests=false -DskipITs -Dquickly \
  -Dtest='PhysicalTenantKeyClassificationTest,PhysicalTenantEnvironmentsTest'
```

Manual use, on the management port of any broker:

```bash
curl -X POST localhost:9600/actuator/physicalTenants/tenantb -H 'Content-Type: application/json' \
  -d '{"name":"Tenant B","data.secondary-storage.type":"none"}'
curl -X PUT  localhost:9600/actuator/physicalTenants/tenantb -H 'Content-Type: application/json' \
  -d '{"name":"Tenant B","data.secondary-storage.type":"none","data.snapshot-period":"7m"}'
curl localhost:9600/actuator/physicalTenants
curl -X DELETE localhost:9600/actuator/physicalTenants/tenantb
```

## Design

|                                Piece                                |         Where          |                                                                                                                                                          What                                                                                                                                                          |
|---------------------------------------------------------------------|------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `PhysicalTenantConfigReference`                                     | dynamic-config `state` | Per tenant, on its partition group: config version (counter of the tenant alone), SHA-256, owner `FILE`/`API`, incarnation id, applied version per member. Own version, merged like `TenantAvailability`; applied versions merge per member by max. Proto field 8 / JSON optional: old configurations load without it. |
| `ApplyPhysicalTenantConfigOperation`, `StopPhysicalTenantOperation` | dynamic-config         | Partition group operations, one per live member, sequential. Dispatched by a per-member executor, like the existing tenant removal, because the tenant's module may not run on the member yet. Retried until they complete.                                                                                            |
| `PhysicalTenantConfigStore`, `PhysicalTenantConfigFetcher`          | dynamic-config `impl`  | Blobs in `<data>/physical-tenant-configs/<tenant>/<version>.config`. A member that lacks a version asks the coordinator, then the members that applied it, then the rest, and keeps the first answer whose hash matches.                                                                                               |
| `PhysicalTenantConfigRequest`, `PhysicalTenantConfigChanges`        | dynamic-config         | One management request (create/update/delete, owner, blob, expected version, partition count, exporter ids). The coordinator stores the blob, moves the reference and starts the group's change in one configuration update.                                                                                           |
| `PhysicalTenantKeyClassification` + `key-classification.txt`        | configuration          | Every overridable key is `RESTART` or `REJECT <reason>`; unlisted keys are rejected; exporter id set changes are rejected. The test fails when the golden list of overridable keys and the classification diverge.                                                                                                     |
| `PhysicalTenantConfigValidator`                                     | configuration          | Extension point; every Spring bean of this type runs on every change (API and file). The classification is the first one.                                                                                                                                                                                              |
| `PhysicalTenantConfigExecutor`                                      | dist                   | Applies changes on the member through slice 0's `PhysicalTenantLifecycleService`; same hash means no restart; waits for readiness after a replace; reports `DEGRADED` while a change cannot apply; publishes the applied-version gauge.                                                                                |
| `PhysicalTenantConfigService`, `PhysicalTenantsEndpoint`            | dist                   | Front door for API and file: validators and boot validation on the receiving node, then the coordinator. Start-up catch-up and file registration. Actuator `physicalTenants`.                                                                                                                                          |

Decisions taken where the scoping leaves a question open (each is easy to change):

- **Coordinator path**: the change starts the group's change directly in one configuration update
  (as startup provisioning does), not as a phased plan, because a new tenant's group has to exist
  in the same step. Concurrency is checked explicitly: no other tenant may have a pending config
  operation, and the tenant's group may have no pending change or phased plan.
- **One change at a time across the cluster** (direction items 6, 7): a second change is rejected
  with `IN_PROGRESS`, also from the file input, which retries.
- **Blob fetch (open option in direction item 4)**: ask the coordinator first, fall back to the
  members whose applied version shows they hold it. No new gossip is needed; the applied versions
  are already in the cluster configuration.
- **Validation location**: classification, plugged validators and the boot validations run on the
  node that receives the change; the coordinator enforces the version the change was validated
  against (`expectedVersion`), ownership and concurrency. Every member runs the boot validations
  again when it applies.
- **Classification (Q3)**: `RESTART` for processing, engine, flow control, primary storage (except
  backup), exporters' args, monitoring, partition count (no effect, as without physical tenants)
  and the display name. `REJECT` for the secondary-storage location (`storage-location`), exporter
  ids, and keys read outside the tenant module (`outside-module`: the rest of secondary storage,
  REST API, document store, backup, audit log, secrets, webapps). Security keys are `REJECT
  security-chain` until the CSL dispatcher can rebuild a tenant's HTTP chain (slice 0 risk 5/12);
  flip them to `RESTART` in the resource file when it lands.
- **Rolling order**: live members by id; a replaced tenant completes on a member only once its
  partitions are installed there. Without this, all three replicas restarted within ~120 ms.
- **Create**: apply on every member, then the partition placement of startup provisioning,
  bootstrapped after every member runs the module. Replication factor: the cluster's current
  minimum. Partition count and exporter ids come from the validated configuration.
- **File registration**: an existing file tenant gets a reference with no change plan, so cluster
  start-up never starts a plan; members record the version when their running config has the same
  hash. A later file difference on the coordinator is sent as a normal rolling update.
- **Incarnation id (Q7)**: generated by the coordinator for API-created tenants and stored in the
  cluster configuration, so every member agrees. Boot tenants keep slice 0's node-local id (aligning
  them would break slice 0's REST incarnation filter).
- **Remove (Q6)**: disable, stop on every member, then the existing `RemovePhysicalTenantOperation`
  tombstone. A removed group no longer counts as an inconsistent topology (that check shut the
  brokers down; the IT found it, see the `fix:` commit).
- **Stuck member (Q4 A)**: a member that cannot apply retries with dynamic-config's backoff and the
  tenant reports `DEGRADED`; the rollout shows it as not applied.
- **Endpoint**: actuator on the management port, `202 Accepted` once the coordinator started the
  rollout; errors map to `400/404/409/503` with `status`, `message`, `violations`.

## Stubbed or missing

- **Leadership transfer before a restart (US-17)**: not implemented. The member that leads the
  tenant's partitions restarts them, and an election follows (see measurements). The extension
  point is `PhysicalTenantConfigExecutor#applyNow`, before `lifecycle.replace`. CLT's initiate is
  gated on the rebalance coordinator's check and reports its result to the coordinator, so a
  member-initiated hand-off needs a small API in the rebalance module rather than a direct call.
- **Graceful drain on remove (Q12)**: not implemented; remove stops at once.
- **Admin permission**: the endpoint is on the management port like the other actuators, with no
  authorisation. Audit events are not emitted.
- **Backups (US-11)**: the blob directory is not in backups.
- **Blob retention**: old versions are never deleted.
- **Storage check on add (US-5 AC)**: slice 0 still requires `secondary-storage.type=none` for a
  runtime-added tenant.
- **Literal-credential guard**: only the validator extension point; no implementation.
- **`default` tenant (Q13)**: registered as `FILE`; a differing version is reported `DEGRADED`, not
  applied.
- **Removing a `FILE` tenant from the file** still goes through the existing disable path.
- **Env-var declared file tenants**: their keys come out in the env-var canonical form
  (`secondarystorage`), which the classification does not list, so their updates are rejected.
- **Standalone gateway**: no lifecycle.

## Measurements

All on an Apple-silicon laptop, three brokers in one JVM (`TestCluster`), one partition, RF 3,
secondary storage none, warm JVM.

|                          What                          |                                               Result                                               |                                                   Method                                                   |
|--------------------------------------------------------|----------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| Update rollout to 3 brokers (PUT to all applied)       | 2762–2806 ms over 4 runs                                                                           | `PhysicalTenantConfigRolloutIT#shouldServeTheUpdatedTenantAgainOnceItsRolloutCompleted`, `System.nanoTime` |
| Interruption of the updated tenant during that rollout | longest gap between accepted commands 2665–2871 ms; 19–29 failed of ~130–190 sync `publishMessage` | same test, one client looping on broker 0's gateway                                                        |
| Per-member apply (log timestamps)                      | leader member ~2.6 s (restart, election, partitions installed); followers ~120 ms                  | `GraphScopeReconciler` applying/applied lines                                                              |
| Same, before waiting for readiness                     | ~37 ms per member; all three replicas restarted within ~120 ms                                     | as above                                                                                                   |
| Dynamic-config rollout, 3 members, fake executor       | create/update/delete/down-member: 5 tests in ~10 s                                                 | `PhysicalTenantConfigRolloutTest`                                                                          |

The ~2.7 s gap is almost all the election after the leader's restart, which is the number a
leadership hand-off before the restart would reduce.

## Open questions for the team

1. One change at a time across the cluster plus retry until complete means one down member blocks
   every tenant change until it returns. Is that acceptable, or should the lock be per tenant and
   only the rollout of different tenants be sequenced?
2. Should a member's rollout step wait for the tenant to accept commands (partition health tree),
   not just for its partitions to be installed (~250 ms earlier, slice 0)?
3. Security keys: reject until the CSL dispatcher lands (as here), or accept and document that REST
   keeps the old chain?
4. Where should classification run: the receiving node (here, with `expectedVersion`) or the
   coordinator (scoping wording)? The coordinator would need the dist-side config model.
5. Incarnation ids of boot tenants: should the coordinator's registration assign them, and how does
   the REST filter learn a new incarnation?
6. Blob storage: include `physical-tenant-configs` in backups (US-11), and when may old versions go?
7. Coordinator failover mid-rollout works for fetching (fallback to members that applied), but two
   coordinators or a file/API race remain open as in the scoping.

## Touch points with other work

- **CLT / rebalance (US-17)**: hook before `lifecycle.replace` in `PhysicalTenantConfigExecutor`;
  needs a member-initiated transfer of the partitions it leads, or a coordinator request for it.
- **dynamic-secrets**: `PhysicalTenantConfigValidator` is where the literal-credential guard plugs
  in (any Spring bean); `DEGRADED` health is shared. The `outside-module` secondary-storage keys can
  become `RESTART` once the client holder exists.
- **CSL dispatcher**: flip `security.*` to `RESTART` in `key-classification.txt`.
- **Carlo's per-tenant actor pools**: unchanged; slice 0's registry listener hooks still apply.
- **System partition (direction item 3)**: `PhysicalTenantConfigChanges` is a pure function of the
  configuration and the request, so it can move to a Raft-backed store.
- **Slice 0 changes** made here: `PhysicalTenantLifecycleService#add(id, config, incarnation)`,
  `#validate`, `#awaitReady`; `BrokerContext#getClusterConfigurationService`;
  `PhysicalTenantEnvironments#subtreeOf`.

