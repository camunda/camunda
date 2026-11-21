# RDBMS concurrency kit

Reproduce lock waits and deadlocks between RDBMS exporters of different partitions and the
history cleanup **deterministically**, statement by statement, on every supported database.

Concurrency bugs in the exporter normally show up as rare, timing-dependent failures under load.
The kit takes the timing out: each participant (an exporter flush, a cleanup statement) runs on
its own thread that **stops before every statement**, and the test decides who runs next. So
"partition 1 updates row A, partition 2 updates row B, then each updates the other row" is
something you write down, not something you hope for.

What is real and what is not:

| Real                                                         | Test-controlled                          |
|--------------------------------------------------------------|------------------------------------------|
| `RdbmsExporter` with its handlers, queue order and flush SQL | when a flush runs, and on which thread   |
| `HistoryCleanupService`/deletion statements (`RdbmsWriters`) | when each statement runs                 |
| the database (Testcontainers) and its locking                | the records (built from `RecordFixtures`) |

## Quick start

```java
@RdbmsConcurrencyTest
class MyCaseConcurrencyIT {

  @TestTemplate
  void shouldNotDeadlockWhenTwoPartitionsUpdateTheSameRows(final CamundaRdbmsTestApplication app)
      throws Exception {
    try (final var kit = new RdbmsConcurrencyKit(app)) {
      // given: one exporter per partition, records queued but not written yet
      final var p1 = kit.exporter(1);
      final var p2 = kit.exporter(2);
      p1.export(/* records of partition 1, see KitRecords */);
      p2.export(/* records of partition 2 */);
      final var flush1 = kit.startFlush(p1); // thread "P1 flush", stops before its first statement
      final var flush2 = kit.startFlush(p2);

      // when: drive them statement by statement
      flush1.stepTo(update("BATCH_OPERATION")); // P1 stops before its first update
      flush2.stepTo(update("BATCH_OPERATION"));
      flush1.step(); // P1 runs its first update
      flush2.step(); // P2 runs its first update
      flush1.release(); // both run on; if they wait for each other,
      flush2.release(); // the database's deadlock detection decides

      // then: both ended without a failed statement
      kit.assertNoErrors(flush1, flush2);
    }
  }
}
```

