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
- Further restore cases (run 2026-10-19, all pass): time ranges across H without RDBMS (`--from`
  before H, `--to` at C → archive, 120 jobs; `--to` at O → old path, 60 jobs; give `--to` with
  milliseconds, a whole second can land on a neighbouring checkpoint). **Since the time-range
  alignment (spec §18.5a) these are rejected like with continuous backup** ("Cannot resolve a
  restore point: no backupId was specified and no exported-position data is available …"); time
  ranges across H are covered with RDBMS in scenario H. Restore with the archive disabled in the restore configuration → still the archive, 120;
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

### I. Event plane (log archive on, commit entries announced)

The brokers announce every commit entry to the event plane server (spec section 19). Scenario
`i-eventplane` is the log archive of C plus `ZEEBE_BROKER_DATA_BACKUP_LOGARCHIVE_EVENTPLANE_URL`
(`http://localhost:8090`, basic auth `demo`/`demo`). The event plane runs from a checkout of
`camunda/event-plane` (`EP_DIR`, default `~/Development/projects/event-plane`, jar built with
`./mvnw -o install -DskipTests`) on port 8090, because broker 0 serves REST on 8080. Its demo profile
keeps the catalog in memory, so every start is an empty catalog. It reads the bucket itself (the
filesystem store of the scenario) to show records. `event-plane.sh` starts and stops it and compares it
with the store; `bucket.sh i-eventplane` shows the store.

```bash
cd zeebe/backup/docs/log-archive-manual && source ./env.sh
./cluster.sh stop; ./cluster.sh wipe; rm -rf "$WORK/store/i-eventplane"
./event-plane.sh start
./cluster.sh start i-eventplane && ./api.sh deploy
```

`./event-plane.sh check [seconds]` passes when, for every timeline the event plane knows, `nextSeq - 1`
equals the newest `commits/<seq>.json` of that timeline in the store (retrying for a few seconds).
`./event-plane.sh objects <p>` reads `GET /v1/log-archive/default/default/partitions/<p>/objects?after=0`
and checks that objects follow each other without an index gap within a timeline and in position order.
`./event-plane.sh overview` prints `GET /v1/log-archive` as lines.

1. **I1, fresh cluster.** `./api.sh load 100 e1`, `./api.sh complete 50`, then `check` and `objects 1`,
   `objects 2`. **Pass:** `check` passes on both partitions; the objects of the current path equal the
   `data/` objects of the store (`bucket.sh` prints the count; none is empty here); no gap.
2. **I2, event plane down.** `./event-plane.sh kill`, `curl` on the event plane gives a connection error
   (not 503), `./api.sh load 150 e2`, `./api.sh complete 50`. **Pass:** the newest commit seq in the
   store grows meanwhile (the log archive does not wait), the brokers log
   `failed to announce to the event plane, retrying in PT..` (backoff up to 10 s). Then
   `./event-plane.sh start` (empty catalog) and `check 60`. **Pass:** caught up from seq 1 for every
   partition, `objects` has no gap and as many objects as the store.
3. **I3, leader kill.** `L=$(./cluster.sh leader 1)`, `NODE=<another broker> ./cluster.sh kill $L`,
   keep loading through the other broker (`NODE=1 ./api.sh load 100 e3`), restart with
   `./cluster.sh restart $L i-eventplane`, load more. **Pass:** `check` passes, `objects` has no gap, the
   store has objects of a higher term (`-t2.rec`).
4. **I4, restore.** Take a checkpoint (the id must be given when continuous backup is off):
   `C=$(date +%s); ./api.sh backup $C`, wait for `./api.sh status $C` `COMPLETED` and for C in the
   `checkpoints` of the commit entries of both partitions (`overview` shows `checkpoints=1`). Load 60 more,
   `check`. Stop the brokers (the event plane keeps running), `./cluster.sh wipe`,
   `./restore.sh i-eventplane --logArchive.timeline=tl-0 --logArchive.checkpointId=$C`, start the brokers
   again, load 40 more. **Pass:** `overview` shows per partition `tl-0.r<C>` as current with
   `parent=tl-0` and `takeover(index=<C's index>,position=<C's position>)` equal to the
   `log-archive-takeover` file in `$WORK/broker-*/data/default/partitions/*/`; `check` passes for both
   timelines; `objects <p>` lists the parent's objects up to the takeover and then the new timeline's: the
   last parent object has `lastPosition` equal to the takeover position, below its `objectLastPosition`
   when the object goes beyond it. The new timeline's first object overlaps the parent's last objects
   (the restore archives again from the snapshot's index); readers skip the positions they have.
   `./api.sh jobs` equals the jobs at C plus those created after the restore.
5. **I5, UI.** `http://localhost:8090/log-archive-ui` needs no login for the page, but its API calls do
   and a headless browser cannot answer the prompt. Put a tiny proxy in front that adds the basic auth
   header (any 20 lines of Python), and take screenshots with
   `chrome --headless --virtual-time-budget=6000 --window-size=1400,1000 --screenshot=<png> <url>`:
   overview `#/`, timeline `#/t/default/default/1/tl-0.r<C>`, records
   `#/r/default/default/1/tl-0/<url-encoded object key>` (the slashes of the key as `%2F`).
   **Pass:** the overview shows both timelines per partition, the restored one current with "takes over
   from tl-0 after position .."; the timeline page shows objects, checkpoint C and the snapshot; the
   records page shows decoded `PROCESS_INSTANCE` rows.

**Confirmed on the first run (2026-10-20):** I1 to I5 pass. Counts: I1 11 objects and 13 commits per
partition on both sides; I2 19/18 commits, caught up from an empty catalog; I3 24/25 commits, objects of
term 2; I4 `tl-0.r<C>` with takeover (929, 5230) on partition 1 and (980, 6491) on partition 2, the path
cuts the parent at the takeover position; 380 jobs after the restore.

### J. Merge, release and event plane compaction (step 2)

Scenario `j-merge` is `i-eventplane` with 8 MB journal segments (`J_SEGMENT_SIZE`; 128 MB in
production) and merged objects in frames of 2 MB of entries (`J_FRAME_BYTES`; 32 MB in production), so
every merged object has about four frames, cleaner `PT10S` / grace `PT30S`, `retentionWindow` `PT3M`, `releaseInterval` `PT10S`, backups every 30 s. The event
plane is started by `event-plane.sh` with rollup period `PT4M`, delay `PT10S`, interval `PT10S` and
retention `PT10M`. Export `SCENARIO=j-merge` in every shell that calls `event-plane.sh` (it defaults to
`i-eventplane`). `bucket.sh merge j-merge` prints per partition the number of `LOG_MERGED` entries, the
merged index, whether the merged ranges are contiguous, the newest `LOG_RELEASED` entry, the number of
level-1 objects and the replaced small objects still in `data/`.

```bash
cd zeebe/backup/docs/log-archive-manual && source ./env.sh && export SCENARIO=j-merge
./cluster.sh stop; ./cluster.sh wipe; rm -rf "$WORK/store/j-merge"
./event-plane.sh start && ./cluster.sh start j-merge && ./api.sh deploy
# load for 8 to 10 minutes: a loop of  ./api.sh load 20 rN 8000; ./api.sh complete 20; sleep 1
# (8 KB payloads fill an 8 MB journal segment in about a minute)
```

1. **J1, merges.** After 2 to 3 minutes. **Pass:** `bucket.sh merge j-merge` shows `LOG_MERGED` entries
   (about one per journal segment, 1 MB of log, roughly every 25 s under this load) and no gaps between
   merged ranges; the brokers log `deleted N replaced or orphaned log objects` every 10 s; the objects
   replaced by a merge disappear from `data/` after the grace period (`replaced-but-still-there` stays
   at the last few merges, it does not grow); `GET /v1/log-archive` shows `mergedIndex` rising and the
   objects API of the event plane lists fewer objects than the bucket (replaced ones are gone from it).
   The merged objects are about 30 KB (the records compress well), so they have a single frame; several
   frames need `mergeFrameBytes` below the object size.
2. **J2, release.** After the 3 minute window. **Pass:** `LOG_RELEASED` entries appear (the released
   index jumps with every uploaded snapshot, about once a minute), the event plane shows `releasedIndex`
   in the overview and the timeline view, and the objects up to it carry the "released" badge.
3. **J3, rollup.** **Pass:** `data/` holds `<first>-<last>-l1.seg` objects covering the released objects
   of finished 1-minute periods; no level-0 object lies inside a level-1 range (bucket and catalog);
   the timeline view shows them as `L1 released`; the records view of a level-1 object decodes (rows
   of `PROCESS_INSTANCE`, `JOB`, ..., in one frame per about 32 MB).
4. **J4, retention.** About 10 minutes after the first records. **Pass:** the objects API answers a `start`
   position above 1, the oldest level-0/level-1 objects are gone from `data/` (the first key of `data/`
   moves), nothing above `releasedIndex` was removed.
5. **J5, leader kill during merging.** `L=$(./cluster.sh leader 1)`, move the load to another broker
   (`NODE=1 ./api.sh load ...`), `./cluster.sh kill $L`, wait a minute, `./cluster.sh restart $L j-merge`.
   **Pass:** `bucket.sh merge` shows more `LOG_MERGED` entries with merged objects of term 2
   (`-t2.seg`), gaps 0; after the restarted broker caught up, `./event-plane.sh check` passes and
   `./event-plane.sh objects 1|2` show no gap.
6. **J6, restore.** Stop the load and the brokers, copy the store
   (`cp -R "$STORE/j-merge" "$STORE/j-merge-copy"`), `export STORE_OVERRIDE=$STORE/j-merge-copy`,
   `./cluster.sh wipe`. Take the oldest `SCHEDULED_BACKUP` checkpoint of the commit entries as `OLD` and a
   recent one present on both partitions as `NEW`:
   `./restore.sh j-merge --logArchive.timeline=tl-0 --logArchive.checkpointId=$OLD`. **Pass:** refused
   with "restore point .. is below the restore floor of partition ..: its snapshot is at index .., the
   log up to index .. was released", before anything is downloaded. The same with `$NEW`: "restored" for
   all brokers; start the cluster, `./api.sh jobs` lists the jobs open at `NEW`.

**Confirmed on the first run (2026-10-21):** J1 to J6 pass. Counts: after 5 minutes 15 `LOG_MERGED`
entries per partition without gaps, `data/` held 44 objects against 18 in the event plane catalog; first
release at index 2672 (p1) and 4661 (p2); level-1 objects such as `2417-4658-l1.seg` (13442 records, one
frame, 211 KiB) readable in the UI; after 10 minutes the objects API answered `start` 13686 and the first
`data/` key moved from `1-282` to `2417-4658-l1`; with broker 0 (leader of partition 1) killed, merges
continued on broker 1 with term 2, ranges contiguous (e.g. 14651-14904, 14905-15163 at term 2);
restore to the first backup refused with "below the restore floor of partition 2 .. log up to index 17976
was released", restore to a recent backup (snapshot 22504 / 24288) worked and 8 jobs were activatable.

### K. Mode B: one authority

Scenario `k-authority` is `j-merge` plus `ZEEBE_BROKER_DATA_BACKUP_LOGARCHIVE_COMPACTIONMODE=AUTHORITY`
(spec section 25). The cluster only writes small objects and moves the restore hold H; the event plane
is the only one that compacts and deletes. `event-plane.sh` starts it with the rollup settings of J and
`authority.l1-target-bytes=1MB`, `authority.l1-max-age=PT1M`, `authority.deletion-grace=PT30S`,
`retention=PT10M`. Export `SCENARIO=k-authority` in every shell that calls `event-plane.sh`.

```bash
cd zeebe/backup/docs/log-archive-manual && source ./env.sh && export SCENARIO=k-authority
./cluster.sh stop; ./cluster.sh wipe; rm -rf "$WORK/store/k-authority"
./event-plane.sh start && ./cluster.sh start k-authority && ./api.sh deploy
# load for about 12 minutes: a loop of  ./api.sh load 20 rN 8000; ./api.sh complete 20; sleep 3
```

Event plane API used below: `/v1/log-archive/default/default/partitions/1/timelines/tl-0/progress`,
`.../activity?limit=500`, `.../bucket` (basic auth `demo`/`demo`). Store folders:
`$STORE/k-authority/log-archive/tl-0/p<n>/{commits,maintenance,data}`.

1. **K1, mode and hold.** **Pass:** `commits/...01.json` is `TIMELINE_STARTED` with
   `"compactionMode":"AUTHORITY"`; `progress` says `AUTHORITY`; no `LOG_MERGED` or `LOG_RELEASED` entry
   exists; `HOLD_MOVED` entries appear after about 3 minutes and `progress.hold.index` rises; the UI shows
   the badge "mode B" and H instead of M and R in the ownership bar.
2. **K2, compaction.** After 1 to 2 minutes. **Pass:** `maintenance/` holds `COMPACTED` entries with keys
   `data/<first>-<last>-c<seq>.seg`, also for ranges above H; the replaced level-0 objects are `REPLACED`
   in the bucket view and are gone from `data/` about 30 s later (activity `OBJECTS_DELETED`); no
   `COMPACTION_FAILED` in the activity and none in `$WORK/ep.log`; bucket view has no `UNKNOWN`.
3. **K3, below the hold.** After 5 to 10 minutes. **Pass:** `COMPACTED` entries of level 2 and the
   `REMOVED` entry (retention `PT10M`) only have `lastIndex` / `upToIndex` below the hold H of their base
   commit; `progress.retained.index` rises; the first `data/` key moves.
4. **K4, the cluster deletes nothing.** **Pass:** the broker logs contain no cleaner or deletion lines
   (`grep -ci "delet" $WORK/broker-*.log` only matches `BackupRetention ... initialized`), and every object
   that disappeared from `data/` was replaced or removed by a `maintenance/` entry.
5. **K5, event plane rebuild.** `./event-plane.sh stop && ./event-plane.sh start` under load (empty catalog).
   **Pass:** `./event-plane.sh check` passes; `progress` shows the same hold and the current objects of the
   timeline (`objects` count equals the current objects of the bucket view), no `UNKNOWN`, no object below
   the hold lost, replaced objects deleted again after the grace period.
6. **K6, restore.** Stop the load and the cluster, copy the store (`cp -R "$STORE/k-authority"
   "$STORE/k-authority-copy"`), `export STORE_OVERRIDE=$STORE/k-authority-copy`, `./cluster.sh wipe`. Take
   the oldest `checkpoints[]` id of the commit entries as `OLD` and a recent one as `NEW`.
   `./restore.sh k-authority --logArchive.timeline=tl-0 --logArchive.checkpointId=$OLD`. **Pass:** refused
   with "restore point .. is below the restore hold of partition ..". The same with `$NEW`: "restored" for
   all brokers with the new timeline `tl-0.r<NEW>`, `compactionMode` `AUTHORITY`; start the cluster.

**Run on 2026-10-21:** K1 to K5 pass (first `COMPACTED` after 1 minute, `HOLD_MOVED` at 3 minutes, level-2
`COMPACTED` and a `REMOVED` below H after about 6 minutes, final hold 10141 / 11134, no `COMPACTION_FAILED`,
no cleaner deletion in the broker logs, rebuild of the catalog consistent). K6 refused `OLD` with the
expected message, but restore of any point first failed with `MismatchedInputException: Cannot map null into
type long` for `MaintenanceEntry.object.term`: the event plane writes the objects of `COMPACTED` entries
without `term`, the restore requires it. After adding `"term":1` to the entries in the store copy `NEW`
restored.

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
