/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.archiver;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.archiver.ArchiverRepository.NoopArchiverRepository;
import io.camunda.exporter.archiver.ProcessInstancesArchiverJobTest.TestRepository.DocumentMove;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.webapps.schema.descriptors.operate.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.operate.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.SequenceFlowTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ProcessInstancesArchiverJobTest {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ProcessInstancesArchiverJobTest.class);

  private final Executor executor = Runnable::run;
  private final TestRepository repository = new TestRepository();
  private final ListViewTemplate processInstanceTemplate = new ListViewTemplate("", true);
  private final DecisionInstanceTemplate decisionInstanceTemplate =
      new DecisionInstanceTemplate("", true);
  private final SequenceFlowTemplate sequenceFlowTemplate = new SequenceFlowTemplate("", true);
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final CamundaExporterMetrics metrics = new CamundaExporterMetrics(meterRegistry);
  private final ProcessInstancesArchiverJob job =
      new ProcessInstancesArchiverJob(
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
  void shouldMoveDependants() {
    // given
    repository.batch = new ArchiveBatch("2024-01-01", List.of("1", "2", "3"));

    // when
    final var result = job.archiveNextBatch();

    // then
    assertThat(result).succeedsWithin(Duration.ZERO).isEqualTo(3);
    assertThat(repository.moves)
        .contains(
            new DocumentMove(
                sequenceFlowTemplate.getFullQualifiedName(),
                sequenceFlowTemplate.getFullQualifiedName() + "2024-01-01",
                ProcessInstanceDependant.PROCESS_INSTANCE_KEY,
                List.of("1", "2", "3"),
                executor),
            new DocumentMove(
                decisionInstanceTemplate.getFullQualifiedName(),
                decisionInstanceTemplate.getFullQualifiedName() + "2024-01-01",
                ProcessInstanceDependant.PROCESS_INSTANCE_KEY,
                List.of("1", "2", "3"),
                executor));
  }

  @Test
  void shouldMoveProcessInstances() {
    // given
    repository.batch = new ArchiveBatch("2024-01-01", List.of("1", "2", "3"));

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
    repository.batch = new ArchiveBatch("2024-01-01", List.of("1", "2", "3"));

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
    repository.batch = new ArchiveBatch("2024-01-01", List.of("1", "2", "3"));

    // when
    final var count = job.archiveNextBatch().join() + job.archiveNextBatch().join();

    // then
    assertThat(meterRegistry.counter("zeebe.camunda.exporter.archived.process.instances").count())
        .isEqualTo(6)
        .isEqualTo(count);
  }

  static final class TestRepository extends NoopArchiverRepository {
    private final List<DocumentMove> moves = new ArrayList<>();
    private ArchiveBatch batch;

    @Override
    public CompletableFuture<ArchiveBatch> getProcessInstancesNextBatch() {
      return CompletableFuture.completedFuture(batch);
    }

    @Override
    public CompletableFuture<Void> moveDocuments(
        final String sourceIndexName,
        final String destinationIndexName,
        final String idFieldName,
        final List<String> ids,
        final Executor executor) {
      moves.add(
          new DocumentMove(sourceIndexName, destinationIndexName, idFieldName, ids, executor));
      return CompletableFuture.completedFuture(null);
    }

    record DocumentMove(
        String sourceIndexName,
        String destinationIndexName,
        String idFieldName,
        List<String> ids,
        Executor executor) {}
  }
}
