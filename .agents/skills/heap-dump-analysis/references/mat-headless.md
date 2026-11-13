# MAT in headless mode

## Launch

Requires Eclipse MAT 1.17 or later and Java 21 or later; tested on macOS. `MAT_HOME` is the
dir that has `plugins/` (default: `/Applications/MemoryAnalyzer.app/Contents/Eclipse`).
`run_mat.sh` launches MAT with:

- **The launcher jar, not the `MemoryAnalyzer` binary.** On macOS the native launcher opens
  a GUI error dialog when MAT fails, and the run hangs on it.
- **Permissive parsing**, so MAT accepts a truncated dump and drops the broken tail.
- **One `-data` workspace per dump**, so parallel runs don't share one.
- **English locale**, so numbers look the same on every machine.
- **Output to `raw/`.** The log echoes queries and can show heap values.

It runs MAT once per pack. It refuses to run without `OUT_DIR`, or when
`OUT_DIR`, the output dir or the dump is inside a git repo. Broker dumps are often named
`<pod>_<date>_java_pid1.hprof`; the output name drops `_java_pid<N>`. `raw/.dump` records
the dump, and another dump with the same name is refused: set `NAME`.

## Files next to the dump

- **Indexes.** The first parse reads the whole dump and writes `<stem>.index`,
  `<stem>.*.index` and `<stem>.threads` next to it, where `<stem>` is the dump's file name
  without its last extension. Reruns reuse them.
- **First parse vs rerun.** Only the first parse logs truncation. `run_mat.sh` keeps that
  log as `raw/first_parse.log` and prints `truncated=N` from it. It prints
  `truncated=unknown` if the first parse ran elsewhere: delete
  `<stem>.index <stem>.*.index <stem>.threads` to parse again.
- **Report zips.** MAT writes `<stem>_<suffix>.zip` next to the dump. `run_mat.sh` moves it
  to `raw/` and unzips the CSVs into `raw/reports/<suffix>/`.

## Sizing

Start with `MAT_XMX` at about 4x the `.hprof` size, for example `MAT_XMX=12g` for a 3 GB
dump. That comes from one dump, so it's a starting point, not a minimum. MAT uses about
0.85x its `MAT_XMX` in RAM, and the indexes take about the dump's size on disk.

When MAT itself runs out of memory, its log has `java.lang.OutOfMemoryError` lines outside
the query echoes (lines with `SELECT`). Count them; don't print them. Then delete
`<stem>.index <stem>.*.index <stem>.threads`, raise `MAT_XMX` and rerun.

Measured on an 8.9 broker dump:

| Dump | Used heap | Objects | First parse | Rerun | MAT RSS (`-Xmx12g`) | Indexes |
|------|-----------|---------|-------------|-------|---------------------|---------|
| 3.1 GB | 2 GiB | 47 M | ~2 min | ~20 s | ~9.5-10.5 GB | ~3.3 GB |

## Parallel runs

- One MAT JVM per dump. Run as many at once as fit in RAM with room to spare, at about
  0.85x `MAT_XMX` each (see Sizing). With `-Xmx12g`, 3 fit on a 48 GB machine. Never two
  runs on the same dump: they share the index and zip files.
- Run them as background jobs. With many dumps, start one subagent per few dumps. Give it
  the rules from [privacy.md](privacy.md), the `run_mat.sh` command and the CSV format from
  [queries.md](queries.md), and ask for numbers per dump only.
