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

## 4. What we record

For every scenario: pass/fail per expectation, anything surprising, log excerpts for failures, and
the bucket listing at the end. Findings go into the PoC's open questions or become fixes.
