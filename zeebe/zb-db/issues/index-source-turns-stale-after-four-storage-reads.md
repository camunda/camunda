# A scan from the key index changes to a storage iterator after four storage reads

Status: open. Pre-existing limit. We found it while we built the key index window.

## Problem

The index source returns keys from memory. It reads a value only when the scan asks for it. The
value comes from the dirty index, then the read cache, then one RocksDB point read.

After `IndexSource.MAX_STORAGE_READS` (4) point reads in one scan, the source turns stale. The
cursor then opens a storage iterator and continues with it. The reason is cost: many random point
reads are slower than one iterator that reads the keys in order.

So the benefit of the key index depends on where the values are. A scan that reads more than four
values that the writer flushed and the read cache does not hold opens an iterator anyway. The key index only
saves the iterator for scans that read keys only, scans that stop early, and scans whose values are
unflushed or cached.

## Open questions

- How many scans of the indexed column families read more than four values from RocksDB? The load
  test (`WriteBehindLoadIT`) shows this when it counts `iteratorsOpened` with the index on.
- Is 4 the right limit? The break-even point of point reads against one iterator was not measured.
- Does a bigger read cache for these column families remove most of the storage reads?

## Where

`IndexSource#readValueUnlessStale`.
