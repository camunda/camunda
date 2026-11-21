/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.concurrency.flush;

import static io.camunda.it.rdbms.concurrency.kit.KitRecords.elementActivating;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.elementCompleted;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.key;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.nextTopKey;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.processCompleted;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.processStarted;
import static io.camunda.it.rdbms.concurrency.kit.SyncPoint.any;
import static io.camunda.it.rdbms.concurrency.kit.SyncPoint.commit;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.it.rdbms.concurrency.kit.Progress;
import io.camunda.it.rdbms.concurrency.kit.RdbmsConcurrencyKit;
import io.camunda.it.rdbms.concurrency.kit.RdbmsConcurrencyTest;
import io.camunda.it.rdbms.concurrency.kit.ThreadControl;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.TestTemplate;

/**
 * <b>Situation.</b> Keys carry the partition in their top bits ({@code partition << 51} plus a
 * counter), so in every key index the partitions' keys form ranges side by side: the newest key of
 * partition 1 sits directly before the oldest key of partition 2, and so on. Each of three
 * partitions inserts a new process instance with such a newest key, and completes (updates) its own
 * oldest instance, the row right after the previous partition's insert:
 *
 * <pre>
 *   key index:  ... P1 oldest ... P1 newest | P2 oldest ... P2 newest | P3 oldest ... P3 newest
 *                   update        insert      update        insert      update        insert
 * </pre>
 *
 * <p>Can the locks of one partition's insert reach the neighbouring partition's update (gap locks
 * at REPEATABLE READ on MySQL/MariaDB, key-range or page locks elsewhere)?
 *
 * <p><b>Today.</b> Passes on every database, in every order: inserts and updates by primary key
 * lock only their own entries.
 */
@RdbmsConcurrencyTest
class AdjacentKeysAcrossPartitionsConcurrencyIT {

  @TestTemplate
  void shouldNotBlockWhenNeighbouringPartitionsWriteAdjacentKeysInTurns(
      final CamundaRdbmsTestApplication app) throws Exception {
    try (final var kit = new RdbmsConcurrencyKit(app)) {
      // given
      final var flushes = startFlushesWritingAdjacentKeys(kit);

      // when: one statement of each flush in turn, up to their commits
      final var progress = stepInTurnsToCommit(flushes);
      flushes.forEach(ThreadControl::release);

      // then: no flush waited for another one's locks
      assertThat(progress).describedAs("%s", progress).noneMatch(Progress::waitsForLock);
      kit.assertNoErrors(flushes.toArray(ThreadControl[]::new));
    }
  }

  @TestTemplate
  void shouldNotBlockWhenEachPartitionHoldsItsLocksInPartitionOrder(
      final CamundaRdbmsTestApplication app) throws Exception {
    try (final var kit = new RdbmsConcurrencyKit(app)) {
      // given
      final var flushes = startFlushesWritingAdjacentKeys(kit);

      // when: P1, then P2, then P3 run up to their commit, keeping their locks
      final List<Progress> progress = new ArrayList<>();
      for (final var flush : flushes) {
        progress.add(flush.runTo(commit()));
      }
      flushes.forEach(ThreadControl::release);

      // then: no flush waited for another one's locks
      assertThat(progress).describedAs("%s", progress).noneMatch(Progress::waitsForLock);
      kit.assertNoErrors(flushes.toArray(ThreadControl[]::new));
    }
  }

  @TestTemplate
  void shouldNotBlockWhenEachPartitionHoldsItsLocksInReversePartitionOrder(
      final CamundaRdbmsTestApplication app) throws Exception {
    try (final var kit = new RdbmsConcurrencyKit(app)) {
      // given
      final var flushes = startFlushesWritingAdjacentKeys(kit);

      // when: P3, then P2, then P1 run up to their commit, keeping their locks
      final List<Progress> progress = new ArrayList<>();
      for (final var flush : flushes.reversed()) {
        progress.add(flush.runTo(commit()));
      }
      flushes.forEach(ThreadControl::release);

      // then: no flush waited for another one's locks
      assertThat(progress).describedAs("%s", progress).noneMatch(Progress::waitsForLock);
      kit.assertNoErrors(flushes.toArray(ThreadControl[]::new));
    }
  }

  /**
   * One statement of each flush in turn, until each stopped before its commit, waits for a lock, or
   * ended.
   *
   * @return how far each flush got
   */
  private static List<Progress> stepInTurnsToCommit(final List<ThreadControl> flushes)
      throws InterruptedException {
    final Map<ThreadControl, Progress> last = new LinkedHashMap<>();
    while (last.size() < flushes.size()) {
      for (final var flush : flushes) {
        if (last.containsKey(flush)) {
          continue;
        }
        final var progress = flush.runTo(any());
        if (!progress.reached() || flush.syncPoint().isCommit()) {
          last.put(flush, progress);
        } else {
          flush.step();
        }
      }
    }
    return List.copyOf(last.values());
  }

  /**
   * For partitions 1 to 3: makes sure the partition's oldest instance (with one element) exists,
   * then starts a flush that inserts a new instance with the partition's newest key and completes
   * the oldest one.
   */
  private static List<ThreadControl> startFlushesWritingAdjacentKeys(final RdbmsConcurrencyKit kit)
      throws Exception {
    final List<ThreadControl> flushes = new ArrayList<>();
    for (int p = 1; p <= 3; p++) {
      final var exporter = kit.exporter(p);
      final long oldestInstance = key(p, 1);
      final long oldestElement = key(p, 2);
      // the oldest keys are fixed, so the tests of this class share them
      if (kit.count(
              "SELECT COUNT(*) FROM PROCESS_INSTANCE WHERE PROCESS_INSTANCE_KEY = "
                  + oldestInstance)
          == 0) {
        exporter.export(
            processStarted(p, oldestInstance), elementActivating(p, oldestElement, oldestInstance));
        exporter.flush();
      }
      final long newInstance = nextTopKey(p);
      exporter.export(
          processStarted(p, newInstance),
          elementActivating(p, nextTopKey(p), newInstance),
          processCompleted(p, oldestInstance),
          elementCompleted(p, oldestElement, oldestInstance));
      flushes.add(kit.startFlush(exporter));
    }
    return flushes;
  }
}
