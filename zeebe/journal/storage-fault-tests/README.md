# Journal storage fault tests (8.8)

Experiments used to investigate journal corruption on Camunda 8.8 when the disk or a storage quota
fills up. They inject storage faults under the real 8.8.41 journal (`SegmentedJournal`) or under
full 8.8.41 brokers, then restart and check what survived.

This is investigation tooling, not part of the build or CI. The Java files here are compiled with
plain `javac` inside the containers against the jars of the `camunda/camunda:8.8.41` image.

## Findings in short

|                Finding                 |                                                                                                            Effect                                                                                                             |                    Where                     |
|----------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------|
| Failed snapshot install on a full disk | Log left half-reset: NPE on partition start, SIGSEGV after a leader change, graceful shutdown hangs. On 8.8 the restart heals; on 8.9+ the partition refuses to restart (see the tests on this branch outside this directory) | `broker/scenario-de.sh`, `stable-8.8/`       |
| `deleteAfter` into a newer segment     | Stored flushed index is raised before flushing; the previous segment's tail is never flushed. After a crash: silent loss of acknowledged entries, or `not aligned` on restart                                                 | `run-sweep.sh` (cross-segment truncation)    |
| `.meta` written before data            | `deleteAfter`/`reset` store a flushed index for data not yet flushed; a crash in between can block the restart                                                                                                                | `run-sweep.sh` with torn `.meta` writes      |
| Segment header shortcut                | On open, the header's "last entry" is trusted without checking the entries before it; a torn flush can leave a hole that is never detected                                                                                    | `run-sweep.sh` with `faults/torn-faults.txt` |
| Failed flush retried                   | A flush (`msync`) that fails once is retried; Linux already marked the pages clean, so the retry succeeds without writing them. Restart: `Expected to find records until index …, but last index is …`                        | `fsyncgate.sh`, `broker/scenario-g-dm.sh`    |
| Storage confirms but does not persist  | Model of a write-back cache with an exhausted quota (patched LazyFS). Reproduces `Expected to find records until index …` with no flush errors logged                                                                         | `quota/`, `broker/scenario-g.sh quota`       |

## Requirements

- Docker with privileged containers and `/dev/fuse` (tested with Docker Desktop on Apple Silicon).
- Build the images once (takes a few minutes; `all` also builds the JuiceFS and NFS images):

  ```bash
  ./build-images.sh [all]
  ```

All scripts expect this directory to be mounted at `/work`. Outputs (`logs-*`, `*.log`, `*.out`)
are written next to the scripts and are git-ignored. Run one container at a time: LazyFS keeps its
cache in memory, and several instances at once can run Docker out of memory.

## Layout

