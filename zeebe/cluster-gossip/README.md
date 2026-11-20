# zeebe/cluster-gossip

A transport-neutral API for sharing small, eventually consistent, in-memory state across all
cluster members (brokers and gateways). Keys are either GLOBAL — one merged value cluster-wide,
writable by any member — or MEMBER — one value per member, written only by that member. See
[ADR 0001](docs/adr/0001-cluster-gossiper-api.md) for the contract and the reasoning behind it.

## Modules

|   Module   |                                                       Purpose                                                       |
|------------|---------------------------------------------------------------------------------------------------------------------|
| `api/`     | `ClusterGossiper`, key descriptors, codecs, merge strategies and listeners. Depends only on `camunda-cluster`.      |
| `testkit/` | `InMemoryGossipCluster` (in-memory members with explicit sync, partitions, restarts) and `MergeStrategyAssertions`. |

Consumers depend on `api/`, and on `testkit/` in test scope. There is no production implementation
yet; the transport is still to be decided (ADR 0001, D2).

`poc/` holds two proof-of-concept implementations, one over SWIM member properties and one with its
own transport, plus a showcase consumer in its tests. They exist to choose the transport and are not
used in production; see the [comparison](docs/poc-transport-comparison.md).
