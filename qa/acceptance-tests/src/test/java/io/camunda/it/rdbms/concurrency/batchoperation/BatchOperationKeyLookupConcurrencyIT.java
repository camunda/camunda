/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.concurrency.batchoperation;

import static io.camunda.it.rdbms.concurrency.kit.KitRecords.batchOperationCanceled;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.canceledByBatchOperation;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.nextKey;
import static io.camunda.it.rdbms.concurrency.kit.SyncPoint.commit;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.it.rdbms.concurrency.kit.RdbmsConcurrencyKit;
import io.camunda.it.rdbms.concurrency.kit.RdbmsConcurrencyTest;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import org.junit.jupiter.api.TestTemplate;

/**
 * <b>Situation.</b> The exporter finds a batch operation's rows by their key (BATCH_OPERATION,
 * BATCH_OPERATION_ITEM). Writes for one batch operation must not wait for locks on another one. On
 * SQL Server the key column is {@code VARCHAR(255)}, but the JDBC driver sends Java strings as
 * {@code NVARCHAR(4000)}. {@code NVARCHAR} ranks higher, so SQL Server converts the column, not the
 * parameter ({@code CONVERT_IMPLICIT} on the column), cannot seek the key, and reads other batch
 * operations' rows on the way, taking an update lock on each: it waits for whoever holds one of
 * them.
 *
 * <p><b>Interleaving</b> (time flows down):
 *
 * <pre>
 *   P1 flush                          P2 flush
 *   write rows of b1, stop before commit (holds them)
 *                                     write rows of b2 up to its commit
 *   then: P2 did not wait for a lock
 * </pre>
 *
 * <p><b>Today.</b> Without the fix SQL Server's P2 waits for P1's lock on a row of b1. Other
 * databases compare the key with the parameter's type directly and pass.
 *
 * <p><b>Fix.</b> The SQL Server statements cast the parameter to {@code VARCHAR(255)}, so the key
 * can be sought ({@code BatchOperationMapper.xml}, fragment {@code batchOperationKey}).
 */
@RdbmsConcurrencyTest
class BatchOperationKeyLookupConcurrencyIT {

  @TestTemplate
  void shouldNotBlockCompletingItemsOfAnotherBatchOperation(final CamundaRdbmsTestApplication app)
      throws Exception {
    try (final var kit = new RdbmsConcurrencyKit(app)) {
      // given: batch operations b1 and b2; P1 completes an item of b1, P2 one of b2
      final var p1 = kit.exporter(1);
      final var p2 = kit.exporter(2);
      final long b1 = p1.seedBatchOperationWithItems();
      final long b2 = p1.seedBatchOperationWithItems();
      p1.export(canceledByBatchOperation(1, nextKey(1), b1));
      p2.export(canceledByBatchOperation(2, nextKey(2), b2));
      final var flush1 = kit.startFlush(p1);
      final var flush2 = kit.startFlush(p2);

      // when: P1 writes the item and the counter of b1 and stops before its commit, holding its
      // locks; P2 writes those of b2
      flush1.stepTo(commit());
      final var progress = flush2.runTo(commit());
      flush1.release();
      flush2.release();

      // then: P2 got to its commit without waiting for P1's locks
      assertThat(progress.waitsForLock()).describedAs("%s", progress).isFalse();
      kit.assertNoErrors(flush1, flush2);
    }
  }

  @TestTemplate
  void shouldNotBlockCancelingAnotherBatchOperation(final CamundaRdbmsTestApplication app)
      throws Exception {
    try (final var kit = new RdbmsConcurrencyKit(app)) {
      // given: batch operations b1 and b2 with items; P1 completes an item of b1, P2 cancels b2
      final var p1 = kit.exporter(1);
      final var p2 = kit.exporter(2);
      final long b1 = p1.seedBatchOperationWithItems();
      final long b2 = p1.seedBatchOperationWithItems();
      p1.export(canceledByBatchOperation(1, nextKey(1), b1));
      p2.export(batchOperationCanceled(2, b2));
      final var flush1 = kit.startFlush(p1);
      final var flush2 = kit.startFlush(p2);

      // when: P1 writes the item and the counter of b1 and stops before its commit, holding its
      // locks; P2 cancels b2 and all its items
      flush1.stepTo(commit());
      final var progress = flush2.runTo(commit());
      flush1.release();
      flush2.release();

      // then: P2 got to its commit without waiting for P1's locks
      assertThat(progress.waitsForLock()).describedAs("%s", progress).isFalse();
      kit.assertNoErrors(flush1, flush2);
    }
  }
}
