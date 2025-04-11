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
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.SequenceFlowTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
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
        new ProcessInstancesArchiverJob(
            repository, processInstanceTemplate, List.of(dependant), metrics, LOGGER, executor);
    repository.batch = new ArchiveBatch("2024-01-01", List.of("1", "2", "3"));

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
