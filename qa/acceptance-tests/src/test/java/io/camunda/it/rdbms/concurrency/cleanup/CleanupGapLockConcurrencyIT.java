/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.concurrency.cleanup;

import static io.camunda.it.rdbms.concurrency.kit.KitRecords.elementActivating;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.nextKey;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.processStarted;
import static io.camunda.it.rdbms.concurrency.kit.SyncPoint.delete;
import static io.camunda.it.rdbms.concurrency.kit.SyncPoint.insert;

import io.camunda.it.rdbms.concurrency.kit.RdbmsConcurrencyKit;
import io.camunda.it.rdbms.concurrency.kit.RdbmsConcurrencyTest;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.TestTemplate;

/**
 * <b>Situation.</b> On MySQL and MariaDB the history cleanup and the flush run at REPEATABLE READ
 * (the flush asks for READ COMMITTED, but the Spring-managed MyBatis transaction ignores it). The
 * cleanup's DELETE of element instances goes through the index (root key, element key) and takes
 * next-key locks: on each deleted entry and on the gap before it. Meanwhile the flush of the same
 * partition inserts the first element instances of running roots, whose keys lie between the
 * expired roots' keys, i.e. into those gaps. Can the two lock each other in a cycle?
 *
 * <p><b>Interleaving</b> (time flows down). Root keys in index order: R0, E1, R, E2 (E expired, R
 * running).
 *
 * <pre>
 *   P1 flush                          P1 cleanup
 *   insert element of R   (right after E1's entries; uncommitted)
 *                                     DELETE elements of E1, E2: locks their entries and
 *                                     the gaps before them; for R's new entry only a
 *                                     gap-only lock, which does not wait for the insert
 *   insert element of R0  (into the gap before E1): waits for the cleanup
 *                                     commits; the flush goes on
 * </pre>
 *
 * <p><b>Today.</b> Passes on every database: the cleanup never waits for the flush, so there is no
 * cycle, only a wait (see {@link HistoryCleanupBlockingConcurrencyIT} for the wait). A cycle would
 * need the cleanup to scan the primary key instead, which MySQL does on tiny tables; the test seeds
 * enough rows for the index plan of production.
 *
 * <p>The exporter merges up to 25 element instance inserts into one multi-row INSERT; the test sets
 * that to 1 so the flush can stop between the two inserts.
 */
@RdbmsConcurrencyTest
class CleanupGapLockConcurrencyIT {

  @TestTemplate
  void shouldNotDeadlockWhenCleanupLocksGapsWhereTheFlushInsertsElementsOfRunningRoots(
      final CamundaRdbmsTestApplication app) throws Exception {
    try (final var kit = new RdbmsConcurrencyKit(app)) {
      // given: root keys R0 < E1 < R < E2; E1 and E2 expired with elements, R0 and R running
      // one INSERT per element instance, so the flush can stop between them
      final var p1 =
          kit.exporter(1, Map.of("insertBatching", Map.of("maxFlowNodeInsertBatchSize", 1)));
      // enough other roots that the cleanup deletes through the root key index, as in production;
      // on a tiny table MySQL scans the primary key instead
      p1.seedRootsWithElements(2_000, 5);
      final long runningRoot0 = nextKey(1);
      final long expiredRoot1 = nextKey(1);
      final long runningRoot = nextKey(1);
      final long expiredRoot2 = nextKey(1);
      for (final long expired : List.of(expiredRoot1, expiredRoot2)) {
        p1.export(processStarted(1, expired));
        for (int e = 0; e < 3; e++) {
          p1.export(elementActivating(1, nextKey(1), expired));
        }
      }
      p1.flush();
      p1.export(
          elementActivating(1, nextKey(1), runningRoot),
          elementActivating(1, nextKey(1), runningRoot0));
      final var flush = kit.startFlush(p1);
      final var cleanup =
          kit.startCleanup(
              1,
              writers ->
                  writers
                      .getFlowNodeInstanceWriter()
                      .deleteRootProcessInstanceRelatedData(
                          List.of(expiredRoot1, expiredRoot2), 10_000));

      // when
      // the flush inserts the element of R and stops before the one of R0
      flush.stepTo(insert("FLOW_NODE_INSTANCE").entry(2));
      // the cleanup deletes the elements of E1 and E2, locking the gaps before their entries
      cleanup.stepTo(delete("FLOW_NODE_INSTANCE"));
      cleanup.step();
      // the flush inserts the element of R0 into the gap before E1's entries: waits, no cycle
      flush.release();
      cleanup.release();

      // then: no deadlock: the flush only waited, both committed
      kit.assertNoErrors(flush, cleanup);
    }
  }
}
