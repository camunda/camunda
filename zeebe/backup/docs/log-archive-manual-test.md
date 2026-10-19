# Log archive PoC: manual test plan

A hands-on session for two people: Claude drives builds, scripts and checks; you watch, poke at the
running system, and judge what you see. It covers the happy paths of backup and restore in general
(existing backups, continuous backup, log archive, and both side by side) plus the failure cases the
automated tests cannot do, such as a hard broker kill.

Expected duration: about 1.5 hours once the prerequisites are done.

## 1. Prerequisites

Do these before the session.

| # | What | Who / model | Why |
|---|------|-------------|-----|
| P1 | Restore CLI mode for the log archive: `restore --logArchive.timeline=tl-0 [--logArchive.checkpointId=<id>]` in `RestoreApp` / `RestoreArguments`, calling `LogArchiveRestore` for the partitions of the node | Sonnet | Today restore from the archive is only a Java API |
| P2 | `docker compose` setup under `zeebe/backup/docs/log-archive-manual/`: MinIO + 3 brokers (2 partitions, replication factor 3), broker-only profile, no secondary storage, S3 store pointing at MinIO, one env file per scenario | Haiku | Repeatable environment with hard kills |
| P3 | Helper scripts in the same folder: `deploy.sh` (deploys a process with a service task), `load.sh N` (creates N instances), `jobs.sh` (activates and counts jobs), `backup.sh` (takes a backup, prints its id), `status.sh <id>`, `bucket.sh` (lists archive keys and prints the newest root and commit entries) | Haiku | Same commands for both of us |
| P4 | Rebuild the distribution from this branch (`clean install -Dquickly`, then the `dist` module) and build a local image from `camunda.Dockerfile` with `BASE=public` and the new tarball | Claude | The current tarball predates the branch |

Fix W15 (backup checkpoints missing from the archive) must be merged first.

## 2. Environment

- 3 brokers `broker-0..2`, 2 partitions, replication factor 3, embedded gateway on `broker-0`
  (REST `:8080`, management `:9600`).
- MinIO on `:9000`, console on `:9001` (bucket `backups`).
- Each scenario starts from empty volumes: `docker compose down -v` between scenarios.
- Verification uses job counts (instances with a service task), as the integration tests do: after
  a restore, exactly the jobs created before the restore point must be activatable.

## 3. Scenarios

Each scenario lists the steps, who does what, and what we expect. A step marked **you** is a
judgement or exploration step; everything else Claude runs and reports.

### A. Existing backup, happy path (log archive off, continuous off)

1. Start the cluster with `env/a-classic.env`. Deploy, create 50 instances.
2. `backup.sh` → id A. `status.sh A` until `COMPLETED`.
3. Create 30 more instances.
4. Stop the cluster, wipe broker volumes (keep MinIO).
5. Run `restore --backupId=A` on each broker volume. Start the cluster.
6. `jobs.sh` → expect exactly 50.
7. **You:** look at the backup folders in the MinIO console; judge logs of restore and startup.

### B. Continuous backup, happy path (log archive off)

1. Start with `env/b-continuous.env` (continuous on, schedule PT1M, markers PT15S, retention P1D).
2. Deploy, then create instances continuously for 4 minutes (`load.sh` in a loop, 10/s).
3. Note the time T1 after about 2 minutes, at a moment the load script prints.
4. After 4 minutes: check `/actuator/backups` state and ranges: one contiguous range, several
   backups.
