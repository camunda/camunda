# zeebe/cluster-gossip

A transport-neutral API for sharing small, eventually consistent, in-memory state across all
cluster members (brokers and gateways). Keys are either GLOBAL — one merged value cluster-wide,
writable by any member — or MEMBER — one value per member, written only by that member. See
[ADR 0001](docs/adr/0001-cluster-gossiper-api.md) for the contract and the reasoning behind it.

## Modules

|   Module   |                                                     Purpose                                                      |
|------------|------------------------------------------------------------------------------------------------------------------|
| `api/`     | `ClusterGossiper`, key descriptors, codecs, merge strategies and listeners. Depends only on `camunda-cluster`.   |

Consumers depend on `api/` only. There is no production implementation yet; the transport is still
to be decided (ADR 0001, D2).
