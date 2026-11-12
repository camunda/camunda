# Key index windows are not validated in the engine and under load

Status: open.

## Problem

The window policies have unit tests, RocksDB contract tests and RocksDB differential tests (run the
randomized ones with `-PincludeRandomTests`). We did not test two things yet:

- The engine test suite with the windows on. Run it, then run it again with an empty
  `keyIndexColumnFamilies` to separate failures that already existed.
- The policy of each column family against the real access pattern. The policies are in `WriteBehindConfiguration`, fixed in code. `NUMBER_OF_TAKEN_SEQUENCE_FLOWS` as `KEEP_HIGHEST` rests on the
  assumption that its scans hit the newest parents.

## Steps

1. Add the key count to the log line "Disabled the key index".
2. Run `WriteBehindLoadIT` with the index on. List the column families that pass the cap, and read `keyIndexHandovers`, `keyIndexRefills`, `keyIndexDiscardedRefills` and `iteratorsOpened`.
3. Run the engine suite with windows on.

