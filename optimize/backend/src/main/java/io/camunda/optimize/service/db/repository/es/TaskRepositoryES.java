/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import static io.camunda.optimize.service.exceptions.ExceptionHelper.safe;

import co.elastic.clients.elasticsearch.tasks.ListRequest;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.repository.TaskRepository;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import jakarta.json.JsonObject;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    final ListRequest request = ListRequest.of(b -> b.actions(action).detailed(true));
    return safe(
        () ->
            esClient.getTaskList(request).tasks().flat().stream()
                .map(taskInfo -> taskInfo.status().toJson().asJsonObject())
                .map(
                    status ->
                        new TaskProgressInfo(
                            getProgress(status),
                            status.getInt("total"),
                            getProcessedTasksCount(status)))
                .toList(),
        e -> "Failed to fetch task progress from Elasticsearch!",
        log);
  }

  private static long getProcessedTasksCount(final JsonObject status) {
    return status.getInt("deleted") + status.getInt("created") + status.getInt("updated");
  }

  private static int getProgress(final JsonObject status) {
    return status.getInt("total") > 0
        ? Double.valueOf((double) getProcessedTasksCount(status) / status.getInt("total") * 100.0D)
            .intValue()
        : 0;
  }
}
