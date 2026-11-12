/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import static io.camunda.zeebe.db.cache.TestColumnFamily.FIRST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthReport;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

final class WriteBehindZeebeDbTest {

  private final InMemoryStorage storage = new InMemoryStorage();

  @Test
  void shouldReportTheStoreFailingAsDead() throws Exception {
    // given
    final var listener = new RecordingFailureListener();
    try (final var db =
        new WriteBehindZeebeDb<>(
            storage.db(),
            new TestConfiguration().maxWriteDelay(Duration.ofMillis(1)).build(),
            new ConsistencyChecksSettings(true, true))) {
      db.addFailureListener(listener);
      assertThat(db.getHealthReport().isHealthy()).isTrue();
      final var columnFamily =
          db.createColumnFamily(FIRST, db.createContext(), new DbLong(), new DbLong());

      // when
      storage.failTransactions(1, () -> new IllegalStateException("expected"));
      columnFamily.insert(dbLong(1), dbLong(10));

      // then
      await().untilAsserted(() -> assertThat(listener.unrecoverableFailures).hasSize(1));
      assertThat(listener.unrecoverableFailures.getFirst().isDead()).isTrue();
      assertThat(db.getHealthReport().isDead()).isTrue();
      assertThat(db.getHealthReport().getIssue()).isNotNull();
    }
  }

  private static DbLong dbLong(final long value) {
    final var dbLong = new DbLong();
    dbLong.wrapLong(value);
    return dbLong;
  }

  private static final class RecordingFailureListener implements FailureListener {

    private final List<HealthReport> unrecoverableFailures = new CopyOnWriteArrayList<>();

    @Override
    public void onFailure(final HealthReport report) {}

    @Override
    public void onRecovered(final HealthReport report) {}

    @Override
    public void onUnrecoverableFailure(final HealthReport report) {
      unrecoverableFailures.add(report);
    }
  }
}
