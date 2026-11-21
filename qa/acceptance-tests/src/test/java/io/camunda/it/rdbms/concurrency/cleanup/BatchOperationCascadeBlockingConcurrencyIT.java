/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.concurrency.cleanup;

import static io.camunda.it.rdbms.concurrency.kit.Database.MARIADB;
import static io.camunda.it.rdbms.concurrency.kit.Database.MSSQL;
import static io.camunda.it.rdbms.concurrency.kit.Database.MYSQL;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.batchOperationChunkCreated;
import static io.camunda.it.rdbms.concurrency.kit.SyncPoint.commit;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.it.rdbms.concurrency.kit.FailsToday;
import io.camunda.it.rdbms.concurrency.kit.RdbmsConcurrencyKit;
import io.camunda.it.rdbms.concurrency.kit.RdbmsConcurrencyTest;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.TestTemplate;

/**
 * <b>Situation.</b> The history cleanup deletes an expired batch operation, and with it, through
 * {@code ON DELETE CASCADE}, all its items in the same statement: the cleanup's limit counts batch
 * operations, not items, so one expired batch operation with 6,000 items is one DELETE of 6,001
 * rows. Meanwhile another partition's flush adds a chunk of items to a batch operation that is
 * still running (insert the items, increment its total count). The two batch operations share no
 * rows.
 *
 * <p><b>Interleaving</b> (time flows down):
 *
 * <pre>
 *   P1 cleanup                        P2 flush
 *   DELETE expired b1 (+ 6,000 items by cascade), stop before commit (holds its locks)
 *                                     insert items of running b2, increment b2, up to its commit
 *   then: the flush did not wait for a lock
 * </pre>
 *
 * <p><b>Today.</b>
 *
 * <ul>
 *   <li>MySQL, MariaDB: the flush waits. Not for the cascade into the items, but for the parent
 *       row: the cleanup finds expired batch operations by {@code HISTORY_CLEANUP_DATE}, which has
 *       no index, so it reads every batch operation, and at REPEATABLE READ it keeps an X lock on
 *       each row it read, running b2 included. P2's item insert needs a shared lock on b2 for its
 *       foreign key check.
 *   <li>SQL Server: the flush waits. Deleting 6,000 items through the cascade takes more than about
 *       5,000 row locks, so SQL Server escalates them to one table lock (X) on
 *       BATCH_OPERATION_ITEM; P2's item insert needs an intent lock (IX) on the table.
 *   <li>PostgreSQL, Oracle, H2: only the deleted rows are locked.
 * </ul>
 *
 * <p><b>Fix direction.</b> MySQL/MariaDB: READ COMMITTED (InnoDB then releases the locks on rows
 * that do not match), or an index on {@code BATCH_OPERATION.HISTORY_CLEANUP_DATE}, so the cleanup
 * reads only expired batch operations. SQL Server: delete the items in chunks below the escalation
 * threshold before the parent, so the cascade finds nothing left.
 */
@RdbmsConcurrencyTest
class BatchOperationCascadeBlockingConcurrencyIT {

  @TestTemplate
  @FailsToday(
      on = {MYSQL, MARIADB},
      reason =
          "the cleanup scans every batch operation at REPEATABLE READ and keeps an X lock on running b2")
  @FailsToday(
      on = MSSQL,
      reason = "the cascade deletes more than ~5,000 items and escalates to a table lock")
  void shouldNotBlockAddingItemsToARunningBatchOperation(final CamundaRdbmsTestApplication app)
      throws Exception {
    try (final var kit = new RdbmsConcurrencyKit(app)) {
      // given: running batch operations, among them b2, and an expired b1 with 6,000 items
      final var p1 = kit.exporter(1);
      final var p2 = kit.exporter(2);
      for (int i = 0; i < 20; i++) {
        p1.seedBatchOperationWithItems();
      }
      final long b2 = p1.seedBatchOperationWithItems();
      final long b1 = p1.seedBatchOperationWithItems(6_000);
      final var now = OffsetDateTime.now();
      kit.write(
          1,
          writers ->
              writers
                  .getBatchOperationWriter()
                  .scheduleForHistoryCleanup(String.valueOf(b1), now.minusDays(1)));
      p2.export(batchOperationChunkCreated(2, b2, 10));
      final var cleanup =
          kit.startCleanup(1, writers -> writers.getBatchOperationWriter().cleanupHistory(now, 1));
      final var flush = kit.startFlush(p2);

      // when: the cleanup deletes b1 and its items and holds its locks; P2 adds items to b2
      cleanup.stepTo(commit());
      final var progress = flush.runTo(commit());
      cleanup.release();
      flush.release();

      // then: the flush got to its commit without waiting for the cleanup's locks
      assertThat(progress.waitsForLock()).describedAs("%s", progress).isFalse();
      kit.assertNoErrors(cleanup, flush);
    }
  }
}