The `*ConcurrencyIT` classes in this package hold complete scenarios to copy from, one case per
class (see [Scenarios](#scenarios-and-their-status)).

## Running

```bash
RDBMS_KIT_DB=POSTGRES ./mvnw verify -Prdbms -pl qa/acceptance-tests \
  -Dit.test='io/camunda/it/rdbms/concurrency/**/*ConcurrencyIT' -DskipTests=false -DskipUTs -Dquickly
```

`RDBMS_KIT_DB` takes `H2`, `POSTGRES`, `MYSQL`, `MARIADB`, `ORACLE` or `MSSQL`, or a list like
`MYSQL,POSTGRES`. Run one case with `-Dit.test=BatchOperationCounterConcurrencyIT`, one test with
`-Dit.test='HistoryCleanupPageScanConcurrencyIT#shouldNot...'`. Without `-Prdbms` no test runs.

Each test class declares the databases its case applies to, all by default:

```java
@RdbmsConcurrencyTest(databases = MSSQL) // only SQL Server has this case
class HistoryCleanupPageScanConcurrencyIT { ... }
```

| `RDBMS_KIT_DB` | A class runs on                                                            |
|----------------|----------------------------------------------------------------------------|
| not set        | nothing: the kit is opt-in, so the regular `rdbms` runs (e.g. in CI) skip it |
| set            | the listed databases the case applies to; skipped if none, with the reason |

In the IDE, set `RDBMS_KIT_DB` in the run configuration: `H2` runs a generic case in seconds,
`MSSQL` starts SQL Server for a SQL Server case.
The database containers are shared by all classes of a run; a run takes 20–90 s per database, most
of it container start.

## Concepts

### Sync points

A controlled thread stops before every statement, before every **row of a JDBC batch**, and
before commit. Each stop is a `SyncPoint`, printed like

```
SELECT EXPORTER_POSITION [1/1] (ExporterPositionMapper.findOneForUpdate)
UPDATE BATCH_OPERATION [1/2] (BatchOperationMapper.incrementCompletedOperationsCount)
UPDATE BATCH_OPERATION [2/2] (BatchOperationMapper.incrementCompletedOperationsCount)
commit
```

that is: operation, table, entry of the batch, MyBatis statement. Name the one to stop at with the
static matchers of `SyncPoint`, by what you see in the writer code:

| Matcher                                   | Stops before                                              |
|-------------------------------------------|-----------------------------------------------------------|
| `insert("FLOW_NODE_INSTANCE")`            | the first insert into the table (also `update`, `delete`)  |
| `upsert("PROCESS_DEF_VAR_NAME_LOOKUP")`   | an insert-or-update, whatever the vendor's SQL (`MERGE`, `INSERT … ON CONFLICT`) |
| `statement("BatchOperationMapper.upsertItem")` | that MyBatis statement: the id the writer passes to the queue |
| `….entry(2)`                              | only the 2nd row of that batch                             |
| `commit()`                                | the commit: everything written, all locks still held       |
| `any()`                                   | the next stop                                              |

Prefer `statement(...)` when a table has several statements of one kind (BATCH_OPERATION has
several updates). The writer code names them, e.g.
`"io.camunda.db.rdbms.sql.BatchOperationMapper.incrementCompletedOperationsCount"` in
`BatchOperationWriter`; the package can be left out.

The exporter writes each statement as one JDBC batch. The kit executes the batch **row by row**
(same statements, same order, one round trip each), so a test can stop a flush between two rows of
the same statement. Without that, all rows go to the database in one call, and a row-order deadlock
depends on microsecond timing.

You do not need to know the flush's order up front: `stepTo` fails with the list of statements the
thread ran when it never reaches the matcher, and `kit.report(thread)` lists them too. To look at a
flush first, start it, `release()` it, `waitUntilDone()`, and print `thread.history()`.

### Steps

| Method             | Does                                                                |
|--------------------|---------------------------------------------------------------------|
| `stepTo(matcher)`  | setup: run until the thread stops before a matching sync point (or stay, if it is there). Fails, with the statements run and the lock waits, if the thread ends or waits for a lock on the way: the scenario does not run as you expect. |
| `runTo(matcher)`   | the same, but returns how far the thread got (`Progress`: reached, waits for a lock, or ended) instead of failing. Use it where not waiting **is** the expectation, and assert in `then`: `assertThat(progress.waitsForLock()).describedAs("%s", progress).isFalse()`. |
| `step()`           | run the next statement. If it waits for a lock held by another thread, `step()` returns anyway: go on with the other thread. |
| `release()`        | run to the end without stopping; returns at once                    |
| `waitUntilDone()`  | wait up to 60 s for the end; `true` if it ended                     |

A stopped thread holds its locks and runs nothing. The kit counts a statement that has not
finished after 3 s as waiting for a lock.

Release **all** threads before waiting for any: a released thread can wait for a lock of a thread
that is still stopped, and only releasing that one too lets the database resolve it.

### Errors

The exporter catches a failed flush, logs `Failed to flush records`, and keeps the records for the
next flush, exactly as in production. So a deadlock never surfaces as an exception of the work.
The kit records the first failed statement of each thread instead: **always assert with
`kit.assertNoErrors(threads...)`** (or on `thread.error()`). `kit.report(thread)` gives the error, the sync points passed and, for a
deadlock, the database's own deadlock record (InnoDB status, PostgreSQL error, Oracle trace,
SQL Server `xml_deadlock_report`).

### Lock evidence

`kit.lockWaits()` returns the database's current lock waits (`pg_locks`,
`performance_schema.data_lock_waits`, `information_schema.innodb_lock_waits`,
`sys.dm_tran_locks`, `v$session`); a failing `stepTo` includes them.

### Records and keys

