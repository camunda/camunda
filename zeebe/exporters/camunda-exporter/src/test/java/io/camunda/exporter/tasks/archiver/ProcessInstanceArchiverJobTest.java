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
import io.camunda.exporter.tasks.archiver.ArchiveBatch.ProcessInstanceArchiveBatch;
import io.camunda.exporter.tasks.archiver.TestRepository.DocumentMove;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.template.AuditLogTemplate;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.SequenceFlowTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ProcessInstanceArchiverJobTest extends ArchiverJobRecordingMetricsAbstractTest {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ProcessInstanceArchiverJobTest.class);

  private final Executor executor = Runnable::run;

  private final HistoryConfiguration historyConfiguration = new HistoryConfiguration();
  private final TestRepository repository = spy(new TestRepository());
  private final ListViewTemplate processInstanceTemplate = new ListViewTemplate("", true);
  private final DecisionInstanceTemplate decisionInstanceTemplate =
      new DecisionInstanceTemplate("", true);
  private final SequenceFlowTemplate sequenceFlowTemplate = new SequenceFlowTemplate("", true);
  private final AuditLogTemplate auditLogTemplate = new AuditLogTemplate("", true);

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final CamundaExporterMetrics metrics = new CamundaExporterMetrics(meterRegistry);

  private final ProcessInstanceArchiverJob job =
      new ProcessInstanceArchiverJob(
          historyConfiguration,
          repository,
          processInstanceTemplate,
          List.of(decisionInstanceTemplate, sequenceFlowTemplate, auditLogTemplate),
          metrics,
          LOGGER,
          executor);

  @BeforeEach
  void setUp() {
    // given
    repository.batches =
        List.of(
            new ProcessInstanceArchiveBatch("2024-01-01", List.of(1L, 2L, 3L), List.of()),
            new ProcessInstanceArchiveBatch("2024-01-01", List.of(4L, 5L, 6L), List.of()));
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
            historyConfiguration,
            repository,
            processInstanceTemplate,
            List.of(),
            metrics,
            LOGGER,
            executor);

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
                Map.of(ListViewTemplate.PROCESS_INSTANCE_KEY, List.of("1", "2", "3")),
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
                Map.of(auditLogTemplate.getProcessInstanceDependantField(), List.of("1", "2", "3")),
                executor),
            new DocumentMove(
                decisionInstanceTemplate.getFullQualifiedName(),
                decisionInstanceTemplate.getFullQualifiedName() + "2024-01-01",
                Map.of(
                    decisionInstanceTemplate.getProcessInstanceDependantField(),
                    List.of("1", "2", "3")),
                executor),
            new DocumentMove(
                sequenceFlowTemplate.getFullQualifiedName(),
                sequenceFlowTemplate.getFullQualifiedName() + "2024-01-01",
                Map.of(
                    sequenceFlowTemplate.getProcessInstanceDependantField(),
                    List.of("1", "2", "3")),
                executor),
            new DocumentMove(
                processInstanceTemplate.getFullQualifiedName(),
                processInstanceTemplate.getFullQualifiedName() + "2024-01-01",
                Map.of(ListViewTemplate.PROCESS_INSTANCE_KEY, List.of("1", "2", "3")),
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
            historyConfiguration,
            repository,
            processInstanceTemplate,
            List.of(dependant),
            metrics,
            LOGGER,
            executor);
    repository.batches =
        List.of(new ProcessInstanceArchiveBatch("2024-01-01", List.of(1L, 2L), List.of()));

    // when
    final int count = job.execute().toCompletableFuture().join();

    // then
    assertThat(count).isEqualTo(2); // batch has 2 ids
    assertArchivingCounts(count); // asserted as 2 above
    assertArchiverTimer(1);
    assertThat(repository.moves)
        .contains(
            new DocumentMove(
                "foo_", "foo_" + "2024-01-01", Map.of("bar", List.of("1", "2")), executor));
  }

  @Test
  void shouldReturnEmptyBatchIfNoIdsGiven() {
    // given
    repository.batches =
        List.of(new ProcessInstanceArchiveBatch("2024-01-01", List.of(), List.of()));

    // when
    final var nextBatch = job.getNextBatch().toCompletableFuture().join();

    // then
    assertThat(nextBatch)
        .isEqualTo(new ProcessInstanceArchiveBatch("2024-01-01", List.of(), List.of()));

    verify(repository).getProcessInstancesNextBatch(1_000);
  }

  @Test
  void shouldRequestLargeBatchAndChunkIt() {
    // given
    repository.batches =
        List.of(
            new ProcessInstanceArchiveBatch(
                "2024-01-01", LongStream.rangeClosed(1L, 300L).boxed().toList(), List.of()));

    // when
    final var first = job.getNextBatch().toCompletableFuture().join();
    final var second = job.getNextBatch().toCompletableFuture().join();
    final var third = job.getNextBatch().toCompletableFuture().join();

    // then
    assertThat(first)
        .isEqualTo(
            new ProcessInstanceArchiveBatch(
                "2024-01-01", LongStream.rangeClosed(1L, 100L).boxed().toList(), List.of()));
    assertThat(second)
        .isEqualTo(
            new ProcessInstanceArchiveBatch(
                "2024-01-01", LongStream.rangeClosed(101L, 200L).boxed().toList(), List.of()));
    assertThat(third)
        .isEqualTo(
            new ProcessInstanceArchiveBatch(
                "2024-01-01", LongStream.rangeClosed(201L, 300L).boxed().toList(), List.of()));

    verify(repository).getProcessInstancesNextBatch(1_000);
  }

  @Test
  void shouldRequestAgainWhenChunksExhausted() {
    // given
    repository.batches =
        List.of(
            new ProcessInstanceArchiveBatch(
                "2024-01-01", LongStream.rangeClosed(1L, 200L).boxed().toList(), List.of()));

    // when
    final var first = job.getNextBatch().toCompletableFuture().join();
    final var second = job.getNextBatch().toCompletableFuture().join();
    final var third = job.getNextBatch().toCompletableFuture().join();

    // then
    assertThat(first)
        .isEqualTo(
            new ProcessInstanceArchiveBatch(
                "2024-01-01", LongStream.rangeClosed(1L, 100L).boxed().toList(), List.of()));
    assertThat(second)
        .isEqualTo(
            new ProcessInstanceArchiveBatch(
                "2024-01-01", LongStream.rangeClosed(101L, 200L).boxed().toList(), List.of()));
    assertThat(third)
        .isEqualTo(
            new ProcessInstanceArchiveBatch(
                "2024-01-01", LongStream.rangeClosed(1L, 100L).boxed().toList(), List.of()));

    verify(repository, times(2)).getProcessInstancesNextBatch(1_000);
  }

  @Test
  void shouldSkipAlreadyArchivedProcessInstances() {
    // given
    repository.batches =
        List.of(
            new ProcessInstanceArchiveBatch("2024-01-01", List.of(1L, 2L, 3L), List.of(7L, 8L)));
    final var count1 = getArchiverJob().execute().toCompletableFuture().join();

    // 2nd batch has overlapping ids with 1st batch, but also new ones. Should skip the overlapping
    // ones, but still use the new ones
    repository.batches =
        List.of(
            new ProcessInstanceArchiveBatch("2024-01-01", List.of(2L, 3L, 4L), List.of(8L, 9L)));

    // when
    final var count2 = getArchiverJob().execute().toCompletableFuture().join();

    // then
    assertThat(count1).isEqualTo(5);
    assertThat(count2).isEqualTo(2);
    assertArchivingCounts(7);
    assertThat(getMeterRegistry().counter(getJobMetricName(), "state", "deduplicated").count())
        .isEqualTo(3);
    assertArchiverTimer(2); // job executed twice

    verify(repository, times(2)).getProcessInstancesNextBatch(1_000);
  }

  private static final class WeirdlyNamedDependant implements ProcessInstanceDependant {

    @Override
    public String getFullQualifiedName() {
      return "foo_";
    }

    @Override
    public String getAlias() {
      return "foo_alias";
    }

    @Override
    public String getIndexName() {
      return "foo";
    }

    @Override
    public String getMappingsClasspathFilename() {
      return "";
    }

    @Override
    public String getAllVersionsIndexNameRegexPattern() {
      return "";
    }

    @Override
    public String getIndexNameWithoutVersion() {
      return "foo_";
    }

    @Override
    public String getVersion() {
      return "";
    }

    @Override
    public String getProcessInstanceDependantField() {
      return "bar";
    }

    @Override
    public String getIndexPattern() {
      return "";
    }

    @Override
    public String getTemplateName() {
      return "";
    }

    @Override
    public List<String> getComposedOf() {
      return List.of();
    }
  }
}
