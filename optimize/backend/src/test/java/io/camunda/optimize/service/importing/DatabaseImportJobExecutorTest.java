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
import io.camunda.zeebe.protocol.record.ValueType;
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
    final var job = new FailingImportJob();
    executor.executeImportJob(job);
    assertThat(job.firstAttemptFailed.await(10, TimeUnit.SECONDS)).isTrue();

    // when
    executor.stopExecutingImportJobs();

    // then
    assertThat(job.attempts).hasValue(1);
  }

  /** Fails on the first attempt; any retry succeeds immediately. */
  private static final class FailingImportJob extends DatabaseImportJob<OptimizeDto> {

    private final CountDownLatch firstAttemptFailed = new CountDownLatch(1);
    private final AtomicInteger attempts = new AtomicInteger();

    private FailingImportJob() {
      super(() -> {}, mock(DatabaseClient.class), ValueType.VARIABLE.name(), 1);
      setEntitiesToImport(List.of(mock(OptimizeDto.class)));
    }

    @Override
    protected void persistEntities(final List<OptimizeDto> newOptimizeEntities) {
      if (attempts.incrementAndGet() == 1) {
        firstAttemptFailed.countDown();
        throw new IllegalStateException("database unavailable");
      }
    }
  }
}
