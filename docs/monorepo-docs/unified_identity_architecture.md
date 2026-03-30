# Unified Identity Architecture

**IMPORTANT**: This document is a work in progress and reflects the current thinking on the unified identity architecture for Camunda Hub and Orchestration Clusters. It is intended to provide a high-level overview of the proposed design, including key components, interactions, and deployment models. The architecture is subject to change as we iterate on the design and gather feedback from stakeholders.

---

## 1. Introduction and goals

This document describes the planned Unified Identity Architecture for Camunda Hub and Orchestration Clusters in an arc42-style structure. It:

- Summarizes the current identity architecture across Camunda platform components (OC Identity, Management Identity, SaaS Auth0).
- Proposes a target architecture with a single identity plane, implemented as a hexagonal library reused in Hub and Orchestration Clusters.
- Shows how the architecture supports multiple engines per cluster and multi-tenancy.
- Emphasizes that standalone Orchestration Cluster (without Hub) remains a first-class deployment option.
- Outlines how a single shared frontend and pluggable backends (persistence, OC command creation, etc.) fit into the design.

IMPORTANT: This document shows the final architecture, we won’t be able to implement it by October.
We need to break the project down into several iterations with interim goals until we actually reach the endgame.

---

## 2. Current identity architecture (Camunda platform today)

### 2.1 Identity components

Today identity responsibilities are split across several components:

- **Orchestration Cluster Identity (OC Identity)**
  - Embedded into the Orchestration Cluster runtime.
  - Manages runtime authentication and fine-grained authorizations (process definitions, instances, tasks, tenants, cluster APIs) for Zeebe, Operate, Tasklist, and OC APIs.

- **Management Identity**
  - Separate service used to control access to Web Modeler, Console, and Optimize and other management-plane functions in earlier releases.
  - Uses Keycloak or an external OIDC provider plus its own SQL database in self-managed deployments (see existing Management Identity arc42 docs).

- **SaaS Auth0 tenant (Console / Hub)**
  - In SaaS today, Console and other management-side UIs use a Camunda-operated Auth0 tenant as their IdP/broker.
  - From the target-architecture perspective, this is an internal broker/IdP implementation detail, not part of the long-term reference model.

- **Customer Enterprise IdPs**
  - In self-managed and in the target state, the Enterprise IdP is always the customer’s IdP (Entra, Okta, Keycloak, etc.), integrated via standard OIDC.
  - SAML is supported via Keycloak.

### 2.2 Current high-level structure

#### 2.2.1 SaaS

```mermaid
flowchart TB
  subgraph SaaS_Mgmt["Management plane"]
    ConsoleHub["Console"]
    WebModeler["Web Modeler"]
  end

  subgraph Execution["Execution plane"]
    Operate["Operate"]
    Tasklist["Tasklist"]
    Identity["Identity"]
  end

  subgraph OC["Orchestration Cluster"]
    OCId["OC Identity</br>(embedded)"]
  end

  subgraph Customer["Customer landscape"]
    CustIdP["Enterprise IdP</br>(customer-managed)"]
  end

  SaaSAuth0["Auth0 tenant</br>(Camunda-managed, SaaS)"]

  ConsoleHub & WebModeler --> SaaSAuth0
  Operate & Tasklist & Identity  --> OC

  OCId --> SaaSAuth0
  SaaSAuth0 --> CustIdP
```

In SaaS today:

- Console and Web Modeler authenticate users against a Camunda-managed Auth0 tenant, which acts as the IdP/broker for all SaaS tenants.
- OC Identity in each Orchestration Cluster also uses Auth0 as its OIDC IdP, applying runtime authorization for Operate, Tasklist, and cluster APIs.
- Auth0 either federates to the customer Enterprise IdP or manages user accounts directly, depending on tenant configuration. The concrete integration code lives in the respective SaaS backends (Console/Hub services and OC Identity OIDC client configuration), which use standard OAuth2/OIDC client libraries to communicate with Auth0.

#### 2.2.2 Self-managed

```mermaid
flowchart TB
  subgraph Mgmt["Management plane"]
    Console["Console"]
    WebModeler["Web Modeler"]
    Optimize["Optimize"]
  end

  MgmtId["Management Identity</br>(service)"]

  subgraph Execution["Execution plane"]
    Operate["Operate"]
    Tasklist["Tasklist"]
    Identity["Identity"]
  end

  subgraph OC["Orchestration Cluster"]
    OCId["OC Identity</br>(embedded)"]
  end

  subgraph Customer["Customer landscape"]
    CustIdP["Enterprise IdP</br>(customer-managed)"]
  end

  Console & WebModeler & Optimize --> MgmtId

  MgmtId --> CustIdP

  Operate & Tasklist & Identity --> OC

  OC --> CustIdP
```

In Self-managed today:

- Console, Web Modeler, and Optimize delegate authentication and authorization to Management Identity, which in turn integrates with the customer Enterprise IdP via OIDC.
- OC Identity is embedded into each Orchestration Cluster and directly integrates with the same Enterprise IdP; it handles runtime authentication and fine-grained authorizations for Operate, Tasklist, and the cluster APIs.
- This results in two identity silos (Management Identity vs OC Identity) that both depend on the Enterprise IdP but use different models and configuration surfaces.

### 2.3 Limitations and motivation for change

Based on the target-architecture appendix and identity roadmap, the current setup has several issues:

- Split identity
  - Separate models and configuration for Management Identity vs OC Identity.
  - SaaS and self-managed use different stacks (Auth0 vs direct IdP).
- SaaS vs self-managed parity gaps
  - Capabilities such as mapping rules, tenants, and fine-grained RBAC/ABAC differ or are missing depending on deployment.
- Manual lifecycle and configuration
  - Joiner/mover/leaver flows are not fully automated from the customer’s IdP/HR system.
  - Tenants, roles, and mappings are often configured by hand in UIs.
- Limited observability and migration tooling
  - Identity migrations (e.g. Management Identity → unified plane) and policy changes are fragile, not first-class “jobs”.
  - It is hard to see and debug identity health end to end.

These limitations motivate a unified identity plane with consistent semantics and tooling across Hub and all clusters, including multi-engine and multi-tenant scenarios.

### 2.4 Preparation work and ongoing epics

