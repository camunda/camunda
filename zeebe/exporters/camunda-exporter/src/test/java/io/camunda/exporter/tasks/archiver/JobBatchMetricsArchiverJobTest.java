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
import io.camunda.exporter.tasks.archiver.ArchiveBatch.BasicArchiveBatch;
import io.camunda.exporter.tasks.archiver.TestRepository.DocumentMove;
import io.camunda.webapps.schema.descriptors.template.JobMetricsBatchTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class JobBatchMetricsArchiverJobTest extends ArchiverJobRecordingMetricsAbstractTest {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(JobBatchMetricsArchiverJobTest.class);

  private final Executor executor = Runnable::run;

  private final TestRepository repository = new TestRepository();
  private final JobMetricsBatchTemplate jobMetricsBatchTemplate =
      new JobMetricsBatchTemplate("", true);
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final CamundaExporterMetrics metrics = new CamundaExporterMetrics(meterRegistry);

  private final JobBatchMetricsArchiverJob job =
      new JobBatchMetricsArchiverJob(
          repository, jobMetricsBatchTemplate, metrics, LOGGER, executor);

  @BeforeEach
  void setUp() {
    // given
    repository.batch = new BasicArchiveBatch("2024-01-01", List.of("1", "2", "3"));
  }

  @AfterEach
  void cleanUp() {
    meterRegistry.clear();
  }

  @Override
  ArchiverJob<?> getArchiverJob() {
    return job;
  }

  @Override
  SimpleMeterRegistry getMeterRegistry() {
    return meterRegistry;
  }

  @Override
  String getJobMetricName() {
    return "zeebe.camunda.exporter.archiver.job.batch.metrics";
  }

  @Test
  void shouldMoveJobBatchMetrics() {
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
                jobMetricsBatchTemplate.getFullQualifiedName(),
                jobMetricsBatchTemplate.getFullQualifiedName() + "2024-01-01",
                Map.of(JobMetricsBatchTemplate.ID, List.of("1", "2", "3")),
                executor));
  }
}
