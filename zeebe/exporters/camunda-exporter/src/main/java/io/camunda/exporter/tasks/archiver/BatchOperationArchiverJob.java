/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.archiver.ArchiveBatch.BasicArchiveBatch;
import io.camunda.webapps.schema.descriptors.BatchOperationDependant;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

public class BatchOperationArchiverJob extends ArchiverJob<ArchiveBatch.BasicArchiveBatch> {

  private final BatchOperationTemplate batchOperationTemplate;
  private final List<BatchOperationDependant> batchOperationDependants;
  private final Executor executor;

  public BatchOperationArchiverJob(
      final ArchiverRepository repository,
      final BatchOperationTemplate batchOperationTemplate,
      final CamundaExporterMetrics metrics,
      final Logger logger,
      final Executor executor,
      final List<BatchOperationDependant> batchOperationDependants) {
    super(
        repository,
        metrics,
        logger,
        executor,
        metrics::recordBatchOperationsArchiving,
        metrics::recordBatchOperationsArchived);
    this.batchOperationTemplate = batchOperationTemplate;
    this.batchOperationDependants =
        batchOperationDependants.stream()
            .sorted(Comparator.comparing(BatchOperationDependant::getFullQualifiedName))
            .toList(); // sort to ensure the execution order is stable;
    this.executor = executor;
  }

  @Override
  String getJobName() {
    return BatchOperationTemplate.INDEX_NAME;
  }

  @Override
  CompletableFuture<ArchiveBatch.BasicArchiveBatch> getNextBatch() {
    return getArchiverRepository().getBatchOperationsNextBatch();
  }

  @Override
  BatchOperationTemplate getTemplateDescriptor() {
    return batchOperationTemplate;
  }

  @Override
  protected Map<String, List<String>> createIdsByFieldMap(
      final IndexTemplateDescriptor templateDescriptor, final BasicArchiveBatch batch) {
    if (templateDescriptor instanceof BatchOperationTemplate) {
      return Map.of(BatchOperationTemplate.ID, batch.ids());
    } else if (templateDescriptor instanceof final BatchOperationDependant bod) {
      return Map.of(bod.getBatchOperationDependantField(), batch.ids());
    }
    throw new IllegalArgumentException(
        "Unsupported template descriptor: " + templateDescriptor.getClass().getName());
  }

  private CompletableFuture<Void> archiveBatchOperationDependants(final BasicArchiveBatch batch) {
    final var moveDependentDocuments =
        batchOperationDependants.stream()
            .map(dependant -> super.archive(dependant, batch))
            .toArray(CompletableFuture[]::new);
    return CompletableFuture.allOf(moveDependentDocuments);
  }
}
