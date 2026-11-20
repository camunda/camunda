/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.concurrency.batchoperation;

import static io.camunda.it.rdbms.concurrency.kit.Database.H2;
import static io.camunda.it.rdbms.concurrency.kit.Database.MARIADB;
import static io.camunda.it.rdbms.concurrency.kit.Database.MSSQL;
import static io.camunda.it.rdbms.concurrency.kit.Database.MYSQL;
import static io.camunda.it.rdbms.concurrency.kit.Database.ORACLE;
import static io.camunda.it.rdbms.concurrency.kit.Database.POSTGRES;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.canceledByBatchOperation;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.nextKey;
import static io.camunda.it.rdbms.concurrency.kit.SyncPoint.statement;

import io.camunda.it.rdbms.concurrency.kit.FailsToday;
import io.camunda.it.rdbms.concurrency.kit.RdbmsConcurrencyKit;
import io.camunda.it.rdbms.concurrency.kit.RdbmsConcurrencyTest;
import io.camunda.it.rdbms.concurrency.kit.SyncPoint;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import org.junit.jupiter.api.TestTemplate;

/**
 * <b>Situation.</b> Two batch operations b1 and b2 run at the same time. Every partition executes
 * its share of their items independently and exports a result per item; for each result the
 * exporter increments the completed-operations counter of the batch operation (one shared
 * BATCH_OPERATION row per batch operation, written by every partition). The counters are
 * incremented in the order the results arrive, so partition 1 may increment b1 then b2 while
 * partition 2 increments b2 then b1.
 *
 * <p><b>Interleaving</b> (time flows down):
 *
 * <pre>
 *   P1 flush                          P2 flush
 *   increment b1   holds b1
 *                                     increment b2   holds b2
 *   increment b2   waits for P2
 *                                     increment b1   waits for P1: deadlock
 * </pre>
 *
 * <p><b>Today.</b> Deadlock on every database except SQL Server. On SQL Server P2 already waits at
 * b2 for P1's lock on b1: the driver sends the key as {@code nvarchar}, the column is {@code
 * VARCHAR}, so the update cannot seek its row and reads (and locks) other rows too. A wait, not a
 * deadlock, but updates of unrelated batch operations block each other.
 *
 * <p><b>Fix direction.</b> Increment the counters of one flush in key order (then both partitions
 * lock b1 before b2); on SQL Server also send strings as {@code varchar}.
 */
@RdbmsConcurrencyTest
class BatchOperationCounterConcurrencyIT {

  private static final SyncPoint.Match INCREMENT_COMPLETED =
      statement("BatchOperationMapper.incrementCompletedOperationsCount");

  @TestTemplate
  @FailsToday(
      on = {H2, POSTGRES, MYSQL, MARIADB, ORACLE, MSSQL},
      reason = "the flushes increment b1 and b2 in opposite order: deadlock")
  void shouldNotDeadlockWhenTwoPartitionsCompleteItemsOfTwoBatchOperationsInOppositeOrder(
      final CamundaRdbmsTestApplication app) throws Exception {
    try (final var kit = new RdbmsConcurrencyKit(app)) {
      // given: batch operations b1 and b2; P1 gets item results for b1, b2, P2 for b2, b1
      final var p1 = kit.exporter(1);
      final var p2 = kit.exporter(2);
      final long b1 = p1.seedBatchOperation();
      final long b2 = p1.seedBatchOperation();
      p1.export(
          canceledByBatchOperation(1, nextKey(1), b1), canceledByBatchOperation(1, nextKey(1), b2));
      p2.export(
          canceledByBatchOperation(2, nextKey(2), b2), canceledByBatchOperation(2, nextKey(2), b1));
      final var flush1 = kit.startFlush(p1);
      final var flush2 = kit.startFlush(p2);

      // when
      flush1.stepTo(INCREMENT_COMPLETED); // P1 stops before incrementing b1
      flush2.stepTo(INCREMENT_COMPLETED); // P2 stops before incrementing b2
      flush1.step(); // P1 increments b1 and holds its row lock
      flush2.step(); // P2 increments b2 and holds its row lock
      flush1.release(); // P1 increments b2: waits for P2
      flush2.release(); // P2 increments b1: waits for P1, the database detects the deadlock

      // then: no deadlock: both flushes committed, neither was chosen as the victim
      kit.assertNoErrors(flush1, flush2);
    }
  }
}
