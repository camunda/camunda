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
import io.camunda.webapps.schema.descriptors.template.JobMetricsBatchTemplate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

public class JobBatchMetricsArchiverJob extends ArchiverJob<BasicArchiveBatch> {

  private final JobMetricsBatchTemplate jobMetricsBatchTemplate;

  public JobBatchMetricsArchiverJob(
      final ArchiverRepository repository,
      final JobMetricsBatchTemplate jobMetricsBatchTemplate,
      final CamundaExporterMetrics exporterMetrics,
      final Logger logger,
      final Executor executor) {
    super(
        repository,
        exporterMetrics,
        logger,
        executor,
        exporterMetrics::recordJobBatchMetricsArchiving,
        exporterMetrics::recordJobBatchMetricsArchived);
    this.jobMetricsBatchTemplate = jobMetricsBatchTemplate;
  }

  @Override
  public String getJobName() {
    return JobMetricsBatchTemplate.INDEX_NAME;
  }

  @Override
  public CompletableFuture<BasicArchiveBatch> getNextBatch() {
    return getArchiverRepository().getJobBatchMetricsNextBatch();
  }

  @Override
  public JobMetricsBatchTemplate getTemplateDescriptor() {
    return jobMetricsBatchTemplate;
  }

  @Override
  protected Map<String, List<String>> createIdsByFieldMap(
      final IndexTemplateDescriptor templateDescriptor, final BasicArchiveBatch batch) {
    return Map.of(JobMetricsBatchTemplate.ID, batch.ids());
  }
}
