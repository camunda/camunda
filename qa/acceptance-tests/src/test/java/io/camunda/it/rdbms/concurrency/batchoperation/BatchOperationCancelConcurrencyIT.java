/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.concurrency.batchoperation;

import static io.camunda.it.rdbms.concurrency.kit.Database.MSSQL;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.batchOperationCanceled;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.batchOperationChunkCreated;
import static io.camunda.it.rdbms.concurrency.kit.SyncPoint.statement;

import io.camunda.it.rdbms.concurrency.kit.FailsToday;
import io.camunda.it.rdbms.concurrency.kit.RdbmsConcurrencyKit;
import io.camunda.it.rdbms.concurrency.kit.RdbmsConcurrencyTest;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import org.junit.jupiter.api.TestTemplate;

/**
 * <b>Situation.</b> A batch operation b1 is canceled while partition 2 still creates its items. The
 * cancel is distributed to all partitions, and each partition's exporter then updates the shared
 * BATCH_OPERATION row of b1 and sets <em>all</em> ACTIVE items of b1 to CANCELED, including the
 * items other partitions' exporters write (with pending items exported, the default). Within a
 * flush inserts come before updates: partition 2 inserts its new items, then increments b1's total
 * count; partition 1 updates b1, then the items.
 *
 * <p><b>Interleaving</b> (time flows down):
 *
 * <pre>
 *   P1 flush (cancel)                 P2 flush (new items)
 *                                     insert items of b1       holds the new items
 *   update b1 (canceled)  holds b1
 *   cancel all items of b1            waits for P2's new items
 *                                     increment total count of b1  waits for P1: deadlock
 * </pre>
 *
 * <p><b>Today.</b>
 *
 * <ul>
 *   <li>SQL Server: deadlock. Its update reads the uncommitted items and waits for them.
 *   <li>MySQL/MariaDB: P1 already waits at b1, before it holds anything: the foreign key check of
 *       P2's item inserts holds a shared lock on b1. P2 finishes, then P1 goes on. A wait only.
 *   <li>PostgreSQL, Oracle, H2: the update does not see the uncommitted items and does not wait.
 * </ul>
 *
 * <p><b>Fix direction.</b> Update the shared BATCH_OPERATION row last, or retry the flush on a
 * deadlock.
 */
@RdbmsConcurrencyTest
class BatchOperationCancelConcurrencyIT {

  @TestTemplate
  @FailsToday(
      on = MSSQL,
      reason =
          "the cancel holds b1 and waits for the other partition's uncommitted items, which waits for b1: deadlock")
  void shouldNotDeadlockWhenBatchOperationIsCanceledWhileAnotherPartitionAddsItems(
      final CamundaRdbmsTestApplication app) throws Exception {
    try (final var kit = new RdbmsConcurrencyKit(app)) {
      // given: batch operation b1; P2 creates a chunk of items, P1 receives the cancel
      final var p1 = kit.exporter(1);
      final var p2 = kit.exporter(2);
      final long b1 = p1.seedBatchOperation();
      p2.export(batchOperationChunkCreated(2, b1));
      p1.export(batchOperationCanceled(1, b1));
      final var flush2 = kit.startFlush(p2);
      final var flush1 = kit.startFlush(p1);

      // when
      // P2 inserts its items and stops before incrementing the total count of b1
      flush2.stepTo(statement("BatchOperationMapper.incrementOperationsTotalCount"));
      // P1 updates b1 (MySQL/MariaDB: waits here for the foreign key lock), then cancels the items
      flush1.stepTo(statement("BatchOperationMapper.updateCompleted"));
      flush1.step();
      // P2 increments the total count of b1; P1 cancels the items, waiting for P2's (SQL Server)
      flush2.release();
      flush1.release();

      // then: no deadlock: both flushes committed, neither was chosen as the victim
      kit.assertNoErrors(flush1, flush2);
    }
  }
}
