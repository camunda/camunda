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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

public class BatchOperationArchiverJob extends ArchiverJob<ArchiveBatch.BasicArchiveBatch> {

  private final BatchOperationTemplate batchOperationTemplate;
  private final List<BatchOperationDependant> batchOperationDependants;

  public BatchOperationArchiverJob(
      final ArchiverRepository repository,
      final BatchOperationTemplate batchOperationTemplate,
      final List<BatchOperationDependant> batchOperationDependants,
      final CamundaExporterMetrics metrics,
      final Logger logger,
      final Executor executor) {
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
            .toList(); // sort to ensure the execution order is stable
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
  protected CompletableFuture<Integer> archive(
      final IndexTemplateDescriptor templateDescriptor, final BasicArchiveBatch batch) {
    return archiveBatchDependants(batch)
        .thenComposeAsync(v -> super.archive(templateDescriptor, batch), getExecutor());
  }

  @Override
  protected Map<String, List<String>> createIdsByFieldMap(
      final IndexTemplateDescriptor templateDescriptor, final BasicArchiveBatch batch) {
    final String field =
        switch (templateDescriptor) {
          case final BatchOperationTemplate ignored -> BatchOperationTemplate.ID;
          case final BatchOperationDependant dependant ->
              dependant.getBatchOperationDependantField();
          default ->
              throw new IllegalArgumentException(
                  "Unsupported template descriptor: " + templateDescriptor.getClass().getName());
        };
    return Map.of(field, batch.ids());
  }

  private CompletableFuture<Void> archiveBatchDependants(final BasicArchiveBatch batch) {
    final var futures = new ArrayList<CompletableFuture<?>>();

    for (final var dependant : batchOperationDependants) {
      futures.add(super.archive(dependant, batch, dependant.getBatchOperationDependantFilters()));
    }

    return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
  }
}
