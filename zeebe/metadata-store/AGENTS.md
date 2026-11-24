# zeebe/metadata-store — Module Map

A namespaced key-value store for cluster metadata, backed by the system partition. Tracking issue:
[#70502](https://github.com/camunda/camunda/issues/70502).

## Modules

`api` owns `io.camunda.zeebe.metadata.store.api` and `impl` owns
`io.camunda.zeebe.metadata.store.impl`, each with its subpackages. The two never share a package.

- `api/` (`zeebe-metadata-store-api`) — the contract consumers compile against: the
  `ClusterMetadataStore` and `NamespaceApi` interfaces, value types (`Namespace`, `StoreKey`,
  `KeyValue`, events, `Subscription`), `Write` and `Condition`, the
  `ClusterMetadataStoreException` hierarchy, and validation that needs no store state (key and
  namespace names, batch rules). Every call returns an `ActorFuture`.
- `impl/` (`zeebe-metadata-store-impl`) — behavior: the state, the Raft-backed store, and
  everything else that reads or writes stored data.

A new type belongs in `api/` when code outside the module must reference it; otherwise it goes in
`impl/`. Keep `api/` free of Raft, broker, and `impl/` dependencies so consumers can build against
it alone. Consumers depend on `api/`; only composition roots (the broker, tests) depend on
`impl/` and construct the store.
