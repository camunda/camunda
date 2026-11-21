/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.concurrency.flush;

import static io.camunda.it.rdbms.concurrency.kit.ControllableExporter.otherBrokersTableOrder;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.elementActivating;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.nextKey;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.processCompleted;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.processStarted;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.variableCreated;

import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.it.rdbms.concurrency.kit.ControllableExporter;
import io.camunda.it.rdbms.concurrency.kit.RdbmsConcurrencyKit;
import io.camunda.it.rdbms.concurrency.kit.RdbmsConcurrencyTest;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import java.util.List;
import org.junit.jupiter.api.TestTemplate;

/**
 * <b>Situation.</b> The everyday case: two partitions flush at the same time and share no rows.
 * Each starts 20 new instances (with elements and a variable) and completes 20 running ones, and
 * partition 2 writes the tables in the other order, as another broker may (see {@link
 * TableOrderAcrossBrokersConcurrencyIT}). Row locks cannot collide; the test checks that no lock
 * broader than a row (gap, page, range, a scan) makes them collide anyway.
 *
 * <p><b>Interleaving.</b> One statement of each flush in turn, so every statement of P1 meets every
 * lock P2 holds at that point, and vice versa.
 *
 * <p><b>Today.</b> Passes on every database, with fresh planner statistics: the test analyzes the
 * tables after seeding.
 *
 * <p><b>Not covered: stale statistics on MySQL.</b> Right after a quick load InnoDB may still plan
 * with the statistics of a near-empty PROCESS_INSTANCE, so the completion's {@code UPDATE ... SET
 * HISTORY_CLEANUP_DATE ... WHERE PROCESS_INSTANCE_KEY IN (...)} scans the whole primary key instead
 * of looking up its keys. At REPEATABLE READ the scan locks every row it reads, including the other
 * partition's, and the two flushes deadlock. InnoDB recalculates statistics in the background, so
 * without the analyze the result depends on timing; production hits it on new or fast-growing
 * installations.
 *
 * <p>The tables are seeded to a realistic size: on tiny tables the databases scan instead of using
 * the key, which makes the test fail on MariaDB and SQL Server for a reason production does not
 * have.
 */
@RdbmsConcurrencyTest
class DisjointFlushesConcurrencyIT {

  private static final int INSTANCES = 20;

  @TestTemplate
  void shouldNotDeadlockWhenTwoPartitionsFlushDisjointInstancesInOppositeTableOrder(
      final CamundaRdbmsTestApplication app) throws Exception {
    try (final var kit = new RdbmsConcurrencyKit(app)) {
      // given: running instances on both partitions, and a flush per partition that starts new
      // instances and completes the running ones
      final var p1 = kit.exporter(1);
      final var p2 = kit.exporter(2);
      p1.seedRootsWithElements(2_000, 2);
      p2.seedRootsWithElements(2_000, 2);
      queueInstances(p1, 1, p1.seedRootsWithElements(INSTANCES, 2));
      queueInstances(p2, 2, p2.seedRootsWithElements(INSTANCES, 2));
      // fresh planner statistics, as a long-running installation has them (see the class doc)
      kit.analyze("PROCESS_INSTANCE", "FLOW_NODE_INSTANCE", "VARIABLE");
      final var flush1 = kit.startFlush(p1);
      final var flush2 =
          kit.start(
              "P2 flush (other broker's table order)",
              () ->
                  p2.flushInTableOrder(
                      otherBrokersTableOrder(
                          ContextType.PROCESS_INSTANCE,
                          ContextType.FLOW_NODE,
                          ContextType.VARIABLE)));

      // when: one statement of each in turn
      for (int turn = 0; turn < 100; turn++) {
        flush1.step();
        flush2.step();
      }
      flush1.release();
      flush2.release();

      // then: no deadlock: both flushes committed, neither was chosen as the victim
      kit.assertNoErrors(flush1, flush2);
    }
  }

  /** New instances with elements and a variable, and the completion of the running ones. */
  private static void queueInstances(
      final ControllableExporter exporter, final int p, final List<Long> running) {
    for (int i = 0; i < INSTANCES; i++) {
      final long instance = nextKey(p);
      exporter.export(
          processStarted(p, instance),
          elementActivating(p, nextKey(p), instance),
          elementActivating(p, nextKey(p), instance),
          // definition key 0: no variable name lookup, which is shared between partitions
          variableCreated(p, nextKey(p), instance, 0, "v"));
    }
    running.forEach(instance -> exporter.export(processCompleted(p, instance)));
  }
}
