# Keep a bounded key index for each column family in the write-behind DB

**DRI**: Carlo Sana

**Status**: Accepted (8.11)

**Purpose**: Defines how the write-behind DB answers lookups and scans for keys that do not exist
without a call to RocksDB.

**Audience**: Zeebe engineers who work on `zb-db` or the engine state, and AI agents that reason
about state lookups.

## Context

Many engine lookups find nothing. One example is the lookup when an element ends. A RocksDB `get`
or iterator seek for an absent key is costly. A cache of absent keys uses heap for each key and
only fills the cache. [ADR 1](0001-811-write-behind-zeebe-db.md) therefore does not cache absent
keys.

## Decision

- **D1. Hold keys only, for each column family.** The index never holds values. It answers that a
  key is absent and gives the scan start for the window it covers.
- **D2. Bound the index by key count, with a policy for each column family.** `KEEP_LOWEST` suits
  due-date column families, which scan from the low end. `KEEP_HIGHEST` suits growing keys, where
  recent keys are the hot ones. `DISABLE` drops the index. `ColumnFamilyAccessPatterns` holds the
  defaults.
- **D3. Load lazily and maintain on commit.** The first lookup or scan loads the index on the
  calling thread. Each commit updates it. A windowed index refills when it falls below a quarter
  of its maximum. If commits overtake a load or refill three times in a row, the index stays
  disabled until restart.
- **D4. Trust the index only when it is consistent for the reader.** It must be loaded, not in
  change, and not changed by a commit newer than the commit sequence of the reader.

## Alternatives considered

- **A cache of absent keys.** It costs heap for each key and is not bounded without extra work.
- **Bloom filters.** They are smaller than an index. They cannot drive scans. We plan them as a
  later addition. Plain RocksDB is the other fallback.

## Consequences

- A key-index load runs on the stream processor thread, so the first access to a column family is
  slow.
- Memory grows with `key-index-max-keys` for each indexed column family.
- A column family without an index calls RocksDB for each absent key until a filter exists.

## Source

[`README.md`](../../README.md) of this module (Key index); PR #69254.
