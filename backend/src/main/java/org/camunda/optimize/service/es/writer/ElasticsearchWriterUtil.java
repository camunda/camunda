/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.HttpGet;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.camunda.optimize.service.util.mapper.ObjectMapperFactory;
import org.camunda.optimize.upgrade.es.TaskResponse;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.indices.rollover.RolloverRequest;
import org.elasticsearch.client.indices.rollover.RolloverResponse;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ElasticsearchWriterUtil {

  private static final String TASKS_ENDPOINT = "_tasks";
  private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);
  private static final String NESTED_DOC_LIMIT_MESSAGE = "The number of nested documents has exceeded the allowed " +
    "limit of";

  public static Script createFieldUpdateScript(final Set<String> fields,
                                               final Object entityDto,
                                               final ObjectMapper objectMapper) {
    final Map<String, Object> params = createFieldUpdateScriptParams(fields, entityDto, objectMapper);
    return createDefaultScriptWithPrimitiveParams(
      ElasticsearchWriterUtil.createUpdateFieldsScript(params.keySet()),
      params
    );
  }

  public static Map<String, Object> createFieldUpdateScriptParams(final Set<String> fields,
                                                                  final Object entityDto,
                                                                  final ObjectMapper objectMapper) {
    Map<String, Object> entityAsMap =
      objectMapper.convertValue(entityDto, new TypeReference<>() {
      });
    final Map<String, Object> params = new HashMap<>();
    for (String fieldName : fields) {
      Object fieldValue = entityAsMap.get(fieldName);
      if (fieldValue != null) {
        if (fieldValue instanceof TemporalAccessor) {
          fieldValue = dateTimeFormatter.format((TemporalAccessor) fieldValue);
        }
        params.put(fieldName, fieldValue);
      }
    }
    return params;
  }

  public static Script createDefaultScriptWithPrimitiveParams(final String inlineUpdateScript,
                                                              final Map<String, Object> params) {
    return new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      inlineUpdateScript,
      params
    );
  }

  public static Script createDefaultScriptWithSpecificDtoParams(final String inlineUpdateScript,
                                                                final Map<String, Object> params,
                                                                final ObjectMapper objectMapper) {
    return new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      inlineUpdateScript,
      mapParamsForScriptCreation(params, objectMapper)
    );
  }

  public static Script createDefaultScript(final String inlineUpdateScript) {
    return new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      inlineUpdateScript,
      Collections.emptyMap()
    );
  }

  static String createUpdateFieldsScript(final Set<String> fieldKeys) {
    return fieldKeys
      .stream()
      .map(fieldKey -> String.format("%s.%s = params.%s;%n", "ctx._source", fieldKey, fieldKey))
      .collect(Collectors.joining());
  }

  public static void executeImportRequestsAsBulk(String bulkRequestName, List<ImportRequestDto> importRequestDtos,
                                                 boolean retryFailedRequestsOnNestedDocLimit) {
    final Map<OptimizeElasticsearchClient, List<ImportRequestDto>> esClientToImportRequests = importRequestDtos.stream()
      .collect(groupingBy(ImportRequestDto::getEsClient));
    esClientToImportRequests.forEach((esClient, requestList) -> {
      if (requestList.isEmpty()) {
        log.warn("No requests supplied, cannot create bulk request");
      } else {
        final BulkRequest bulkRequest = new BulkRequest();
        final Map<String, List<ImportRequestDto>> requestsByType = requestList.stream()
          .collect(groupingBy(ImportRequestDto::getImportName));
        requestsByType.forEach((type, requests) -> {
          log.debug("Adding [{}] requests of type {} to bulk request", requests.size(), type);
          requests.forEach(importRequest -> {
            if (importRequest.getRequest() != null) {
              bulkRequest.add(importRequest.getRequest());
            } else {
              log.warn(
                "Cannot add request to bulk as no request was provided. Import type [{}]",
                importRequest.getImportName()
              );
            }
          });
        });
        doBulkRequest(esClient, bulkRequest, bulkRequestName, retryFailedRequestsOnNestedDocLimit);
      }
    });
  }

  public static <T> void doImportBulkRequestWithList(OptimizeElasticsearchClient esClient,
                                                     String importItemName,
                                                     Collection<T> entityCollection,
                                                     BiConsumer<BulkRequest, T> addDtoToRequestConsumer,
                                                     boolean retryRequestIfNestedDocLimitReached) {
    if (entityCollection.isEmpty()) {
      log.warn("Cannot perform bulk request with empty collection of {}.", importItemName);
    } else {
      final BulkRequest bulkRequest = new BulkRequest();
      entityCollection.forEach(dto -> addDtoToRequestConsumer.accept(bulkRequest, dto));
      doBulkRequest(esClient, bulkRequest, importItemName, retryRequestIfNestedDocLimitReached);
    }
  }

  public static boolean tryUpdateByQueryRequest(OptimizeElasticsearchClient esClient,
                                                String updateItemIdentifier,
                                                Script updateScript,
                                                AbstractQueryBuilder filterQuery,
                                                String... indices) {
    log.debug("Updating {}", updateItemIdentifier);
    UpdateByQueryRequest request = new UpdateByQueryRequest(indices)
      .setQuery(filterQuery)
      .setAbortOnVersionConflict(false)
      .setMaxRetries(NUMBER_OF_RETRIES_ON_CONFLICT)
      .setScript(updateScript)
      .setRefresh(true);
    esClient.applyIndexPrefixes(request);

    final String taskId;
    try {
      taskId = esClient.submitUpdateTask(request).getTask();
    } catch (IOException e) {
      final String errorMessage = String.format(
        "Could not create updateBy task for [%s] with query [%s]!",
        updateItemIdentifier,
        filterQuery
      );
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
        String.format("Error while trying to read Elasticsearch task status with ID: [%s]", taskId), e
      );
    }
  }

  public static boolean tryDeleteByQueryRequest(OptimizeElasticsearchClient esClient,
                                                AbstractQueryBuilder<?> queryBuilder,
                                                String deletedItemIdentifier,
                                                final boolean refresh,
                                                String... indices) {
    log.debug("Deleting {}", deletedItemIdentifier);

    DeleteByQueryRequest request = new DeleteByQueryRequest(indices)
      .setAbortOnVersionConflict(false)
      .setQuery(queryBuilder)
      .setRefresh(refresh);
    esClient.applyIndexPrefixes(request);

    final String taskId;
    try {
      taskId = esClient.submitDeleteTask(request).getTask();
    } catch (IOException e) {
      final String errorMessage = String.format(
        "Could not create delete task for [%s] with query [%s]!",
        deletedItemIdentifier,
        queryBuilder
      );
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    waitUntilTaskIsFinished(esClient, taskId, deletedItemIdentifier);

    try {
      final TaskResponse.Status taskStatus = getTaskResponse(esClient, taskId).getTaskStatus();
      log.debug("Deleted [{}] out of [{}] {}.", taskStatus.getDeleted(), taskStatus.getTotal(), deletedItemIdentifier);
      return taskStatus.getDeleted() > 0L;
    } catch (IOException e) {
      throw new OptimizeRuntimeException(
        String.format("Error while trying to read Elasticsearch task status with ID: [%s]", taskId), e
      );
    }
  }

  public static boolean triggerRollover(final OptimizeElasticsearchClient esClient, final String indexAliasName,
                                        final int maxIndexSizeGB) {
    RolloverRequest rolloverRequest = new RolloverRequest(indexAliasName, null);
    rolloverRequest.addMaxIndexSizeCondition(new ByteSizeValue(maxIndexSizeGB, ByteSizeUnit.GB));

    log.info("Executing rollover request on {}", indexAliasName);

    try {
      RolloverResponse rolloverResponse = esClient.rollover(rolloverRequest);
      if (rolloverResponse.isRolledOver()) {
        log.info(
          "Index with alias {} has been rolled over. New index name: {}",
          indexAliasName,
          rolloverResponse.getNewIndex()
        );
      } else {
        log.debug("Index with alias {} has not been rolled over.", indexAliasName);
      }
      return rolloverResponse.isRolledOver();
    } catch (Exception e) {
      String message = "Failed to execute rollover request";
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  public static void doBulkRequest(OptimizeElasticsearchClient esClient,
                                   BulkRequest bulkRequest,
                                   String itemName,
                                   boolean retryRequestIfNestedDocLimitReached) {
    if (retryRequestIfNestedDocLimitReached) {
      doBulkRequestWithNestedDocHandling(esClient, bulkRequest, itemName);
    } else {
      doBulkRequestWithoutRetries(esClient, bulkRequest, itemName);
    }
  }

  private static void doBulkRequestWithoutRetries(OptimizeElasticsearchClient esClient,
                                                  BulkRequest bulkRequest,
                                                  String itemName) {
    if (bulkRequest.numberOfActions() > 0) {
      try {
        BulkResponse bulkResponse = esClient.bulk(bulkRequest);
        if (bulkResponse.hasFailures()) {
          throw new OptimizeRuntimeException(String.format(
            "There were failures while performing bulk on %s.%n%s Message: %s",
            itemName,
            getHintForErrorMsg(bulkResponse),
            bulkResponse.buildFailureMessage()
          ));
        }
      } catch (IOException e) {
        String reason = String.format("There were errors while performing a bulk on %s.", itemName);
        log.error(reason, e);
        throw new OptimizeRuntimeException(reason, e);
      }
    } else {
      log.debug("Bulkrequest on {} not executed because it contains no actions.", itemName);
    }
  }

  private static void doBulkRequestWithNestedDocHandling(OptimizeElasticsearchClient esClient,
                                                         BulkRequest bulkRequest,
                                                         String itemName) {
    if (bulkRequest.numberOfActions() > 0) {
      try {
        BulkResponse bulkResponse = esClient.bulk(bulkRequest);
        if (bulkResponse.hasFailures()) {
          if (containsNestedDocumentLimitErrorMessage(bulkResponse)) {
            log.warn("There were failures while performing bulk on {} due to the nested document limit being reached." +
                       " Removing failed items and retrying", itemName);
            final Set<String> failedItemIds = Arrays.stream(bulkResponse.getItems())
              .filter(BulkItemResponse::isFailed)
              .filter(responseItem -> responseItem.getFailureMessage().contains(NESTED_DOC_LIMIT_MESSAGE))
              .map(BulkItemResponse::getId)
              .collect(Collectors.toSet());
            bulkRequest.requests().removeIf(request -> failedItemIds.contains(request.id()));
            if (!bulkRequest.requests().isEmpty()) {
              doBulkRequestWithNestedDocHandling(esClient, bulkRequest, itemName);
            }
          } else {
            throw new OptimizeRuntimeException(String.format(
              "There were failures while performing bulk on %s. Message: %s",
              itemName,
              bulkResponse.buildFailureMessage()
            ));
          }
        }
      } catch (IOException e) {
        String reason = String.format("There were errors while performing a bulk on %s.", itemName);
        log.error(reason, e);
        throw new OptimizeRuntimeException(reason, e);
      }
    } else {
      log.debug("Bulkrequest on {} not executed because it contains no actions.", itemName);
    }
  }

  private static boolean containsNestedDocumentLimitErrorMessage(final BulkResponse bulkResponse) {
    return bulkResponse.buildFailureMessage().contains(NESTED_DOC_LIMIT_MESSAGE);
  }

  private static String getHintForErrorMsg(final BulkResponse bulkResponse) {
    if (containsNestedDocumentLimitErrorMessage(bulkResponse)) {
      // exception potentially related to nested object limit
      return "If you are experiencing failures due to too many nested documents, try carefully increasing the " +
        "configured nested object limit (es.settings.index.nested_documents_limit) or enabling the skipping of " +
        "documents that have reached this limit during import (import.skipDataAfterNestedDocLimitReached). " +
        "See Optimize documentation for details.";
    }
    return "";
  }

  private static Map<String, Object> mapParamsForScriptCreation(final Map<String, Object> parameters,
                                                                final ObjectMapper objectMapper) {
    return Optional.ofNullable(parameters)
      // this conversion seems redundant but it's not
      // in case the values are specific dto objects this ensures they get converted to generic objects
      // that the elasticsearch client is happy to serialize while it complains on specific DTO's
      .map(value -> objectMapper.convertValue(
        value,
        new TypeReference<Map<String, Object>>() {
        }
      ))
      .orElse(Collections.emptyMap());
  }

  public static void waitUntilTaskIsFinished(final OptimizeElasticsearchClient esClient,
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
            taskResponse.isCompleted()
          );
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
          String.format("Error while trying to read Elasticsearch task (ID: %s) progress!", taskId), e
        );
      }
    }
  }

  private static TaskResponse getTaskResponse(final OptimizeElasticsearchClient esClient,
                                              final String taskId) throws IOException {
    final Request request = new Request(HttpGet.METHOD_NAME, "/" + TASKS_ENDPOINT + "/" + taskId);
    final Response response = esClient.performRequest(request);
    final ObjectMapper objectMapper = new ObjectMapperFactory(
      dateTimeFormatter,
      ConfigurationServiceBuilder.createDefaultConfiguration()
    ).createOptimizeMapper();
    return objectMapper.readValue(response.getEntity().getContent(), TaskResponse.class);
  }

  private static void validateTaskResponse(final TaskResponse taskResponse) {
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
