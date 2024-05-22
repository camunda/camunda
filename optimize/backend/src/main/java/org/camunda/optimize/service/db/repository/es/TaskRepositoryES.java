/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.repository.es;

import static org.camunda.optimize.service.exceptions.ExceptionHelper.safe;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.repository.TaskRepository;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.action.admin.cluster.node.tasks.list.ListTasksRequest;
import org.elasticsearch.index.reindex.BulkByScrollTask;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class TaskRepositoryES implements TaskRepository {
  private final OptimizeElasticsearchClient esClient;

  @Override
  public List<TaskRepository.TaskProgressInfo> tasksProgress(final String action) {
    ListTasksRequest request = new ListTasksRequest().setActions(action).setDetailed(true);
    return safe(
        () ->
            esClient.getTaskList(request).getTasks().stream()
                .filter(taskInfo -> taskInfo.getStatus() instanceof BulkByScrollTask.Status)
                .map(taskInfo -> (BulkByScrollTask.Status) taskInfo.getStatus())
                .map(
                    status ->
                        new TaskProgressInfo(
                            getProgress(status), status.getTotal(), getProcessedTasksCount(status)))
                .toList(),
        e -> "Failed to fetch task progress from Elasticsearch!",
        log);
  }

  private static long getProcessedTasksCount(BulkByScrollTask.Status status) {
    return status.getDeleted() + status.getCreated() + status.getUpdated();
  }

  private static int getProgress(BulkByScrollTask.Status status) {
    return status.getTotal() > 0
        ? Double.valueOf((double) getProcessedTasksCount(status) / status.getTotal() * 100.0D)
            .intValue()
        : 0;
  }
}
