/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.camunda.application.commons.migration.AsyncMigrationsRunner.AsyncMigrationsFinishedEvent;
import io.camunda.migration.api.Migrator;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class AsyncMigrationsRunnerTest {

  @BeforeEach
  void setUp() {}

  private void waitForMigrationFinishedEvent(final ApplicationEventPublisher eventPublisher) {
    await("All migrations should finish in parallel")
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> verify(eventPublisher).publishEvent(any()));
  }

  @Test
  void shouldRunAllMigrationsInParallel() throws Exception {
    // given
    final var numMigrations = 10;
    final var started = new CountDownLatch(numMigrations);
    final var finished = new CountDownLatch(numMigrations);
    final var eventPublisher = mock(ApplicationEventPublisher.class);
    final var migrationFinishedAC = ArgumentCaptor.forClass(AsyncMigrationsFinishedEvent.class);
    final List<Migrator> migrators =
        IntStream.range(0, numMigrations)
            .mapToObj(i -> new TestMigrator(started, finished))
            .map(Migrator.class::cast)
            .toList();
    final var runner = new AsyncMigrationsRunner(migrators, eventPublisher);

    // when
    runner.run(null);
    waitForMigrationFinishedEvent(eventPublisher);

    // then
    verify(eventPublisher).publishEvent(migrationFinishedAC.capture());
    assertThat(started.getCount()).isEqualTo(0);
    assertThat(finished.getCount()).isEqualTo(0);
    assertThat(migrationFinishedAC.getValue().isSuccess()).isTrue();
  }

  record TestMigrator(CountDownLatch started, CountDownLatch finished) implements Migrator {
    @Override
    public Void call() throws Exception {
      started.countDown();
      started.await(); // wait for all to start
      finished.countDown();
      finished.await(); // wait for all to finish
      return null;
    }
  }
}
