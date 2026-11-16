# zeebe/zb-db/writebehind — Agent Instructions

You are working in the write-behind `ZeebeDb` layer. Read the monorepo-wide instructions first:

@../../../AGENTS.md

## Keep the README in sync

[`README.md`](README.md) is the reference for how this module works: architecture, read and write
paths, flush lifecycle, consistency and durability guarantees, memory bounds, configuration,
metrics and known limitations.

When a change affects any of these, update `README.md` in the same PR, ideally in the same commit:

- the write, read, iteration or flush path, or how in-memory layers are merged
- visibility, durability, snapshot or recovery semantics
- threading model or what runs on which thread
- memory accounting, backpressure or any bound (flush delay/threshold, max pending, cache size,
  key index limits)
- the key index or its policies, including the default per-column-family list
- configuration properties, their defaults or how the broker wires the factory
- metrics (added, removed, renamed or changed meaning)
- supported or unsupported `ZeebeDb` operations and known caveats

Pure refactorings, test-only changes and performance tweaks that don't change behaviour, bounds or
defaults don't need a README update. If unsure, update it.

Before finishing, re-read the affected README sections and check them against the code; don't
document behaviour you haven't verified.
