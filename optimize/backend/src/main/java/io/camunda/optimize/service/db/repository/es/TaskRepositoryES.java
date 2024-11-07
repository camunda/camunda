/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import static io.camunda.optimize.service.exceptions.ExceptionHelper.safe;
import static io.camunda.optimize.service.util.mapper.ObjectMapperFactory.OPTIMIZE_MAPPER;

import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.UpdateByQueryRequest;
import co.elastic.clients.elasticsearch.tasks.ListRequest;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.repository.TaskRepository;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ElasticSearchConfiguration;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import io.camunda.optimize.upgrade.es.TaskResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class TaskRepositoryES extends TaskRepository {

  private final OptimizeElasticsearchClient esClient;
  private final ElasticSearchConfiguration configuration;

  public TaskRepositoryES(
      final OptimizeElasticsearchClient esClient, ConfigurationService configurationService) {
    this.esClient = esClient;
    this.configuration = configurationService.getElasticSearchConfiguration();
  }

  @Override
  public List<TaskRepository.TaskProgressInfo> tasksProgress(final String action) {
    final ListRequest request = ListRequest.of(b -> b.actions(action).detailed(true));
    return safe(
        () ->
            Optional.ofNullable(esClient.getTaskList(request).tasks())
                .map(
                    tasks ->
                        tasks.flat().stream()
                            .filter(taskInfo -> taskInfo.status() != null)
                            .map(taskInfo -> taskInfo.status().toJson().asJsonObject())
                            .map(
                                status ->
                                    new TaskProgressInfo(
                                        getProgress(status),
                                        status.getInt("total"),
                                        getProcessedTasksCount(status)))
                            .toList())
                .orElse(List.of()),
        e -> "Failed to fetch task progress from Elasticsearch!",
        log);
  }

  public boolean tryUpdateByQueryRequest(
      final String updateItemIdentifier,
      final Script updateScript,
      final Query filterQuery,
      final String... indices) {
    log.debug("Updating {}", updateItemIdentifier);
    final UpdateByQueryRequest updateByQueryRequest =
        UpdateByQueryRequest.of(
            b ->
                b.index(esClient.addPrefixesToIndices(indices))
                    .query(filterQuery)
                    .conflicts(Conflicts.Proceed)
                    .script(updateScript)
                    .waitForCompletion(false)
                    .refresh(true));

    final String taskId;
    try {
      taskId = esClient.submitUpdateTask(updateByQueryRequest).task();
    } catch (IOException e) {
      final String errorMessage =
          String.format(
              "Could not create updateBy task for [%s] with query [%s]!",
              updateItemIdentifier, filterQuery);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    waitUntilTaskIsFinished(taskId, updateItemIdentifier);

    try {
      final TaskResponse.Status taskStatus = getTaskResponse(taskId).getTaskStatus();
      log.debug("Updated [{}] {}.", taskStatus.getDeleted(), updateItemIdentifier);
      return taskStatus.getUpdated() > 0L;
    } catch (IOException e) {
      throw new OptimizeRuntimeException(
          String.format(
              "Error while trying to read Elasticsearch task status with ID: [%s]", taskId),
          e);
    }
  }

  public boolean tryDeleteByQueryRequest(
      final Query query,
      final String deletedItemIdentifier,
      final boolean refresh,
      final String... indices) {
    log.debug("Deleting {}", deletedItemIdentifier);

    final DeleteByQueryRequest request =
        DeleteByQueryRequest.of(
            b ->
                b.index(esClient.addPrefixesToIndices(indices))
                    .query(query)
                    .refresh(refresh)
                    .waitForCompletion(false)
                    .conflicts(Conflicts.Proceed));

    if (configuration.getConnection().isHealthCheckEnabled()) {
      return async(query, deletedItemIdentifier, request);
    } else {
      return sync(request);
    }
  }

  private boolean sync(final DeleteByQueryRequest request) {
    try {
      Long deleted = esClient.submitDeleteTask(request).deleted();
      return deleted != null && deleted > 0L;
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(
          "Error while trying to read Elasticsearch task status with ID");
    }
  }

  private boolean async(
      final Query query, final String deletedItemIdentifier, final DeleteByQueryRequest request) {
    final String taskId;
    try {
      taskId = esClient.submitDeleteTask(request).task();
    } catch (IOException e) {
      final String errorMessage =
          String.format(
              "Could not create delete task for [%s] with query [%s]!",
              deletedItemIdentifier, query);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    waitUntilTaskIsFinished(taskId, deletedItemIdentifier);

    try {
      final TaskResponse.Status taskStatus = getTaskResponse(taskId).getTaskStatus();
      log.debug(
          "Deleted [{}] out of [{}] {}.",
          taskStatus.getDeleted(),
          taskStatus.getTotal(),
          deletedItemIdentifier);
      return taskStatus.getDeleted() > 0L;
    } catch (IOException e) {
      throw new OptimizeRuntimeException(
          String.format(
              "Error while trying to read Elasticsearch task status with ID: [%s]", taskId),
          e);
    }
  }

  @Override
  public TaskResponse getTaskResponse(final String taskId) throws IOException {
    final Request request = new Request(HttpGet.METHOD_NAME, "/" + TASKS_ENDPOINT + "/" + taskId);
    final Response response = esClient.performRequest(request);
    return OPTIMIZE_MAPPER.readValue(response.getEntity().getContent(), TaskResponse.class);
  }
}
