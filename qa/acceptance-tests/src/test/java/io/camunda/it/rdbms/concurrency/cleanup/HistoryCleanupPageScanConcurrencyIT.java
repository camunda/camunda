/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.concurrency.cleanup;

import static io.camunda.it.rdbms.concurrency.kit.Database.MSSQL;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.elementCompleted;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.processCompleted;
import static io.camunda.it.rdbms.concurrency.kit.SyncPoint.delete;
import static io.camunda.it.rdbms.concurrency.kit.SyncPoint.update;

import io.camunda.it.rdbms.concurrency.kit.FailsToday;
import io.camunda.it.rdbms.concurrency.kit.RdbmsConcurrencyKit;
import io.camunda.it.rdbms.concurrency.kit.RdbmsConcurrencyTest;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import org.junit.jupiter.api.TestTemplate;

/**
 * <b>Situation</b> (SQL Server only). The history cleanup of partition 2 deletes the data of its
 * expired root instances. On SQL Server the DELETE does not use the root key index but scans the
 * whole table (Clustered Index Scan) with page locks, so it also reads the pages that hold
 * partition 1's rows. Meanwhile the flush of partition 1 updates two of its rows that lie on
 * neighbouring pages, the upper page first. The test picks the two rows by their physical page
 * ({@link RdbmsConcurrencyKit#neighbouringRows}).
 *
 * <p><b>Interleaving</b> (time flows down):
 *
 * <pre>
 *   P1 flush                          P2 cleanup
 *   update row on upper page  holds it
 *                                     DELETE scans: holds lower page, waits for upper page
 *   update row on lower page  waits for P2: deadlock
 * </pre>
 *
 * <p><b>Today.</b> Deadlock; the cleanup is the victim. Same for the element instance cleanup
 * (FLOW_NODE_INSTANCE) and the child instance cleanup (PROCESS_INSTANCE). Other databases use the
 * index and do not run this test.
 *
 * <p><b>Fix direction.</b> A DELETE that seeks the index (e.g. select the keys first, delete by
 * primary key), and {@code sendStringParametersAsUnicode=false}.
 */
@RdbmsConcurrencyTest(databases = MSSQL)
class HistoryCleanupPageScanConcurrencyIT {

  @TestTemplate
  @FailsToday(
      on = MSSQL,
      reason = "the cleanup's DELETE scans the other partition's pages with page locks: deadlock")
  void shouldNotDeadlockWhenElementInstanceCleanupScansPagesOfAnotherPartitionsFlush(
      final CamundaRdbmsTestApplication app) throws Exception {
    try (final var kit = new RdbmsConcurrencyKit(app)) {
      // given: expired roots on partition 2, running instances on partition 1
      final var p1 = kit.exporter(1);
      final var p2 = kit.exporter(2);
      final var expiredRoots = p2.seedRootsWithElements(2_000, 10).subList(0, 1_000);
      p1.seedRootsWithElements(2_000, 10);
      final var rows = kit.neighbouringRows("FLOW_NODE_INSTANCE", 1);
      p1.export(
          elementCompleted(1, rows.upper().key(), rows.upper().processInstanceKey()),
          elementCompleted(1, rows.lower().key(), rows.lower().processInstanceKey()));
      final var flush = kit.startFlush(p1);
      final var cleanup =
          kit.startCleanup(
              2,
              writers ->
                  writers
                      .getFlowNodeInstanceWriter()
                      .deleteRootProcessInstanceRelatedData(expiredRoots, 10_000));

      // when
      // the flush updates the row on the upper page and stops before the one on the lower page
      flush.stepTo(update("FLOW_NODE_INSTANCE").entry(2));
      // the cleanup scans from the lower page and waits at the upper one
      cleanup.stepTo(delete("FLOW_NODE_INSTANCE"));
      cleanup.step();
      // the flush updates the row on the lower page: deadlock
      flush.release();
      cleanup.release();

      // then: no deadlock: flush and cleanup committed, neither was chosen as the victim
      kit.assertNoErrors(flush, cleanup);
    }
  }

  @TestTemplate
  @FailsToday(
      on = MSSQL,
      reason = "the cleanup's DELETE scans the other partition's pages with page locks: deadlock")
  void shouldNotDeadlockWhenChildInstanceCleanupScansPagesOfAnotherPartitionsFlush(
      final CamundaRdbmsTestApplication app) throws Exception {
    try (final var kit = new RdbmsConcurrencyKit(app)) {
      // given: expired roots with children on partition 2, running instances on partition 1
      final var p1 = kit.exporter(1);
      final var p2 = kit.exporter(2);
      final var expiredRoots = p2.seedRootsWithChildren(2_000, 10).subList(0, 1_000);
      p1.seedRootsWithChildren(2_000, 10);
      final var rows = kit.neighbouringRows("PROCESS_INSTANCE", 1);
      p1.export(processCompleted(1, rows.upper().key()), processCompleted(1, rows.lower().key()));
      final var flush = kit.startFlush(p1);
      final var cleanup =
          kit.startCleanup(
              2,
              writers ->
                  writers
                      .getProcessInstanceWriter()
                      .deleteChildrenByRootProcessInstances(expiredRoots, 10_000));

      // when
      // the flush updates the row on the upper page and stops before the one on the lower page
      flush.stepTo(update("PROCESS_INSTANCE").entry(2));
      // the cleanup scans from the lower page and waits at the upper one
      cleanup.stepTo(delete("PROCESS_INSTANCE"));
      cleanup.step();
      // the flush updates the row on the lower page: deadlock
      flush.release();
      cleanup.release();

      // then: no deadlock: flush and cleanup committed, neither was chosen as the victim
      kit.assertNoErrors(flush, cleanup);
    }
  }
}
