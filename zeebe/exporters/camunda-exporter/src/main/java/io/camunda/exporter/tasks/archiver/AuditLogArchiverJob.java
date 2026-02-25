/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

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

  public AuditLogArchiverJob(
      final AuditLogArchiverRepository repository,
      final ArchiverRepository archiverRepository,
      final AuditLogTemplate auditLogTemplate,
      final CamundaExporterMetrics exporterMetrics,
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
    return super.archive(templateDescriptor, batch)
        .thenComposeAsync(r -> deleteAuditLogCleanupMetadata(batch));
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
    return repository.deleteAuditLogCleanupMetadata(batch);
  }
}
