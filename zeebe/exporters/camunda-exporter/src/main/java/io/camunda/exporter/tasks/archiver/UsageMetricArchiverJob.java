/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.exporter.metrics.ArchiverJobMetrics;
import io.camunda.exporter.metrics.CamundaArchiverMetrics;
import io.camunda.exporter.tasks.archiver.ArchiveBatch.BasicArchiveBatch;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTemplate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

public class UsageMetricArchiverJob extends ArchiverJob<BasicArchiveBatch> {

  private final UsageMetricTemplate usageMetricTemplate;

  public UsageMetricArchiverJob(
      final ArchiverRepository repository,
      final UsageMetricTemplate usageMetricTemplate,
      final CamundaArchiverMetrics archiverMetrics,
      final Logger logger,
      final Executor executor) {
    super(repository, archiverMetrics, logger, executor);
    this.usageMetricTemplate = usageMetricTemplate;
  }

  @Override
  public String getJobName() {
    return UsageMetricTemplate.INDEX_NAME;
  }

  @Override
  public CompletableFuture<ArchiveBatch.BasicArchiveBatch> getNextBatch(
      final ArchiverJobMetrics archiverJobMetrics) {
    return getArchiverRepository().getUsageMetricNextBatch(archiverJobMetrics);
  }

  @Override
  public UsageMetricTemplate getTemplateDescriptor() {
    return usageMetricTemplate;
  }

  @Override
  protected Map<String, List<String>> createIdsByFieldMap(
      final IndexTemplateDescriptor templateDescriptor,
      final ArchiveBatch.BasicArchiveBatch batch) {
    return Map.of(UsageMetricTemplate.ID, batch.ids());
  }
}
