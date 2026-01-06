/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTemplate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

public class UsageMetricArchiverJob extends ArchiverJob {

  private final UsageMetricTemplate usageMetricTemplate;

  public UsageMetricArchiverJob(
      final ArchiverRepository repository,
      final UsageMetricTemplate usageMetricTemplate,
      final CamundaExporterMetrics exporterMetrics,
      final Logger logger,
      final Executor executor) {
    super(
        repository,
        exporterMetrics,
        logger,
        executor,
        exporterMetrics::recordUsageMetricsArchiving,
        exporterMetrics::recordUsageMetricsArchived);
    this.usageMetricTemplate = usageMetricTemplate;
  }

  @Override
  public String getJobName() {
    return UsageMetricTemplate.INDEX_NAME;
  }

  @Override
  public CompletableFuture<ArchiveBatch> getNextBatch() {
    return getArchiverRepository().getUsageMetricNextBatch();
  }

  @Override
  public String getSourceIndexName() {
    return usageMetricTemplate.getFullQualifiedName();
  }
}
