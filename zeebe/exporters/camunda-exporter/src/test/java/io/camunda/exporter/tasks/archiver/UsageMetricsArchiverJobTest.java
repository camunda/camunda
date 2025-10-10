<<<<<<< HEAD
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
import io.camunda.webapps.schema.descriptors.template.UsageMetricTUTemplate;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class UsageMetricsArchiverJobTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(UsageMetricsArchiverJobTest.class);

  private final Executor executor = Runnable::run;
  private final UsageMetricsTestRepository repository = new UsageMetricsTestRepository();
  private final UsageMetricTemplate usageMetricTemplate = new UsageMetricTemplate("", true);
  private final UsageMetricTUTemplate usageMetricTUTemplate = new UsageMetricTUTemplate("", true);
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final CamundaExporterMetrics metrics = new CamundaExporterMetrics(meterRegistry);
  private final UsageMetricsArchiverJob job =
      new UsageMetricsArchiverJob(
          repository, usageMetricTemplate, usageMetricTUTemplate, metrics, LOGGER, executor);

  @Test
  void shouldReturnZeroIfNoBatches() {
    // given - both batches null

    // when
    final var result = job.archiveNextBatch();

    // then
    assertThat(result).succeedsWithin(Duration.ZERO).isEqualTo(0);
    assertThat(repository.moves).isEmpty();
  }

  @Test
  void shouldReturnZeroIfNoIdsInBatches() {
    // given
    repository.usageBatch = new ArchiveBatch("2024-01-01", List.of());
    repository.usageTUBatch = new ArchiveBatch("2024-01-01", List.of());

    // when
    final var result = job.archiveNextBatch();

    // then
    assertThat(result).succeedsWithin(Duration.ZERO).isEqualTo(0);
    assertThat(repository.moves).isEmpty();
  }

  @Test
  void shouldArchiveUsageMetricOnly() {
    // given
    repository.usageBatch = new ArchiveBatch("2024-01-01", List.of("1", "2"));

    // when
    final var result = job.archiveNextBatch();

    // then
    assertThat(result).succeedsWithin(Duration.ZERO).isEqualTo(2);
    assertThat(repository.moves)
        .containsExactly(
            new UsageMetricsTestRepository.DocumentMove(
                usageMetricTemplate.getFullQualifiedName(),
                usageMetricTemplate.getFullQualifiedName() + "2024-01-01",
                UsageMetricTemplate.ID,
                List.of("1", "2"),
                executor));

    assertMetricCounter("usage.metrics", 2);
    assertMetricCounter("usage.metrics.tu", 0);
  }

  @Test
  void shouldArchiveUsageMetricTUOnly() {
    // given
    repository.usageTUBatch = new ArchiveBatch("2024-01-01", List.of("10", "11", "12"));

    // when
    final var result = job.archiveNextBatch();

    // then
    assertThat(result).succeedsWithin(Duration.ZERO).isEqualTo(3);
    assertThat(repository.moves)
        .containsExactly(
            new UsageMetricsTestRepository.DocumentMove(
                usageMetricTUTemplate.getFullQualifiedName(),
                usageMetricTUTemplate.getFullQualifiedName() + "2024-01-01",
                UsageMetricTUTemplate.ID,
                List.of("10", "11", "12"),
                executor));

    assertMetricCounter("usage.metrics", 0);
    assertMetricCounter("usage.metrics.tu", 3);
  }

  @Test
  void shouldArchiveBothBatchesAndSum() {
    // given
    repository.usageBatch = new ArchiveBatch("2024-01-01", List.of("1", "2"));
    repository.usageTUBatch = new ArchiveBatch("2024-01-01", List.of("10"));

    // when
    final var result = job.archiveNextBatch();

    // then
    assertThat(result).succeedsWithin(Duration.ZERO).isEqualTo(3);
    assertThat(repository.moves)
        .containsExactly(
            new UsageMetricsTestRepository.DocumentMove(
                usageMetricTemplate.getFullQualifiedName(),
                usageMetricTemplate.getFullQualifiedName() + "2024-01-01",
                UsageMetricTemplate.ID,
                List.of("1", "2"),
                executor),
            new UsageMetricsTestRepository.DocumentMove(
                usageMetricTUTemplate.getFullQualifiedName(),
                usageMetricTUTemplate.getFullQualifiedName() + "2024-01-01",
                UsageMetricTUTemplate.ID,
                List.of("10"),
                executor));

    assertMetricCounter("usage.metrics", 2);
    assertMetricCounter("usage.metrics.tu", 1);
  }

  @Test
  void shouldArchiveUsageMetricBeforeTU() {
    // given
    repository.usageBatch = new ArchiveBatch("2024-01-01", List.of("1"));
    repository.usageTUBatch = new ArchiveBatch("2024-01-01", List.of("10"));

    // when
    job.archiveNextBatch();

    // then
    assertThat(repository.moves)
        .extracting(m -> m.sourceIndexName)
        .containsExactly(
            usageMetricTemplate.getFullQualifiedName(),
            usageMetricTUTemplate.getFullQualifiedName());
  }

  private void assertMetricCounter(final String metricName, final int expected) {
    // then
    assertThat(
            meterRegistry
                .counter(
                    String.format("zeebe.camunda.exporter.archiver.%s", metricName),
                    "state",
                    "archiving")
                .count())
        .isEqualTo(expected);
    assertThat(
            meterRegistry
                .counter(
                    String.format("zeebe.camunda.exporter.archiver.%s", metricName),
                    "state",
                    "archived")
                .count())
        .isEqualTo(expected);
  }

  private static final class UsageMetricsTestRepository
      extends ArchiverRepository.NoopArchiverRepository {

    final List<DocumentMove> moves = new ArrayList<>();
    ArchiveBatch usageBatch;
    ArchiveBatch usageTUBatch;

    @Override
    public CompletableFuture<ArchiveBatch> getUsageMetricTUNextBatch() {
      return CompletableFuture.completedFuture(usageTUBatch);
    }

    @Override
    public CompletableFuture<ArchiveBatch> getUsageMetricNextBatch() {
      return CompletableFuture.completedFuture(usageBatch);
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
||||||| 4f0d68366a8
=======
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.webapps.schema.descriptors.template.UsageMetricTUTemplate;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTemplate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class UsageMetricsArchiverJobTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(UsageMetricsArchiverJobTest.class);

  private final Executor executor = Runnable::run;
  private final UsageMetricsTestRepository repository = new UsageMetricsTestRepository();
  private final UsageMetricTemplate usageMetricTemplate = new UsageMetricTemplate("", true);
  private final UsageMetricTUTemplate usageMetricTUTemplate = new UsageMetricTUTemplate("", true);
  private final UsageMetricsArchiverJob job =
      new UsageMetricsArchiverJob(
          repository, LOGGER, usageMetricTemplate, usageMetricTUTemplate, executor);

  @Test
  void shouldReturnZeroIfNoBatches() {
    // given - both batches null

    // when
    final var result = job.archiveNextBatch();

    // then
    assertThat(result).succeedsWithin(Duration.ZERO).isEqualTo(0);
    assertThat(repository.moves).isEmpty();
  }

  @Test
  void shouldReturnZeroIfNoIdsInBatches() {
    // given
    repository.usageBatch = new ArchiveBatch("2024-01-01", List.of());
    repository.usageTUBatch = new ArchiveBatch("2024-01-01", List.of());

    // when
    final var result = job.archiveNextBatch();

    // then
    assertThat(result).succeedsWithin(Duration.ZERO).isEqualTo(0);
    assertThat(repository.moves).isEmpty();
  }

  @Test
  void shouldArchiveUsageMetricOnly() {
    // given
    repository.usageBatch = new ArchiveBatch("2024-01-01", List.of("1", "2"));

    // when
    final var result = job.archiveNextBatch();

    // then
    assertThat(result).succeedsWithin(Duration.ZERO).isEqualTo(2);
    assertThat(repository.moves)
        .containsExactly(
            new UsageMetricsTestRepository.DocumentMove(
                usageMetricTemplate.getFullQualifiedName(),
                usageMetricTemplate.getFullQualifiedName() + "2024-01-01",
                UsageMetricTemplate.ID,
                List.of("1", "2"),
                executor));
  }

  @Test
  void shouldArchiveUsageMetricTUOnly() {
    // given
    repository.usageTUBatch = new ArchiveBatch("2024-01-01", List.of("10", "11", "12"));

    // when
    final var result = job.archiveNextBatch();

    // then
    assertThat(result).succeedsWithin(Duration.ZERO).isEqualTo(3);
    assertThat(repository.moves)
        .containsExactly(
            new UsageMetricsTestRepository.DocumentMove(
                usageMetricTUTemplate.getFullQualifiedName(),
                usageMetricTUTemplate.getFullQualifiedName() + "2024-01-01",
                UsageMetricTUTemplate.ID,
                List.of("10", "11", "12"),
                executor));
  }

  @Test
  void shouldArchiveBothBatchesAndSum() {
    // given
    repository.usageBatch = new ArchiveBatch("2024-01-01", List.of("1", "2"));
    repository.usageTUBatch = new ArchiveBatch("2024-01-01", List.of("10"));

    // when
    final var result = job.archiveNextBatch();

    // then
    assertThat(result).succeedsWithin(Duration.ZERO).isEqualTo(3);
    assertThat(repository.moves)
        .containsExactly(
            new UsageMetricsTestRepository.DocumentMove(
                usageMetricTemplate.getFullQualifiedName(),
                usageMetricTemplate.getFullQualifiedName() + "2024-01-01",
                UsageMetricTemplate.ID,
                List.of("1", "2"),
                executor),
            new UsageMetricsTestRepository.DocumentMove(
                usageMetricTUTemplate.getFullQualifiedName(),
                usageMetricTUTemplate.getFullQualifiedName() + "2024-01-01",
                UsageMetricTUTemplate.ID,
                List.of("10"),
                executor));
  }

  @Test
  void shouldArchiveUsageMetricBeforeTU() {
    // given
    repository.usageBatch = new ArchiveBatch("2024-01-01", List.of("1"));
    repository.usageTUBatch = new ArchiveBatch("2024-01-01", List.of("10"));

    // when
    job.archiveNextBatch();

    // then
    assertThat(repository.moves)
        .extracting(m -> m.sourceIndexName)
        .containsExactly(
            usageMetricTemplate.getFullQualifiedName(),
            usageMetricTUTemplate.getFullQualifiedName());
  }

  private static final class UsageMetricsTestRepository
      extends ArchiverRepository.NoopArchiverRepository {

    final List<DocumentMove> moves = new ArrayList<>();
    ArchiveBatch usageBatch;
    ArchiveBatch usageTUBatch;

    @Override
    public CompletableFuture<ArchiveBatch> getUsageMetricNextBatch() {
      return CompletableFuture.completedFuture(usageBatch);
    }

    @Override
    public CompletableFuture<ArchiveBatch> getUsageMetricTUNextBatch() {
      return CompletableFuture.completedFuture(usageTUBatch);
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
>>>>>>> origin/release-8.8.0
