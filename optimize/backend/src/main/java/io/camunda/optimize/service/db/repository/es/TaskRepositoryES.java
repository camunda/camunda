/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import static io.camunda.optimize.service.exceptions.ExceptionHelper.safe;

import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.UpdateByQueryRequest;
import co.elastic.clients.elasticsearch.tasks.ListRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.repository.TaskRepository;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import io.camunda.optimize.upgrade.es.TaskResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class TaskRepositoryES extends TaskRepository {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(TaskRepositoryES.class);
  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper optimizeObjectMapper;

  public TaskRepositoryES(
      final OptimizeElasticsearchClient esClient,
      final ConfigurationService configurationService,
      final ObjectMapper optimizeObjectMapper) {
    this.esClient = esClient;
    this.configurationService = configurationService;
    this.optimizeObjectMapper = optimizeObjectMapper;
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
        LOG);
  }

  @Override
  public TaskResponse getTaskResponse(final String taskId) throws IOException {
    final Request request = new Request(HttpGet.METHOD_NAME, "/" + TASKS_ENDPOINT + "/" + taskId);
    final Response response = esClient.performRequest(request);
    return optimizeObjectMapper.readValue(response.getEntity().getContent(), TaskResponse.class);
  }

  public boolean tryUpdateByQueryRequest(
      final String updateItemIdentifier,
      final Script updateScript,
      final Query filterQuery,
      final String... indices) {
    LOG.debug("Updating {}", updateItemIdentifier);
    final boolean clusterTaskCheckingEnabled =
        configurationService
            .getElasticSearchConfiguration()
            .getConnection()
            .isClusterTaskCheckingEnabled();

    final UpdateByQueryRequest updateByQueryRequest =
        UpdateByQueryRequest.of(
            b ->
                b.index(esClient.addPrefixesToIndices(indices))
                    .query(filterQuery)
                    .conflicts(Conflicts.Proceed)
                    .script(updateScript)
                    .waitForCompletion(!clusterTaskCheckingEnabled)
                    .refresh(true));

    if (clusterTaskCheckingEnabled) {
      return asyncUpdate(updateItemIdentifier, filterQuery, updateByQueryRequest);
    } else {
      return syncUpdate(updateByQueryRequest);
    }
  }

  public boolean tryDeleteByQueryRequest(
      final Query query,
      final String deletedItemIdentifier,
      final boolean refresh,
      final String... indices) {
    LOG.debug("Deleting {}", deletedItemIdentifier);
    final boolean clusterTaskCheckingEnabled =
        configurationService
            .getElasticSearchConfiguration()
            .getConnection()
            .isClusterTaskCheckingEnabled();

    final DeleteByQueryRequest request =
        DeleteByQueryRequest.of(
            b ->
                b.index(esClient.addPrefixesToIndices(indices))
                    .query(query)
                    .refresh(refresh)
                    .waitForCompletion(!clusterTaskCheckingEnabled)
                    .conflicts(Conflicts.Proceed));

    if (clusterTaskCheckingEnabled) {
      return asyncDelete(query, deletedItemIdentifier, request);
    } else {
      return syncDelete(request);
    }
  }

  private boolean syncUpdate(final UpdateByQueryRequest request) {
    try {
      final Long deleted = esClient.submitUpdateTask(request).updated();
      return deleted != null && deleted > 0L;
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Error while trying to submit update task", e);
    }
  }

  private boolean asyncUpdate(
      final String updateItemIdentifier,
      final Query filterQuery,
      final UpdateByQueryRequest updateByQueryRequest) {
    final String taskId;
    try {
      taskId = esClient.submitUpdateTask(updateByQueryRequest).task();
    } catch (final IOException e) {
      final String errorMessage =
          String.format(
              "Could not create updateBy task for [%s] with query [%s]!",
              updateItemIdentifier, filterQuery);
      LOG.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    waitUntilTaskIsFinished(taskId, updateItemIdentifier);

    try {
      final TaskResponse.Status taskStatus = getTaskResponse(taskId).getTaskStatus();
      LOG.debug("Updated [{}] {}.", taskStatus.getDeleted(), updateItemIdentifier);
      return taskStatus.getUpdated() > 0L;
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(
          String.format(
              "Error while trying to read Elasticsearch task status with ID: [%s]", taskId),
          e);
    }
  }

  private boolean syncDelete(final DeleteByQueryRequest request) {
    try {
      final Long deleted = esClient.submitDeleteTask(request).deleted();
      return deleted != null && deleted > 0L;
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Error while trying to submit update task", e);
    }
  }

  private boolean asyncDelete(
      final Query query, final String deletedItemIdentifier, final DeleteByQueryRequest request) {
    final String taskId;
    try {
      taskId = esClient.submitDeleteTask(request).task();
    } catch (final IOException e) {
      final String errorMessage =
          String.format(
              "Could not create delete task for [%s] with query [%s]!",
              deletedItemIdentifier, query);
      LOG.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    waitUntilTaskIsFinished(taskId, deletedItemIdentifier);

    try {
      final TaskResponse.Status taskStatus = getTaskResponse(taskId).getTaskStatus();
      LOG.debug(
          "Deleted [{}] out of [{}] {}.",
          taskStatus.getDeleted(),
          taskStatus.getTotal(),
          deletedItemIdentifier);
      return taskStatus.getDeleted() > 0L;
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(
          String.format(
              "Error while trying to read Elasticsearch task status with ID: [%s]", taskId),
          e);
    }
  }
}
