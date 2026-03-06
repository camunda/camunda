```yaml
---
applyTo: "zeebe/zb-db/src/**"
---
```
# Zeebe DB — RocksDB Storage Abstraction Layer

## Module Purpose

`zeebe/zb-db` provides a type-safe key-value storage abstraction over RocksDB for the Zeebe process engine. It implements "virtual column families" by prefixing all keys with a `long` column family identifier within a single physical RocksDB column family, enabling type-safe per-column-family access with `DbKey`/`DbValue` serialization, optimistic transactions, prefix-based iteration, foreign key consistency checks, and snapshot/checkpoint support. This is the sole state storage backend for the Zeebe engine's event-sourced processing.

## Architecture

```
io.camunda.zeebe.db (public API interfaces)
├── ZeebeDb<CFType>              — Database handle; creates ColumnFamily instances
├── ZeebeDbFactory<CFType>       — Factory to open/create databases
├── ColumnFamily<K,V>            — Type-safe CRUD + iteration on a virtual column family
├── DbKey / DbValue              — Serialization contracts (extend BufferReader + BufferWriter)
├── TransactionContext            — Runs operations within a nestable transaction
├── ZeebeDbTransaction            — Manual commit/rollback control
└── ConsistencyChecksSettings     — Toggle precondition and foreign key checks

io.camunda.zeebe.db.impl (built-in key/value types)
├── DbLong, DbInt, DbShort, DbByte, DbString, DbBytes, DbNil
├── DbCompositeKey<A,B>           — Concatenates two keys for compound lookups
├── DbForeignKey<K>               — Wraps a key referencing another column family
├── DbTenantAwareKey<K>           — Prepends or appends tenant ID to a key
├── DbEnumValue<T>                — Stores enums in a single byte
└── DefaultColumnFamily           — Single-entry enum for tests

io.camunda.zeebe.db.impl.rocksdb (RocksDB implementation)
├── ZeebeRocksDbFactory           — Configures and opens OptimisticTransactionDB
├── RocksDbConfiguration          — Memory, WAL, compaction, SST partitioning settings
├── SnapshotOnlyDb                — Read-only DB for snapshot copy operations
├── ChecksumProviderRocksDBImpl   — CRC32C checksums from SST file metadata
└── transaction/
    ├── ZeebeTransactionDb        — Core ZeebeDb impl; wraps OptimisticTransactionDB
    ├── TransactionalColumnFamily  — ColumnFamily impl with transaction + prefix iteration
    ├── ColumnFamilyContext        — Key/value buffer management, CF prefix serialization
    ├── ZeebeTransaction           — Transaction wrapper with MethodHandle-based JNI calls
    ├── DefaultTransactionContext  — Nestable transaction lifecycle (reset → run → commit/rollback)
    ├── ForeignKeyChecker          — Validates foreign key references across column families
    ├── RocksDbInternal            — Reflective access to RocksDB native handles
    └── RawTransactionalColumnFamily — Low-level byte-array iteration for snapshot copy
```

## Key Design Decisions

### Virtual Column Families
All data lives in a single RocksDB column family. Each logical column family is identified by a `long` prefix (from `EnumValue.getValue()`) prepended to every key via `ColumnFamilyContext.writeKey()`. Use `BIG_ENDIAN` byte order (`ZeebeDbConstants.ZB_DB_BYTE_ORDER`) to preserve ascending sort order in RocksDB. The `FixedPrefix` extractor of `Long.BYTES` enables efficient prefix-based seeks and bloom filters.

### Optimistic Transactions
Uses `OptimisticTransactionDB` — no locks on writes; conflicts detected at commit. Transactions are reused via `TransactionRenovator.renewTransaction()` to avoid allocation overhead. Nesting is supported: `DefaultTransactionContext.runInTransaction()` reuses the current transaction if one is open; otherwise starts a new one and auto-commits on success.

### Consistency Checks
Controlled by `ConsistencyChecksSettings`: `enablePreconditions` guards `insert` (key must not exist) and `deleteExisting`/`update` (key must exist); `enableForeignKeyChecks` verifies referenced keys via `ForeignKeyChecker`. Both are **disabled by default** in production and **enabled in tests** via `DefaultZeebeDbFactory`.

