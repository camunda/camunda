# zeebe/cluster-gossip

A transport-neutral API for sharing small, eventually consistent, in-memory state across all
cluster members (brokers and gateways). Keys are either GLOBAL — one merged value cluster-wide,
writable by any member — or MEMBER — one value per member, written only by that member. See
[ADR 0001](docs/adr/0001-cluster-gossiper-api.md) for the contract and the reasoning behind it.

## Modules

|   Module   |                                                                                     Purpose                                                                                      |
|------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `api/`     | `ClusterGossiper`, key descriptors, codecs, merge strategies and listeners. Depends only on `camunda-cluster`.                                                                   |
| `testkit/` | `InMemoryGossipCluster` (in-memory members with explicit sync, partitions, restarts), `MergeStrategyAssertions`, and the contract tests every implementation runs (`contract/`). |
| `swim/`    | `SwimClusterGossiper`: MEMBER keys as SWIM member properties. GLOBAL keys are not supported yet.                                                                                 |

Consumers depend on `api/`, and on `testkit/` in test scope. An implementation proves it meets the
contract by extending `ClusterGossiperContractTest` with its own cluster. `swim/` supports MEMBER keys
only; its GLOBAL operations throw `UnsupportedOperationException`.