|        Path         |                                                              Contents                                                               |
|---------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| `Dockerfile`        | LazyFS 0.3.1 (Jepsen's fault-injection FUSE filesystem) with a `statfs` fix, JDK 21                                                 |
| `lazyfs-lib.sh`     | Mount LazyFS, simulate a power loss (drop everything not fsynced)                                                                   |
| `harness/`          | Journal-level harnesses: `JournalCrash` (workloads + verify), `CrashSweep` (fixed step list for sweeps), `FsyncGate` (failed flush) |
| `run-journal.sh`    | Random power-loss campaign at journal level                                                                                         |
| `run-sweep.sh`      | Crash or torn write at an exact syscall, armed before each step of `CrashSweep`                                                     |
| `faults/`           | Fault lists for `run-sweep.sh`                                                                                                      |
| `fsyncgate.sh`      | Failed flush on ext4 via device-mapper (`linear`/`error` targets)                                                                   |
| `quota/`            | LazyFS patch: once a "quota full" switch is on, fsync of matching files returns success without persisting                          |
| `broker/`           | Full-broker scenarios (Camunda 8.8.41 distribution, 1 or 3 brokers)                                                                 |
| `juicefs/`, `nfs/`  | Journal on JuiceFS (`--writeback`, quota) and on NFS (async export, server crash)                                                   |
| `probe/`, `sigbus/` | Calibration: how LazyFS/NFS see memory-mapped writes; what the JVM does on `SIGBUS` in a mapped segment                             |
| `stable-8.8/`       | Copies of the out-of-disk snapshot install tests adapted to stable/8.8; copy them into the 8.8 tree to run                          |
| `*-log4j2.xml`      | Journal debug/trace logging for the harnesses                                                                                       |

## Running the tests

Common prefix for the journal-level tests:

```bash
RUN="docker run --rm --privileged --device /dev/fuse -v $PWD:/work"
```

### 1. Crash at an exact syscall (journal level)

```bash
# 13 fault types x 27 steps, about 30 minutes each
$RUN -e CROSS=false lazyfs-camunda:8.8.41 bash /work/run-sweep.sh /work/faults/crash-faults.txt
$RUN -e CROSS=true  lazyfs-camunda:8.8.41 bash /work/run-sweep.sh /work/faults/crash-faults.txt
# single steps: append "3 12" to the command; keep run/verify output: -e KEEP=1
```

`CROSS=true` truncates into a newer segment at step 9 (`deleteAfter` bug): every crash after that
step fails with the journal ending at the old flushed index. A crash during the reset step
(`reset-snapshot-install`) is reported as `EXPECTED`: an empty log that opens cleanly is by design.

### 2. Torn writes (journal level)

```bash
$RUN -e CROSS=false lazyfs-camunda:8.8.41 bash /work/run-sweep.sh /work/faults/torn-faults.txt
```

The variant `tornop-cur-keep1+3of3` at step 14 shows the header shortcut: the journal opens with
`last=136` while entry 117 fails its checksum. Add `-e VERIFY_OPTS=-Dlog4j2.configurationFile=/work/trace-log4j2.xml`
to see `Found lastEntryPosition … in descriptor`.

### 3. Failed flush

```bash
# journal level: segment blocks fail for a few seconds, then reopen (dropcache or umount)
docker run --rm --privileged -v $PWD:/work lazyfs-camunda:8.8.41 bash /work/fsyncgate.sh umount
# full brokers: 3 brokers, 6 partitions, all segment files fail for 10s, then each broker restarts
docker run --rm --privileged --memory 7g -v $PWD:/work lazyfs-camunda:8.8.41 bash /work/broker/scenario-g-dm.sh 10
```

Expected: `FLUSH_FAILED … msync … Input/output error` during the window (brokers log
`Failed to flush appended entries to the log, cannot guarantee durability` and
`Failed to flush commit at index …`), then `Expected to find records until index …, but last index is …`
on reopen/restart. Logs: `fsyncgate-logs/`, `broker/logs-g-dm/`.

### 4. Storage confirms writes it does not persist (quota model)

```bash
# journal level
$RUN lazyfs-camunda:8.8.41-quota bash /work/quota/run.sh
# full brokers: disks fill up, partitions go idle, broker 1 is replaced and loses its cache
$RUN --memory 7g lazyfs-camunda:8.8.41-quota bash /work/broker/scenario-g.sh quota graceful
$RUN --memory 7g lazyfs-camunda:8.8.41-quota bash /work/broker/scenario-g.sh plain graceful   # control
```

### 5. Failed snapshot install on a full disk (half-reset log)

```bash
# D: follower stopped (graceful or kill) during the failing install loop, space freed, restarted
# E: leader change while the follower's install keeps failing (expected: follower JVM SIGSEGV)
docker run --rm --privileged --memory 7g -v $PWD:/work lazyfs-camunda:8.8.41 bash /work/broker/scenario-de.sh tmpfs D-graceful
broker/run-de-all.sh tmpfs:D-kill tmpfs:E ext4:D-graceful ext4:D-kill ext4:E   # one container per variant
```

### 6. Other experiments

|                       Command (in a container as above)                       |                                     What it does                                      |
|-------------------------------------------------------------------------------|---------------------------------------------------------------------------------------|
| `run-journal.sh <follower\|leader-truncate\|mixed> <lazy\|forced> <n> <seed>` | Random power losses during journal workloads                                          |
| `broker/run-broker.sh <cycles> <minLoad> <maxLoad>`                           | One broker on LazyFS, repeated power losses under load                                |
| `broker/scenario-rolling.sh <cycles> <minLoad> <maxLoad> [all]`               | Three brokers, rolling power loss (or all at once)                                    |
| `broker/broker-sweep.sh /work/broker/bsweep-faults.txt` (quota image)         | One broker: crash faults around snapshot persist and log compaction (only partly run) |
| `juicefs/run.sh`, `juicefs/run-nodelay.sh` (JuiceFS image)                    | Journal on JuiceFS write-back with an exhausted directory quota, then node cache loss |
| `nfs/run-journal-nfs.sh <scenario> <n> <seed>`, `nfs/probe.sh` (NFS image)    | Journal on NFS with an async export, then a server crash                              |
| `run-probe.sh`, `sigbus/run.sh`, `sigbus/run2.sh`                             | Calibration of mapped writes on LazyFS; JVM behaviour on `SIGBUS` in a full tmpfs     |

## Notes

- LazyFS faults are written to its FIFO, for example
  `lazyfs::crash::timing=before::op=fsync::from_rgx=journal-` or
  `lazyfs::torn-op::file=<backing path>::parts=3::persist=1,3`. `CrashSweep` replaces `{CUR}`,
  `{NEXT}` and `{META}` with the backing paths of the current segment, the next segment and `.meta`.
- `JournalCrash.FileMetaStore` mimics Raft's `MetaStore`: an unforced 8-byte write of the flushed
  index, forced only on close (`lazy`), or on every write (`forced`).
- The harnesses emulate the Raft caller (acknowledge only after a successful flush, keep entries
  after a failed one). The `broker/` scenarios use the real broker.

