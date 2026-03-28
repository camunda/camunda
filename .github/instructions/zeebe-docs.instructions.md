```yaml
---
applyTo: "zeebe/docs/**"
---
```
# Zeebe Developer Documentation

## Module Purpose

This directory contains **internal developer documentation** for the Zeebe process engine — the core distributed runtime of Camunda 8. It covers engine internals, distributed systems concepts, operational guidance, and contributor workflows. The `zeebe/docs/.htaccess` file is a legacy redirect to external Camunda docs; the actual developer docs live under `docs/zeebe/`.

Note: `zeebe/docs/` contains only `.htaccess` (a redirect stub). The substantive Zeebe developer docs are at `docs/zeebe/` in the repository root.

## Document Inventory and Organization

| File | Topic |
|------|-------|
| `docs/zeebe/developer_handbook.md` | Contributor how-tos: extending gRPC protocol, creating/extending records, SBE protocol, exporter support, authorization checks, inter-partition communication, REST endpoints |
| `docs/zeebe/batch-operation.md` | Batch operations architecture: two-phase init/execute, leader/follower partitions, RocksDB storage, ADRs |
| `docs/zeebe/generalized_distribution.md` | Generic command distribution across partitions: STARTED→DISTRIBUTING→ACKNOWLEDGE→FINISHED flow, ordered queues, retry/backoff |
| `docs/zeebe/dynamic_scaling.md` | Beta feature: adding partitions post-creation, redistribution phase, snapshot bootstrapping |
| `docs/zeebe/backpressure.md` | Request rate limiting: algorithms (vegas, aimd, fixed, gradient), whitelisting, `CommandRateLimiter` |
| `docs/zeebe/events.md` | BPMN event semantics: triggers, scopes, event scope instances, interrupting vs non-interrupting |
| `docs/zeebe/messaging.md` | Netty-based inter-cluster messaging: protocol handshake (V1/V2), message framing, exception types |
| `docs/zeebe/rolling_updates.md` | Compatibility guidelines for rolling updates: network protocol, SBE versioning, event behavior immutability, data migration avoidance |
| `docs/zeebe/failing-tests.md` | Reproducing/debugging flaky tests: local reproduction, CI isolation, remote JVM debugging via k8s port-forward |
| `docs/zeebe/engine_questions.md` | FAQ: tokens as activated elements, element instance cardinality, variable scoping, deployment distribution |
| `docs/zeebe/building_docker_images.md` | Building native Docker images for ARM64/AMD64 with BuildKit |
| `docs/zeebe/parallel_gateway.md` | Minimal reference diagram for joining parallel gateway behavior |
| `docs/zeebe/assets/` | BPMN diagrams (`.bpmn`) and rendered PNGs referenced by the docs above |

## Key Concepts Documented

### Event Sourcing and Record Protocol
The developer handbook (`developer_handbook.md`) is the primary reference for extending the engine's record protocol. Follow these steps when creating a new record type:
1. Add `<validValue>` to `ValueType` in `zeebe/protocol/src/main/resources/protocol.xml`
2. Create `Intent` enum implementing `zeebe/protocol/.../Intent.java`
3. Create `RecordValue` interface extending `zeebe/protocol/.../RecordValue.java`
4. Implement in `zeebe/protocol-impl/.../record/value/`
5. Add exporter support (ES + OS templates, configuration, test support)
6. Add to `Engine.java` supported types to ensure processing and replay

### Inter-Partition Communication
Two docs cover this: `generalized_distribution.md` for the generic distribution framework (`CommandDistributionBehavior`, `InterPartitionCommandSender`) and `batch-operation.md` for a concrete application using leader/follower partition coordination. Key invariant: **all inter-partition command processors must be idempotent** — reject redundant commands rather than re-applying events.

### Rolling Update Compatibility
`rolling_updates.md` establishes critical rules: never modify released `EventApplier` behavior (introduce new events or versioned appliers instead), use SBE message versioning carefully per `protocol.xml` and `revapi.json`, and minimize data migrations to avoid performance impact on large state stores.

## Writing and Editing Guidelines

- Reference source files using repo-root-relative paths with leading `/` (e.g., `/zeebe/engine/src/main/java/.../Engine.java`). Follow the convention in `developer_handbook.md`.
- Place diagrams in `docs/zeebe/assets/` as both `.bpmn` source and rendered `.png`. Reference PNGs via relative paths (e.g., `assets/batch-operation.png`).
- Use Mermaid diagrams inline for sequence flows where appropriate (see `batch-operation.md` and `dynamic_scaling.md` for examples).
- Structure operational docs with sections: Introduction/Goals, Technical Context, Sequence Flow, Records/Intents, State Storage, and Architecture Decisions (ADR format with Rationale/Consequences).
- Use GitHub-flavored Markdown alerts (`> [!NOTE]`, `> [!WARNING]`, `> [!TIP]`, `> [!IMPORTANT]`) for callouts — see `generalized_distribution.md` and `dynamic_scaling.md`.
- Keep FAQ-style docs (like `engine_questions.md`) with bold `**A:**` answer markers.

## Cross-References to Maintain

When editing these docs, verify that referenced source files still exist:
- `developer_handbook.md` references ~20 source files across `zeebe/protocol`, `zeebe/protocol-impl`, `zeebe/exporters/`, `zeebe/broker`, and `zeebe/test-util`
- `generalized_distribution.md` references `CommandDistributionBehavior`, `InterPartitionCommandSender`, `DistributedTypedRecordProcessor`
- `messaging.md` references Atomix messaging classes in `zeebe/atomix/cluster/src/main/java/io/atomix/cluster/messaging/impl/`
- `rolling_updates.md` references `protocol.xml`, `revapi.json`, and links to `messaging.md`
- `dynamic_scaling.md` references `ClusterConfigurationManagementApi` and `ConfigurationChangeCoordinator` in `zeebe/dynamic-config/`
- `failing-tests.md` references CI workflow `ci-zeebe.yml`

## Common Pitfalls

- Do not confuse `zeebe/docs/` (contains only `.htaccess` redirect) with `docs/zeebe/` (actual developer documentation). All substantive content lives under `docs/zeebe/`.
- Do not edit `zeebe/docs/.htaccess` — it is a legacy redirect to `docs.camunda.io` and serves no development purpose.
- When documenting new engine features, always update `developer_handbook.md` if the feature involves new record types, intents, or protocol changes.
- When documenting new distributed features, reference and link to `generalized_distribution.md` for the underlying distribution mechanism rather than re-explaining it.
- Keep ADR sections (in `batch-operation.md`) immutable once decisions are made — add new ADRs rather than modifying existing ones.

## Essential Reference Files

- `docs/zeebe/developer_handbook.md` — Primary contributor guide for engine protocol extension
- `docs/zeebe/generalized_distribution.md` — Command distribution framework documentation
- `docs/zeebe/rolling_updates.md` — Compatibility rules for version upgrades
- `docs/zeebe/batch-operation.md` — Most comprehensive architectural doc with ADRs, sequence diagrams, and state management details
- `docs/zeebe/events.md` — BPMN event scope semantics critical for understanding engine behavior