5. Stop, wipe, `restore --from=<start> --to=<T1>`. Start.
6. `jobs.sh` → expect the number created up to the restore point the tool reports (it prints the
   chosen checkpoint; compare with the load script's log).
7. **You:** check `metadata/<p>/metadata.json` in MinIO; does it match what the actuator shows?

### C. Log archive, happy path (continuous off)

1. Start with `env/c-archive.env` (log archive on, cutInterval PT1S, snapshotUploadInterval PT1M,
   chunkSize 50, rootEvery 20).
2. Deploy, create 100 instances. `bucket.sh`: commit entries grow, `data/` objects appear within
   seconds, a snapshot appears within a minute.
3. `backup.sh` → id C (this is a checkpoint; after this PoC it is the restore point). Wait until
   `bucket.sh` shows C as a covered checkpoint on both partitions.
4. Create 60 more instances.
5. Stop, wipe broker volumes, `restore --logArchive.timeline=tl-0 --logArchive.checkpointId=C`.
   Start.
6. `jobs.sh` → expect exactly 100.
7. `bucket.sh`: new timeline `tl-0.rC` appears and grows.
8. **Snapshot checks.**
   - The restore log names the archived snapshot it starts from, per partition: its processed
     position is just below C's position, and it may come from another broker (the broker id is
     part of the snapshot id).
   - **Older snapshot when the newest is too late:** before restoring, wait until a snapshot taken
     *after* C is archived (`state/` grows). The restore must skip it and use the last one taken
     before C.
   - **Bigger snapshot:** in a second run, create 3,000 instances with a 10 KB payload each before
     C, so the snapshot is several MB. The upload completes (descriptor present, `fileCount` and
     `sizeBytes` match the files) and the restore from it works.
9. **You:** browse the bucket layout (`commits/`, `root/`, `index/`, `state/`, `_latest.json`); read
   a commit entry and a root; does the layout match the design doc?

### D. Both side by side

1. Start with `env/d-both.env` (continuous on and log archive on).
2. Load for 3 minutes; take a manual backup D in the middle.
3. Check both: backup ranges contiguous (as in B) and archive growing (as in C); D appears in both.
4. Restore twice from the same bucket, each time from empty volumes:
   - `restore --backupId=D` → expect the job count at D;
   - `restore --logArchive.timeline=tl-0 --logArchive.checkpointId=D` → expect the same count.
5. **You:** compare the two restored clusters; anything different besides the timeline?

### E. Hard leader kill during load (log archive on)

