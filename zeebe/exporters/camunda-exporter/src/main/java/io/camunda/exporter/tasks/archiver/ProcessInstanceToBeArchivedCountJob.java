/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.BackgroundTask;
import io.camunda.exporter.tasks.util.ElasticsearchArchiverQueries;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;

public class ProcessInstanceToBeArchivedCountJob implements BackgroundTask {
  public static final int DELAY_BETWEEN_RUNS = 60000;
  public static final int MAX_DELAY_BETWEEN_RUNS = 300000;

  private final CamundaExporterMetrics metrics;
  private final ElasticsearchAsyncClient client;
  private final int partitionId;
  private final String archivingTimePoint;
  private final String listViewIndexName;
  private final Logger logger;

  public ProcessInstanceToBeArchivedCountJob(
      final CamundaExporterMetrics metrics,
      final ElasticsearchAsyncClient client,
      final int partitionId,
      final String archivingTimePoint,
      final String listViewIndexName,
      final Logger logger) {
    this.metrics = metrics;
    this.client = client;
    this.partitionId = partitionId;
    this.archivingTimePoint = archivingTimePoint;
    this.listViewIndexName = listViewIndexName;
    this.logger = logger;
  }

  @Override
  public CompletionStage<Integer> execute() {
    final var countRequest =
        ElasticsearchArchiverQueries.createFinishedInstancesCountRequest(
            listViewIndexName, archivingTimePoint, partitionId);
    final var countFuture = client.count(countRequest);

    return countFuture
        .whenCompleteAsync(
            (res, err) -> {
              if (err == null) {
                metrics.setProcessInstancesAwaitingArchival((int) res.count());
              } else {
                logger.error("Failed to count number of process instances awaiting archival", err);
              }
            })
        .thenApply(res -> (int) res.count()); // return the count as Integer
  }

  @Override
  public String getCaption() {
    return "Process instances to be archived metric job";
  }
}
