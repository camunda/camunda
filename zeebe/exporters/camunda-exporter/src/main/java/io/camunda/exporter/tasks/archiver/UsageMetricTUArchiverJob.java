/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTUTemplate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

public class UsageMetricTUArchiverJob extends ArchiverJob<ArchiveBatch.BasicArchiveBatch> {

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
  CompletableFuture<ArchiveBatch.BasicArchiveBatch> getNextBatch() {
    return getArchiverRepository().getUsageMetricTUNextBatch();
  }

  @Override
  UsageMetricTUTemplate getTemplateDescriptor() {
    return usageMetricTUTemplate;
  }

  @Override
  protected Map<String, List<String>> createIdsByFieldMap(
      final IndexTemplateDescriptor templateDescriptor,
      final ArchiveBatch.BasicArchiveBatch batch) {
    return Map.of(UsageMetricTUTemplate.ID, batch.ids());
  }
}
