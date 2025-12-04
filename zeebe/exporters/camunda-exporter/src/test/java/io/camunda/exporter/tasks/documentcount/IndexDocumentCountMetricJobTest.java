/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.documentcount;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.archiver.ArchiverRepository.NoopArchiverRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class IndexDocumentCountMetricJobTest {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(IndexDocumentCountMetricJobTest.class);

  private SimpleMeterRegistry registry;
  private CamundaExporterMetrics metrics;
  private TestArchiverRepository repository;
  private IndexDocumentCountMetricJob job;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    metrics = new CamundaExporterMetrics(registry);
    repository = new TestArchiverRepository();
    job = new IndexDocumentCountMetricJob(metrics, repository, LOGGER);
  }

  @Test
  void shouldRecordDocumentCountsForAllIndices() {
    // given
    repository.documentCounts = Map.of("index-1", 100L, "index-2", 200L, "index-3", 50L);

    // when
    final var result = job.execute().toCompletableFuture().join();

    // then
    assertThat(result).isEqualTo(3);

    final var index1Gauge =
        registry.get("zeebe.camunda.exporter.index.doc.count").tag("index", "index-1").gauge();
    assertThat(index1Gauge.value()).isEqualTo(100);

    final var index2Gauge =
        registry.get("zeebe.camunda.exporter.index.doc.count").tag("index", "index-2").gauge();
    assertThat(index2Gauge.value()).isEqualTo(200);

    final var index3Gauge =
        registry.get("zeebe.camunda.exporter.index.doc.count").tag("index", "index-3").gauge();
    assertThat(index3Gauge.value()).isEqualTo(50);
  }

  @Test
  void shouldReturnZeroWhenNoIndices() {
    // given
    repository.documentCounts = Map.of();

    // when
    final var result = job.execute().toCompletableFuture().join();

    // then
    assertThat(result).isEqualTo(0);
    assertThat(registry.find("zeebe.camunda.exporter.index.doc.count").gauges()).isEmpty();
  }

  @Test
  void shouldHandleRepositoryFailureGracefully() {
    // given
    repository.failOnGetDocumentCounts = true;

    // when - the job handles the error gracefully and returns 0
    final var result = job.execute().toCompletableFuture().join();

    // then - should complete normally with 0 (no indices updated)
    assertThat(result).isEqualTo(0);
  }

  @Test
  void shouldUpdateExistingGaugeOnSubsequentCalls() {
    // given
    repository.documentCounts = Map.of("index-1", 100L);
    job.execute().toCompletableFuture().join();

    // when - update with new count
    repository.documentCounts = Map.of("index-1", 200L);
    final var result = job.execute().toCompletableFuture().join();

    // then - gauge should be updated
    assertThat(result).isEqualTo(1);
    final var gauge =
        registry.get("zeebe.camunda.exporter.index.doc.count").tag("index", "index-1").gauge();
    assertThat(gauge.value()).isEqualTo(200);
  }

  @Test
  void shouldReturnCorrectCaption() {
    // then
    assertThat(job.getCaption()).isEqualTo("Index document count metric job");
  }

  private static class TestArchiverRepository extends NoopArchiverRepository {
    Map<String, Long> documentCounts = Map.of();
    boolean failOnGetDocumentCounts = false;

    @Override
    public CompletableFuture<Map<String, Long>> getDocumentCountsPerIndex() {
      if (failOnGetDocumentCounts) {
        return CompletableFuture.failedFuture(
            new RuntimeException("Simulated failure getting document counts"));
      }
      return CompletableFuture.completedFuture(documentCounts);
    }
  }
}
