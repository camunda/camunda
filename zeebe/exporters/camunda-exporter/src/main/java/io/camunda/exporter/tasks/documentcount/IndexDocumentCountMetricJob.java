/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.documentcount;

import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.BackgroundTask;
import io.camunda.exporter.tasks.archiver.ArchiverRepository;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;

/**
 * A background job that fetches document counts from all known indices and reports them as metrics.
 * This job runs periodically to update the metric gauges.
 */
public class IndexDocumentCountMetricJob implements BackgroundTask {

  private final CamundaExporterMetrics metrics;
  private final ArchiverRepository repository;
  private final Logger logger;

  public IndexDocumentCountMetricJob(
      final CamundaExporterMetrics metrics,
      final ArchiverRepository repository,
      final Logger logger) {
    this.metrics = metrics;
    this.repository = repository;
    this.logger = logger;
  }

  @Override
  public CompletionStage<Integer> execute() {
    return repository
        .getDocumentCountsPerIndex()
        .handle(
            (result, err) -> {
              if (err != null) {
                logger.warn("Failed to get document counts per index", err);
                return Map.<String, Long>of();
              }
              return result;
            })
        .thenApply(
            result -> {
              result.forEach(
                  (indexName, count) -> metrics.recordIndexDocumentCount(indexName, count));
              return result.size();
            });
  }

  @Override
  public String getCaption() {
    return "Index document count metric job";
  }
}
