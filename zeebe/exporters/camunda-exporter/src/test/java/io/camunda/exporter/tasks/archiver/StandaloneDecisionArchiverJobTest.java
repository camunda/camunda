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
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class StandaloneDecisionArchiverJobTest {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(StandaloneDecisionArchiverJobTest.class);

  private final Executor executor = Runnable::run;
  private final TestRepository repository = new TestRepository();
  private final DecisionInstanceTemplate decisionInstanceTemplate =
      new DecisionInstanceTemplate("", true);
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final CamundaExporterMetrics metrics = new CamundaExporterMetrics(meterRegistry);
  private final StandaloneDecisionArchiverJob job =
      new StandaloneDecisionArchiverJob(
          repository, decisionInstanceTemplate, metrics, LOGGER, executor);

  @Test
  void shouldReturnZeroIfNoBatchGiven() {
    // given - when
    final var result = job.archiveNextBatch();

    // then
    assertThat(result).succeedsWithin(Duration.ZERO).isEqualTo(0);
    assertThat(repository.moves).isEmpty();
  }

  @Test
  void shouldReturnZeroIfNoStandaloneDecisionIdsGiven() {
    // given
    repository.batch = new ArchiveBatch("2024-01-01", List.of());

    // when
    final var result = job.archiveNextBatch();

    // then
    assertThat(result).succeedsWithin(Duration.ZERO).isEqualTo(0);
    assertThat(repository.moves).isEmpty();
  }

  @Test
  void shouldMoveStandaloneDecisionInstances() {
    // given
    repository.batch = new ArchiveBatch("2024-01-01", List.of("1", "2", "3"));

    // when
    final var result = job.archiveNextBatch();

    // then
    assertThat(result).succeedsWithin(Duration.ZERO).isEqualTo(3);
    assertThat(repository.moves)
        .contains(
            new DocumentMove(
                decisionInstanceTemplate.getFullQualifiedName(),
                decisionInstanceTemplate.getFullQualifiedName() + "2024-01-01",
                DecisionInstanceTemplate.ID,
                List.of("1", "2", "3"),
                executor));
  }

  @Test
  void shouldRecordStandaloneDecisionInstancesIncrease() {
    // given
    repository.batch = new ArchiveBatch("2024-01-01", List.of("1", "2", "3"));

    // when
    final var count =
        job.archiveNextBatch().toCompletableFuture().join()
            + job.archiveNextBatch().toCompletableFuture().join();

    // then
    assertThat(
            meterRegistry
                .counter(
                    "zeebe.camunda.exporter.archiver.standalone.decisions", "state", "archiving")
                .count())
        .isEqualTo(6)
        .isEqualTo(count);
    assertThat(
            meterRegistry
                .counter(
                    "zeebe.camunda.exporter.archiver.standalone.decisions", "state", "archived")
                .count())
        .isEqualTo(6)
        .isEqualTo(count);
  }
}
