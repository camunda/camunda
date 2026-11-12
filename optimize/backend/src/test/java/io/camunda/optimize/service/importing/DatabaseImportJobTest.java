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

import io.camunda.optimize.dto.optimize.OptimizeDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatabaseImportJobTest {

  @Test
  void shouldWaitAboutOneAndAHalfSecondsBeforeTheFirstRetry() {
    // given
    final var job = new FailingImportJob(new OptimizeRuntimeException("Request timed out"));
    job.setEntitiesToImport(List.of(mock(OptimizeDto.class)));

    // when
    final long start = System.nanoTime();
    job.run();
    final long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

    // then
    assertThat(elapsedMillis).isGreaterThanOrEqualTo(1_400);
  }

  /** Fails on the first attempt; any retry succeeds immediately. */
  private static final class FailingImportJob extends DatabaseImportJob<OptimizeDto> {

    private final RuntimeException failure;
    private boolean failed;

    private FailingImportJob(final RuntimeException failure) {
      super(() -> {}, mock(DatabaseClient.class));
      this.failure = failure;
    }

    @Override
    protected void persistEntities(final List<OptimizeDto> newOptimizeEntities) {
      if (!failed) {
        failed = true;
        throw failure;
      }
    }
  }
}
