# Running the log archive PoC locally

How to start the whole PoC stack on one machine, in each of its configurations: three brokers built
from the branch, a filesystem store standing in for the bucket and, where needed, the event plane.
The concept is in [`log-archive-concept.md`](log-archive-concept.md); the detailed test scenarios
and their expected results are in [`log-archive-manual-test.md`](log-archive-manual-test.md).

Everything below uses the scripts in [`log-archive-manual/`](log-archive-manual).

## What runs

- **Three brokers** from the local distribution: 2 partitions, replication factor 3, embedded
  gateway. Broker i serves REST on `8080 + i` and the management API on `9600 + i`.
- **A filesystem store** as the bucket, one folder per configuration:
  `/tmp/log-archive-manual/store/<configuration>`.
- **The event plane** (only for the configurations that use it) on port `8090`, with its demo
  profile: the catalog is in memory, so every start begins with an empty catalog and rebuilds it.
- Logs: `/tmp/log-archive-manual/broker-<i>.log`, `restore-<i>.log` and `ep.log`.

## Prerequisites

- Java 21, `bash`, `curl` and `jq`. Free ports: 8080–8082, 9600–9602, 26500–26522 and 8090.
- **The cluster**, built from `camunda/camunda`, branch `poc/log-archive`:

  ```bash
  git checkout poc/log-archive
  ./mvnw install -Dquickly -T1C        # also builds dist/target/camunda-zeebe
  ```
- **The event plane**, only for the configurations `i-eventplane`, `j-merge` and `k-authority`:
  `camunda/event-plane`, branch `poc/log-archive`, built with `./mvnw install -DskipTests`. The
  scripts expect it in `~/Development/projects/event-plane`; set `EP_DIR` if it is elsewhere.
- In every shell:

  ```bash
  cd zeebe/backup/docs/log-archive-manual && source ./env.sh
  ```

## Choose a configuration

Each configuration is a set of broker settings, chosen by name. The layers are those of the concept.

|   Configuration   |                                    What runs                                     |      Layer       | Event plane |
|-------------------|----------------------------------------------------------------------------------|------------------|-------------|
| `a-classic`       | existing on-demand backups only, as before the PoC                               | baseline         | no          |
| `b-continuous`    | continuous backup only: a backup every minute, a marker every 15 s, kept 1 day  | baseline         | no          |
| `c-archive`       | log archive only: a cut every second, a snapshot upload every minute             | 1                | no          |
| `d-both`          | continuous backup and log archive from the start; the handover follows once the archive is ready (not run since the handover was added) | 1 + 2 | no |
| `g-brownfield`    | continuous backup first; `cluster.sh upgrade` restarts with the log archive on and the handover follows | 2 | no |
| `h-rdbms`         | continuous backup with an RDBMS (H2) as secondary storage                       | baseline         | no          |
| `h-handover`      | like `g-brownfield`, with the RDBMS                                              | 2                | no          |
| `h-compacted`     | like `h-handover`, with tiny journal segments, so the log is compacted before the archive starts: the bridged restore | 2 | no |
| `i-eventplane`    | log archive, every commit entry announced to the event plane; it serves the catalog and the UI and changes nothing | 3, read only | yes |
| `j-merge`         | split ownership: the cluster merges and releases below the floor, the event plane rolls up and applies retention to what is released | 3 | yes |
| `k-authority`     | one authority: the event plane compacts (level 1 and 2) and removes below the hold, the cluster deletes nothing | 3 | yes |

`j-merge` and `k-authority` use short times so that something happens within minutes: 8 MB
journal segments, a backup every 30 seconds, a restore window of 3 minutes, a rollup period of 4
minutes and an event plane retention of 10 minutes. `k-authority` also compacts to level 1 at 1 MB
or after 1 minute and deletes after 30 seconds.

## Start, stop, reset

```bash
./event-plane.sh start                  # only for i-eventplane, j-merge, k-authority; start it first
./cluster.sh start <configuration>      # waits until every partition is healthy
./api.sh deploy                         # deploys the test process (one service task)

./cluster.sh stop                       # stops the brokers, keeps their data
./event-plane.sh stop
./cluster.sh wipe                       # deletes the brokers' data, keeps the store
rm -rf "$STORE/<configuration>"         # deletes the store of a configuration
```

`event-plane.sh` reads the configuration from `SCENARIO` (default `i-eventplane`): run
`export SCENARIO=j-merge` or `export SCENARIO=k-authority` in every shell that calls it.

For a fresh start, stop everything, wipe the brokers and delete the configuration's store.

## Load and backups

```bash
./api.sh load 100 tag1                  # creates 100 instances tagged tag1 (each waits in a job)
./api.sh load 20 tag2 8000              # 20 instances with an 8 KB payload, fills segments faster
./api.sh complete 50                    # completes up to 50 jobs: more records
./api.sh jobs                           # counts activatable jobs per tag (activates them for 10 min)
C=$(date +%s); ./api.sh backup $C       # takes a backup with id C (an id is needed without continuous backup)
./api.sh status $C                      # IN_PROGRESS, then COMPLETED
./api.sh backups                        # lists all backups
```

