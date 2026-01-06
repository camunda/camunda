/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTUTemplate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

public class UsageMetricTUArchiverJob extends ArchiverJob {

  private final UsageMetricTUTemplate usageMetricTUTemplate;

  public UsageMetricTUArchiverJob(
      final ArchiverRepository repository,
      final UsageMetricTUTemplate usageMetricTUTemplate,
      final CamundaExporterMetrics metrics,
      final Logger logger,
      final Executor executor) {
    super(
        repository,
        metrics,
        logger,
        executor,
        metrics::recordUsageMetricsTUArchiving,
        metrics::recordUsageMetricsTUArchived);
    this.usageMetricTUTemplate = usageMetricTUTemplate;
  }

  @Override
  String getJobName() {
    return UsageMetricTUTemplate.INDEX_NAME;
  }

  @Override
  CompletableFuture<ArchiveBatch> getNextBatch() {
    return getArchiverRepository().getUsageMetricTUNextBatch();
  }

  @Override
  String getSourceIndexName() {
    return usageMetricTUTemplate.getFullQualifiedName();
  }
}
