# ADR-0002: Placement of the Security Gateway Framework (embedded vs standalone service)

## Status

Proposed

## Context

The unified identity architecture introduces a shared Security Gateway Framework (SGF) that implements authentication and authorization for both Hub and Orchestration Clusters (OCs).

The core question for this ADR is:

> Where should this identity and policy engine live at runtime?

Concretely, we are deciding between:

- Embedding the SGF as a shared library in Hub and OC (current proposal), or
- Running one or more dedicated identity services that Hub/OCs connect to over the network.

This decision is closely related to:

- Our desire to make Hub the identity control plane while keeping policy enforcement local to each cluster.
- Our experience with Management Identity as a separate service (for Web Modeler / Console / Optimize) and the operational overhead that comes with additional services.
- The need to support multi‑engine and multi‑tenant scenarios without fragmenting policy.

## Decision

We will:

- Embed the Security Gateway Framework as a library into both Hub and each Orchestration Cluster, and
- Not introduce a separate, always‑on identity microservice (either per cluster or globally) for unified identity.

In other words, we adopt Option 1 (embedded SGF in Hub + OC) and explicitly reject the alternatives described below.

## Options considered

### Option 1 – Embedded Security Gateway Framework in Hub + OC (chosen)

- Shape:
  - SGF is a shared library.
  - Hub embeds one instance; each OC embeds one instance.
  - Engines integrate via a narrow Security Engine Framework; they never talk to IdPs directly.
- Characteristics:
  - Hub:
    - Acts as policy SoT (tenants, roles, mapping rules, authorizations) per cluster.
    - Exposes Admin UI and APIs for policy authoring.
    - Integrates with existing persistence, outbox propagation, and IdP connections.
  - OC:
    - Hosts a cluster‑local policy projection and runtime enforcement.
    - Exposes Admin UI (read‑only in full mode, read/write in OC‑only mode).
    - Uses SGF for IdP integration, sessions, multi‑tenancy, and authorization checks for UIs/APIs.
  - Engines:
    - Consume scoped policy projections.
    - Do not embed IdP clients or full policy logic.

This is the architecture described in the unified identity document (sections 4–5).

### Option 2 – Standalone identity service per cluster

- Shape:
  - Run a separate Identity Service per OC (or per environment).
  - Hub, Operate, Tasklist, Admin, workers, etc. call this service via HTTP/gRPC for authN/authZ.
  - The service owns IdP clients, policy state, and exposes decision APIs.
- Pros:
  - Clear operational boundary: identity could be scaled and rolled out independently from Hub/OC.
  - Language‑agnostic: any client that can speak HTTP/gRPC can ask for decisions.
  - Familiar to teams used to “central IAM service per cluster” patterns.
- Cons:
  - Additional service to operate:
    - More deployments, monitoring, on‑call, SLAs.
    - We explicitly want to avoid adding more bespoke services to run/manage when we can keep logic embedded.
  - Every authZ check becomes a network call to the identity service:
    - Latency and availability of that service directly affect all cluster operations.
    - Increases the blast radius of outages in the identity service.
  - We still need some client/library for policy types and enforcement behavior on the caller side, so we do not fully avoid coupling.
  - For self‑managed, this introduces yet another system customers must deploy and operate correctly.

### Option 3 – Single global identity service for Hub + all clusters (similar to Management Identity)

- Shape:
  - One global identity service (similar in spirit to today’s Management Identity):
    - Manages users, IdP connections, and policy centrally.
    - Hub and OCs call it synchronously for authentication/authorization.
  - No per‑cluster SGF; everything goes through the central service.
- Pros:
  - Maximum centralization:
    - Single place for identity logs, audit, and configuration.
  - Reuses the Management Identity mental model (dedicated identity service).
- Cons:
  - Operationally and architecturally similar to Management Identity, which we are trying to move away from:
    - Another central service to deploy, scale, and maintain.
    - Adds a hard dependency between every cluster and a shared control‑plane service.
  - Conflicts with our goal that policy enforcement remains local, especially for runtime decisions on OCs:
    - High latency or outages in the central service would impact all clusters.
    - Poor fit for disconnected / self‑managed deployments where OCs should remain operable without constant control‑plane connectivity.
  - Creates a single, large blast radius for identity failures across Hub and all clusters.

Given our experience with Management Identity, we explicitly want fewer standalone services, not more, especially for foundational concerns like identity.

## Decision outcomes

### Why Option 1 is preferred

We choose embedded SGF in both Hub and OC because it:

- Keeps policy enforcement local to each runtime boundary:
  - Hub: for management-plane UIs and APIs.
  - OC: for execution-plane UIs and APIs.
- Avoids introducing new standalone identity services:
  - No extra microservice per cluster (Option 2).
  - No global, central identity service for all clusters (Option 3).
- Reuses the same domain model and library across Hub and OCs:
  - One conceptual model (tenants, roles, groups, mapping rules, authorizations).
  - Different adapters per runtime (Hub vs OC vs engine).
- Respects self‑managed and disconnected deployment needs:
  - Each OC can keep making identity decisions as long as its own SGF instance and DB are healthy.
  - Hub can be offline without breaking runtime authZ on clusters (after last successful policy sync).

### Consequences

- We must maintain SGF as a library with clear interfaces.
- Hub and OC will each:
  - Have their own SGF configuration and adapter implementations.
  - Own their local identity/session/policy state and observability.
- We do not introduce:
  - A separate Identity microservice per cluster.
  - A single global Identity service for all clusters and Hub.
- Migration from Management Identity will converge towards:
  - Hub using embedded SGF for management-plane identity instead of an external Management Identity service.
  - OCs using embedded SGF for cluster identity, as described in the unified identity architecture.
