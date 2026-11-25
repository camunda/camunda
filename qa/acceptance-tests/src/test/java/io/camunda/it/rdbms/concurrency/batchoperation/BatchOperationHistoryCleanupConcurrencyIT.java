/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.concurrency.batchoperation;

import static io.camunda.it.rdbms.concurrency.kit.Database.H2;
import static io.camunda.it.rdbms.concurrency.kit.SyncPoint.delete;

import io.camunda.it.rdbms.concurrency.kit.FailsToday;
import io.camunda.it.rdbms.concurrency.kit.RdbmsConcurrencyKit;
import io.camunda.it.rdbms.concurrency.kit.RdbmsConcurrencyTest;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.TestTemplate;

/**
 * <b>Situation.</b> Unlike the other history cleanups, the one of batch operations has no partition
 * filter: every partition deletes the same expired batch operations, and with them, by cascading
 * foreign keys, their items and errors. Two partitions running it at the same time delete the same
 * rows. Do the cascades lock rows in an order that lets them deadlock?
 *
 * <p><b>Interleaving</b> (time flows down):
 *
 * <pre>
 *   P1 cleanup                        P2 cleanup
 *   DELETE expired batch operations (+ cascades), holds them
 *                                     DELETE the same: waits for P1
 *   commits
 *                                     finds nothing left, commits
 * </pre>
 *
 * <p><b>Today.</b> Passes on every production database: both delete in the same order, so the
 * second only waits. H2 fails with a lock timeout, as its timeout is shorter than the test holds P1
 * (a test artefact).
 */
@RdbmsConcurrencyTest
class BatchOperationHistoryCleanupConcurrencyIT {

  @TestTemplate
  @FailsToday(
      on = H2,
      reason =
          "H2's lock timeout is shorter than the test holds the first thread (a test artefact)")
  void shouldNotDeadlockWhenTwoPartitionsCleanUpTheSameBatchOperations(
      final CamundaRdbmsTestApplication app) throws Exception {
    try (final var kit = new RdbmsConcurrencyKit(app)) {
      // given: five batch operations with items, past their history cleanup date
      final var p1 = kit.exporter(1);
      final List<Long> batchOperations = new ArrayList<>();
      for (int i = 0; i < 5; i++) {
        batchOperations.add(p1.seedBatchOperationWithItems());
      }
      final var now = OffsetDateTime.now();
      kit.write(
          1,
          writers ->
              batchOperations.forEach(
                  key ->
                      writers
                          .getBatchOperationWriter()
                          .scheduleForHistoryCleanup(String.valueOf(key), now.minusDays(1))));
      final var cleanup1 =
          kit.startCleanup(1, writers -> writers.getBatchOperationWriter().cleanupHistory(now, 10));
      final var cleanup2 =
          kit.startCleanup(2, writers -> writers.getBatchOperationWriter().cleanupHistory(now, 10));

      // when
      cleanup1.stepTo(delete("BATCH_OPERATION"));
      cleanup1.step(); // P1 deletes them (with cascades) and holds its locks
      cleanup2.stepTo(delete("BATCH_OPERATION"));
      cleanup2.step(); // P2 deletes the same rows: waits for P1
      cleanup1.release(); // P1 commits
      cleanup2.release(); // P2 goes on

      // then: no deadlock: both cleanups committed, neither was chosen as the victim
      kit.assertNoErrors(cleanup1, cleanup2);
    }
  }
}
