# Refill of a key index window stalls the processing thread

Status: open. Not planned for the first version.

## Problem

A key index window can drain. Deleted keys leave it, for example fired timers in a `KEEP_LOWEST`
window. A scan that finds fewer than `keyIndexMaxKeys / 4` keys in the window extends it. The scan
thread reads one chunk of keys from RocksDB. This is the processing thread. With the default cap
of 100000 keys, the chunk is 25000 keys. The processing thread does no other work while it reads
them. Nobody measured how long this takes.

## Idea

- Use a much smaller chunk, for example 1000 keys. Each stall is then short. The window drains
  below the limit sooner, so refills happen more often, but each one costs less.
- Later, load the chunk in the background. A commit that writes the column family during the
  load makes the load obsolete, so a background load needs a way to keep its result. The first
  version does not have one.

## Open questions

- How long does a refill of 25000 keys take on the target hardware?
- What chunk size keeps the stall below the latency target of the processing thread?
- Can a background load stay valid? A commit to the column family changes the sequence that the
  refill checks.

## Where

`WriteBehindStore#refillKeyIndex` and `KeyIndex#needsRefill`.