1. Start with `env/c-archive.env`, load continuously.
2. Find the leader of partition 1 (`/actuator/cluster` or topology), `docker kill` it.
3. Keep loading for a minute, then `docker start` it again.
4. `bucket.sh`: object keys switch to a higher term (`-t<n>`), commit entries stay gapless,
   `LogArchiveView` reads without errors (via the restore tool's dry run or the verification script).
5. Take backup E, wait until covered, restore to E (as in C), check the job count.
6. **You:** look at the killed broker's logs after restart: does it resume cleanly?

### F. Store outage (log archive on)

1. Start with `env/c-archive.env`, load continuously.
2. `docker stop minio` for 2 minutes.
3. Watch: broker logs show upload retries, local data directory grows (`du -sh` on the volumes),
   processing continues.
4. `docker start minio`: archiving catches up, `bucket.sh` shows F close to the log again, local
   data directory shrinks after the next snapshot.
5. **You:** is the retry logging reasonable, or too noisy?

### G. Brownfield upgrade with handover (continuous backup first, log archive enabled later)

Simulates a cluster that runs continuous backup and is then upgraded to a build with the log
archive. The old backups and the archive share one store folder (`$WORK/store/g-brownfield`), as
they do on a real cluster. Everything runs on the local dist with `env` from `cluster.sh`:
`g-brownfield` has continuous backup (schedule PT1M, as in B) and the log archive is off unless
`LOG_ARCHIVE=on` is set (`cluster.sh upgrade`). Restores run on copies of the store
(`STORE_OVERRIDE`), so a restored cluster never writes into the store the other restores read.

Tags and checkpoints used below:

| Tag | Created | Checkpoint | Expected job count at that checkpoint |
|-----|---------|------------|---------------------------------------|
| p1, p2 | before the upgrade | O = manual backup after p2 (old-style) | 60 (p1 + p2) |
| p3 | right after the upgrade | H = handover, taken by the scheduler | 90 (p1 + p2 + p3) |
| p4 | after H is recorded | C = manual backup after p4 (archive only) | 120 (p1 to p4) |
| p5 | after C | none | not restorable to, must not appear in any restore |

Start from empty volumes:

```bash
cd zeebe/backup/docs/log-archive-manual && source ./env.sh
./cluster.sh stop; ./cluster.sh wipe; rm -rf "$WORK/store/g-brownfield"*
```

1. **Before the upgrade (archive off).** `./cluster.sh start g-brownfield`, then `./api.sh deploy`,
   `./api.sh load 30 p1`. Wait for two scheduled backups:
   `until [ "$(./api.sh backups | grep -c COMPLETED)" -ge 2 ]; do sleep 10; done`.
   Then `./api.sh load 30 p2`, `O=$(./api.sh backup | jq -r .backupId)` and wait with
   `./api.sh status $O` until `COMPLETED`. Save the listing:
   `./bucket.sh old-backups g-brownfield > "$WORK/g-before-upgrade.txt"`.
   - **Look at:** `ls "$WORK/store/g-brownfield"` (no `log-archive/` folder);
     `./bucket.sh old-backups g-brownfield` (two or more checkpoint folders under `manifests/1` and
     `manifests/2`); `grep -hi "log archive activ" "$WORK"/broker-*.log` (no output).
   - **Pass:** at least two COMPLETED old-style backups per partition, O among them, no archive
     folder, no activation line.

2. **Upgrade (all brokers restarted with the archive on, same data).**
   `./cluster.sh upgrade g-brownfield`, then `./api.sh load 30 p3` right away, so p3 is created
   before the handover (the handover needs an archived snapshot, which takes about a minute).
   - **Look at:** `grep -hi "log archive" "$WORK"/broker-*.log | grep -i "activ\|timeline"`: the
     scheduler line `Log archive activation started for physical tenant ...` and, per partition,
     `log archive is activated; starting the first timeline 'tl-0'` (or
     `started log archive timeline tl-0 at index ...`). Then
     `./bucket.sh g-brownfield`, and `./bucket.sh old-backups g-brownfield` compared with
     `g-before-upgrade.txt`.
   - **Pass:** each partition has a `log-archive/tl-0/p<n>/` folder whose first commit entry is
     `TIMELINE_STARTED`; old-style backups keep being created (new checkpoint folders appear in
     `manifests/`), so both sides run side by side; `./api.sh backups` has no FAILED entry.

3. **Handover.** Wait for H, then add the tags and the manual backup C:
   ```bash
   H=$(./bucket.sh wait-handover g-brownfield)   # up to 15 min, prints the id H
   ./api.sh load 30 p4
   C=$(./api.sh backup | jq -r .backupId)
   until ./api.sh status $C | grep -q COMPLETED; do sleep 2; done
   ./api.sh load 30 p5
   ```
   - **Look at:** `./bucket.sh handover g-brownfield` (one file per partition,
     `metadata/<p>/handover/<H>.json`, printed); `./bucket.sh handover-commits g-brownfield`
     (one HANDOVER commit entry per partition, `handover.checkpointId` equal to H);
     `./api.sh status $C`; `./api.sh backups`.
   - **Pass:**
     - every partition has the file with the same `checkpointId` H, `logArchiveTimeline` `tl-0`,
       and `partitionId` matching its folder;
     - every partition has exactly one HANDOVER commit entry, with H;
     - `metadata.json` has the same keys as in `g-before-upgrade.txt` (format unchanged);
     - `./api.sh status $C` is `COMPLETED`, and H and C appear in `./api.sh backups` in id order;
     - C has no old-style folder: `ls "$WORK/store/g-brownfield/manifests/1" | grep -c "^$C$"` prints `0`.
   - **No new old-style backups after H:** after C is COMPLETED, save
     `./bucket.sh old-backups g-brownfield > "$WORK/g-after-H-1.txt"`, wait 150 s (two scheduled
     ticks), save `g-after-H-2.txt`. `diff` must print nothing: the scheduled backups still run as
     restore points but upload nothing old-style.

4. **Restore (stop the cluster; restore three checkpoints from copies of the store).** Freeze the
   store first:
   ```bash
   ./cluster.sh stop
   cp -R "$WORK/store/g-brownfield" "$WORK/store/g-brownfield-frozen"
   ```
   Then for each run (a), (b), (c):
   ```bash
   ./cluster.sh wipe
   rm -rf "$WORK/store/g-run-<x>"; cp -R "$WORK/store/g-brownfield-frozen" "$WORK/store/g-run-<x>"
   export STORE_OVERRIDE="$WORK/store/g-run-<x>"
   LOG_ARCHIVE=on ./restore.sh g-brownfield --backupId=<id>   # (a) O, (b) H, (c) C
   LOG_ARCHIVE=on ./cluster.sh start g-brownfield && ./api.sh jobs
   ```
   Keep the logs of each run (`mkdir -p "$WORK/runs/<x>" && cp "$WORK"/*-*.log "$WORK/runs/<x>/"`
   before the next run), then `./cluster.sh stop`. `unset STORE_OVERRIDE` at the end.

   - **(a) old backup before H:** `--backupId=$O`. **Pass:** `./api.sh jobs` reports exactly 60,
     tags p1 and p2 only; `grep -h "Restored partition" "$WORK"/restore-*.log` names old backups.
   - **(b) at H:** `--backupId=$H`. **Pass:** exactly 90, tags p1 to p3; the log says old backups
     (H is an old-style backup).
   - **(c) after H, archive only:** `--backupId=$C`. **Pass:** exactly 120, tags p1 to p4 (p4 was
     created after H, so it can only come from the archive); the log names the log archive for
     each partition; `ls "$STORE_OVERRIDE/log-archive"` shows a new timeline derived from `tl-0`.
   - **In every run:** no tag other than the expected ones, and p5 never appears.

5. **(g-extra, optional) second handover after a restore before H.** Continue with run (a) (its
   cluster is still up with the archive on, `STORE_OVERRIDE` still set):
   Before waiting, record the checksums of the existing handover files:
   `md5 -q "$STORE_OVERRIDE/metadata/1/handover/$H.json" "$STORE_OVERRIDE/metadata/2/handover/$H.json"`.
   Then `H2=$(./bucket.sh wait-handover g-brownfield $H)` (the wait is for a handover above H, so the
   existing one does not match), and `./bucket.sh handover g-brownfield`.
   - **Pass:** each partition has two files, `<H>.json` and `<H2>.json`, with `H2` on the new
     timeline (`logArchiveTimeline` other than `tl-0`); the checksums of `<H>.json` are unchanged.
     Old files are never modified, only added next to.

**Confirmed on the first run (2026-10-19):**
- The first run restored without the log archive in the restore configuration: the restore app
  then only knew old backups ((c) failed with "No completed backup found"; (a)/(b) wrote no
  timeline file). Fixed: when the old store has handover files, the restore app takes the
  handover-aware path whatever the flag says. `LOG_ARCHIVE=on` is still passed to match the
  upgraded brokers; `restore.sh` unsets it for the restore app itself, because Spring would bind
  it to the app's `--logArchive.*` arguments.
- Handover files have `formatVersion` 1. The scheduler took H about 40 s after the upgrade, after
  p3. Timeline files after restore: `tl-0.b<H>` (b), `tl-0.r<C>` (c), `tl-0.b<O>` (a).
- Results: (a) 60 jobs (p1, p2), (b) 90 (p1–p3), (c) 120 (p1–p4), p5 never.
- Further restore cases (run 2026-10-19, all pass): time range `--from` before H, `--to` at C →
  archive path, 120 jobs; time range ending at O, before H (no RDBMS) → old path, 60 jobs (failed
  before fix fcabcdc1959; give `--to` with milliseconds, a whole second can land on a neighbouring
  backup); restore with the archive disabled in the restore configuration → still the archive, 120;
  restore to O, then a second handover H2 on `tl-0.b<O>`, `<H>.json` unchanged; scale-up to 3
  partitions after the handover (`PATCH /actuator/cluster` partitions count 3): partition 3 records
  the handover, a backup S has no old-style upload on it, restore to S (`PARTITIONS=3`) → 90 jobs.
- Counting jobs activates them for 10 minutes. A restore to a later checkpoint of the same branch
  brings them back activated; count after their timeout or before the checkpoint.
- Fixed gap: in this run, archive checkpoints in a sealed index chunk dropped out of
  `/actuator/backups` (status DOES_NOT_EXIST) about two minutes after H. They are now kept in
  `LOG_ARCHIVE_BACKUP_CHECKPOINTS` and stay listed and `COMPLETED`.

### H. RDBMS (H2) as secondary storage

The `h-*` scenarios use an H2 file database in server mode (`$WORK/h2/<scenario>`,
`AUTO_SERVER=TRUE`), shared by the three brokers and the restore app. Each restore runs on copies
of the store and the database (`STORE_OVERRIDE`, `H2_OVERRIDE`), so a restored exporter does not
change the exported position E another restore reads. E is frozen by disabling the RDBMS exporter
(`POST /actuator/exporters/rdbms/disable`); rows are counted in `PROCESS_INSTANCE`, positions read
from `EXPORTER_POSITION` (`java -cp $DIST/lib/h2-*.jar org.h2.tools.Shell -url ...`).

With RDBMS, restore with `--to` only: E decides where the journal starts. A `--from` makes the
existing old-path resolver discard backups before it, and it must then still lie inside a backup
range and at or before the backup that covers E (one second off either way fails).

| Case | Setup | Restore | Pass |
|---|---|---|---|
| H1 | `h-rdbms`: continuous backup + RDBMS; 50 instances, E frozen, 50 more, backup | `--to=<now>` | old path; rows 50 → 100 after the restored exporter resumes; 100 jobs |
| H2 | `h-handover`: brownfield (archive off, then `upgrade`), handover H, E frozen after H, C, more instances | `--to=<now>` | archive path (`.r`); rows 90 → 120; 120 jobs |
| H2b | same store | `--to=<C's time>` | archive path; rows 120, nothing after C; 120 jobs |
| H3 | `h-compacted`: 1 MB segments, 512 KB max message size; E frozen early, 300 instances × 10 KB, wait for compaction, then `upgrade` | `--to=<now>` | archive starts after E+1 (`timeline.json` startIndex > 1); restore bridges old backups and archive (section 18.5a): "Restoring partition N bridged: old backups … up to index A−1, log archive timeline 'tl-0' from switch index A", to the latest checkpoint T (`tl-0.r<T>`, not the fallback `tl-0.b<H>`); rows 30 → every instance up to T (≥ 330); as many jobs |
| H4 | `h-handover`: E frozen before the upgrade, old backup O, then handover | `--to=<O's time>` | old path (`.b`); rows 30 → 60; 60 jobs |
| H5 | `h-handover`: E frozen before the upgrade, handover H, C after H | `--to=<C's time>` | archive path (`.r`); the restored journal starts at or before E+1, before H (the archive's log reaches back before H); rows 30 → 120 across H, nothing after C; 120 jobs |
| H5b | `h-compacted`: 300 × 10 KB with the exporter on (it holds compaction back), compaction, `upgrade` (archive starts at index 351/284), E frozen right after activation, handover H, C | `--to=<C's time>` | archive path; the restored journal starts at E+1 (index 633/487), after the archive start and before H; rows 300 → 360; 360 jobs |

### Snapshot checks (S)

- **Snapshot choice**, for every archive restore: take "Restoring partition N to checkpoint … at
  position P: snapshot X" from the restore log and check against the archived descriptors
  (`state/<id>/descriptor.json`) that X is the newest snapshot whose `processedPosition`,
  `lastFollowupEventPosition` and `maxExportedPosition` are all ≤ P.
- **Descriptors**: `fileCount` and `sizeBytes` match the files next to them.
- **Big snapshot**: 3,000 instances with a 10 KB payload before C (snapshots of about 20 MB),
  restore to C → 3,000 jobs.

## 4. What we record

For every scenario: pass/fail per expectation, anything surprising, log excerpts for failures, and
the bucket listing at the end. Findings go into the PoC's open questions or become fixes.
