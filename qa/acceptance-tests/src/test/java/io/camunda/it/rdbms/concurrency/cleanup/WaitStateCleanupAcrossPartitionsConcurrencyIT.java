/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.concurrency.cleanup;

import static io.camunda.it.rdbms.concurrency.kit.Database.MARIADB;
import static io.camunda.it.rdbms.concurrency.kit.Database.MYSQL;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.nextKey;
import static io.camunda.it.rdbms.concurrency.kit.SyncPoint.commit;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.domain.WaitStateDbModel;
import io.camunda.it.rdbms.concurrency.kit.FailsToday;
import io.camunda.it.rdbms.concurrency.kit.RdbmsConcurrencyKit;
import io.camunda.it.rdbms.concurrency.kit.RdbmsConcurrencyTest;
import io.camunda.it.rdbms.db.fixtures.WaitStateFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.TestTemplate;

/**
 * <b>Situation.</b> The history cleanup of a partition deletes the wait states of its expired root
 * process instances by {@code ROOT_PROCESS_INSTANCE_KEY}. Wait states are short-lived (the exporter
 * deletes one when its element moves on), so expired roots usually have none left: the DELETE finds
 * nothing in the root key index. Keys carry the partition in their top bits, so partition 2's
 * expired root keys lie above every key of partition 1, in the same index. Meanwhile the flush of
 * partition 1 deletes one of its wait states and creates one for a new instance, whose root key is
 * partition 1's newest, i.e. the highest key below partition 2's range.
 *
 * <p><b>Interleaving</b> (time flows down):
 *
 * <pre>
 *   P2 cleanup                        P1 flush
 *   DELETE wait states of P2's expired roots (none left), stop before commit (holds its locks)
 *                                     delete a wait state of a running instance, create another,
 *                                     up to its commit
 *   then: the flush did not wait for a lock
 * </pre>
 *
 * <p><b>Today.</b> MySQL and MariaDB block: at REPEATABLE READ the cleanup's search for its missing
 * keys locks the gap up to the next index entry, and with no rows of partition 2 left in it, that
 * gap reaches down to partition 1's newest wait state, where the flush inserts. Any table blocks
 * the same way when a partition's expired roots have no rows left in it. PostgreSQL, Oracle, SQL
 * Server and H2 lock only the deleted rows.
 *
 * <p><b>Fix direction.</b> READ COMMITTED on MySQL/MariaDB: InnoDB then takes no gap locks for the
 * search.
 */
@RdbmsConcurrencyTest
class WaitStateCleanupAcrossPartitionsConcurrencyIT {

  @TestTemplate
  @FailsToday(
      on = {MYSQL, MARIADB},
      reason =
          "the cleanup's search for missing keys locks the gap down to the other partition's newest entry (REPEATABLE READ)")
  void shouldNotBlockWaitStatesOfAnotherPartition(final CamundaRdbmsTestApplication app)
      throws Exception {
    try (final var kit = new RdbmsConcurrencyKit(app)) {
      // given: wait states of 3,000 running instances on partition 1; 100 expired roots on
      // partition 2, whose wait states are gone already
      final List<Long> waitStates = new ArrayList<>();
      for (int chunk = 0; chunk < 15; chunk++) {
        kit.write(
            1,
            writers -> {
              for (int i = 0; i < 200; i++) {
                final long root = nextKey(1);
                final long waitState = nextKey(1);
                waitStates.add(waitState);
                writers.getWaitStateWriter().create(waitState(waitState, root));
              }
            });
      }
      kit.analyze("WAIT_STATE");
      final List<Long> expiredRoots = new ArrayList<>();
      for (int i = 0; i < 100; i++) {
        expiredRoots.add(nextKey(2));
      }
      final var cleanup =
          kit.startCleanup(
              2,
              writers ->
                  writers
                      .getWaitStateWriter()
                      .deleteRootProcessInstanceRelatedData(expiredRoots, 10_000));
      final long running = nextKey(1);
      final var flush =
          kit.startWrite(
              1,
              writers -> {
                writers.getWaitStateWriter().delete(waitStates.get(1_500));
                writers.getWaitStateWriter().create(waitState(nextKey(1), running));
              });

      // when: P2's cleanup deletes and holds its locks; P1's flush deletes and creates wait states
      cleanup.stepTo(commit());
      final var progress = flush.runTo(commit());
      cleanup.release();
      flush.release();

      // then: the flush got to its commit without waiting for the cleanup's locks
      assertThat(progress.waitsForLock()).describedAs("%s", progress).isFalse();
      kit.assertNoErrors(cleanup, flush);
    }
  }

  private static WaitStateDbModel waitState(final long key, final long root) {
    return WaitStateFixtures.createRandomized(
        b -> b.waitStateKey(key).rootProcessInstanceKey(root).processInstanceKey(root));
  }
}
