/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.archiver.ArchiveBatch.AuditLogCleanupBatch;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.template.AuditLogTemplate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

public class AuditLogArchiverJob extends ArchiverJob<AuditLogCleanupBatch> {

  private final AuditLogArchiverRepository repository;
  private final AuditLogTemplate auditLogTemplate;
  private final HistoryConfiguration historyConfig;

  public AuditLogArchiverJob(
      final AuditLogArchiverRepository repository,
      final ArchiverRepository archiverRepository,
      final AuditLogTemplate auditLogTemplate,
      final CamundaExporterMetrics exporterMetrics,
      final HistoryConfiguration historyConfig,
      final Logger logger,
      final Executor executor) {
    super(
        archiverRepository,
        exporterMetrics,
        logger,
        executor,
        exporterMetrics::recordAuditLogsArchiving,
        exporterMetrics::recordAuditLogsArchived);
    this.repository = repository;
    this.auditLogTemplate = auditLogTemplate;
    this.historyConfig = historyConfig;
  }

  @Override
  String getJobName() {
    return "audit-log-cleanup";
  }

  @Override
  CompletableFuture<AuditLogCleanupBatch> getNextBatch() {
    return repository.getNextBatch();
  }

  @Override
  AuditLogTemplate getTemplateDescriptor() {
    return auditLogTemplate;
  }

  @Override
  protected CompletableFuture<Integer> archive(
      final IndexTemplateDescriptor templateDescriptor, final AuditLogCleanupBatch batch) {
    if (batch.auditLogIds().isEmpty()) {
      return deleteAuditLogCleanupMetadata(batch);
    }
    return super.archive(templateDescriptor, batch)
        .thenComposeAsync(
            ignored -> {
              final int archivedCount = batch.auditLogIds().size();

              // Only delete cleanup metadata if the batch auditLogIds is lower than the batch size,
              // which means there is no more to archive
              if (archivedCount < historyConfig.getRolloverBatchSize()) {
                return deleteAuditLogCleanupMetadata(batch)
                    .thenApply(deletedCleanup -> deletedCleanup + archivedCount);
              }
              return CompletableFuture.completedFuture(archivedCount);
            },
            getExecutor());
  }

  @Override
  protected Map<String, List<String>> createIdsByFieldMap(
      final IndexTemplateDescriptor templateDescriptor, final AuditLogCleanupBatch batch) {
    final List<String> auditLogIds = batch.auditLogIds();
    if (auditLogIds.isEmpty()) {
      return Map.of();
    }
    return Map.of(AuditLogTemplate.ID, auditLogIds);
  }

  private CompletableFuture<Integer> deleteAuditLogCleanupMetadata(
      final AuditLogCleanupBatch batch) {
    if (batch.auditLogCleanupIds().isEmpty()) {
      return CompletableFuture.completedFuture(0);
    }
    return repository.deleteAuditLogCleanupMetadata(batch);
  }
}
