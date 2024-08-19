/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.writer.DatabaseWriterUtil.createFieldUpdateScriptParams;
import static io.camunda.optimize.service.db.writer.DatabaseWriterUtil.createUpdateFieldsScript;
import static io.camunda.optimize.service.util.mapper.ObjectMapperFactory.OPTIMIZE_MAPPER;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.writer.DatabaseWriterUtil;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.upgrade.es.TaskResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.http.client.methods.HttpGet;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;

public class ElasticsearchWriterUtil {

  private static final String TASKS_ENDPOINT = "_tasks";
  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(ElasticsearchWriterUtil.class);

  private ElasticsearchWriterUtil() {}

  public static Script createFieldUpdateScript(
      final Set<String> fields, final Object entityDto, final ObjectMapper objectMapper) {
    final Map<String, Object> params =
        createFieldUpdateScriptParams(fields, entityDto, objectMapper);
    return createDefaultScriptWithPrimitiveParams(
        createUpdateFieldsScript(params.keySet()), params);
  }

  public static Script createDefaultScriptWithPrimitiveParams(
      final String inlineUpdateScript, final Map<String, Object> params) {
    return new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, inlineUpdateScript, params);
  }

  public static Script createDefaultScriptWithSpecificDtoParams(
      final String inlineUpdateScript,
      final Map<String, Object> params,
      final ObjectMapper objectMapper) {
    return new Script(
        ScriptType.INLINE,
        Script.DEFAULT_SCRIPT_LANG,
        inlineUpdateScript,
        DatabaseWriterUtil.mapParamsForScriptCreation(params, objectMapper));
  }

  public static Script createDefaultScript(final String inlineUpdateScript) {
    return new Script(
        ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, inlineUpdateScript, Collections.emptyMap());
  }

  public static boolean tryUpdateByQueryRequest(
      final OptimizeElasticsearchClient esClient,
      final String updateItemIdentifier,
      final Script updateScript,
      final AbstractQueryBuilder filterQuery,
      final String... indices) {
    log.debug("Updating {}", updateItemIdentifier);
    final UpdateByQueryRequest request =
        new UpdateByQueryRequest(indices)
            .setQuery(filterQuery)
            .setAbortOnVersionConflict(false)
            .setMaxRetries(NUMBER_OF_RETRIES_ON_CONFLICT)
            .setScript(updateScript)
            .setRefresh(true);
    esClient.applyIndexPrefixes(request);

    final String taskId;
    try {
      taskId = esClient.submitUpdateTask(request).getTask();
    } catch (final IOException e) {
      final String errorMessage =
          String.format(
              "Could not create updateBy task for [%s] with query [%s]!",
              updateItemIdentifier, filterQuery);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    waitUntilTaskIsFinished(esClient, taskId, updateItemIdentifier);

    try {
      final TaskResponse.Status taskStatus = getTaskResponse(esClient, taskId).getTaskStatus();
      log.debug("Updated [{}] {}.", taskStatus.getDeleted(), updateItemIdentifier);
      return taskStatus.getUpdated() > 0L;
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(
          String.format(
              "Error while trying to read Elasticsearch task status with ID: [%s]", taskId),
          e);
    }
  }

  public static boolean tryDeleteByQueryRequest(
      final OptimizeElasticsearchClient esClient,
      final AbstractQueryBuilder<?> queryBuilder,
      final String deletedItemIdentifier,
      final boolean refresh,
      final String... indices) {
    log.debug("Deleting {}", deletedItemIdentifier);

    final DeleteByQueryRequest request =
        new DeleteByQueryRequest(indices)
            .setAbortOnVersionConflict(false)
            .setQuery(queryBuilder)
            .setRefresh(refresh);
    esClient.applyIndexPrefixes(request);

    final String taskId;
    try {
      taskId = esClient.submitDeleteTask(request).getTask();
    } catch (final IOException e) {
      final String errorMessage =
          String.format(
              "Could not create delete task for [%s] with query [%s]!",
              deletedItemIdentifier, queryBuilder);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    waitUntilTaskIsFinished(esClient, taskId, deletedItemIdentifier);

    try {
      final TaskResponse.Status taskStatus = getTaskResponse(esClient, taskId).getTaskStatus();
      log.debug(
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

  public static void waitUntilTaskIsFinished(
      final OptimizeElasticsearchClient esClient,
      final String taskId,
      final String taskItemIdentifier) {
    final BackoffCalculator backoffCalculator = new BackoffCalculator(1000, 10);
    boolean finished = false;
    int progress = -1;
    while (!finished) {
      try {
        final TaskResponse taskResponse = getTaskResponse(esClient, taskId);
        validateTaskResponse(taskResponse);

        final int currentProgress = (int) (taskResponse.getProgress() * 100.0);
        if (currentProgress != progress) {
          final TaskResponse.Status taskStatus = taskResponse.getTaskStatus();
          progress = currentProgress;
          log.info(
              "Progress of task (ID:{}) on {}: {}% (total: {}, updated: {}, created: {}, deleted: {}). Completed: {}",
              taskId,
              taskItemIdentifier,
              progress,
              taskStatus.getTotal(),
              taskStatus.getUpdated(),
              taskStatus.getCreated(),
              taskStatus.getDeleted(),
              taskResponse.isCompleted());
        }
        finished = taskResponse.isCompleted();
        if (!finished) {
          Thread.sleep(backoffCalculator.calculateSleepTime());
        }
      } catch (final InterruptedException e) {
        log.error("Waiting for Elasticsearch task (ID: {}) completion was interrupted!", taskId, e);
        Thread.currentThread().interrupt();
      } catch (final Exception e) {
        throw new OptimizeRuntimeException(
            String.format(
                "Error while trying to read Elasticsearch task (ID: %s) progress!", taskId),
            e);
      }
    }
  }

  public static TaskResponse getTaskResponse(
      final OptimizeElasticsearchClient esClient, final String taskId) throws IOException {
    final Request request = new Request(HttpGet.METHOD_NAME, "/" + TASKS_ENDPOINT + "/" + taskId);
    final Response response = esClient.performRequest(request);
    return OPTIMIZE_MAPPER.readValue(response.getEntity().getContent(), TaskResponse.class);
  }

  public static void validateTaskResponse(final TaskResponse taskResponse) {
    if (taskResponse.getError() != null) {
      log.error("An Elasticsearch task failed with error: {}", taskResponse.getError());
      throw new OptimizeRuntimeException(taskResponse.getError().toString());
    }

    if (taskResponse.getResponseDetails() != null) {
      final List<Object> failures = taskResponse.getResponseDetails().getFailures();
      if (failures != null && !failures.isEmpty()) {
        log.error("An Elasticsearch task contained failures: {}", failures);
        throw new OptimizeRuntimeException(failures.toString());
      }
    }
  }
}
