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
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

public class BatchOperationArchiverJob extends ArchiverJob<ArchiveBatch.BasicArchiveBatch> {

  private final BatchOperationTemplate batchOperationTemplate;

  public BatchOperationArchiverJob(
      final ArchiverRepository repository,
      final BatchOperationTemplate batchOperationTemplate,
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
    return Map.of(BatchOperationTemplate.ID, batch.ids());
  }
}
