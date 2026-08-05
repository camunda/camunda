# Orchestration Cluster ADRs

Architecture Decision Records scoped to the orchestration cluster — decisions that span the broker,
gateways, and the authentication/authorization layer but do not affect the entire monorepo.
Cross-cutting monorepo-wide decisions live in [`docs/adr/`](../README.md).

## Index

|                                         ADR                                          |                                    Decision                                     |
|--------------------------------------------------------------------------------------|---------------------------------------------------------------------------------|
| [0001](0001-jvm-options-argfile-for-runtime-specific-flags.md)                       | Optional `@argfile` for runtime-specific JVM flags without rebuilding images    |
| [0002](0002-jdk-25-base-images-with-jdk-21-runtime-support.md)                       | JRE 25 Docker base images while retaining JRE 21 runtime support                |
| [0003](0003-physical-tenant-request-scoping-via-pre-security-filter.md)              | Single pre-security filter for physical-tenant request scoping                  |
| [0004](0004-per-physical-tenant-provider-selection-via-assigned.md)                  | Per-physical-tenant provider selection via OC-side `assigned` narrowing         |
| [0005](0005-physical-tenant-routing-of-authorization-reads.md)                       | Physical-tenant routing of the authorization layer                              |
| [0006](0006-physical-tenant-scoped-grpc-authentication.md)                           | Physical-tenant-scoped gRPC authentication                                      |
| [0007](0007-physical-tenant-configuration-resolution-and-validation.md)              | Shared-config-with-override resolution and per-PT / cross-PT validation split   |
| [0008](0008-physical-tenant-exporter-assignment-and-args-merge.md)                   | Explicit exporter assignment and opt-in type-aware args merge                   |
| [0009](0009-propagating-physical-tenant-context-across-async-authorization-reads.md) | Propagating physical-tenant context across async authorization reads (Proposed) |

