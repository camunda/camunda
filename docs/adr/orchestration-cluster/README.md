# Orchestration Cluster ADRs

Architecture Decision Records scoped to the orchestration cluster — decisions that span the broker,
gateways, and the authentication/authorization layer but do not affect the entire monorepo.
Cross-cutting monorepo-wide decisions live in [`docs/adr/`](/adr).

## Index

|                                                   ADR                                                   |                                    Decision                                     |
|---------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------|
| [0001](/adr/orchestration-cluster/jvm-options-argfile-for-runtime-specific-flags)                       | Optional `@argfile` for runtime-specific JVM flags without rebuilding images    |
| [0002](/adr/orchestration-cluster/jdk-25-base-images-with-jdk-21-runtime-support)                       | JRE 25 Docker base images while retaining JRE 21 runtime support                |
| [0003](/adr/orchestration-cluster/physical-tenant-request-scoping-via-pre-security-filter)              | Single pre-security filter for physical-tenant request scoping                  |
| [0004](/adr/orchestration-cluster/per-physical-tenant-provider-selection-via-assigned)                  | Per-physical-tenant provider selection via OC-side `assigned` narrowing         |
| [0005](/adr/orchestration-cluster/physical-tenant-routing-of-authorization-reads)                       | Physical-tenant routing of the authorization layer                              |
| [0006](/adr/orchestration-cluster/physical-tenant-scoped-grpc-authentication)                           | Physical-tenant-scoped gRPC authentication                                      |
| [0007](/adr/orchestration-cluster/physical-tenant-configuration-resolution-and-validation)              | Shared-config-with-override resolution and per-PT / cross-PT validation split   |
| [0008](/adr/orchestration-cluster/physical-tenant-exporter-assignment-and-args-merge)                   | Explicit exporter assignment and opt-in type-aware args merge                   |
| [0009](/adr/orchestration-cluster/propagating-physical-tenant-context-across-async-authorization-reads) | Propagating physical-tenant context across async authorization reads (Proposed) |

