/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.archiver.TestRepository.DocumentMove;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ArchiverJobTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(ArchiverJobTest.class);

  private static final String SOURCE_INDEX_NAME = "test-index_";
  private static final String ID_FIELD_NAME = "id-field";

  private final Executor executor = Runnable::run;

  private final TestRepository repository = new TestRepository();
  private final CamundaExporterMetrics metrics = mock(CamundaExporterMetrics.class);
  private final Consumer<Integer> recordArchiving = mock(Consumer.class);
  private final Consumer<Integer> recordArchived = mock(Consumer.class);

  private final IdxTemplateArchiver job =
      new IdxTemplateArchiver(
          repository, metrics, LOGGER, executor, recordArchiving, recordArchived);

  @Test
  void shouldReturnZeroIfNoBatchGiven() {
    // given no batch
    repository.batch = null;

    // when
    final int count = job.execute().toCompletableFuture().join();

    // then
    assertThat(count).isEqualTo(0);
    assertThat(repository.moves).isEmpty();

    // then verify recording metrics
    verifyNoInteractions(recordArchiving);
    verifyNoInteractions(recordArchived);
    verify(metrics).measureArchivingDuration(any());
  }

  @Test
  void shouldReturnZeroIfNoBatchIdsGiven() {
    // given
    repository.batch = new ArchiveBatch("2024-01-01", List.of());

    // when
    final int count = job.execute().toCompletableFuture().join();

    // then
    assertThat(count).isEqualTo(0);
    assertThat(repository.moves).isEmpty();

    // then verify recording metrics
    verifyNoInteractions(recordArchiving);
    verifyNoInteractions(recordArchived);
    verify(metrics).measureArchivingDuration(any());
  }

  @Test
  void shouldMoveInstancesById() {
    // given
    repository.batch = new ArchiveBatch("2024-01-01", List.of("1", "2", "3"));

    // when
    final int count = job.execute().toCompletableFuture().join();

    // then
    assertThat(count).isEqualTo(3);
    assertThat(repository.moves)
        .containsExactly(
            new DocumentMove(
                SOURCE_INDEX_NAME,
                SOURCE_INDEX_NAME + "2024-01-01",
                ID_FIELD_NAME,
                List.of("1", "2", "3"),
                executor));

    // then verify recording metrics
    verify(recordArchiving).accept(3);
    verify(recordArchived).accept(3);
    verify(metrics).measureArchivingDuration(any());
  }

  @Test
  void shouldRecordMetricsEvenWhenArchivingFails() {
    // given
    repository.batch = new ArchiveBatch("2026-01-01", List.of("1", "2", "3"));
    repository.shouldFailOnMove = true;

    // when
    assertThat(job.execute())
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withRootCauseExactlyInstanceOf(RuntimeException.class)
        .withMessageContaining("Simulated archiving failure");

    // then verify recording metrics - duration should still be measured
    verify(recordArchiving).accept(3);
    verifyNoInteractions(recordArchived);
    verify(metrics).measureArchivingDuration(any());
  }

  static class IdxTemplateArchiver extends ArchiverJob {

    public IdxTemplateArchiver(
        final ArchiverRepository archiverRepository,
        final CamundaExporterMetrics exporterMetrics,
        final Logger logger,
        final Executor executor,
        final Consumer<Integer> recordArchivingMetric,
        final Consumer<Integer> recordArchivedMetric) {
      super(
          archiverRepository,
          exporterMetrics,
          logger,
          executor,
          recordArchivingMetric,
          recordArchivedMetric);
    }

    @Override
    protected String getJobName() {
      return "test-archiver";
    }

    @Override
    CompletableFuture<ArchiveBatch> getNextBatch() {
      return ((TestRepository) getArchiverRepository()).getNextBatch();
    }

    @Override
    String getSourceIndexName() {
      return SOURCE_INDEX_NAME;
    }

    @Override
    String getIdFieldName() {
      return ID_FIELD_NAME;
    }
  }
}
