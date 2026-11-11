/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DatabaseImportJobExecutorTest {

  @Test
  void shouldNotRetryImportJobInterruptedByShutdown() throws Exception {
    // given
    final var configurationService = mock(ConfigurationService.class);
    when(configurationService.getJobExecutorThreadCount()).thenReturn(1);
    when(configurationService.getJobExecutorQueueSize()).thenReturn(1);
    final var executor = new DatabaseImportJobExecutor("test", configurationService);
    final var job = new BlockingImportJob();
    executor.executeImportJob(job);

    try {
      assertThat(job.firstAttemptStarted.await(10, TimeUnit.SECONDS)).isTrue();
    } finally {
      // when
      executor.stopExecutingImportJobs();
    }

    // then
    assertThat(job.attempts).hasValue(1);
  }

  /**
   * Blocks the first attempt until the executor interrupts it, then fails it with the interrupt
   * flag restored. Any further attempt succeeds immediately, so a retry shows up as a second
   * attempt.
   */
  private static final class BlockingImportJob extends DatabaseImportJob<OptimizeDto> {

    private final CountDownLatch firstAttemptStarted = new CountDownLatch(1);
    private final AtomicInteger attempts = new AtomicInteger();

    private BlockingImportJob() {
      super(() -> {}, mock(DatabaseClient.class));
      setEntitiesToImport(List.of(mock(OptimizeDto.class)));
    }

    @Override
    protected void persistEntities(final List<OptimizeDto> newOptimizeEntities) {
      if (attempts.incrementAndGet() == 1) {
        firstAttemptStarted.countDown();
        try {
          new CountDownLatch(1).await();
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("interrupted while writing", e);
        }
      }
    }
  }
}
