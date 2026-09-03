# Atomix Cluster ADRs

Architecture Decision Records scoped to `zeebe/atomix/cluster` — cluster membership (SWIM), the
messaging and unicast transports, node discovery, and cluster communication. These are module-scoped
decisions; see the [top-level ADR README](../../../../../docs/adr/README.md) for the tier structure
and cross-cutting ADRs.

## Index

|                        ADR                        |                                                                                  Decision                                                                                   |
|---------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [0001](0001-tcp-transport-for-cluster-unicast.md) | No-UDP mode: carry unicast over the TCP messaging transport under a prefixed subject namespace, routed by a stateless composite that receives on both transports (Proposed) |