`KitRecords` builds records with explicit keys from `RecordFixtures`; everything else in them is
random. Keys follow the engine layout, `KitRecords.key(partition, counter)`, so all keys of
partition 2 sort after all keys of partition 1, and `topCounter(n)` gives keys above every other
key of a partition (directly before the next partition's keys). Take counters from a run-unique
source: `KitRecords.nextKey(partition)`, `nextTopKey(partition)` and `nextName(prefix)`, since
the container is shared by all tests of a run.

### Seeding

`exporter.seedRootsWithElements(roots, elementsPerRoot)` and `seedRootsWithChildren(roots,
childrenPerRoot)` export and write root instances through the real exporter and return the root
keys, e.g. as expired roots for a cleanup. `seedBatchOperation()` and
`seedBatchOperationWithItems()` write an active batch operation (with a chunk of items) and return
its key. Seed before starting controlled threads.

On SQL Server, `kit.neighbouringRows(table, partition)` returns two rows of a partition on
neighbouring pages of the table's clustered index (via `%%physloc%%`), for scenarios about page
locks.

### Cleanup and deletion statements

`kit.startCleanup(partition, writers -> ...)` runs a statement with the `RdbmsWriters` the history
cleanup uses, on a controlled thread named "P2 cleanup":

```java
final var cleanup = kit.startCleanup(2, writers -> writers
    .getFlowNodeInstanceWriter()
    .deleteRootProcessInstanceRelatedData(rootKeys, 10_000));
```

Each such statement commits on its own, as in `HistoryCleanupService`.

## Scenarios and their status

Each deadlock found by the lock investigation has a scenario. ❌ = fails today because it reproduces
a known problem; it should pass once that is fixed.

The source of truth is the test method: each ❌ is a `@FailsToday(on = ..., reason = ...)` marker.
On those databases the expected failure is reported as **skipped** with the reason, so the suite
is green; if the test passes there, it fails with "fixed? remove @FailsToday". Only a failed
assertion counts as the expected failure: a `stepTo` that does not get where the scenario expects
still fails the test. The table below summarises the markers.

| Test class: scenario | H2 | MySQL | MariaDB | PostgreSQL | Oracle | SQL Server |
|---|---|---|---|---|---|---|
| `BatchOperationCounterConcurrencyIT`: two partitions increment the counters of b1, b2 in opposite order | ❌ deadlock | ❌ deadlock | ❌ deadlock | ❌ deadlock | ❌ deadlock | ❌ deadlock¹ |
| `VariableNameLookupConcurrencyIT`: two partitions insert the same new variable names of one process definition in opposite order | ❌ lock timeout² | ❌ deadlock | ❌ deadlock | ❌ deadlock | ❌ deadlock | ❌ deadlock |
| `VariableNameLookupConcurrencyIT`: two partitions insert the same single new name at the same time | ❌ lock timeout⁷ | ✅ | ✅ | ✅ | ❌ ORA-00001⁸ | ✅ |
| `VariableNameLookupConcurrencyIT`: existing names, and existing + new names, in opposite order (e.g. after an exporter restart) | ❌ | ✅ | ✅ | ✅ | ✅ / ❌ ORA-00001⁸ | ✅ |
| `VariableNameOrderFromEngineConcurrencyIT`: like the first row, with the VARIABLE records of a real broker with two partitions, which emitted the names in opposite order | ❌ lock timeout² | ❌ deadlock | ❌ deadlock | ❌ deadlock | ❌ deadlock | ❌ deadlock |
| `TableOrderAcrossBrokersConcurrencyIT`: a batch operation counter and a new variable name, the second broker with the other table order³ | ❌ lock timeout² | ❌ deadlock | ❌ deadlock | ❌ deadlock | ❌ deadlock | ❌ deadlock |
| `HistoryCleanupPageScanConcurrencyIT`: FLOW_NODE_INSTANCE cleanup vs another partition's flush | – | – | – | – | – | ❌ deadlock⁴ |
| `HistoryCleanupPageScanConcurrencyIT`: PROCESS_INSTANCE child cleanup vs another partition's flush | – | – | – | – | – | ❌ deadlock⁴ |
| `BatchOperationKeyLookupConcurrencyIT`: completing items of b2, and canceling b2, while another partition holds b1 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅¹³ |
| `BatchOperationCancelConcurrencyIT`: a batch operation is canceled on one partition while another partition still inserts its items | ✅ | ✅⁵ | ✅⁵ | ✅ | ✅ | ❌ deadlock |
| `CleanupGapLockConcurrencyIT`: the cleanup deletes elements of expired roots while the same partition's flush inserts the first elements of running roots in the gaps between them | ✅ | ✅⁶ | ✅⁶ | ✅ | ✅ | ✅ |
| `HistoryCleanupBlockingConcurrencyIT`: the cleanup holds its locks; the flush of the same partition inserts for a running instance inside the deleted range | ✅ | ❌ blocked⁹ | ❌ blocked⁹ | ✅ | ✅ | ❌ blocked¹⁰ |
| `HistoryCleanupBlockingConcurrencyIT`: the same, flush of another partition | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ blocked¹⁰ |
| `HistoryCleanupBlockingConcurrencyIT`: history deletion of 100 instances holds its locks; the flush inserts for a running instance between them | ✅ | ❌ blocked⁹ | ❌ blocked⁹ | ✅ | ✅ | ✅ |
| `BatchOperationCascadeBlockingConcurrencyIT`: the cleanup deletes an expired batch operation with 6,000 items (cascade) and holds its locks; another partition adds items to a running one | ✅ | ❌ blocked¹⁴ | ❌ blocked¹⁴ | ✅ | ✅ | ❌ blocked¹⁴ |
| `HistoryCleanupCascadeBlockingConcurrencyIT`: process instance tags (cascade) vs a new child instance with tags | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `HistoryCleanupCascadeBlockingConcurrencyIT`: user task candidates (cascade) vs changing a running task's candidates | ✅ | ❌ blocked¹⁵ | ✅ | ✅ | ✅ | ❌ blocked¹⁵ |
| `HistoryCleanupCascadeBlockingConcurrencyIT`: decision inputs/outputs (cascade) vs a new decision instance | ✅ | ❌ blocked¹⁵ | ❌ blocked¹⁵ | ✅ | ✅ | ❌ blocked¹⁵ |
| `HistoryCleanupCascadeBlockingConcurrencyIT`: agent element instances (cascade) vs replacing a running agent's element instances | ✅ | ❌ blocked¹⁵ | ✅ | ✅ | ✅ | ❌ blocked¹⁵ |
| `WaitStateCleanupAcrossPartitionsConcurrencyIT`: P2's cleanup finds no wait states; P1's flush inserts its newest | ✅ | ❌ blocked¹⁶ | ❌ blocked¹⁶ | ✅ | ✅ | ✅ |
| `BatchOperationHistoryCleanupConcurrencyIT`: two partitions clean up the same batch operations (with cascades) | ❌ lock timeout⁷ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `FlushIsolationLevelConcurrencyIT`: the open flush transaction's isolation level, read from the database | – | ❌ REPEATABLE READ | ❌ REPEATABLE READ | – | – | ✅ READ COMMITTED |
| `DisjointFlushesConcurrencyIT`: two partitions flush disjoint instances, the second in the other table order | ✅ | ✅¹¹ | ✅ | ✅ | ✅ | ✅ |
| `AdjacentKeysAcrossPartitionsConcurrencyIT`: three partitions insert/update adjacent keys, in turns and with locks held, both orders | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

1. SQL Server, before the fix of footnote 13: no deadlock, because P2 already waits for P1 at b2, before it holds anything P1
   needs. The driver sends the string key as `nvarchar(4000)`, the column is `VARCHAR(255)`, so the
   update cannot seek the primary key and reads (and locks) other rows: updates of different batch
   operations wait for each other.
2. H2 does not detect a cycle on a pending unique insert; the flushes wait for each other until
   the lock timeout.
3. The flush orders tables by a `HashMap` keyed by the `ContextType` enum, i.e. by the constants'
   identity hash codes: fixed within one JVM, different between brokers. All exporters of a test
   share one JVM, so partition 2 uses `ControllableExporter#flushInTableOrder` with
   `otherBrokersTableOrder(...)` to write its real queued statements in the other broker's table
   order.
4. SQL Server: the cleanup DELETE scans the whole table (Clustered Index Scan) with page locks; it
   holds one page while waiting for the next, which the other partition's flush holds, and the
   flush then needs the first page. The scenario picks the two rows by their physical page
   (`%%physloc%%`). The cleanup is the deadlock victim.
5. MySQL/MariaDB: the cancel already waits at the batch operation row, for the shared lock the
   other partition's item inserts hold for their foreign key check; the other partition then
   finishes and the cancel continues. A wait, not a deadlock.
6. MySQL/MariaDB: the cleanup's next-key locks make the flush wait, but for the entry after its
   range (the flush's uncommitted insert) the cleanup takes a gap-only lock, so it never waits for
   the flush: no cycle with the production plan (root key index). A primary-key scan, which MySQL
   picks on tiny tables, could form one.
7. H2's lock timeout is shorter than the time the test holds P1 at its step: a test artefact.
8. Oracle's `MERGE ... WHEN NOT MATCHED THEN INSERT` is not atomic against a concurrent insert of
   the same key: both partitions see the name as missing, the second waits for the first, then fails
   with a unique constraint violation instead of skipping the row. No opposite order needed: any two
   partitions seeing the same new name at the same time.
9. MySQL/MariaDB: cleanup and deletion run at REPEATABLE READ; their next-key locks block inserts
   right next to the deleted entries (same partition only). READ COMMITTED would avoid it.
10. SQL Server: the 6,000-row DELETE escalates to a table lock (`OBJECT | IX | LCK_M_IX`), which
    blocks every partition. Smaller deletes (below ~5,000 locks) would avoid it.
11. MySQL with stale statistics: after a quick load InnoDB still reports the table as empty, and
    the flush's `updateHistoryCleanupDate ... WHERE PROCESS_INSTANCE_KEY IN (...)` scans the whole
    primary key (`EXPLAIN`: type `index`) instead of looking up its keys (type `range` after
    `ANALYZE TABLE`). At REPEATABLE READ the scan locks every row, including other partitions':
    two flushes deadlock. Real until statistics are recalculated (new or fast-growing installs).
    InnoDB recalculates them in the background, so the outcome depends on timing; the scenario
    therefore runs with fresh statistics (`kit.analyze(...)` after seeding) and the stale case is
    documented, not tested.

12. SQL Server with `READ_COMMITTED_SNAPSHOT ON`, which the
    [RDBMS troubleshooting guide](https://docs.camunda.io/docs/self-managed/deployment/helm/configure/database/rdbms-troubleshooting/#transaction-isolation-levels)
    recommends against deadlocks (`RDBMS_KIT_MSSQL_RCSI=true`; the container then creates a
    database `camunda`, since `master` does not allow the option): every scenario has the same
    result as without it. The option only stops plain reads from taking shared locks; these
    deadlocks and the blocking are between writers (UPDATE, DELETE, MERGE), which lock as before.

13. SQL Server: failed before the fix in `BatchOperationMapper.xml` (P2 waits for an update lock
    on a `PK_BATCH_OPERATION` key of b1): `BATCH_OPERATION_KEY` is `VARCHAR`, the driver sends
    strings as `NVARCHAR`, so SQL Server converted the column and could not seek the key. The SQL
    Server statements now cast the parameter (fragment `batchOperationKey`). With it,
    `BatchOperationCounterConcurrencyIT` deadlocks on SQL Server too (no early wait left; the
    opposite-order cycle remains), as on every other database.

14. MySQL/MariaDB: the parent DELETE, not the cascade. `HISTORY_CLEANUP_DATE` has no index, so the
    cleanup reads every batch operation and at REPEATABLE READ keeps an X lock on each, running ones
    included; the other partition's item insert waits for its foreign key check. SQL Server: the
    cascade into 6,000 items escalates to a table lock on `BATCH_OPERATION_ITEM`
    (`OBJECT | IX | LCK_M_IX`), which blocks every partition's item inserts.

15. SQL Server runs cascades with key-range locks (`RangeX-X` per deleted child, SERIALIZABLE
    regardless of the session's isolation or `READ_COMMITTED_SNAPSHOT`); an insert into a locked
    range waits (`RangeI-N`), already with 100 expired roots. The decision cascade (14 children per
    instance) escalates to a table lock at 100 decision instances. MySQL/MariaDB: REPEATABLE READ
    gap locks on the parent's root key index or the child's index.
16. MySQL/MariaDB at REPEATABLE READ: the cleanup searches the root key index for keys without
    rows and locks the gap up to the next entry; partition 2's keys lie above all of partition 1's,
    so the gap reaches down to partition 1's newest entry, where its flush inserts. Any table
    blocks this way when a partition's expired roots have no rows left in it.

## Writing a scenario

1. Write down the interleaving you suspect, as statements: "P1 updates X, P2 updates Y, P1 updates
   Y, P2 updates X".
2. Build the records that make the exporter write exactly those statements. Check with
   `thread.history()` that the flush has the shape you expect.
3. Drive the threads with `stepTo`/`step` into the interleaving. `stepTo` fails when the statement
   never comes or the thread gets stuck before it, so a scenario that drifts fails loudly.
4. Release all threads, then state the expectation in `// then`:
   * **must not deadlock**: `kit.assertNoErrors(threads...)`. It waits for the threads and fails
     with each failed thread's report; a deadlock victim is a failed statement.
   * **must not wait** (blocking): let the waiting side get there with `runTo` instead of
     `stepTo` in `// when`, and assert on the result:
     `assertThat(progress.waitsForLock()).describedAs("%s", progress).isFalse()`, then
     `kit.assertNoErrors(...)`.

   Write the expectation in the `// then` comment, e.g. `// then: no deadlock: both flushes
   committed, neither was chosen as the victim`.
5. Where it fails today, mark the test method with `@FailsToday(on = ..., reason = ...)`, one per
   reason.
6. Run on H2 first (seconds), then on every database: lock behaviour differs per vendor.
7. Document the class so a reader understands the case without running it. Every scenario uses
   the same sections:

   ```java
   /**
    * <b>Situation.</b> What happens in production, which rows are shared, and why the order can
    * differ between the participants.
    *
    * <p><b>Interleaving</b> (time flows down):
    *
    * <pre>
    *   P1 flush                          P2 flush
    *   update b1   holds b1
    *                                     update b2   holds b2
    *   update b2   waits for P2
    *                                     update b1   waits for P1: deadlock
    * </pre>
    *
    * <p><b>Today.</b> The result per database, and why it differs.
    *
    * <p><b>Fix direction.</b> What would make the test pass.
    */
   ```

   In the test, comment each `step`/`release` with what the statement does and which lock it
   takes or waits for. Name participants and rows the same way in the timeline, the variables and
   the comments (P1/P2, b1/b2, ...). Add the case to the
   [status table](#scenarios-and-their-status).

## Pitfalls

* **Deadlock detection takes time.** MySQL, MariaDB and H2 detect at once, PostgreSQL after
  `deadlock_timeout` (1 s), Oracle after about 3 s, SQL Server after up to 5 s. `waitUntilDone()`
  waits 60 s. A step that "waits for a lock" only means the statement did not finish within 3 s.
* **Isolation.** The flush asks for READ COMMITTED, but the Spring-managed MyBatis transaction
  ignores the requested level: flush and cleanup both run at the database default, i.e. REPEATABLE
  READ on MySQL and MariaDB (next-key and gap locks for range and `IN` statements), READ COMMITTED
  elsewhere. Check with `information_schema.innodb_trx.trx_isolation_level`.
* **Large seeds.** `export()` may flush on the calling (test) thread when the queue exceeds its
  memory limit. Seed before starting controlled threads.
* **SQL Server collation** is case-insensitive: random strings like tags `T` and `t` collide.
  `KitRecords` drops tags for that reason.
* **Data size changes plans.** Locking follows the plan, and the plan follows table size. Seed
  enough rows to get the production plan, and check it when a vendor surprises you.

## Files

| File                       | Role                                                                  |
|----------------------------|-----------------------------------------------------------------------|
| `RdbmsConcurrencyTest`     | annotation for a test class: tag `rdbms`, the databases the case applies to |
| `Database`                 | the supported databases                                               |
| `RdbmsConcurrencyKit`      | entry point: exporters, `startFlush`, `startCleanup`, `assertNoErrors`, database evidence |
| `ThreadControl`            | one controlled thread: `step`, `stepTo`, `release`, `error`, `history` |
| `SyncPoint`                | where a thread stops, and the matchers to name it                     |
| `ControlledStatements`     | MyBatis plugin that adds the sync points and steps through JDBC batches |
| `ControllableExporter`     | the real exporter of one partition, flushed only on demand; `flushInTableOrder` simulates another broker |
| `KitRecords`               | records with engine-layout keys                                       |
| `FailsToday`               | marks a test method that fails today on some databases, with the reason |
| `Progress`                 | how far `runTo` got: reached, waits for a lock, or ended               |

All of the above are in the package `kit`. The scenarios, one case per `*ConcurrencyIT` class, are
grouped by what they write:

| Package          | Scenarios                                                                    |
|------------------|------------------------------------------------------------------------------|
| `batchoperation` | `BatchOperationCounter`, `BatchOperationCancel`, `BatchOperationHistoryCleanup` |
| `variable`       | `VariableNameLookup`, `VariableNameOrderFromEngine`                          |
| `cleanup`        | `HistoryCleanupBlocking`, `HistoryCleanupPageScan`, `CleanupGapLock`, `BatchOperationCascadeBlocking`, `HistoryCleanupCascadeBlocking`, `WaitStateCleanupAcrossPartitions` |
| `flush`          | `TableOrderAcrossBrokers`, `DisjointFlushes`, `AdjacentKeysAcrossPartitions`, `FlushIsolationLevel` |
