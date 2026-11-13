# Commit state to memory and write it to RocksDB in the background

**DRI**: Carlo Sana

**Status**: Accepted (8.11)

**Purpose**: Defines how the stream processor commits state without a RocksDB write for each
command, and which durability guarantee this costs.

**Audience**: Zeebe engineers who work on `zb-db`, the engine state or the broker, and AI agents
that reason about state durability.

## Context

The stream processor commits one RocksDB transaction for each command. Most of the state is
short-lived: a job, an element instance or a timer is created and deleted again within seconds.
RocksDB already runs without a WAL in Zeebe, so runtime state is always rebuilt from the last
snapshot plus log replay. We want to skip the RocksDB writes for state that dies young.

## Decision

- **D1. Commit to memory and flush in the background.** A commit publishes its changes to an
  in-memory batch and returns. One writer thread for each DB seals the batch and writes it to
  RocksDB as one atomic write. A flush holds whole transactions only, so RocksDB always holds a
  transaction-consistent prefix. A key that is written and deleted inside one flush window never
  reaches RocksDB.
- **D2. Accept that unflushed commits exist only on the heap.** A crash loses them. Recovery
  rebuilds them from snapshot plus log, as it does for RocksDB without a WAL. A snapshot should
  wait for pending flushes or trigger one. This ADR does not require it, and the implementation
  does it in `createSnapshot`. Commits block when the pending bytes exceed `max-pending`. A dead
  writer fails the store for good.
- **D3. Wrap RocksDB through a raw-access SPI.** `zb-db` splits into `api`, `rocksdb` and
  `writebehind`. `RawAccessibleDb` exposes committed bytes. `WriteBehindZeebeDb` wraps only the
  runtime DB. Snapshot restore and bulk copies stay on plain RocksDB, because a bulk copy is one
  huge transaction.
- **D4. Hold committed values as encoded bytes.** The batches and the read cache hold the encoded
  value. A reader wraps the bytes into its own instance. When `DbValue` gets a cheaper way to copy
  an object, the store holds the Java object instead and encodes it only when the writer writes it
  to RocksDB. This removes the encode and the parse from the commit and read paths.
- **D5. Serve reads in layers.** The order is: transaction writes, active and sealed batch as of a
  commit sequence, read cache, RocksDB. The cache holds only values that exist. A lookup of an
  absent key is not cached (see ADR 2).
- **D6. Scans read the unflushed batches lazily as of one commit.** Nothing is copied when a scan
  opens.
- **D7. Opt-in and off by default.** The broker enables it through unified configuration.

## Alternatives considered

- **RocksDB tuning only.** The WAL is already off. Bigger memtables need more memory, and they do
  not remove the writes or the other costs of each commit.
- **A cache in front of RocksDB with synchronous writes.** This is close to the chosen design. We
  also need values to stay cached between transactions for a short time. This matters for
  job-driven processes. The chosen design gives this and also removes the writes.

## Consequences

- Up to `flush-delay` or `max-pending` of commits exists only on the heap.
- Memory use grows by about 96 MB for each partition with the defaults, on top of the RocksDB
  limit.
- A dead writer stops the partition until the DB reopens.
- Each put encodes the value once, and each read parses it once, until D4 changes.
- A change to the write, read or flush path needs an update of the module `README.md`.

## Source

[`README.md`](../../README.md) of this module; PRs #69232, #69234, #69247, #69254, #69326.
