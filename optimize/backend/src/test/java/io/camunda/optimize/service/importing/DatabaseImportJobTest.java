/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import static io.camunda.optimize.MetricEnum.ERROR_METRIC;
import static io.camunda.optimize.MetricEnum.IMPORT_DB_WRITE_FAILURES_METRIC;
import static io.camunda.optimize.OptimizeMetrics.ERROR_TYPE_TAG;
import static io.camunda.optimize.OptimizeMetrics.PARTITION_ID_TAG;
import static io.camunda.optimize.OptimizeMetrics.RECORD_TYPE_TAG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.optimize.ErrorType;
import io.camunda.optimize.dto.optimize.OptimizeDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.zeebe.protocol.record.ValueType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DatabaseImportJobTest {

  private static final String VARIABLE = ValueType.VARIABLE.name();

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final int partitionId = ThreadLocalRandom.current().nextInt(1_000, Integer.MAX_VALUE);

  @BeforeEach
  void setUp() {
    Metrics.addRegistry(registry);
  }

  @AfterEach
  void tearDown() {
    Metrics.removeRegistry(registry);
    registry.close();
  }

  @Test
  void shouldCountEveryFailedWriteOfAnImportPage() {
    // given
    final var job =
        new FailingImportJob(
            2,
            partitionId,
            new OptimizeRuntimeException("cluster_block_exception index [a] blocked"));
    job.setEntitiesToImport(List.of(mock(OptimizeDto.class)));
    final double errorsBefore = errorCount(ErrorType.CLUSTER_BLOCK);

    // when
    job.run();

    // then
    assertThat(
            registry
                .get(IMPORT_DB_WRITE_FAILURES_METRIC.getName())
                .tag(RECORD_TYPE_TAG, VARIABLE)
                .tag(PARTITION_ID_TAG, String.valueOf(partitionId))
                .tag(ERROR_TYPE_TAG, ErrorType.CLUSTER_BLOCK.getValue())
                .counter()
                .count())
        .isEqualTo(2);
    assertThat(errorCount(ErrorType.CLUSTER_BLOCK)).isEqualTo(errorsBefore + 2);
  }

  @Test
  void shouldCountUnclassifiedFailedWritesAsUnknownErrors() {
    // given
    final var job =
        new FailingImportJob(1, partitionId, new OptimizeRuntimeException("Request timed out"));
    job.setEntitiesToImport(List.of(mock(OptimizeDto.class)));
    final double errorsBefore = errorCount(ErrorType.UNKNOWN);

    // when
    job.run();

    // then
    assertThat(
            registry
                .get(IMPORT_DB_WRITE_FAILURES_METRIC.getName())
                .tag(RECORD_TYPE_TAG, VARIABLE)
                .tag(PARTITION_ID_TAG, String.valueOf(partitionId))
                .tag(ERROR_TYPE_TAG, ErrorType.UNKNOWN.getValue())
                .counter()
                .count())
        .isEqualTo(1);
    assertThat(errorCount(ErrorType.UNKNOWN)).isEqualTo(errorsBefore + 1);
  }

  private double errorCount(final ErrorType errorType) {
    final Counter counter =
        registry.find(ERROR_METRIC.getName()).tag(ERROR_TYPE_TAG, errorType.getValue()).counter();
    return counter == null ? 0 : counter.count();
  }

  private static final class FailingImportJob extends DatabaseImportJob<OptimizeDto> {

    private final RuntimeException failure;
    private int failuresLeft;

    private FailingImportJob(
        final int failures, final int partitionId, final RuntimeException failure) {
      super(() -> {}, mock(DatabaseClient.class), VARIABLE, partitionId);
      failuresLeft = failures;
      this.failure = failure;
    }

    @Override
    protected void persistEntities(final List<OptimizeDto> newOptimizeEntities) {
      if (failuresLeft-- > 0) {
        throw failure;
      }
    }
  }
}
