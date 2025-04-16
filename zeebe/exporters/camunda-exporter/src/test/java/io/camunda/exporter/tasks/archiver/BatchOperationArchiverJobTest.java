/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.archiver.TestRepository.DocumentMove;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class BatchOperationArchiverJobTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(BatchOperationArchiverJobTest.class);

  private final Executor executor = Runnable::run;
  private final TestRepository repository = new TestRepository();
  private final BatchOperationTemplate batchOperationTemplate =
      new BatchOperationTemplate("", true);
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final CamundaExporterMetrics metrics = new CamundaExporterMetrics(meterRegistry);
  private final BatchOperationArchiverJob job =
      new BatchOperationArchiverJob(repository, batchOperationTemplate, metrics, LOGGER, executor);

  @Test
  void shouldReturnZeroIfNoBatchGiven() {
    // given - when
    final var result = job.archiveNextBatch();

    // then
    assertThat(result).succeedsWithin(Duration.ZERO).isEqualTo(0);
  }

  @Test
  void shouldReturnZeroIfNoBatchIdsGiven() {
    // given
    repository.batch = new ArchiveBatch("2024-01-01", List.of());

    // when
    final var result = job.archiveNextBatch();

    // then
    assertThat(result).succeedsWithin(Duration.ZERO).isEqualTo(0);
  }

  @Test
  void shouldMoveBatchOperations() {
    // given
    repository.batch = new ArchiveBatch("2024-01-01", List.of("1", "2", "3"));

    // when
    final var result = job.archiveNextBatch();

    // then
    assertThat(result).succeedsWithin(Duration.ZERO).isEqualTo(3);
    assertThat(repository.moves)
        .contains(
            new DocumentMove(
                batchOperationTemplate.getFullQualifiedName(),
                batchOperationTemplate.getFullQualifiedName() + "2024-01-01",
                BatchOperationTemplate.ID,
                List.of("1", "2", "3"),
                executor));
  }

  @Test
  void shouldRecordBatchOperationsIncrease() {
    // given
    repository.batch = new ArchiveBatch("2024-01-01", List.of("1", "2", "3"));

    // when
    final var count =
        job.archiveNextBatch().toCompletableFuture().join()
            + job.archiveNextBatch().toCompletableFuture().join();

    // then
    assertThat(
            meterRegistry
                .counter("zeebe.camunda.exporter.archiver.batch.operations", "state", "archiving")
                .count())
        .isEqualTo(6)
        .isEqualTo(count);
    assertThat(
            meterRegistry
                .counter("zeebe.camunda.exporter.archiver.batch.operations", "state", "archived")
                .count())
        .isEqualTo(6)
        .isEqualTo(count);
  }
}
