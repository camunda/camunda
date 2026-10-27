# MAT in headless mode

## Launch

We used Eclipse MAT 1.17 on macOS with Java 21+. `MAT_HOME` is the dir that has `plugins/`
(default: `/Applications/MemoryAnalyzer.app/Contents/Eclipse`).

```bash
java -Xmx12g -DhprofStrictnessPermissive=true -Duser.language=en -Duser.country=US \
  -jar $MAT_HOME/plugins/org.eclipse.equinox.launcher_*.jar -consolelog -nosplash -data <out>/raw/.ws \
  -application org.eclipse.mat.api.parse <dump.hprof> <pack.xml> ><out>/raw/mat_<suffix>.log 2>&1
```

- **The launcher jar, not the `MemoryAnalyzer` binary.** On macOS the native launcher opens
  a GUI error dialog when MAT fails, and the run hangs on it.
- **Permissive parsing**, so MAT accepts a truncated dump and drops the broken tail.
- **One `-data` workspace per dump**, so parallel runs don't share one.
- **English locale**, so numbers look the same on every machine.
- **Output to `raw/`.** The log echoes queries and can show heap values.

`run_mat.sh` does this once per pack. It refuses to run without `OUT_DIR`, or when
`OUT_DIR`, the output dir or the dump is inside a git repo. Our dumps were named
`<pod>_<date>_java_pid1.hprof`; the output name drops `_java_pid<N>`. `raw/.dump` records
the dump, and another dump with the same name is refused: set `NAME`.

## Files next to the dump

- **Indexes.** The first parse reads the whole dump and writes `<stem>.index`,
  `<stem>.*.index` and `<stem>.threads` next to it, where `<stem>` is the dump's file name
  without its last extension. Reruns reuse them.
- **First parse vs rerun.** Only the first parse logs truncation. `run_mat.sh` keeps that
  log as `raw/first_parse.log`. If the first parse ran elsewhere, delete
  `<stem>.index <stem>.*.index <stem>.threads` to parse again.
- **Report zips.** MAT writes `<stem>_<suffix>.zip` next to the dump. `run_mat.sh` moves it
  to `raw/` and unzips the CSVs into `raw/reports/<suffix>/`.

## Sizing (measured, 8.9 broker dumps)

| Dump | Used heap | Objects | First parse | Rerun | MAT RSS (`-Xmx12g`) | Indexes |
|------|-----------|---------|-------------|-------|---------------------|---------|
| 3.1 GB | 2 GiB | 47 M | ~2 min | ~20 s | ~9.5-10.5 GB | ~3.3 GB |

`MAT_XMX` of about 5-6x the used heap was enough. When MAT itself runs out of memory,
`grep -E 'java\.lang\.OutOfMemoryError(:|$)' raw/mat_<suffix>.log | grep -vc SELECT` is
above 0 (`SELECT` skips query echoes).

## Parallel runs

- One MAT JVM per dump, `(RAM GiB - 8) / (MAT_XMX GiB + 1)` at a time: 3 on a 48 GB Mac
  with `12g`. Never two runs on the same dump: they share the index and zip files.
- Run them as background jobs. With many dumps, start one subagent per few dumps. Give it
  the rules from [privacy.md](privacy.md), the `run_mat.sh` command and the one-liners from
  [queries.md](queries.md), and ask for numbers per dump only.
