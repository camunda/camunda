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
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.elementActivating;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.nextKey;
import static io.camunda.it.rdbms.concurrency.kit.SyncPoint.commit;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.it.rdbms.concurrency.kit.FailsToday;
import io.camunda.it.rdbms.concurrency.kit.RdbmsConcurrencyKit;
import io.camunda.it.rdbms.concurrency.kit.RdbmsConcurrencyTest;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.TestTemplate;

/**
 * <b>Situation.</b> The history cleanup (and the history deletion of single instances) must not
 * hold up the exporter: a blocked flush stops the partition's export, and with it the visibility of
 * new data. Roots that ran at the same time have neighbouring keys, so expired and running roots
 * interleave in the key indexes: every fifth root is expired here, and the running root the flush
 * writes for lies right before an expired one. The cleanup deletes the element instances of 600
 * expired roots (6,000 rows, enough for SQL Server's lock escalation at about 5,000 locks); the
 * deletion those of 100 instances.
 *
 * <p><b>Interleaving</b> (time flows down). Not a deadlock test: the question is only whether the
 * flush gets through while the cleanup holds its locks.
 *
 * <pre>
 *   cleanup / deletion                flush
 *   DELETE elements of expired roots, stop before commit (holds its locks)
 *                                     insert an element of a running root, up to its commit
 *   then: the flush did not wait for a lock
 * </pre>
 *
 * <p><b>Today.</b>
 *
 * <ul>
 *   <li>MySQL/MariaDB: the flush of the same partition is blocked, by the cleanup and by the
 *       deletion. Both run at REPEATABLE READ, and their next-key locks cover the gaps right next
 *       to the deleted entries. Other partitions' keys lie elsewhere and are not blocked.
 *   <li>SQL Server: the 6,000-row DELETE escalates to a table lock and blocks the flushes of
 *       <em>every</em> partition. The 100-instance deletion stays below the threshold.
 *   <li>PostgreSQL, Oracle, H2: lock only the deleted rows; nothing is blocked.
 * </ul>
 *
 * <p><b>Fix direction.</b> READ COMMITTED on MySQL/MariaDB (which the flush already asks for), and
 * deletes below the escalation threshold on SQL Server.
 */
@RdbmsConcurrencyTest
class HistoryCleanupBlockingConcurrencyIT {

  @TestTemplate
  @FailsToday(
      on = {MYSQL, MARIADB},
      reason =
          "the cleanup's next-key locks at REPEATABLE READ cover the gap the flush inserts into")
  @FailsToday(on = MSSQL, reason = "the 6,000-row DELETE escalates to a table lock")
  void shouldNotBlockTheFlushOfTheSamePartition(final CamundaRdbmsTestApplication app)
      throws Exception {
    try (final var kit = new RdbmsConcurrencyKit(app)) {
      // given: expired and running roots on partition 1
      final var p1 = kit.exporter(1);
      final var roots = Roots.of(p1.seedRootsWithElements(3_000, 10));
      final long running = roots.runningInTheMiddle();
      p1.export(elementActivating(1, nextKey(1), running));
      final var cleanup =
          kit.startCleanup(
              1,
              writers ->
                  writers
                      .getFlowNodeInstanceWriter()
                      .deleteRootProcessInstanceRelatedData(roots.expired(), 10_000));
      final var flush = kit.startFlush(p1);

      // when: the cleanup deletes and holds its locks; the flush inserts an element of a running
      // root inside the deleted range
      cleanup.stepTo(commit());
      final var progress = flush.runTo(commit());
      cleanup.release();
      flush.release();

      // then: the flush got to its commit without waiting for the cleanup's locks
      assertThat(progress.waitsForLock()).describedAs("%s", progress).isFalse();
      kit.assertNoErrors(cleanup, flush);
    }
  }

  @TestTemplate
  @FailsToday(on = MSSQL, reason = "the 6,000-row DELETE escalates to a table lock")
  void shouldNotBlockTheFlushOfAnotherPartition(final CamundaRdbmsTestApplication app)
      throws Exception {
    try (final var kit = new RdbmsConcurrencyKit(app)) {
      // given: expired and running roots on partitions 1 and 2
      final var p1 = kit.exporter(1);
      final var p2 = kit.exporter(2);
      final var roots1 = Roots.of(p1.seedRootsWithElements(3_000, 10));
      final var roots2 = Roots.of(p2.seedRootsWithElements(3_000, 10));
      p1.export(elementActivating(1, nextKey(1), roots1.runningInTheMiddle()));
      final var cleanup =
          kit.startCleanup(
              2,
              writers ->
                  writers
                      .getFlowNodeInstanceWriter()
                      .deleteRootProcessInstanceRelatedData(roots2.expired(), 10_000));
      final var flush = kit.startFlush(p1);

      // when: partition 2's cleanup deletes and holds its locks; partition 1's flush inserts
      cleanup.stepTo(commit());
      final var progress = flush.runTo(commit());
      cleanup.release();
      flush.release();

      // then: the flush got to its commit without waiting for the cleanup's locks
      assertThat(progress.waitsForLock()).describedAs("%s", progress).isFalse();
      kit.assertNoErrors(cleanup, flush);
    }
  }

  @TestTemplate
  @FailsToday(
      on = {MYSQL, MARIADB},
      reason =
          "the deletion's next-key locks at REPEATABLE READ cover the gap the flush inserts into")
  void shouldNotBlockTheFlushWhenHistoryDeletionDeletesProcessInstances(
      final CamundaRdbmsTestApplication app) throws Exception {
    try (final var kit = new RdbmsConcurrencyKit(app)) {
      // given: 100 completed instances to delete, with running ones between them
      final var p1 = kit.exporter(1);
      final var roots = Roots.of(p1.seedRootsWithElements(500, 10));
      final long running = roots.runningInTheMiddle();
      p1.export(elementActivating(1, nextKey(1), running));
      final var deletion =
          kit.startCleanup(
              1,
              writers ->
                  writers
                      .getFlowNodeInstanceWriter()
                      .deleteProcessInstanceRelatedData(roots.expired(), 10_000));
      final var flush = kit.startFlush(p1);

      // when: the deletion deletes and holds its locks; the flush inserts an element of a
      // running instance between the deleted ones
      deletion.stepTo(commit());
      final var progress = flush.runTo(commit());
      deletion.release();
      flush.release();

      // then: the flush got to its commit without waiting for the deletion's locks
      assertThat(progress.waitsForLock()).describedAs("%s", progress).isFalse();
      kit.assertNoErrors(deletion, flush);
    }
  }

  /**
   * Seeded roots in key order: every fifth one expired, the others running. A realistic share: when
   * most of the table is expired, the databases may scan the whole table instead of using the root
   * key index, which locks far more.
   */
  private record Roots(List<Long> seeded, List<Long> expired, List<Long> running) {
    static Roots of(final List<Long> seeded) {
      final List<Long> expired = new ArrayList<>();
      final List<Long> running = new ArrayList<>();
      for (int i = 0; i < seeded.size(); i++) {
        (i % 5 == 0 ? expired : running).add(seeded.get(i));
      }
      return new Roots(seeded, expired, running);
    }

    /**
     * A running root in the middle of the key range whose next root is expired: its new entries
     * land right before the deleted entries of that root, where the cleanup's locks are.
     */
    long runningInTheMiddle() {
      return seeded.get(seeded.size() / 2 / 5 * 5 + 4);
    }
  }
}
