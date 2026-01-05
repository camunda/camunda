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
import io.camunda.webapps.schema.descriptors.template.AuditLogTemplate;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.SequenceFlowTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ProcessInstanceArchiverJobTest extends ArchiverJobRecordingMetricsAbstractTest {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ProcessInstanceArchiverJobTest.class);

  private final Executor executor = Runnable::run;

  private final TestRepository repository = new TestRepository();
  private final ListViewTemplate processInstanceTemplate = new ListViewTemplate("", true);
  private final DecisionInstanceTemplate decisionInstanceTemplate =
      new DecisionInstanceTemplate("", true);
  private final SequenceFlowTemplate sequenceFlowTemplate = new SequenceFlowTemplate("", true);
  private final AuditLogTemplate auditLogTemplate = new AuditLogTemplate("", true);

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final CamundaExporterMetrics metrics = new CamundaExporterMetrics(meterRegistry);

  private final ProcessInstanceArchiverJob job =
      new ProcessInstanceArchiverJob(
          repository,
          processInstanceTemplate,
          List.of(decisionInstanceTemplate, sequenceFlowTemplate, auditLogTemplate),
          metrics,
          LOGGER,
          executor);

  @BeforeEach
  void setUp() {
    // given
    repository.batch = new ArchiveBatch("2024-01-01", List.of("1", "2", "3"));
  }

  @AfterEach
  void cleanUp() {
    meterRegistry.clear();
  }

  @Override
  ArchiverJob getArchiverJob() {
    return job;
  }

  @Override
  SimpleMeterRegistry getMeterRegistry() {
    return meterRegistry;
  }

  @Override
  String getJobMetricName() {
    return "zeebe.camunda.exporter.archiver.process.instances";
  }

  @Test
  void shouldOnlyMoveProcessInstancesWhenNoDependentTemplates() {
    // given
    final ProcessInstanceArchiverJob processInstanceJob =
        new ProcessInstanceArchiverJob(
            repository, processInstanceTemplate, List.of(), metrics, LOGGER, executor);

    // when
    final int count = processInstanceJob.execute().toCompletableFuture().join();

    // then
    assertThat(count).isEqualTo(3); // batch has 3 ids
    assertArchivingCounts(count); // asserted as 3 above
    assertArchiverTimer(1);

    // then should move
    assertThat(repository.moves)
        .containsExactly(
            new DocumentMove(
                processInstanceTemplate.getFullQualifiedName(),
                processInstanceTemplate.getFullQualifiedName() + "2024-01-01",
                ListViewTemplate.PROCESS_INSTANCE_KEY,
                List.of("1", "2", "3"),
                executor));
  }

  @Test
  void shouldMoveDependants() {
    // when
    final int count = job.execute().toCompletableFuture().join();

    // then
    assertThat(count).isEqualTo(3); // batch has 3 ids
    assertArchivingCounts(count); // asserted as 3 above
    assertArchiverTimer(1);

    // then should move
    assertThat(repository.moves)
        .containsExactly(
            new DocumentMove(
                auditLogTemplate.getFullQualifiedName(),
                auditLogTemplate.getFullQualifiedName() + "2024-01-01",
                auditLogTemplate.getProcessInstanceDependantField(),
                List.of("1", "2", "3"),
                executor),
            new DocumentMove(
                decisionInstanceTemplate.getFullQualifiedName(),
                decisionInstanceTemplate.getFullQualifiedName() + "2024-01-01",
                decisionInstanceTemplate.getProcessInstanceDependantField(),
                List.of("1", "2", "3"),
                executor),
            new DocumentMove(
                sequenceFlowTemplate.getFullQualifiedName(),
                sequenceFlowTemplate.getFullQualifiedName() + "2024-01-01",
                sequenceFlowTemplate.getProcessInstanceDependantField(),
                List.of("1", "2", "3"),
                executor),
            new DocumentMove(
                processInstanceTemplate.getFullQualifiedName(),
                processInstanceTemplate.getFullQualifiedName() + "2024-01-01",
                ListViewTemplate.PROCESS_INSTANCE_KEY,
                List.of("1", "2", "3"),
                executor));
  }

  @Test
  void shouldMoveDependantsBeforeProcessInstances() {
    // when
    final int count = job.execute().toCompletableFuture().join();

    // then
    assertThat(count).isEqualTo(3); // batch has 3 ids
    assertArchivingCounts(count); // asserted as 3 above
    assertArchiverTimer(1);

    // then should move in correct order
    assertThat(repository.moves)
        .map(DocumentMove::sourceIndexName)
        .containsExactly(
            auditLogTemplate.getFullQualifiedName(),
            decisionInstanceTemplate.getFullQualifiedName(),
            sequenceFlowTemplate.getFullQualifiedName(),
            processInstanceTemplate.getFullQualifiedName());
  }

  @Test
  void shouldMoveDependantsViaCorrectField() {
    // given
    final var dependant = new WeirdlyNamedDependant();
    final var job =
        new ProcessInstanceArchiverJob(
            repository, processInstanceTemplate, List.of(dependant), metrics, LOGGER, executor);
    repository.batch = new ArchiveBatch("2024-01-01", List.of("1", "2"));

    // when
    final int count = job.execute().toCompletableFuture().join();

    // then
    assertThat(count).isEqualTo(2); // batch has 2 ids
    assertArchivingCounts(count); // asserted as 2 above
    assertArchiverTimer(1);
    assertThat(repository.moves)
        .contains(
            new DocumentMove("foo_", "foo_" + "2024-01-01", "bar", List.of("1", "2"), executor));
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