### JNI Performance Optimization
`RocksDbInternal` uses `MethodHandle` to call private native methods on `Transaction` (put/get/delete) directly via native handles, bypassing Java wrapper overhead. This is critical for hot-path performance.

## Data Flow

1. Engine defines column families via `ZbColumnFamilies` enum (in `zeebe/protocol`)
2. `ZeebeRocksDbFactory.createDb()` opens RocksDB with tuned options → returns `ZeebeTransactionDb`
3. Engine state classes call `zeebeDb.createColumnFamily(CF_ENUM, context, keyInstance, valueInstance)`
4. All CRUD goes through `TransactionalColumnFamily` → `ColumnFamilyContext` serializes CF prefix + key → `ZeebeTransaction` invokes native RocksDB calls
5. Iteration uses `RocksIterator` with `PrefixReadOptions` (prefix-same-as-start), validating each key starts with the CF prefix bytes

## Extension Points

### Adding a New Key/Value Type
Implement `DbKey` and/or `DbValue` in `io.camunda.zeebe.db.impl`. Implement `BufferReader.wrap()` for deserialization and `BufferWriter.write()`/`getLength()` for serialization. Use `ZB_DB_BYTE_ORDER` (BIG_ENDIAN) for all numeric writes to preserve sort order. If the type contains foreign key references, implement `ContainsForeignKeys`.

### Adding a New Column Family
Add an entry to the `ZbColumnFamilies` enum in `zeebe/protocol` with a unique `int` value. Never reuse or change existing values — the integer is the physical prefix in stored data.

## Invariants

- Every key is prefixed with `Long.BYTES` of the column family's `EnumValue.getValue()` in `BIG_ENDIAN` order — never write keys without this prefix
- All column family operations must run inside a transaction — `TransactionalColumnFamily` enforces this via `ensureInOpenTransaction()`
- `DbKey`/`DbValue` instances passed to `createColumnFamily` are mutable singletons reused across reads — never store references to returned values from `get()` or iteration; copy data immediately
- WAL is disabled by default (`RocksDbConfiguration.DEFAULT_WAL_DISABLED = true`) — safe because Zeebe uses a single column family and recovers from snapshots
- Resources must be closed in reverse order: transaction → options → column family handles → database → db options → column family options (see `ZeebeTransactionDb.close()`)
- `ZeebeDbException` wraps recoverable RocksDB errors; `ZeebeDbInconsistentException` is unrecoverable (consistency violation)
- SST partitioning by column family prefix is enabled by default to improve compaction on large state

## Common Pitfalls

- **Storing iteration references**: `get()` and iterators write into the shared `keyInstance`/`valueInstance` — copy values before the next operation
- **Byte order**: Using `LITTLE_ENDIAN` breaks key ordering in RocksDB. Always use `ZeebeDbConstants.ZB_DB_BYTE_ORDER`
- **Nested prefix iteration**: Only 2 levels of prefix iteration nesting are supported (2 prefix key buffers in `ColumnFamilyContext`)
- **Foreign key match types**: `DbForeignKey.MatchType.Full` checks exact key existence; `Prefix` does a prefix scan — use `Prefix` when the referenced column family has composite keys
- **DbTenantAwareKey placement**: `PREFIX` sorts by tenant first (can cause preferential ordering); `SUFFIX` sorts by wrapped key first — choose based on iteration access pattern

## Key Reference Files

- `src/main/java/io/camunda/zeebe/db/ZeebeDb.java` — Core database interface
- `src/main/java/io/camunda/zeebe/db/ColumnFamily.java` — Column family CRUD and iteration API
- `src/main/java/io/camunda/zeebe/db/impl/rocksdb/transaction/ZeebeTransactionDb.java` — Primary RocksDB implementation
- `src/main/java/io/camunda/zeebe/db/impl/rocksdb/transaction/TransactionalColumnFamily.java` — Column family operations with transaction enforcement and prefix iteration
- `src/main/java/io/camunda/zeebe/db/impl/rocksdb/ZeebeRocksDbFactory.java` — Factory with memory budget calculation and RocksDB tuning
- `src/main/java/io/camunda/zeebe/db/impl/DbCompositeKey.java` — Compound key composition pattern
- `src/test/java/io/camunda/zeebe/db/impl/DefaultZeebeDbFactory.java` — Test factory with consistency checks enabled