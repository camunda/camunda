/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.BackgroundTask;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;

public class ProcessInstanceToBeArchivedCountJob implements BackgroundTask {
  public static final int DELAY_BETWEEN_RUNS = 60000;
  public static final int MAX_DELAY_BETWEEN_RUNS = 300000;

  private final CamundaExporterMetrics metrics;
  private final ArchiverRepository repository;
  private final Logger logger;

  public ProcessInstanceToBeArchivedCountJob(
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
        .getCountOfProcessInstancesAwaitingArchival()
        .whenCompleteAsync(
            (res, err) -> {
              if (err == null) {
                metrics.setProcessInstancesAwaitingArchival(res);
              } else {
                logger.error("Failed to count number of process instances awaiting archival", err);
              }
            });
  }

  @Override
  public String getCaption() {
    return "Process instances to be archived metric job";
  }
}
