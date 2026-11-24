# zeebe/metadata-store — Module Map

A namespaced key-value store for cluster metadata, backed by the system partition. Tracking issue:
[#70502](https://github.com/camunda/camunda/issues/70502).

## Modules

Each module owns one package and its subpackages, and no two modules share one: `api` owns
`io.camunda.zeebe.metadata.store.api` and `impl` owns `io.camunda.zeebe.metadata.store.impl`. PIT
mutates each module's own package only.

- `api/` (`zeebe-metadata-store-api`) — the contract consumers compile against: the
  `ClusterMetadataStore` and `NamespaceApi` interfaces, value types (`Namespace`,
  `NamespaceScope`, `StoreKey`, `StoreValue`, `KeyValue`, `KeyEvent`, `NamespaceChange`),
  `WatchListener` and `Subscription`, `Write`, `Batch` and `Condition`, the
  `ClusterMetadataStoreException` hierarchy, and validation that needs no store state: a key,
  namespace name or batch that breaks a rule cannot be built. Every call except the two watches
  returns an `ActorFuture`; a store implements `batch`, and the single writes are batches of one.
- `impl/` (`zeebe-metadata-store-impl`) — the parts every backing store shares, with no actor of
  its own:
  - `state` — the immutable `StoreState`, `KeyPath` (prefix ranges on plain canonical keys), the
    `Entry` a write commits, and `EntryApplier`, the pure function that applies an entry and
    reports its changes.
  - `write` — `WriteEvaluator`, which turns a batch into an entry, or no entry, or an error.

A new type belongs in `api/` when code outside the module must reference it; otherwise it goes in
`impl/` if every store uses it, or in the module of the one store that does. Keep `api/` free of
Raft, broker, and `impl/` dependencies so consumers can build against it alone. Consumers depend on
`api/`; only composition roots (the broker, tests) depend on a store module and construct the store.
