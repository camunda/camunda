/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.elasticsearch.action.admin.cluster.node.tasks.list.ListTasksRequest;
import org.elasticsearch.action.admin.cluster.node.tasks.list.ListTasksResponse;
import org.elasticsearch.index.reindex.BulkByScrollTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class EsBulkByScrollTaskActionProgressReporter {
  private final Logger logger;
  private final ScheduledExecutorService executorService;
  private final String action;
  private final OptimizeElasticsearchClient esClient;

  public EsBulkByScrollTaskActionProgressReporter(String loggerName, OptimizeElasticsearchClient esClient,
                                                  String action) {
    this.logger = LoggerFactory.getLogger(loggerName);
    this.esClient = esClient;
    this.action = action;
    this.executorService = Executors.newSingleThreadScheduledExecutor(
      new ThreadFactoryBuilder().setNameFormat(loggerName + "-progress-%d").build()
    );
  }

  public void start() {
    logger.debug("Start reporting progress...");
    executorService.scheduleAtFixedRate(
      () -> {
        ListTasksRequest request = new ListTasksRequest()
          .setActions(action)
          .setDetailed(true);
        try {
          ListTasksResponse response = esClient.getTaskList(request);
          final List<BulkByScrollTask.Status> currentTasksStatus = response
            .getTasks()
            .stream()
            .filter(taskInfo -> taskInfo.getStatus() instanceof BulkByScrollTask.Status)
            .map(taskInfo -> (BulkByScrollTask.Status) taskInfo.getStatus())
            .collect(Collectors.toList());

          currentTasksStatus
            .forEach(status -> {
              final long sumOfProcessedDocs = status.getDeleted() + status.getCreated() + status.getUpdated();
              int progress = status.getTotal() > 0
                ? Double.valueOf((double) sumOfProcessedDocs / status.getTotal() * 100.0D).intValue()
                : 0;
              logger.info(
                "Current {} BulkByScrollTaskTask progress: {}%, total: {}, done: {}",
                action,
                progress,
                status.getTotal(),
                sumOfProcessedDocs
              );
            });

        } catch (IOException e) {
          logger.error("Could not retrieve progress from Elasticsearch.", e);
        }
      },
      0,
      30,
      TimeUnit.SECONDS
    );
  }

  public void stop() {
    try {
      logger.debug("Stop reporting progress!");
      executorService.shutdownNow();
    } catch (Exception e) {
      logger.error("Failed stopping progress reporting thread");
    }
  }

}
