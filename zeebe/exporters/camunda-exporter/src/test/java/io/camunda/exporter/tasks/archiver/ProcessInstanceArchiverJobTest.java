/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.archiver.TestRepository.DocumentMove;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.SequenceFlowTemplate;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ProcessInstanceArchiverJobTest {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ProcessInstanceArchiverJobTest.class);

  private final Executor executor = Runnable::run;
  private final HistoryConfiguration historyConfiguration = new HistoryConfiguration();
  private final TestRepository repository = spy(new TestRepository());
  private final ListViewTemplate processInstanceTemplate = new ListViewTemplate("", true);
  private final DecisionInstanceTemplate decisionInstanceTemplate =
      new DecisionInstanceTemplate("", true);
  private final SequenceFlowTemplate sequenceFlowTemplate = new SequenceFlowTemplate("", true);
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final CamundaExporterMetrics metrics = new CamundaExporterMetrics(meterRegistry);
  private final ProcessInstanceArchiverJob job =
      new ProcessInstanceArchiverJob(
          historyConfiguration,
          repository,
          processInstanceTemplate,
          List.of(sequenceFlowTemplate, decisionInstanceTemplate),
          metrics,
          LOGGER,
          executor);

  @Test
  void shouldReturnZeroIfNoBatchGiven() {
    // given - when
    final var result = job.archiveNextBatch();

    // then
    assertThat(result).succeedsWithin(Duration.ZERO).isEqualTo(0);
    assertThat(repository.moves).isEmpty();
  }

  @Test
  void shouldReturnZeroIfNoBatchIdsGiven() {
    // given
    repository.batches = List.of(new ArchiveBatch("2024-01-01", List.of()));

    // when
    final var result = job.archiveNextBatch();

    // then
    assertThat(result).succeedsWithin(Duration.ZERO).isEqualTo(0);
    assertThat(repository.moves).isEmpty();
  }

  @Test
  void shouldMoveDependants() {
    // given
    repository.batches = List.of(new ArchiveBatch("2024-01-01", List.of("1", "2", "3")));

    // when
    final var result = job.archiveNextBatch();

    // then
    assertThat(result).succeedsWithin(Duration.ZERO).isEqualTo(3);
    assertThat(repository.moves)
        .contains(
            new DocumentMove(
                sequenceFlowTemplate.getFullQualifiedName(),
                sequenceFlowTemplate.getFullQualifiedName() + "2024-01-01",
                sequenceFlowTemplate.getProcessInstanceDependantField(),
                List.of("1", "2", "3"),
                executor),
            new DocumentMove(
                decisionInstanceTemplate.getFullQualifiedName(),
                decisionInstanceTemplate.getFullQualifiedName() + "2024-01-01",
                decisionInstanceTemplate.getProcessInstanceDependantField(),
                List.of("1", "2", "3"),
                executor));
  }

  @Test
  void shouldMoveDependantsViaCorrectField() {
    // given
    final var dependant = new WeirdlyNamedDependant();
    final var job =
        new ProcessInstanceArchiverJob(
            historyConfiguration,
            repository,
            processInstanceTemplate,
            List.of(dependant),
            metrics,
            LOGGER,
            executor);
    repository.batches = List.of(new ArchiveBatch("2024-01-01", List.of("1", "2", "3")));

    // when
    final var result = job.archiveNextBatch();

    // then
    assertThat(result).succeedsWithin(Duration.ZERO).isEqualTo(3);
    assertThat(repository.moves)
        .contains(
            new DocumentMove(
                "foo_", "foo_" + "2024-01-01", "bar", List.of("1", "2", "3"), executor));
  }

  @Test
  void shouldMoveProcessInstances() {
    // given
    repository.batches = List.of(new ArchiveBatch("2024-01-01", List.of("1", "2", "3")));

    // when
    final var result = job.archiveNextBatch();

    // then
    assertThat(result).succeedsWithin(Duration.ZERO).isEqualTo(3);
    assertThat(repository.moves)
        .contains(
            new DocumentMove(
                processInstanceTemplate.getFullQualifiedName(),
                processInstanceTemplate.getFullQualifiedName() + "2024-01-01",
                ProcessInstanceDependant.PROCESS_INSTANCE_KEY,
                List.of("1", "2", "3"),
                executor));
  }

  @Test
  void shouldMoveDependantsBeforeProcessInstances() {
    // given
    repository.batches = List.of(new ArchiveBatch("2024-01-01", List.of("1", "2", "3")));

    // when
    final var result = job.archiveNextBatch();

    // then
    assertThat(result).succeedsWithin(Duration.ZERO).isEqualTo(3);
    assertThat(repository.moves)
        .map(DocumentMove::sourceIndexName)
        .containsExactly(
            sequenceFlowTemplate.getFullQualifiedName(),
            decisionInstanceTemplate.getFullQualifiedName(),
            processInstanceTemplate.getFullQualifiedName());
  }

  @Test
  void shouldRecordProcessInstancesArchived() {
    // given
    repository.batches =
        List.of(
            new ArchiveBatch("2024-01-01", List.of("1", "2", "3")),
            new ArchiveBatch("2024-01-01", List.of("4", "5", "6")));

    // when
    final var count =
        job.archiveNextBatch().toCompletableFuture().join()
            + job.archiveNextBatch().toCompletableFuture().join();

    // then
    assertThat(
            meterRegistry
                .counter("zeebe.camunda.exporter.archiver.process.instances", "state", "archiving")
                .count())
        .isEqualTo(6)
        .isEqualTo(count);
    assertThat(
            meterRegistry
                .counter("zeebe.camunda.exporter.archiver.process.instances", "state", "archived")
                .count())
        .isEqualTo(6)
        .isEqualTo(count);
  }

  @Test
  void shouldRequestLargeBatchesWhenArchiving() {
    // given
    repository.batches =
        List.of(
            new ArchiveBatch(
                "2024-01-01", LongStream.rangeClosed(1L, 125L).mapToObj(String::valueOf).toList()));

    // when
    final var count1 = job.archiveNextBatch().toCompletableFuture().join();
    final var count2 = job.archiveNextBatch().toCompletableFuture().join();

    // then
    assertThat(count1).isEqualTo(100);
    assertThat(count2).isEqualTo(25);

    // two batches processed, but only one request made to get the batches
    verify(repository).getProcessInstancesNextBatch(1_000);
  }

  @Test
  void shouldRequestLargeBatchAndChunkIt() {
    // given
    repository.batches =
        List.of(
            new ArchiveBatch(
                "2024-01-01", LongStream.rangeClosed(1L, 300L).mapToObj(String::valueOf).toList()));

    // when
    final var first = job.getNextBatch().toCompletableFuture().join();
    final var second = job.getNextBatch().toCompletableFuture().join();
    final var third = job.getNextBatch().toCompletableFuture().join();

    // then
    assertThat(first)
        .isEqualTo(
            new ArchiveBatch(
                "2024-01-01", LongStream.rangeClosed(1L, 100L).mapToObj(String::valueOf).toList()));
    assertThat(second)
        .isEqualTo(
            new ArchiveBatch(
                "2024-01-01",
                LongStream.rangeClosed(101L, 200L).mapToObj(String::valueOf).toList()));
    assertThat(third)
        .isEqualTo(
            new ArchiveBatch(
                "2024-01-01",
                LongStream.rangeClosed(201L, 300L).mapToObj(String::valueOf).toList()));

    verify(repository).getProcessInstancesNextBatch(1_000);
  }

  @Test
  void shouldRequestAgainWhenChunksExhausted() {
    // given
    repository.batches =
        List.of(
            new ArchiveBatch(
                "2024-01-01", LongStream.rangeClosed(1L, 200L).mapToObj(String::valueOf).toList()));

    // when
    final var first = job.getNextBatch().toCompletableFuture().join();
    final var second = job.getNextBatch().toCompletableFuture().join();
    final var third = job.getNextBatch().toCompletableFuture().join();

    // then
    assertThat(first)
        .isEqualTo(
            new ArchiveBatch(
                "2024-01-01", LongStream.rangeClosed(1L, 100L).mapToObj(String::valueOf).toList()));
    assertThat(second)
        .isEqualTo(
            new ArchiveBatch(
                "2024-01-01",
                LongStream.rangeClosed(101L, 200L).mapToObj(String::valueOf).toList()));
    assertThat(third)
        .isEqualTo(
            new ArchiveBatch(
                "2024-01-01", LongStream.rangeClosed(1L, 100L).mapToObj(String::valueOf).toList()));

    verify(repository, times(2)).getProcessInstancesNextBatch(1_000);
  }

  @Test
  void shouldSkipAlreadyArchivedProcessInstances() {
    // given
    repository.batches = List.of(new ArchiveBatch("2024-01-01", List.of("1", "2", "3", "7", "8")));
    final var count1 = job.execute().toCompletableFuture().join();

    // 2nd batch has overlapping ids with 1st batch, but also new ones. Should skip the overlapping
    // ones, but still use the new ones
    repository.batches = List.of(new ArchiveBatch("2024-01-01", List.of("2", "3", "4", "8", "9")));

    // when
    final var count2 = job.execute().toCompletableFuture().join();

    // then
    assertThat(count1).isEqualTo(5);
    assertThat(count2).isEqualTo(2);

    assertThat(
            meterRegistry
                .counter("zeebe.camunda.exporter.archiver.process.instances", "state", "archiving")
                .count())
        .isEqualTo(7);
    assertThat(
            meterRegistry
                .counter("zeebe.camunda.exporter.archiver.process.instances", "state", "archived")
                .count())
        .isEqualTo(7);
    assertThat(
            meterRegistry
                .counter(
                    "zeebe.camunda.exporter.archiver.process.instances", "state", "deduplicated")
                .count())
        .isEqualTo(3);

    final Timer archiverTimer = meterRegistry.timer("zeebe.camunda.exporter.archiver.duration");
    // job executed twice, so two recordings expected
    assertThat(archiverTimer.count()).isEqualTo(2);
    assertThat(archiverTimer.totalTime(TimeUnit.NANOSECONDS)).isGreaterThan(0);

    verify(repository, times(2)).getProcessInstancesNextBatch(1_000);
  }

  private static final class WeirdlyNamedDependant implements ProcessInstanceDependant {

    @Override
    public String getFullQualifiedName() {
      return "foo_";
    }

    @Override
    public String getProcessInstanceDependantField() {
      return "bar";
    }
  }
}
