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
import io.camunda.exporter.tasks.RunnableTask;
import io.camunda.exporter.tasks.util.ElasticsearchArchiverQueries;

public class ProcessInstanceToBeArchivedCountJob implements RunnableTask {
  private final CamundaExporterMetrics metrics;
  private final ElasticsearchAsyncClient client;
  private final int partitionId;
  private final String archivingTimePoint;
  private final String listViewIndexName;

  public ProcessInstanceToBeArchivedCountJob(
      final CamundaExporterMetrics metrics,
      final ElasticsearchAsyncClient client,
      final int partitionId,
      final String archivingTimePoint,
      final String listViewIndexName) {
    this.metrics = metrics;
    this.client = client;
    this.partitionId = partitionId;
    this.archivingTimePoint = archivingTimePoint;
    this.listViewIndexName = listViewIndexName;
  }

  @Override
  public void run() {
    final var countRequest =
        ElasticsearchArchiverQueries.createFinishedInstancesCountRequest(
            listViewIndexName, archivingTimePoint, partitionId);
    final var countFuture = client.count(countRequest);

    countFuture.whenCompleteAsync(
        (res, err) -> metrics.addToProcessInstancesAwaitingArchival((int) res.count()));
  }
}
