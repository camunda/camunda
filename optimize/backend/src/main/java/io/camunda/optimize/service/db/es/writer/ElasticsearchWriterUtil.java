/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.writer;

import static io.camunda.optimize.service.db.writer.DatabaseWriterUtil.createFieldUpdateScriptParams;
import static io.camunda.optimize.service.db.writer.DatabaseWriterUtil.createUpdateFieldsScript;
import static io.camunda.optimize.service.util.mapper.ObjectMapperFactory.OPTIMIZE_MAPPER;

import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.ScriptLanguage;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.UpdateByQueryRequest;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.upgrade.es.TaskResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.HttpGet;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ElasticsearchWriterUtil {

  private static final String TASKS_ENDPOINT = "_tasks";

  public static Script createFieldUpdateScript(
      final Set<String> fields, final Object entityDto, final ObjectMapper objectMapper) {
    final Map<String, String> params =
        createFieldUpdateScriptParams(fields, entityDto, objectMapper);
    return createDefaultScriptWithPrimitiveParams(
        createUpdateFieldsScript(params.keySet()), params);
  }

  public static <T> Script createDefaultScriptWithPrimitiveParams(
      final String inlineUpdateScript, final Map<String, T> params) {
    return Script.of(
        b ->
            b.inline(
                i ->
                    i.lang(ScriptLanguage.Painless)
                        .source(inlineUpdateScript)
                        .params(
                            params.entrySet().stream()
                                .collect(
                                    Collectors.toMap(
                                        Map.Entry::getKey, e -> JsonData.of(e.getValue()))))));
  }

  public static Script createDefaultScriptWithJsonParams(
      final String inlineUpdateScript, final Map<String, JsonData> params) {
    return Script.of(
        b ->
            b.inline(
                i -> i.lang(ScriptLanguage.Painless).source(inlineUpdateScript).params(params)));
  }

  public static Script createDefaultScriptWithSpecificDtoParams(
      final String inlineUpdateScript, final Map<String, Object> params) {
    return Script.of(
        b ->
            b.inline(
                i -> {
                  i.lang(ScriptLanguage.Painless).source(inlineUpdateScript);
                  if (params != null) {
                    i.params(
                        params.entrySet().stream()
                            .collect(
                                Collectors.toMap(
                                    Map.Entry::getKey, e -> JsonData.of(e.getValue()))));
                  }
                  return i;
                }));
  }

  public static Script createDefaultScriptWithSpecificDtoStringParams(
      final String inlineUpdateScript, final Map<String, String> params) {
    return Script.of(
        b ->
            b.inline(
                i ->
                    i.lang(ScriptLanguage.Painless)
                        .source(inlineUpdateScript)
                        .params(
                            params.entrySet().stream()
                                .collect(
                                    Collectors.toMap(
                                        Map.Entry::getKey, e -> JsonData.of(e.getValue()))))));
  }

  public static Script createDefaultScript(final String inlineUpdateScript) {
    return Script.of(
        b -> b.inline(i -> i.lang(ScriptLanguage.Painless).source(inlineUpdateScript)));
  }

  public static boolean tryUpdateByQueryRequest(
      OptimizeElasticsearchClient esClient,
      String updateItemIdentifier,
      Script updateScript,
      Query filterQuery,
      String... indices) {
    log.debug("Updating {}", updateItemIdentifier);
    UpdateByQueryRequest updateByQueryRequest =
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

    waitUntilTaskIsFinished(esClient, taskId, updateItemIdentifier);

    try {
      final TaskResponse.Status taskStatus = getTaskResponse(esClient, taskId).getTaskStatus();
      log.debug("Updated [{}] {}.", taskStatus.getDeleted(), updateItemIdentifier);
      return taskStatus.getUpdated() > 0L;
    } catch (IOException e) {
      throw new OptimizeRuntimeException(
          String.format(
              "Error while trying to read Elasticsearch task status with ID: [%s]", taskId),
          e);
    }
  }

  public static boolean tryDeleteByQueryRequest(
      OptimizeElasticsearchClient esClient,
      final Query query,
      String deletedItemIdentifier,
      final boolean refresh,
      String... indices) {
    log.debug("Deleting {}", deletedItemIdentifier);

    DeleteByQueryRequest request =
        DeleteByQueryRequest.of(
            b ->
                b.index(esClient.addPrefixesToIndices(indices))
                    .query(query)
                    .refresh(refresh)
                    .waitForCompletion(false)
                    .conflicts(Conflicts.Proceed));

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

    waitUntilTaskIsFinished(esClient, taskId, deletedItemIdentifier);

    try {
      final TaskResponse.Status taskStatus = getTaskResponse(esClient, taskId).getTaskStatus();
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

        int currentProgress = (int) (taskResponse.getProgress() * 100.0);
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
      } catch (InterruptedException e) {
        log.error("Waiting for Elasticsearch task (ID: {}) completion was interrupted!", taskId, e);
        Thread.currentThread().interrupt();
      } catch (Exception e) {
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
