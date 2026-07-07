# ADR 0008 — Byte-backed dimension keys: the key is its serialized form

- Status: Accepted
- Date: 2026-07-07
- Scope: `analytics/analytics-model` (DimensionKey/DimensionKeySelector/DimensionKeyValue, Fact,
  MeasureRef), `analytics/analytics-engine` (projection state interfaces, derivers, sinks),
  `analytics/analytics-pipeline` (Stage-2 applier), `analytics/analytics-store-*` (edge decode)
- Builds on: ADR 0001 (declarative datasets), ADR 0004 (cube read path); part of the garbage-free
  workstream ("no Strings and no String serde on the steady-state path")

## Context

A dimension key today is transformed at every hop. Stage 1: the selector reads `String`/boxed
values off the fact (each string getter UTF-8-decodes MsgPack bytes it already had), copies them
into two lists plus an unmodifiable wrapper, and the heap cell maps hash/compare those objects.
The shuffle then serializes the key through `DimensionKeyValue` (each `String` UTF-8-encoded
again). Stage 2 decodes the bytes back into objects (`bufferAsString` per string column per cell)
only to re-encode them for the RocksDB cell key — and the serving writer renders parts once more.
The same logical value is UTF-8 decoded/encoded up to three times and allocated at every layer,
per (fact × meter) on the hottest path.

The variable path has the same shape one level down: values are stored as UTF-8 (`DbString`),
decoded to `String` for the enrichment map, carried through the fact, and re-encoded into the key.
The state interfaces (`ProjectionState`/`MutableProjectionState`) are `String`-typed only because
they predate this goal.

## Decision

### 1. `DimensionKey` wraps its canonical encoded bytes; identity is byte equality

`DimensionKey` becomes a thin holder of `(DimensionSchema schema, byte[] encoded, int hash)` where
`encoded` is today's `DimensionKeyValue` MsgPack form (kind-tagged values, schema order — the
layout is already canonical: one writer code path, fixed property order). Equality is
`Arrays.equals(encoded)`; the hash is computed once at construction. UTF-8 byte equality is string
equality, so semantics are unchanged.

Consequences by hop:
- **Selector (Stage 1)**: encodes each column straight from the fact's value views into a reusable
  encoder buffer — no lists, no boxing, no `String` materialization for string-typed fields.
- **Heap cell maps / `Windowed` probe**: a scratch probe key wraps the encoder buffer for the
  lookup; an owned copy is allocated only when a new cell is inserted (get-then-copy-on-insert).
- **Shuffle**: `keyCodec.toBytes(key)` returns the already-encoded bytes (a slice copy at the
  envelope boundary, which must own its buffer anyway).
- **Stage 2**: the received key bytes are wrapped, not decoded — the applier's `Windowed` key and
  the RocksDB cell key reuse the same bytes. The per-cell decode-to-`String` disappears.
- **Serving edge**: `values()`/`get(name)` decode lazily, per column, only where a materialized
  value is genuinely needed — the `cell_key` render and the JDBC binds — using the schema to walk
  the encoding. This is the one place UTF-8 decode remains, once per written cell.

### 2. Value views end-to-end feed the selector without Strings

- Facts expose string-typed fields as UTF-8 buffer slices (`DirectBuffer` views over the record
  payload / variable bytes); the positional fact redesign carries them unboxed. Record reads use
  the `get…Buffer()` variants.
- The projection state interfaces change to byte views: `putVariable(scopeKey, DirectBuffer name,
  DirectBuffer value)` (names also precomputed once per topology as UTF-8), and the name-targeted
  enrichment read hands the deriver value slices instead of building a `Map<String, String>`.
- `MeasureRef.asString` sites: HLL/`update(byte[])` hashes UTF-8 identically to `update(String)`
  (DataSketches converts strings to UTF-8 before hashing), so distinct-count feeds slices without
  changing existing sketch contents. Top-k (`ItemsSketch<String>`) genuinely stores items —
  it materializes the `String` at the sketch edge, which is a mandatory-edge exception like the
  JDBC bind.
- `FilterPredicate` values are pre-encoded once at compile time; filter evaluation is a byte
  compare for string fields, a typed compare otherwise.

### 3. What deliberately stays `String`

The mandatory edges from the garbage-free principle: `cell_key` (durable serving identity — the
rendered format is FROZEN and pinned by determinism tests), JDBC/VARCHAR binds, the REST/JSON
layer, top-k sketch items, and logging.

## Invariants and tests

1. **`cell_key` determinism**: for a corpus of keys built the old way and the new way from the same
   facts, the rendered `cell_key` strings and the serving rows are byte-identical. This is the
   contract test that gates the whole change.
2. **Encoding canonicity**: same logical values → identical `encoded` bytes regardless of source
   (record slice vs. test-constructed string); equality/hash agree with logical equality
   (property-style test over the value kinds incl. null buckets).
3. **Wire/store compatibility**: the encoded form IS today's `DimensionKeyValue` layout — sealed
   segments and durable cells written before the change decode into byte-backed keys unchanged
   (same bytes, same identity). No re-seed required for the key change itself.
4. **Sketch-hash compatibility**: distinct-count estimates over mixed old (`String`) / new
   (`byte[]`) updates of the same values are identical.

## Considered and rejected

- **`Object[]`-backed key with an interned UTF-8 view type**: removes the copies but keeps the
  per-hop encode/decode (objects still serialize at the shuffle and re-decode in Stage 2), and
  needs a custom value type everywhere. The canonical-bytes form gets the same wins plus free
  serde, with one representation.
- **Interning/pooling decoded Strings**: caps allocation but keeps every serde pass; interning
  tables are their own leak/contention surface.

## Sequencing

Gated behind the in-flight algorithmic work (the dispatch/table rewrite touches the selector call
sites) and implemented as one coherent cut with the positional `Fact` redesign (bucket B of the
garbage-free plan): Fact views → selector/encoder → `DimensionKey` → probe pattern → Stage-2 wrap
→ serving-edge lazy decode, with the determinism tests written FIRST.
