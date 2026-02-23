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
import io.camunda.exporter.tasks.archiver.ArchiveBatch.AuditLogCleanupBatch;
import io.camunda.webapps.schema.descriptors.template.AuditLogTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuditLogArchiverJobTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuditLogArchiverJobTest.class);

  private final Executor executor = Runnable::run;

  private final NoopAuditLogArchiverRepository auditLogArchiverRepository =
      new NoopAuditLogArchiverRepository();
  private final TestRepository archiverRepository = new TestRepository();
  private final AuditLogTemplate auditLogTemplate = new AuditLogTemplate("", true);
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final CamundaExporterMetrics metrics = new CamundaExporterMetrics(meterRegistry);
  private final AuditLogArchiverJob job =
      new AuditLogArchiverJob(
          auditLogArchiverRepository,
          archiverRepository,
          auditLogTemplate,
          metrics,
          LOGGER,
          executor);

  @AfterEach
  void cleanUp() {
    meterRegistry.clear();
  }

  @Test
  void shouldMoveAuditLogs() {
    // given
    auditLogArchiverRepository.batch =
        new AuditLogCleanupBatch("2026-01-01", List.of("cleanup-1"), List.of("audit-1", "audit-2"));

    // when
    final int count = job.execute().toCompletableFuture().join();

    // then - should count both cleanup and audit log IDs
    assertThat(count).isEqualTo(3);

    // then - should move documents
    assertThat(archiverRepository.moves).hasSize(1);
    final var move = archiverRepository.moves.getFirst();
    assertThat(move.sourceIndexName()).isEqualTo(auditLogTemplate.getFullQualifiedName());
    assertThat(move.destinationIndexName())
        .isEqualTo(auditLogTemplate.getFullQualifiedName() + "2026-01-01");
  }

  @Test
  void shouldMoveAuditLogsByCorrectField() {
    // given
    auditLogArchiverRepository.batch =
        new AuditLogCleanupBatch(
            "2026-01-01",
            List.of("cleanup-1", "cleanup-2"),
            List.of("audit-1", "audit-2", "audit-3"));

    // when
    final int count = job.execute().toCompletableFuture().join();

    // then
    assertThat(count).isEqualTo(5);

    // then - should use the ID field from AuditLogTemplate
    final var move = archiverRepository.moves.getFirst();
    assertThat(move.keysByField())
        .containsEntry(AuditLogTemplate.ID, List.of("audit-1", "audit-2", "audit-3"));
  }

  @Test
  void shouldDeleteAuditLogCleanupMetadataAfterArchiving() {
    // given
    final var batch =
        new AuditLogCleanupBatch(
            "2026-01-01", List.of("cleanup-1", "cleanup-2"), List.of("audit-1", "audit-2"));
    auditLogArchiverRepository.batch = batch;

    // when
    final int count = job.execute().toCompletableFuture().join();

    // then - should have moved documents
    assertThat(count).isEqualTo(4);
    assertThat(archiverRepository.moves).hasSize(1);

    // then - should have deleted cleanup metadata
    assertThat(auditLogArchiverRepository.deletedBatch).isEqualTo(batch);
  }

  @Test
  void shouldReturnZeroWhenNoBatch() {
    // given
    auditLogArchiverRepository.batch = null;

    // when
    final int count = job.execute().toCompletableFuture().join();

    // then
    assertThat(count).isZero();
    assertThat(archiverRepository.moves).isEmpty();
    assertThat(auditLogArchiverRepository.deletedBatch).isNull();
  }

  @Test
  void shouldReturnZeroWhenEmptyBatch() {
    // given
    auditLogArchiverRepository.batch = new AuditLogCleanupBatch("2026-01-01", List.of(), List.of());

    // when
    final int count = job.execute().toCompletableFuture().join();

    // then
    assertThat(count).isZero();
    assertThat(archiverRepository.moves).isEmpty();
    assertThat(auditLogArchiverRepository.deletedBatch).isNull();
  }

  @Test
  void shouldHandleBatchWithOnlyCleanupIds() {
    // given
    auditLogArchiverRepository.batch =
        new AuditLogCleanupBatch("2026-01-01", List.of("cleanup-1", "cleanup-2"), List.of());

    // when
    final int count = job.execute().toCompletableFuture().join();

    // then
    assertThat(count).isEqualTo(2);
    assertThat(archiverRepository.moves).hasSize(1);
    assertThat(auditLogArchiverRepository.deletedBatch).isEqualTo(auditLogArchiverRepository.batch);
  }

  static class NoopAuditLogArchiverRepository implements AuditLogArchiverRepository {

    AuditLogCleanupBatch batch;
    AuditLogCleanupBatch deletedBatch;

    @Override
    public CompletableFuture<AuditLogCleanupBatch> getNextBatch() {
      return CompletableFuture.completedFuture(batch);
    }

    @Override
    public CompletableFuture<Integer> deleteAuditLogCleanupMetadata(
        final AuditLogCleanupBatch batch) {
      deletedBatch = batch;
      return CompletableFuture.completedFuture(batch.size());
    }

    @Override
    public void close() throws Exception {}
  }
}
