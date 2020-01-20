/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.PropertyUtils;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.RequestOptions;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

@UtilityClass
@Slf4j
public class ElasticsearchWriterUtil {
  private static DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);

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

    return createDefaultScript(ElasticsearchWriterUtil.createUpdateFieldsScript(params.keySet()), params);
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

    return createDefaultScript(ElasticsearchWriterUtil.createUpdateFieldsScript(params.keySet()), params);
  }

  public static Script createDefaultScript(final String inlineUpdateScript, final Map<String, Object> params) {
    return new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      inlineUpdateScript,
      params
    );
  }

  static String createUpdateFieldsScript(final Set<String> fieldKeys) {
    return createUpdateFieldsScript("ctx._source", fieldKeys);
  }

  static String createUpdateFieldsScript(final String fieldPath, final Set<String> fieldKeys) {
    return fieldKeys
      .stream()
      .map(fieldKey -> String.format("%s.%s = params.%s;\n", fieldPath, fieldKey, fieldKey))
      .collect(Collectors.joining());
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

  public static <T extends OptimizeDto> void doBulkRequestWithMap(OptimizeElasticsearchClient esClient,
                                                                  String importItemName,
                                                                  Map<String, List<T>> dtoMap,
                                                                  BiConsumer<BulkRequest, Map.Entry<String, List<T>>> addDtoToRequestConsumer) {
    if (dtoMap.isEmpty()) {
      log.warn("Cannot import empty map of {}.", importItemName);
    } else {
      final BulkRequest bulkRequest = new BulkRequest();
      dtoMap.entrySet().forEach(dto -> addDtoToRequestConsumer.accept(bulkRequest, dto));
      doBulkRequest(esClient, bulkRequest, importItemName);
    }
  }

  public static boolean tryUpdateByQueryRequest(OptimizeElasticsearchClient esClient,
                                                String updateItemName,
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
      checkBulkByScrollResponse(updateResponse, updateItemName, "updating");
    } catch (IOException e) {
      String reason =
        String.format(
          "Could not update %s with [%s].",
          updateItemName,
          updateItemIdentifier
        );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    log.debug("Updated [{}] {} with {}.", updateResponse.getUpdated(), updateItemName, updateItemIdentifier);

    return updateResponse.getUpdated() > 0L;
  }

  public static boolean tryDeleteByQueryRequest(OptimizeElasticsearchClient esClient,
                                                AbstractQueryBuilder queryBuilder,
                                                String deletedItemName,
                                                String deletedItemIdentifier,
                                                String... indices) {
    log.debug("Deleting {} with {}", deletedItemName, deletedItemIdentifier);

    DeleteByQueryRequest request = new DeleteByQueryRequest(indices)
      .setAbortOnVersionConflict(false)
      .setQuery(queryBuilder)
      .setRefresh(true);

    BulkByScrollResponse deleteResponse;
    try {
      deleteResponse = esClient.deleteByQuery(request, RequestOptions.DEFAULT);
      checkBulkByScrollResponse(deleteResponse, deletedItemName, "deleting");
    } catch (IOException e) {
      String reason =
        String.format("Could not delete %s with %s.", deletedItemName, deletedItemIdentifier);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    log.debug("Deleted [{}] {} with {}.", deleteResponse.getDeleted(), deletedItemName, deletedItemIdentifier);

    return deleteResponse.getDeleted() > 0L;
  }

  private void doBulkRequest(OptimizeElasticsearchClient esClient,
                             BulkRequest bulkRequest,
                             String itemName) {
    try {
      BulkResponse bulkResponse = esClient.bulk(bulkRequest, RequestOptions.DEFAULT);
      if (bulkResponse.hasFailures()) {
        String errorMessage = String.format(
          "There were failures while writing %s with message: %s",
          itemName,
          bulkResponse.buildFailureMessage()
        );
        throw new OptimizeRuntimeException(errorMessage);
      }
    } catch (IOException e) {
      String reason = String.format("There were errors while writing {}.", itemName);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  private void checkBulkByScrollResponse(BulkByScrollResponse bulkByScrollResponse,
                                         String itemName,
                                         String verb) {
    if (!bulkByScrollResponse.getBulkFailures().isEmpty()) {
      String errorMessage = String.format(
        "There were failures while %s %s with message: %s",
        verb,
        itemName,
        bulkByScrollResponse.getBulkFailures()
      );
      throw new OptimizeRuntimeException(errorMessage);
    }
  }
}
