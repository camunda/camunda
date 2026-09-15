/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.incident;

import io.camunda.exporter.ExporterMetadata;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.zeebe.exporter.common.tasks.BackgroundTask;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;

public class PendingIncidentUpdateCountTask implements BackgroundTask {
  public static final int DELAY_BETWEEN_RUNS = 60000;
  public static final int MAX_DELAY_BETWEEN_RUNS = 300000;

  private final CamundaExporterMetrics metrics;
  private final ExporterMetadata metadata;
  private final IncidentUpdateRepository repository;
  private final Logger logger;

  public PendingIncidentUpdateCountTask(
      final CamundaExporterMetrics metrics,
      final ExporterMetadata metadata,
      final IncidentUpdateRepository repository,
      final Logger logger) {
    this.metrics = metrics;
    this.metadata = metadata;
    this.repository = repository;
    this.logger = logger;
  }

  @Override
  public CompletionStage<Integer> execute() {
    return repository
        .getCountOfPendingIncidentUpdates(metadata.getLastIncidentUpdatePosition())
        .whenCompleteAsync(
            (res, err) -> {
              if (err == null) {
                metrics.setPendingIncidentUpdatesCount(res);
              } else {
                logger.warn("Failed to count number of pending incident updates", err);
              }
            });
  }
}
