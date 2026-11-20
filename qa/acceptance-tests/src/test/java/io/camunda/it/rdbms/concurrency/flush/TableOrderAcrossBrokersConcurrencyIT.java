/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.concurrency.flush;

import static io.camunda.it.rdbms.concurrency.kit.ControllableExporter.otherBrokersTableOrder;
import static io.camunda.it.rdbms.concurrency.kit.Database.H2;
import static io.camunda.it.rdbms.concurrency.kit.Database.MARIADB;
import static io.camunda.it.rdbms.concurrency.kit.Database.MSSQL;
import static io.camunda.it.rdbms.concurrency.kit.Database.MYSQL;
import static io.camunda.it.rdbms.concurrency.kit.Database.ORACLE;
import static io.camunda.it.rdbms.concurrency.kit.Database.POSTGRES;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.canceledByBatchOperation;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.nextKey;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.nextName;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.variableCreated;
import static io.camunda.it.rdbms.concurrency.kit.SyncPoint.statement;

import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.it.rdbms.concurrency.kit.ControllableExporter;
import io.camunda.it.rdbms.concurrency.kit.FailsToday;
import io.camunda.it.rdbms.concurrency.kit.RdbmsConcurrencyKit;
import io.camunda.it.rdbms.concurrency.kit.RdbmsConcurrencyTest;
import io.camunda.it.rdbms.concurrency.kit.SyncPoint;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import org.junit.jupiter.api.TestTemplate;

/**
 * <b>Situation.</b> Sorting the writes within each table (the fix for {@code
 * BatchOperationCounterConcurrencyIT} and {@code VariableNameLookupConcurrencyIT}) is not enough: a
 * flush writes table by table, and the table order differs between brokers. The flush groups its
 * statements in a {@code HashMap} keyed by the {@link ContextType} enum, i.e. by the constants'
 * identity hash codes: fixed within one JVM, different in every other one. Two brokers that write
 * one shared row each in two tables can lock them in opposite order. Here: the counter of batch
 * operation b1 (BATCH_OPERATION) and a new variable name x of one process definition
 * (PROCESS_DEF_VAR_NAME_LOOKUP).
 *
 * <p><b>How.</b> All exporters of a test run in one JVM, so partition 2 plays the other broker: it
 * writes its real queued statements in the opposite table order ({@link
 * ControllableExporter#flushInTableOrder}).
 *
 * <p><b>Interleaving</b> (time flows down; A and B are the two tables, in this JVM's order):
 *
 * <pre>
 *   P1 flush (this broker)            P2 flush (other broker)
 *   write row of A   holds it
 *                                     write row of B   holds it
 *   write row of B   waits for P2
 *                                     write row of A   waits for P1: deadlock
 * </pre>
 *
 * <p><b>Today.</b> Deadlock on every database (H2 waits until its lock timeout).
 *
 * <p><b>Fix direction.</b> A fixed table order, e.g. an {@code EnumMap} instead of the {@code
 * HashMap}.
 */
@RdbmsConcurrencyTest
class TableOrderAcrossBrokersConcurrencyIT {

  /** The write of the shared row in either table. */
  private static final SyncPoint.Match SHARED_ROW =
      statement("BatchOperationMapper.incrementCompletedOperationsCount")
          .or(statement("VariableMapper.insertLookupIfNotExists"));

  @TestTemplate
  @FailsToday(
      on = {POSTGRES, MYSQL, MARIADB, ORACLE, MSSQL},
      reason =
          "each flush holds the shared row of its first table and needs the other one: deadlock")
  @FailsToday(
      on = H2,
      reason = "H2 does not detect this deadlock; the flushes wait until the lock timeout")
  void shouldNotDeadlockWhenTwoBrokersWriteSharedRowsInDifferentTableOrder(
      final CamundaRdbmsTestApplication app) throws Exception {
    try (final var kit = new RdbmsConcurrencyKit(app)) {
      // given: both partitions complete an item of b1 and see the new name x
      final var p1 = kit.exporter(1);
      final var p2 = kit.exporter(2);
      final long b1 = p1.seedBatchOperation();
      final long processDefinition = nextKey(1);
      final String x = nextName("x");
      p1.export(
          canceledByBatchOperation(1, nextKey(1), b1),
          variableCreated(1, nextKey(1), nextKey(1), processDefinition, x));
      p2.export(
          canceledByBatchOperation(2, nextKey(2), b1),
          variableCreated(2, nextKey(2), nextKey(2), processDefinition, x));
      final var otherBrokersTableOrder =
          otherBrokersTableOrder(
              ContextType.BATCH_OPERATION, ContextType.PROCESS_DEF_VAR_NAME_LOOKUP);
      final var flush1 = kit.startFlush(p1);
      final var flush2 =
          kit.start(
              "P2 flush (other broker's table order)",
              () -> p2.flushInTableOrder(otherBrokersTableOrder));

      // when
      flush1.stepTo(SHARED_ROW); // P1 stops before the shared row of its first table
      flush2.stepTo(SHARED_ROW); // P2 stops before the shared row of the other table
      flush1.step(); // each writes and holds its first shared row
      flush2.step();
      flush1.release(); // each writes the row the other one holds: deadlock
      flush2.release();

      // then: no deadlock: both flushes committed, neither was chosen as the victim
      kit.assertNoErrors(flush1, flush2);
    }
  }
}
