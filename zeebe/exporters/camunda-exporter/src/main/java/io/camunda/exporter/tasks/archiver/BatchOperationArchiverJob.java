/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

public class BatchOperationArchiverJob extends ArchiverJob {

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
  CompletableFuture<ArchiveBatch> getNextBatch() {
    return getArchiverRepository().getBatchOperationsNextBatch();
  }

  @Override
  String getSourceIndexName() {
    return batchOperationTemplate.getFullQualifiedName();
  }
}