For continuous load, run `./api.sh load …; ./api.sh complete …` in a loop in a second shell.
`NODE=1 ./api.sh …` sends the requests to broker 1, for example while broker 0 is down.

## Look inside

```bash
./bucket.sh <configuration> [timeline]  # per partition: counts of commits, objects, chunks, snapshots; the newest commit entry
./bucket.sh old-backups <configuration> # old-style backups and metadata.json
./bucket.sh handover <configuration>    # handover files on the old side
./bucket.sh handover-commits <configuration>   # HANDOVER commit entries in the archive
./bucket.sh wait-handover <configuration>      # waits for the handover on every partition, prints H
./bucket.sh merge <configuration>       # j-merge: merges, releases, level-1 rollups
ls -R "$STORE/<configuration>/log-archive" | less   # the bucket itself

./event-plane.sh overview               # timelines per partition as the event plane sees them
./event-plane.sh check                  # has the event plane caught up with the store?
./event-plane.sh objects 1              # the objects of partition 1's current path, checked for gaps
```

The event plane's UI is at `http://localhost:8090/log-archive-ui` (user and password `demo`). It
shows the timelines, objects with their levels, the ownership bar (released index R or hold H),
the activity and the bucket view.

## Fault drills

```bash
L=$(./cluster.sh leader 1)              # the leader of partition 1
NODE=1 ./cluster.sh kill $L             # SIGKILL it (use another broker as NODE if L is 1)
./cluster.sh restart $L <configuration> # start it again
./event-plane.sh kill                   # SIGKILL the event plane; start it again with an empty catalog
```

A store outage: with `c-archive`, `./cluster.sh store-off` makes the store read-only, and
`./cluster.sh store-on` makes it writable again.

## Restore

Restore runs per broker on its data directory, with the cluster stopped. Restore into a copy of the
store, so the restored cluster does not write into the store you still want to read:

```bash
./cluster.sh stop && ./cluster.sh wipe
cp -R "$STORE/<configuration>" "$STORE/<configuration>-copy"
export STORE_OVERRIDE="$STORE/<configuration>-copy"
./restore.sh <configuration> <restore arguments>
./cluster.sh start <configuration> && ./api.sh jobs
```

|                    Restore                    |                        Arguments                         |
|-----------------------------------------------|----------------------------------------------------------|
| an old-style backup                           | `--backupId=<id>`                                        |
| a checkpoint in the archive, timeline named   | `--logArchive.timeline=tl-0 --logArchive.checkpointId=<id>` |
| after a handover, by id                       | `--backupId=<id>` (restore finds old backup or archive)  |
| a time range (with an RDBMS, `h-*`)           | `--to=<time>`, an ISO time with milliseconds             |

After a restore from the archive, the cluster continues on the new timeline `tl-0.r<id>`; with an
event plane running, `./event-plane.sh overview` shows it as current with its takeover point. With
`j-merge` and `k-authority`, a restore point older than the window is refused ("below the restore
floor" or "below the restore hold").

## Recipes

**Layer 1, the archive alone** (`c-archive`): start, deploy, `load 100 a`, wait a minute for the
first snapshot, `backup $C`, `load 60 b`, restore to C with the timeline named: 100 jobs.

**Layer 2, the upgrade** (`g-brownfield`): start, deploy, load, wait for two old backups, then
`./cluster.sh upgrade g-brownfield`. `./bucket.sh wait-handover g-brownfield` prints H after a few
minutes. Restore to an old backup and to a backup after H, each with `--backupId`, on copies of the
store.

**Layer 3, the event plane reading** (`i-eventplane`): start the event plane, then the cluster,
load, and watch `./event-plane.sh check` and the UI. Kill and start the event plane: it catches up
from an empty catalog.

**Layer 3, one authority** (`k-authority`): `export SCENARIO=k-authority`, start the event plane
and the cluster, deploy, and load for about 12 minutes
(`./api.sh load 20 rN 8000; ./api.sh complete 20; sleep 3` in a loop). Level 1 compactions appear
after a minute, the hold moves after about 3 minutes, level 2 and removals below the hold after
about 6. Then restore an old checkpoint (refused) and a recent one (restored) on a copy of the store.

**Split ownership, for comparison** (`j-merge`): the same with `SCENARIO=j-merge` and
`./bucket.sh merge j-merge` to watch merges and releases.

## Pitfalls

- Run one configuration at a time: they share the brokers' ports and data directories.
- `./api.sh jobs` activates the jobs it counts for 10 minutes; a second count within that time sees
  fewer.
- Without continuous backup, `./api.sh backup` needs an id: use `$(date +%s)`.
- With an RDBMS, restore with `--to` only; `--from` must fall into a narrow window, inherited from
  continuous backup. `--to` needs milliseconds.
- Broker 0 is the gateway the scripts talk to. When it is down, prefix commands with `NODE=1`.
- macOS ships bash 3.2; the scripts work with it, but `source <(…)` does not.
