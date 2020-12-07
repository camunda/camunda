/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.PropertyUtils;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.indices.rollover.RolloverRequest;
import org.elasticsearch.client.indices.rollover.RolloverResponse;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
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
  private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);

  public static Script createPrimitiveFieldUpdateScript(final Set<String> fields,
                                                        final Object entityDto) {
    final Map<String, Object> params = new HashMap<>();
    for (String fieldName : fields) {
      try {
        Object fieldValue = PropertyUtils.getProperty(entityDto, fieldName);
        if (fieldValue instanceof TemporalAccessor) {
          fieldValue = dateTimeFormatter.format((TemporalAccessor) fieldValue);
        }
        params.put(fieldName, fieldValue);
      } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
        throw new OptimizeRuntimeException("Could not read field from entity: " + fieldName, e);
      }
    }

    return createDefaultScriptWithPrimitiveParams(
      ElasticsearchWriterUtil.createUpdateFieldsScript(params.keySet()),
      params
    );
  }

  static Script createFieldUpdateScript(final Set<String> fields,
                                        final Object entityDto,
                                        final ObjectMapper objectMapper) {
    Map<String, Object> entityAsMap =
      objectMapper.convertValue(entityDto, new TypeReference<Map<String, Object>>() {
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

    return createDefaultScriptWithPrimitiveParams(
      ElasticsearchWriterUtil.createUpdateFieldsScript(params.keySet()),
      params
    );
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
      .map(fieldKey -> String.format("%s.%s = params.%s;\n", "ctx._source", fieldKey, fieldKey))
      .collect(Collectors.joining());
  }

  public static void executeImportRequestsAsBulk(String bulkRequestName, List<ImportRequestDto> importRequestDtos) {
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
        doBulkRequest(esClient, bulkRequest, bulkRequestName);
      }
    });
  }

  public static <T> void doBulkRequestWithList(OptimizeElasticsearchClient esClient,
                                               String importItemName,
                                               Collection<T> entityCollection,
                                               BiConsumer<BulkRequest, T> addDtoToRequestConsumer) {
    if (entityCollection.isEmpty()) {
      log.warn("Cannot perform bulk request with empty collection of {}.", importItemName);
    } else {
      final BulkRequest bulkRequest = new BulkRequest();
      entityCollection.forEach(dto -> addDtoToRequestConsumer.accept(bulkRequest, dto));
      doBulkRequest(esClient, bulkRequest, importItemName);
    }
  }

  public static boolean tryUpdateByQueryRequest(OptimizeElasticsearchClient esClient,
                                                String updateItemIdentifier,
                                                Script updateScript,
                                                AbstractQueryBuilder filterQuery,
                                                String... indices) {
    UpdateByQueryRequest request = new UpdateByQueryRequest(indices)
      .setQuery(filterQuery)
      .setAbortOnVersionConflict(false)
      .setMaxRetries(NUMBER_OF_RETRIES_ON_CONFLICT)
      .setScript(updateScript)
      .setRefresh(true);

    BulkByScrollResponse updateResponse;
    try {
      updateResponse = esClient.updateByQuery(request, RequestOptions.DEFAULT);
      checkBulkByScrollResponse(updateResponse, updateItemIdentifier, "updating");
    } catch (IOException e) {
      String reason = String.format("Could not update %s.", updateItemIdentifier);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    log.debug("Updated [{}] {}.", updateResponse.getUpdated(), updateItemIdentifier);

    return updateResponse.getUpdated() > 0L;
  }

  public static boolean tryDeleteByQueryRequest(OptimizeElasticsearchClient esClient,
                                                AbstractQueryBuilder<?> queryBuilder,
                                                String deletedItemName,
                                                String deletedItemIdentifier,
                                                final boolean refresh,
                                                String... indices) {
    log.debug("Deleting {} with {}", deletedItemName, deletedItemIdentifier);

    DeleteByQueryRequest request = new DeleteByQueryRequest(indices)
      .setAbortOnVersionConflict(false)
      .setQuery(queryBuilder)
      .setRefresh(refresh);

    BulkByScrollResponse deleteResponse;
    try {
      deleteResponse = esClient.deleteByQuery(request, RequestOptions.DEFAULT);
      checkBulkByScrollResponse(deleteResponse, deletedItemName, "deleting");
    } catch (IOException e) {
      String reason = String.format("Could not delete %s with %s.", deletedItemName, deletedItemIdentifier);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    log.debug("Deleted [{}] {} with {}.", deleteResponse.getDeleted(), deletedItemName, deletedItemIdentifier);
    return deleteResponse.getDeleted() > 0L;
  }

  public static boolean triggerRollover(final OptimizeElasticsearchClient esClient, final String indexAliasName,
                                        final int maxIndexSizeGB) {
    RolloverRequest rolloverRequest = new RolloverRequest(indexAliasName, null);
    rolloverRequest.addMaxIndexSizeCondition(new ByteSizeValue(maxIndexSizeGB, ByteSizeUnit.GB));

    log.info("Executing Rollover Request...");

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

  public static void doBulkRequest(OptimizeElasticsearchClient esClient, BulkRequest bulkRequest, String itemName) {
    try {
      BulkResponse bulkResponse = esClient.bulk(bulkRequest, RequestOptions.DEFAULT);
      if (bulkResponse.hasFailures()) {
        throw new OptimizeRuntimeException(String.format(
          "There were failures while performing bulk on %s.%n%s Message: %s",
          itemName,
          getHintForErrorMsg(bulkResponse.buildFailureMessage()),
          bulkResponse.buildFailureMessage()
        ));
      }
    } catch (IOException e) {
      String reason = String.format("There were errors while performing a bulk on %s.", itemName);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  private static void checkBulkByScrollResponse(BulkByScrollResponse bulkByScrollResponse,
                                                String itemName,
                                                String verb) {
    if (!bulkByScrollResponse.getBulkFailures().isEmpty()) {
      throw new OptimizeRuntimeException(String.format(
        "There were failures while %s %s with message: %s",
        verb,
        itemName,
        bulkByScrollResponse.getBulkFailures()
      ));
    }
  }

  private static String getHintForErrorMsg(final String errorMsg) {
    if (errorMsg.contains("nested")) {
      // exception potentially related to nested object limit
      return "If you are experiencing failures due to too many nested documents, " +
        "try carefully increasing the configured nested object limit (es.settings.index.nested_documents_limit). " +
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

}