- [Prepare Authentication for Hub Integration](https://github.com/camunda/camunda/issues/38556)
- Spike about extraction of code: [Spike/new replacement auth lib](https://github.com/camunda/camunda/pull/49058)

---

## 3. Solution strategy: unified identity plane and library

The target architecture introduces one consistent identity and policy model shared between Hub and all Orchestration Clusters:

- Hub Identity & Policy
  - Source of Truth (SoT) for users, groups, roles, tenants, mapping rules, and authorizations for all clusters and Hub apps.
- OC Identity
  - Per-cluster projection and enforcement of that policy, optimized for runtime access checks, and aware of multiple engines/tenants per cluster.
- Single identity plane for all consumers
  - Web UIs, user apps, workers, and integrations are all just API clients authenticated by the Enterprise IdP and authorized against this unified policy model.

Technically, this is implemented as a pluggable identity/security library:

- Embedded into Hub and Orchestration Cluster.
- Exposes Authentication (OIDC/SAML) and Authorization (RBAC/ABAC) capabilities via well-defined SPIs.
- Reuses the host application’s existing storage and infrastructure via SPI interfaces (no new standalone database or service).

Key design principles (selected):

- One identity plane for Hub and OC, with Hub as policy SoT whenever present.
- SaaS / self-managed parity: same concepts (tenants, mapping rules, fine‑grained permissions, BYO IdP) in both deployment models.
- Hexagonal architecture: all persistence, messaging, OC command creation, and engine‑level wiring are behind interfaces; default implementations can be swapped or replaced entirely.
- IdP-agnostic: only relies on OIDC and SAML standards, so any compliant IdP can integrate.
- Automated lifecycle and migrations: IdP claim mapping and outbox-based policy replication, with idempotent and observable migrations.
- Standalone OC support: OC can act as the top-level policy authority when Hub is absent, mirroring the fallback topologies in existing docs.

---

## 4. Target system context

This section describes the unified identity system at a high level, showing how the new library integrates into the platform across two supported deployment modes. The diagrams illustrate key components (Hub, Orchestration Clusters, identity UIs, infrastructure) and their relationships.

### 4.1 Full mode (Hub + OC)

In full mode, the platform runs with both Hub (management/control plane) and Orchestration Cluster (execution plane). Both use the same identity model and the same Security Gateway Framework.

Configuration flows top-down: Hub is the central source of truth for all policy. Configuration is authored once in Hub and propagated via the outbox pattern to each OC, which then propagates scoped views to individual engines. The Admin UI in each OC runs in read-only mode, showing the local projection of Hub policy.

```mermaid
flowchart TB

  subgraph Mgmt["Management plane"]
    Console["Console"]
    WebModeler["Web Modeler"]
    Admin["Admin"]

    Hub["Hub"]

    Console & WebModeler & Admin --> Hub
  end

  subgraph Execution["Execution plane"]
    Operate["Operate"]
    Tasklist["Tasklist"]
    SecGaUIExec["Admin UI (view only)"]

    OC["Orchestration Cluster"]

    Operate & Tasklist & SecGaUIExec --> OC
  end

  Infra["Infrastructure (IDPs, DBs)"]

  Hub --> OC
  OC --> Infra
  Hub --> Infra
```

- Hub and each OC use the same Security Gateway Framework as the shared identity and policy engine.
- Hub is the single source of truth for all policy and configuration.
- Hub propagates policy changes via the outbox pattern to OC, which maintains a local projection and handles runtime enforcement per engine/tenant.
- Existing infrastructure is reused, no new databases or services are introduced.

### 4.2 OC-only mode (standalone OC)

In OC-only mode, Hub is not present. The Orchestration Cluster becomes the local source of truth for all policy and configuration. OC is a first-class deployment option and acts as top-level policy authority for its engines. This mode is useful for development environments and self-contained production scenarios.

Configuration flows locally: OC manages all policies directly, and the Admin UI in OC runs in read-write mode, allowing full policy authoring and management. Configuration then propagates from OC to individual engines.

```mermaid
flowchart TB

  subgraph Execution["Execution plane"]
    Operate["Operate"]
    Tasklist["Tasklist"]
    Admin["Admin"]

    OC["Orchestration Cluster"]

    Operate & Tasklist & Admin --> OC
  end

  Infra["Infrastructure (IDPs, DBs)"]

  OC --> Infra
```

- OC is the single source of truth for all policy and configuration.
- The same Security Gateway Framework is used but configured for standalone operation.
- Existing infrastructure is reused, no new databases or services are introduced.

---

## 5. Building block view (target)

### 5.1 High-level components

The following diagrams show the internal structure of Hub and Orchestration Cluster, including how Security Gateway Framework instance connects to frontend applications, infrastructure, and (in multi-engine scenarios) individual engine instances.

#### 5.1.1 Full mode (Hub + OC)

```mermaid
flowchart TB
  subgraph Mgmt["Management plane"]
    Console["Console"]
    WebModeler["Web Modeler"]
    AdminHub["Admin UI</br>(read/write)"]

    subgraph Hub["Hub"]
      SecGatHub["Security Gateway Framework"]
    end

    Console & WebModeler & AdminHub --> Hub
  end

  subgraph Execution["Execution plane"]
    Operate["Operate"]
    Tasklist["Tasklist"]
    AdminOC["Admin UI (view only)"]

    subgraph OC["Orchestration Cluster"]
      SecGatOC["Security Gateway Framework"]

      SecGatOC -->|"config propagation"| Engine2
      SecGatOC -->|"config propagation"| EngineN

      subgraph Engine2["Engine2 (optional)"]
        SecEngFrame1["Security Engine Framework"]
      end

      subgraph EngineN["Default Engine"]
        SecEngFrameN["Security Engine Framework"]
      end
    end

    AdminOC & Operate & Tasklist --> OC
  end

  IdPs[("[1 - N] IDPs (per tenant/engine)")]
  DBs[("DBs (primary/secondary)")]
  HubDb[("Hub DB")]

  SecGatHub -->|"config propagation"| SecGatOC
  Engine2 & EngineN --> DBs
  OC & Hub --> IdPs

  Hub --> HubDb

  style Engine2 fill:#34a853,color:#fff
  style EngineN fill:#34a853,color:#fff
```

Key building blocks in full mode:

- Console, Web Modeler, Admin UI (read-write): Frontend applications in the management plane. The Admin UI here allows full policy authoring for all configurable layers (Hub, OCs, engines, tenants).
- Hub + Security Gateway Framework: Central source of truth. Manages all policy configuration for all clusters, OCs, and engines. All policy changes originate here.
- Operate, Tasklist, Admin UI (read-only): Runtime frontends in the execution plane. The Admin UI shows the cluster-local projection of Hub policy; configuration is read-only.
- OC + Security Gateway Framework: Per-cluster policy enforcement and projection layer. Receives policy snapshots from Hub via the outbox pattern. Propagates scoped policy views to individual engines.
- Engine instances (Default Engine, Engine2 optional): Optional multi-engine support. Each engine receives its scoped projection of cluster policy from OC. No direct Hub connection.
- Security Engine Framework: Engine-specific policy enforcement layer.
- Infrastructure (IDPs, DBs): Shared existing persistence and IdP connectivity for authentication and authorization across all layers.

Configuration propagation chain: Hub → OC → Engine.

#### 5.1.2 OC-only mode (standalone OC)

```mermaid
flowchart TB

  subgraph Execution["Execution plane"]
    Operate["Operate"]
    Tasklist["Tasklist"]
    AdminUI["Admin"]

    subgraph OC["Orchestration Cluster"]
      OCLib["Security Gateway Framework"]

      OCLib -->|"config propagation"| Engine2
      OCLib -->|"config propagation"| EngineN

      subgraph EngineN["Default Engine"]
        EngLibN["Security Engine Framework"]
      end

      subgraph Engine2["Engine2 (optional)"]
        EngLib2["Security Engine Framework"]
      end
    end
  end

  Operate & Tasklist & AdminUI --> OC

  IdPs[("[1 - N] IDPs (per tenant/engine)")]
  DBs[("DBs (primary/secondary)")]

  Engine2 & EngineN --> DBs
  OC --> IdPs

  style Engine2 fill:#34a853,color:#fff
  style EngineN fill:#34a853,color:#fff
```

Key building blocks in OC-only mode:

- Operate, Tasklist, Admin UI (read-write): Runtime frontends that interact directly with OC. The Admin UI allows full policy authoring (no Hub restrictions).
- OC + Security Gateway Framework: Local source of truth. Manages all policy and authorization directly without Hub coordination. All policy changes originate here.
- Engine instances (Default Engine, Engine2 optional): Each engine receives its scoped projection of local OC policy. OC is the single source of all policy.
- Security Engine Framework: Engine-specific policy enforcement layer.
- Infrastructure (IDPs, DBs): Local persistence and IdP connectivity; no cross-cluster replication or Hub involvement.

Configuration propagation chain: OC → Engine.

### 5.2 Unified policy model

The unified identity architecture is built around a single **policy model** that is shared between Hub Identity & Policy and OC Identity. Hub is the **source of truth** for this model per cluster; each OC hosts a **cluster-local projection** of the same concepts for enforcement.

At a high level, the shared policy model consists of:

- **Tenant**
  - Logical partition for data and access in a cluster (for example `default`, `retail`, `wholesale`, `customer-x`).
  - Used to scope where a principal is allowed to read or write data.
  - Tenant configuration (names, descriptions, flags) is authored in Hub and projected to each OC.

- **Group / Role**
  - **Group**
    - Collection of principals (users, mapping rules, clients) that should share the same permissions.
  - **Role**
    - Reusable set of permissions that can be attached to groups, users, or clients.
    - Roles are used both on the management plane (Hub apps) and execution plane (cluster APIs and UIs).

- **MappingRule**
  - Declarative rule that maps **IdP claims** to Camunda concepts:
    - `claimName` (for example `groups`, `org`, `department`).
    - `operator` (for example `EQUALS`, `CONTAINS`).
    - `claimValue` (for example `camunda-platform-admin`, `Retail`).
  - Targets one or more **roles**, **groups**, and **tenants**:
    - When an incoming token’s claims match, the principal automatically receives those roles/groups/tenants.
  - This is the main mechanism that turns IdP attributes into platform-level permissions.

- **Principal**
  - Represents an actor that can authenticate and be authorized:
  - **User principal**
    - Identified by an IdP user claim (for example `preferred_username`, `email`).
    - May have direct role/group assignments in addition to mapping-rule derived ones.
  - **Machine principal**
    - Identified by client credentials (for example `client_id`).
    - Used by workers, automation, and integrations (job workers, CI/CD, connectors, etc.).

- **Authorization**
  - Fine-grained permission record that ties **owners** to **resources** and **actions**:
  - Conceptually:
    - `(ownerType, ownerId, resourceType, resourceId, permissions[])`
  - Examples:
    - Owner: `GROUP:RetailDevelopers`
      ResourceType: `PROCESS_DEFINITION`
      ResourceId: `*`
      Permissions: `[READ_PROCESS_DEFINITION, READ_PROCESS_INSTANCE, CREATE_PROCESS_INSTANCE]`
    - Owner: `ROLE:ClusterAdmin`
      ResourceType: `CLUSTER_API`
      ResourceId: `*`
      Permissions: `[MANAGE_CLUSTER_SETTINGS, MANAGE_USERS]`
  - The same structure is used on:
    - **OC side** for engine and cluster resources (definitions, instances, tasks, cluster APIs).
    - **Hub side** for management resources (orgs, workspaces, projects, assets, clusters).

#### 5.2.1 Hub vs. OC responsibilities

Both Hub and OC use exactly the same policy model, but with different responsibilities.

- **Hub Identity & Policy**
  - Acts as **policy source of truth** for all clusters.
  - Authoring location for tenants, roles, groups, mapping rules, and authorizations.
  - Stores `PolicyVersion` records per cluster and drives propagation via Outbox/`OutboxEvent`. See section `5.3`.

- **OC Identity**
  - Hosts a **cluster-local projection** of the same entities.
  - Enforces authorizations for:
    - Web UIs on the cluster (Operate, Tasklist, Admin).
    - Cluster runtime APIs (gRPC/REST) for workers and integrations.
  - Does not invent new policy; it only applies and enforces what Hub (or, in standalone mode, local OC configuration) defines.

This unified model allows:

- The same concepts (tenants, roles, groups, mapping rules, authorizations, principals) to be used consistently on both **management** and **execution** planes.
- Identity-as-code and migrations to operate on one canonical representation (`PolicyVersion`) per cluster, with clear ownership (Hub) and enforcement (OC).

### 5.3 Outbox-based policy propagation (Hub → Orchestration Clusters)

The Security Gateway Framework uses an outbox pattern to propagate policy changes from Hub (policy SoT) to each Orchestration Cluster in a reliable, observable, and idempotent way.

**Snapshot vs. incremental diff:** Sending a full snapshot on every change would be unnecessarily expensive at scale. The propagation therefore follows a two-phase approach:

- **Initial sync (full snapshot):** When an OC connects for the first time (or after a reset), Hub sends a complete `POLICY_SNAPSHOT` — the full current state of tenants, roles, groups, mapping rules, principals, and authorizations for that cluster.
- **Subsequent updates (incremental diff):** After the initial sync, Hub sends only the changed entities as a `POLICY_DIFF` event. The OC applies the diff on top of its locally cached state. This keeps payloads small and propagation fast.

If an OC falls behind (e.g. due to a gap in its applied version sequence), it can request a full re-sync from Hub to recover a consistent baseline.

**Who decides snapshots, and when?**

- **Decision owner:** Hub (via the Outbox Dispatcher) decides per target OC whether to send `POLICY_DIFF` or `POLICY_SNAPSHOT`.
- **Snapshot triggers:**
  - OC has no acknowledged version yet (initial sync).
  - OC requests re-sync because `base_version` does not match `last_applied_version`.
  - Operator-triggered/manual re-sync for a specific OC.
  - Optional periodic checkpoint snapshot (operational safety net, typically low cadence).
- **How snapshot is built:** Hub reconstructs the current state for the cluster from latest non-deleted revisions per entity/scope and sends it as `POLICY_SNAPSHOT`.

`base_version` means the version an incoming `POLICY_DIFF` is built on top of (expected previous version in OC), not the last snapshot version.

The outbox pattern we use here is a specific instance of the “Transactional Outbox” architecture pattern.

Reference: [Transactional outbox](https://microservices.io/patterns/data/transactional-outbox.html)

#### 5.3.1 High-level flow

```mermaid
flowchart TB
  subgraph Hub["Hub"]
    subgraph subHubLib["Security Gateway Framework</br>(Hub instance)"]
      Dispatcher["Outbox Dispatcher"]
    end
  end

  HubDB[("Hub DB")]

  subgraph OC1["Orchestration Cluster A"]
    OC1Lib["Security Gateway Framework A</br>(OC instance)"]
    Engine1["Engine"]
  end

  OC1DB[("OC1 DB (Primary / Secondary)")]
  OC2DB[("OC2 DB (Primary / Secondary)")]

  subgraph OC2["Orchestration Cluster B"]
    OC2Lib["Security Gateway Framework B</br>(OC instance)"]
    Engine2["Engine"]
  end

  Hub --> HubDB
  OC1 --> OC1DB
  OC2 --> OC2DB

%% Dispatch path
  Dispatcher -->|"POST POLICY_SNAPSHOT (initial) or POLICY_DIFF (incremental)</br>/identity/policies/apply"| OC1Lib & OC2Lib

%% Apply on OC side
  OC1Lib -->|"Apply snapshot / diff</br>to local projection"| Engine1
  OC2Lib -->|"Apply snapshot / diff</br>to local projection"| Engine2
```

#### 5.3.2 Detailed outbox flow

```mermaid
flowchart TB
  subgraph HUB["Hub scope"]
    direction TB
    Admin["Admin UI/API"] --> HubSvc["Hub Security Gateway Framework"]

    subgraph HubTx["Hub transaction"]
      direction TB
      HubSvc --> WritePolicy["Write PolicyVersion"]
      HubSvc --> WriteOutbox["Write OutboxEvent</br>status=PENDING"]
    end

    WritePolicy & WriteOutbox --> Commit["Commit TX"]
    Commit --> Poll["Outbox Dispatcher polls</br>PENDING events"]
    Poll --> Decide["Determine payload type"]
    Decide -->|"first sync / resync"| Snapshot["Build POLICY_SNAPSHOT"]
    Decide -->|"normal update"| Diff["Build POLICY_DIFF</br>(full changed entities)"]
    Snapshot & Diff --> Send["POST /identity/policies/apply"]
    Ack["(Response from OC) ACK with last_applied_version"] --> Delivered["Mark OutboxEvent DELIVERED"]
    Failed["(Response from OC) Mark OutboxEvent FAILED</br>attempts++ / next_attempt_at"] --> Poll
  end

  subgraph OC_SCOPE["OC scope"]
    direction TB
    OCApply["Security Gateway Framework applies payload"]
    VersionCheck{"base_version matches</br>last_applied_version?"}
    Upsert["Update local projection</br>set last_applied_version"]
    ResyncReq["Reject + request resync"]

    OCApply --> VersionCheck
    VersionCheck -->|"yes"| Upsert
    VersionCheck -->|"no"| ResyncReq
  end

  Send --> OCApply
```

- Hub Security Gateway Framework
  - Accepts policy changes via UI/API.
  - Writes a delivery-neutral `PolicyVersion` and a corresponding `OutboxEvent` (`POLICY_SNAPSHOT` or `POLICY_DIFF`, status = PENDING) in the same transaction.
  - Tracks per-OC `last_acked_version` via `OcSyncState` to decide whether to send a diff or fall back to a full snapshot.
- Outbox Dispatcher
  - Periodically selects due PENDING events.
  - Loads the corresponding `PolicyVersion` and either prepares the full snapshot or computes the diff against the OC's last acknowledged version.
    - The diff contains only the changed entities. (But the full entity, not just updated fields)
  - Calls each OC's public admin endpoint (e.g. `POST /identity/policies/apply`) with the snapshot or diff payload.
  - Updates `OutboxEvent.status` to DELIVERED or FAILED and manages retries via `attempts` and `next_attempt_at`.
  - On successful delivery, updates `OcSyncState.last_acked_version` for the target OC.
- OC Security Gateway Framework
  - Tracks `last_applied_version` locally.
  - For `POLICY_SNAPSHOT`: replaces the local projection entirely and updates `last_applied_version`.
  - For `POLICY_DIFF`: applies the delta on top of local state; rejects and requests a re-sync if the diff's `base_version` does not match `last_applied_version`.
  - Treats every apply as idempotent per `policyVersionId`.
  - Returns an ACK (including `last_applied_version`) to the dispatcher.

This pattern decouples policy authoring (Hub) from policy enforcement (OCs), ensures at-least-once delivery, keeps incremental payloads small, and provides clear observability hooks (per-cluster status, last error, retry attempts) for identity operations.

#### 5.3.3 Linking `PolicyVersion` with the policy data model

At a high level, Hub persists policy propagation in three layers:

- `PolicyVersion` is the cluster-scoped commit marker (`version_number`, `base_version`).
- `EntityRevision` stores immutable payload snapshots per changed entity (or tombstones for deletes) and is linked to the policy version where it was introduced.
- `PolicyVersionChange` is the ordered index that links one `PolicyVersion` to the changed entity revisions in apply order.

In practice, each policy update writes a new `PolicyVersion`, writes one or more `EntityRevision` rows for changed entities, and writes matching `PolicyVersionChange` rows. Each `EntityRevision.payload` contains only the single referenced entity JSON. `EntityRevision` is tied to policy in two ways: directly via `introduced_in_policy_version`, and indirectly via `PolicyVersionChange.entity_revision_id` + `PolicyVersionChange.policy_version_id`. From these helper tables, Hub can deterministically build either a full snapshot (latest revisions up to target version) or an incremental diff (ordered changes of target version).

To keep snapshots and incremental updates consistent, `PolicyVersion` should be treated as a **cluster-scoped commit** over the unified policy model.

**How payload resources are calculated**

- **For `POLICY_SNAPSHOT` (full state at target version `V`)**
  - Start from all `EntityRevision` rows with `introduced_in_policy_version <= V` for the target cluster.
  - Group by `(entity_type, entity_id, scope_type, scope_id)`.
  - Keep only the latest revision in each group (highest `introduced_in_policy_version`, then latest sequence if needed).
  - Drop rows where the latest revision is a tombstone (`is_deleted = true`).
  - Emit the remaining payloads as the full snapshot resource set.
  - Result: exactly the effective current policy state for that OC at version `V`.

- **For `POLICY_DIFF` (increment from base `B` to target `V`)**
  - Read ordered `PolicyVersionChange` rows for `policy_version_id = V`.
  - Join each change to its `EntityRevision` via `entity_revision_id`.
  - For `UPSERT`, include the full resource payload from the joined revision.
  - For `DELETE`, include a tombstone-style change entry (entity identity + scope + operation), no full object required.
  - Preserve `sequence_no` order in the payload.
  - Result: only the resources changed in version `V`, applied on top of base version `B`.

Operational note:

- Hub chooses snapshot vs diff per target OC using `OcSyncState.last_acked_version`.
- If `last_acked_version` is `null` or incompatible with diff preconditions, Hub sends snapshot.
- Otherwise, Hub sends diff.

#### 5.3.4 Model for Policy Linking

Minimal conceptual schema:

```text
OcSyncState
  oc_id              -- stable identifier of the target OC
  cluster_id
  last_acked_version -- last PolicyVersion successfully delivered and ACKed
  last_sync_at

OutboxEvent
  id
  policy_version_id
  event_type         -- POLICY_SNAPSHOT | POLICY_DIFF
  status             -- 'PENDING' | 'DELIVERED' | 'FAILED'
  attempts
  next_attempt_at

PolicyVersion
  id
  cluster_id
  version_number
  base_version

EntityRevision
  id
  entity_type
  entity_id
  introduced_in_policy_version
  scope_type
  scope_id
  is_deleted
  payload            -- single referenced entity JSON

PolicyVersionChange
  policy_version_id
  entity_type        -- TENANT | GROUP | ROLE | MAPPING_RULE | PRINCIPAL | AUTHORIZATION
  entity_id          -- stable logical ID
  operation          -- UPSERT | DELETE
  scope_type         -- ALL | TENANT | ENGINE | TENANT_ENGINE
  scope_id           -- nullable; for TENANT_ENGINE use a composite key format
  revision_ref       -- reference to concrete entity revision payload
```

- `OutboxEvent`
  - One row per policy delivery attempt to a specific cluster.
  - `event_type` distinguishes `POLICY_SNAPSHOT` (initial / re-sync) from `POLICY_DIFF` (incremental).
  - Drives asynchronous delivery and retry without coupling Hub writes to OC availability.
- `OcSyncState`
  - One row per target OC.
  - Tracks `last_acked_version`: the last `PolicyVersion` successfully delivered and acknowledged.
  - Read by the Outbox Dispatcher to decide snapshot vs diff per OC.
  - `null` `last_acked_version` triggers a full `POLICY_SNAPSHOT` on next dispatch.
- `PolicyVersion`
  - One row per cluster and version of the desired policy.
  - Represents the canonical policy commit for a cluster.
- `PolicyVersionChange`
  - Ordered list of changed entities for one `PolicyVersion`.
  - Contains `entity_type`, `entity_id`, `operation`, and scope (`scope_type`, `scope_id`).
  - Used to build `POLICY_DIFF` payloads.
- `EntityRevision`
  - Immutable revision payload per changed entity.
  - Stores one referenced entity JSON payload per row or delete tombstones (`is_deleted`).
  - Used to reconstruct snapshots (latest non-deleted revision per entity/scope up to target version).
  - Practical persistence split:
    - Keep canonical current-state tables per entity (role/group/authorization/etc.) for normal queries and constraints.
    - Keep `EntityRevision.payload` as JSON for versioned transport, idempotent apply, and snapshot/diff reconstruction.
    - Do not require fully normalized historical tables per version.

The full policy data model is described in [5.2 Unified policy model](#52-unified-policy-model). The tables above are the outbox-relevant persistence model in Hub.


```mermaid
erDiagram
  OutboxEvent {
    UUID id PK
    UUID policy_version_id FK
    STRING event_type
    STRING status
    INT attempts
    TIMESTAMP next_attempt_at
  }

  PolicyVersion {
    UUID id PK
    STRING cluster_id
    INT version_number
    INT base_version
  }

  OcSyncState {
    STRING oc_id PK
    STRING cluster_id
    INT last_acked_version
    TIMESTAMP last_sync_at
  }

  EntityRevision {
    UUID id PK
    STRING entity_type
    STRING entity_id
    UUID introduced_in_policy_version FK
    STRING scope_type
    STRING scope_id
    BOOLEAN is_deleted
    JSON payload
  }

  PolicyVersionChange {
    UUID id PK
    UUID policy_version_id FK
    INT sequence_no
    STRING entity_type
    STRING entity_id
    STRING operation
    STRING scope_type
    STRING scope_id
    UUID entity_revision_id FK
  }

  PolicyVersion ||--o{ EntityRevision : introduces
  PolicyVersion ||--o{ PolicyVersionChange : contains
  PolicyVersion ||--o{ OutboxEvent : publishes
  OcSyncState }o--|| PolicyVersion : tracks
  EntityRevision ||--o{ PolicyVersionChange : referenced_by
```
### 5.3.5 Example database rows (Hub)

The following simplified rows show selected database rows for the same entities used in examples A/B/C in section `5.3.6`.

**OcSyncState**

| oc_id | cluster_id | last_acked_version | last_sync_at |
|---|---|---:|---|
| `oc-a` | `oc-a` | 3 | `2025-01-03T10:00:00Z` |

**OutboxEvent**

| id | policy_version_id | event_type | status | attempts | next_attempt_at |
|---|---|---|---|---:|---|
| `evt-1` | `pv-1` | `POLICY_SNAPSHOT` | `DELIVERED` | 1 | `null` |
| `evt-2` | `pv-2` | `POLICY_DIFF` | `DELIVERED` | 1 | `null` |
| `evt-3` | `pv-3` | `POLICY_DIFF` | `DELIVERED` | 1 | `null` |

**PolicyVersion**

| id | cluster_id | version_number | base_version |
|---|---|---:|---:|
| `pv-1` | `oc-a` | 1 | `null` |
| `pv-2` | `oc-a` | 2 | 1 |
| `pv-3` | `oc-a` | 3 | 2 |

**EntityRevision**

| id | entity_type | entity_id | introduced_in_policy_version | scope_type | scope_id | is_deleted | payload |
|---|---|---|---|---|---|---|---|
| `rev-tenant-default-v1` | `TENANT` | `default` | `pv-1` | `ALL` |  | `false` | `{"id":"default"}` |
| `rev-role-admin-v1` | `ROLE` | `role-cluster-admin` | `pv-1` | `ALL` |  | `false` | `{"id":"role-cluster-admin","permissions":["MANAGE_CLUSTER_SETTINGS","MANAGE_USERS"]}` |
| `rev-authz-cluster-admin-api-v1` | `AUTHORIZATION` | `authz-cluster-admin-api` | `pv-1` | `ALL` |  | `false` | `{"id":"authz-cluster-admin-api","ownerType":"ROLE","ownerId":"role-cluster-admin","resourceType":"CLUSTER_API","resourceId":"*","permissions":["MANAGE_CLUSTER_SETTINGS","MANAGE_USERS"]}` |
| `rev-role-support-agent-v2` | `ROLE` | `role-support-agent` | `pv-2` | `ALL` |  | `false` | `{"id":"role-support-agent","permissions":["READ_PROCESS_INSTANCE","READ_TASK","UPDATE_TASK"]}` |
| `rev-authz-support-task-v2` | `AUTHORIZATION` | `authz-support-task` | `pv-2` | `ALL` |  | `false` | `{"id":"authz-support-task","ownerType":"ROLE","ownerId":"role-support-agent","resourceType":"USER_TASK","resourceId":"*","permissions":["READ_TASK","UPDATE_TASK"]}` |
| `rev-authz-engine2-support-v3` | `AUTHORIZATION` | `authz-engine2-support` | `pv-3` | `ENGINE` | `engine-2` | `false` | `{"id":"authz-engine2-support","ownerType":"ROLE","ownerId":"role-support-agent","resourceType":"PROCESS_INSTANCE","resourceId":"*","permissions":["READ_PROCESS_INSTANCE"]}` |

**PolicyVersionChange**

| id | policy_version_id | sequence_no | entity_type | entity_id | operation | scope_type | scope_id | entity_revision_id |
|---|---|---:|---|---|---|---|---|---|
| `chg-1` | `pv-1` | 1 | `TENANT` | `default` | `UPSERT` | `ALL` |  | `rev-tenant-default-v1` |
| `chg-2` | `pv-1` | 2 | `ROLE` | `role-cluster-admin` | `UPSERT` | `ALL` |  | `rev-role-admin-v1` |
| `chg-3` | `pv-1` | 3 | `AUTHORIZATION` | `authz-cluster-admin-api` | `UPSERT` | `ALL` |  | `rev-authz-cluster-admin-api-v1` |
| `chg-4` | `pv-2` | 1 | `ROLE` | `role-support-agent` | `UPSERT` | `ALL` |  | `rev-role-support-agent-v2` |
| `chg-5` | `pv-2` | 2 | `AUTHORIZATION` | `authz-support-task` | `UPSERT` | `ALL` |  | `rev-authz-support-task-v2` |
| `chg-6` | `pv-3` | 1 | `AUTHORIZATION` | `authz-engine2-support` | `UPSERT` | `ENGINE` | `engine-2` | `rev-authz-engine2-support-v3` |

### 5.3.6 Example policy versions (initial + 2 updates)

The following examples show one initial policy and two incremental updates for the same cluster.

#### Example A: Initial policy (`PolicyVersion = 1`, `POLICY_SNAPSHOT`)

```json
{
  "clusterId": "oc-a",
  "policyVersion": 1,
  "kind": "POLICY_SNAPSHOT",
  "tenants": [
    {"id": "default"}
  ],
  "roles": [
    {"id": "role-cluster-admin", "permissions": ["MANAGE_CLUSTER_SETTINGS", "MANAGE_USERS"]}
  ],
  "authorizations": [
    {
      "id": "authz-cluster-admin-api",
      "ownerType": "ROLE",
      "ownerId": "role-cluster-admin",
      "resourceType": "CLUSTER_API",
      "resourceId": "*",
      "permissions": ["MANAGE_CLUSTER_SETTINGS", "MANAGE_USERS"]
    }
  ]
}
```

#### Example B: Update 1 (`PolicyVersion = 2`, `POLICY_DIFF`)

Change: add support role and support task authorization.

```json
{
  "clusterId": "oc-a",
  "policyVersion": 2,
  "baseVersion": 1,
  "kind": "POLICY_DIFF",
  "changes": [
    {
      "entityType": "ROLE",
      "entityId": "role-support-agent",
      "operation": "UPSERT",
      "scope": {
        "scopeType": "ALL"
      },
      "value": {
        "id": "role-support-agent",
        "permissions": ["READ_PROCESS_INSTANCE", "READ_TASK", "UPDATE_TASK"]
      }
    },
    {
      "entityType": "AUTHORIZATION",
      "entityId": "authz-support-task",
      "operation": "UPSERT",
      "scope": {
        "scopeType": "ALL"
      },
      "value": {
        "id": "authz-support-task",
        "ownerType": "ROLE",
        "ownerId": "role-support-agent",
        "resourceType": "USER_TASK",
        "resourceId": "*",
        "permissions": ["READ_TASK", "UPDATE_TASK"]
      }
    }
  ]
}
```

#### Example C: Update 2 (`PolicyVersion = 3`, `POLICY_DIFF`)

Change: add one engine-scoped authorization for support role on `engine-2`.

```json
{
  "clusterId": "oc-a",
  "policyVersion": 3,
  "baseVersion": 2,
  "kind": "POLICY_DIFF",
  "changes": [
    {
      "entityType": "AUTHORIZATION",
      "entityId": "authz-engine2-support",
      "operation": "UPSERT",
      "scope": {
        "scopeType": "ENGINE",
        "scopeId": "engine-2"
      },
      "value": {
        "id": "authz-engine2-support",
        "ownerType": "ROLE",
        "ownerId": "role-support-agent",
        "resourceType": "PROCESS_INSTANCE",
        "resourceId": "*",
        "permissions": ["READ_PROCESS_INSTANCE"]
      }
    }
  ]
}
```

These examples illustrate the expected apply behavior:

- OC at version `1` can apply diff `2` directly.
- OC at version `2` can apply diff `3` directly.
- OC at version `1` cannot apply diff `3` without first applying version `2` (or requesting a snapshot).

#### 5.3.7 Open Questions

Those questions can maybe also be addressed later in the project:

- If the endpoints to apply policy changes are public, hub will NOT be aware of what a customer applies to OC and will run out of sync ...
- How will the Hub know which cluster it should talk to?
- Who will initiate the communication between Hub and OCs? Does OC know about Hub?
- Should Hub or OC keep track of the last policy version applied?

### 5.4 Security Gateway Framework – hexagonal architecture

The **Security Gateway Framework** is a **hexagonal (ports and adapters)** library. Its core domain never imports a concrete database class, OIDC library, or Zeebe API — all external dependencies are hidden behind port interfaces that the host application (Hub, OC) wires in.

**Key rule: all port interfaces — both inbound and outbound — are defined inside the library core.** The host application depends on the library, never the other way around.

- **Inbound (driving) side:** A Spring MVC controller or security filter lives in the host application. It imports and calls an inbound port interface (e.g. `PolicyService`) from the library. The domain service inside the library implements that interface.
- **Outbound (driven) side:** The domain service calls an outbound port interface (e.g. `PolicyRepository`) defined in the library. The host application (or a default adapter module) provides the concrete implementation.

***WIP***: The following diagram is a work-in-progress and more an example than the actual library architecture.

```mermaid
graph LR
  subgraph EXT_IN["Inbound adapters (host application)"]
    AC["Admin REST Controller</br>@RestController, Spring MVC"]
    PEP["PEP / Security Filter</br>Spring Security Filter Chain"]
    PAC["Policy Apply Controller</br>POST /identity/policies/apply"]
  end

  subgraph CORE["Security Gateway Framework (library)"]
    subgraph IN_PORTS["Inbound port interfaces</br>(defined in Core)"]
      PS["PolicyService"]
      AZ["AuthorizationService"]
      TS["TenantService"]
      PA["PolicyApplyService"]
    end
    DL["Domain Logic</br>(implements inbound ports,</br>calls outbound ports)"]
    subgraph OUT_PORTS["Outbound port interfaces</br>(defined in Core)"]
      PR["PolicyRepository"]
      OX["OutboxPort"]
      IDP_P["IdpPort"]
      CMD_P["OcCommandPort"]
      FT_P["FeatureTogglePort"]
    end
  end

  subgraph EXT_OUT["Outbound adapter implementations (host application or default modules)"]
    PR_I["PolicyRepository</br>Hub: Spring Data JPA</br>OC: RDBMS / ES adapter"]
    OX_I["OutboxPort</br>SQL transactional outbox</br>(same TX as business change)"]
    IDP_I["IdpPort</br>OIDC/SAML client</br>(Keycloak, Entra, Auth0)"]
    CMD_I["OcCommandPort</br>Identity command adapter</br>(OC backend service layer)"]
    FT_I["FeatureTogglePort</br>Spring @ConfigurationProperties</br>or Unleash / LaunchDarkly"]
  end

  AC -->|"calls"| PS
  PEP -->|"calls"| AZ
  PAC -->|"calls"| PA

  PS & AZ & TS & PA -->|"implemented by"| DL

  DL -->|"calls"| PR & OX & IDP_P & CMD_P & FT_P

  PR -->|"implemented by"| PR_I
  OX -->|"implemented by"| OX_I
  IDP_P -->|"implemented by"| IDP_I
  CMD_P -->|"implemented by"| CMD_I
  FT_P -->|"implemented by"| FT_I
```

**Outbound port responsibilities and example implementations:**

| Port interface | Responsibility | Example implementations |
|---|---|---|
| `PolicyRepository` | Persist and query tenants, roles, mapping rules, principals, authorizations | Hub: Spring Data JPA; OC: RDBMS adapter, Elasticsearch adapter |
| `OutboxPort` | Write outbox events transactionally alongside business changes | SQL transactional outbox (same DB as policy store) |
| `IdpPort` | Resolve IdP metadata, validate tokens, fetch claim mappings | OIDC client (Keycloak, Entra, Auth0), SAML adapter |
| `OcCommandPort` | Emit identity persistence commands so OC stores the identity model through its command path | OC identity command adapter backed by engine command handling; no-op stub in Hub |
| `FeatureTogglePort` | Gate capabilities (outbox publishing, multi-tenancy, shadow-mode evaluation) | Spring `@ConfigurationProperties`, Unleash, LaunchDarkly |
| `SessionStore` | Persist and retrieve authenticated sessions (create, read, update, delete, cleanup) | Redis (distributed), SQL database, in-memory cache |

This design guarantees that **swapping a database, replacing the IdP client, or providing a custom command backend requires only a new adapter class** — no changes to the domain core.

### 5.5 Persistent sessions

Persistent sessions are required for the OC authentication UX and remain part of the unified architecture.

#### Current status

- OC already uses persistent server-side web sessions.
- Sessions are backed by existing secondary storage integrations (RDBMS or search backends).
- Session cleanup and logout invalidation are already implemented in the current authentication module.

#### Target in unified architecture

- Keep persistent sessions as the default behavior in `Security Gateway Framework`.
- Expose session persistence behind a `SessionStore` port so integrating applications can choose the backend.
- Enforce timeout-based expiry (idle and absolute timeout) and explicit logout invalidation.
- Keep session handling local per deployment:
  - In full mode, Hub and each OC manage their own sessions independently.
  - In OC-only mode, OC is the only session authority.
- Session data is not propagated via the policy outbox flow.

### 5.6 Single shared Admin UI

TODO

### 5.7 Multi-engine support

The unified identity plane supports multiple engines per Orchestration Cluster in both deployment modes:

- Full mode (Hub + OC): Hub as SoT defines cluster-scoped policies (roles, mappings, tenants, authorizations), OC projects them, and engines consume scoped views.
- OC-only mode: OC is SoT for local policies and propagates scoped views directly to engines.
- In both modes: Engines do not define their own identity models.

```mermaid
flowchart TB
  subgraph HubLayer["Hub – Policy SoT"]
    HubPolicy["Security Gateway Framework</br>(cluster-scoped policies)"]
  end

  subgraph OC1["Orchestration Cluster A"]
    OC1Id["Security Gateway Framework A</br>(OC instance)"]
    E1["Engine 1"]
    E2["Engine 2"]
  end

  subgraph OC2["Orchestration Cluster B"]
    OC2Id["Security Gateway Framework B</br>(OC instance)"]
    E3["Engine 3"]
  end

  HubPolicy -->|"Policies (outbox pattern)"| OC1Id & OC2Id

  OC1Id -->|"Engine-local role/perm view"| E1 & E2
  OC2Id -->|"Engine-local role/perm view"| E3
```

- In full mode, Hub is the single SoT; policy flows downward: Hub → all OCs → all Engines.
- OC identity instances maintain cluster-local projections and handle engine-level scoping.
- Engines consume the cluster-level projection; they do not define their own identity models and cannot override OC policy.
- In OC-only mode, the same projection model applies with local flow: OC → Engines.

### 5.8 Multi-tenancy support

Multi-tenancy is a first-class concern in the policy model. Tenant configuration is authored once in Hub and propagated top-down to OC and then to engines. Each layer maintains tenant-aware roles, mapping rules, and authorizations.

```mermaid
graph TB
  subgraph HubTenants["Hub – Tenant registry & IDP config"]
    TR["Tenant Registry</br>(e.g. tenant-a, tenant-b)"]
    IdpMap["IDP Connection Map</br>(per tenant)"]
  end

  subgraph OCView["OC – Tenant-scoped security context"]
    OC_TA["Tenant A context</br>(roles, perms, mappings)"]
    OC_TB["Tenant B context</br>(roles, perms, mappings)"]
  end

  subgraph EngineView["Engine – Tenant-scoped enforcement"]
    ENG_TA["Tenant A</br>Authorization filter active"]
    ENG_TB["Tenant B</br>Authorization filter active"]
  end

  TR --> IdpMap
  IdpMap --> OC_TA & OC_TB
  OC_TA --> ENG_TA
  OC_TB --> ENG_TB
```

On each request, the Security Gateway Framework:

1. Resolves the tenant context from token claims and/or headers.
2. Loads the tenant-specific policy view (roles, mappings, authorizations).
3. Enforces permissions within the tenant boundary, preventing cross-tenant data access.

---

## 6. Runtime view (selected scenarios)

TODO

## 7. Deployment view

TODO

## 8. Crosscutting concepts (target)

- IdP-agnostic: Any OIDC/SAML IdP integrating via standards (no IdP-specific code in the domain layer).
- RBAC + ABAC: Roles and authorizations with optional attribute-based policies (resource attributes, environment conditions).
- Multi-tenancy: Tenant-aware identity context propagated from tokens/headers; tenant-specific policy and IdP configuration; outbox filters by tenant.
- Lifecycle handling: Principal and tenant assignment are derived from IdP claims and mapping rules; clusters receive derived principals and policies from Hub.
- Observability: Identity flows emit metrics, logs, and traces (e.g. authn attempts, authz decisions, outbox propagation delay, health indicators). For Spring Security instrumentation, align with the Spring Security observability integration guidance: https://docs.spring.io/spring-security/reference/reactive/integrations/observability.html

---

## 9. Architecture decisions and open points

This unified architecture builds on existing identity arc42 docs and ADRs for OC Identity and Management Identity; those ADRs remain the canonical source for detailed trade-offs. The main new decisions here are:

- Use a shared hexagonal Security Gateway Framework with SPIs for persistence, outbox, IdP, OC commands, and (optionally) engine-level integration.
- Use Hub as policy SoT whenever present; OC-only deployments are treated as documented first-class modes, not afterthoughts.
- Ship a single shared Admin UI package, feature-gated by configuration for Hub vs OC, standalone vs Hub-managed.
- Make multi-tenancy and multi-engine support explicit in the core model and diagrams, not side effects.

Open High Level points (to be refined in separate ADRs):

- Exact SPI boundaries for OC/engine command creation.
- Migration path from current Auth0-based SaaS setup to “Enterprise IdP as SoT” while keeping Auth0 as a private implementation detail.

### 9.1 Detailed ADRs

#### ADR: Link `PolicyVersion` with policy data via versioned change sets

**Status:** Proposed

**Context:**

- Hub propagates policy updates to 1-N existing OCs.
- The primary requirement is current-state correctness, not historical reconstruction.
- New OC joins are expected to be rare and only require the current state.

**Decision (Option 1):**

- Use `PolicyVersion` as a delivery-neutral cluster-scoped commit (`version_number`, `base_version`).
- Store snapshot vs diff only on `OutboxEvent.event_type`.
- Keep stable entity IDs.
- Persist full-entity revisions (`EntityRevision`) for each change (including tombstones for deletes).
- Link ordered changes via `PolicyVersionChange`.
- Build `POLICY_DIFF` from the target version's change rows.
- Build `POLICY_SNAPSHOT` from latest non-deleted revisions per entity/scope up to a target version.

**Alternatives considered:**

- **Option 2 – Event sourcing style (full-resource events)**
  - Pros: strong audit trail and replay capabilities.
  - Cons: higher complexity and operational overhead.
  - Not chosen because replay/history is not a core requirement.

- **Option 3 – Full materialized snapshot per version**
  - Pros: simplest read/bootstrap behavior.
  - Cons: high write/storage amplification due to full duplication each version, and high network traffic when full resources are repeatedly posted to OCs.
  - Not chosen because it is unnecessary for frequent incremental updates.

**Consequences:**

- Efficient incremental propagation for the common case (existing OCs).
- Idempotent apply behavior with straightforward gap handling.
- No patch/merge ambiguity because each change carries a full resource payload.

---

### Sources

- [Unified Identity Target Architecture for Camunda Hub and Orchestration Clusters](https://docs.google.com/document/d/1ExLH2KYmz_V7Zq51adzz9c1Yk2s5ZR7ZhhIKwaEcPs0)
