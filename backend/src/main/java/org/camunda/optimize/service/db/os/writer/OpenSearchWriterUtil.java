/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.writer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.BulkOperationBase;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;

import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.service.db.writer.DatabaseWriterUtil.createUpdateFieldsScript;
import static org.camunda.optimize.service.db.writer.DatabaseWriterUtil.mapParamsForScriptCreation;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OpenSearchWriterUtil {

  private static final String NESTED_DOC_LIMIT_MESSAGE = "nested";

  public static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);

  public static Script createFieldUpdateScript(final Set<String> fields,
                                               final Object entityDto,
                                               final ObjectMapper objectMapper) {
    final Map<String, JsonData> params = createFieldUpdateScriptParams(fields, entityDto, objectMapper);
    return createDefaultScriptWithPrimitiveParams(createUpdateFieldsScript(params.keySet()), params);
  }

  public static Script createDefaultScriptWithPrimitiveParams(final String inlineUpdateScript,
                                                              final Map<String, JsonData> params) {
    return QueryDSL.scriptFromJsonData(inlineUpdateScript, params);
  }

  public static Script createDefaultScriptWithSpecificDtoParams(final String inlineUpdateScript,
                                                                final Map<String, JsonData> params,
                                                                final ObjectMapper objectMapper) {
    return QueryDSL.scriptFromJsonData(inlineUpdateScript, mapParamsForScriptCreation(params, objectMapper));
  }

  public static Map<String, JsonData> createFieldUpdateScriptParams(final Set<String> fields,
                                                                    final Object entityDto,
                                                                    final ObjectMapper objectMapper) {
    Map<String, Object> entityAsMap =
      objectMapper.convertValue(entityDto, new TypeReference<>() {
      });
    final Map<String, JsonData> params = new HashMap<>();
    for (String fieldName : fields) {
      Object fieldValue = entityAsMap.get(fieldName);
      if (fieldValue != null) {
        if (fieldValue instanceof TemporalAccessor temporalAccessor) {
          fieldValue = dateTimeFormatter.format(temporalAccessor);
        }
        params.put(fieldName, JsonData.of(fieldValue));
      }
    }
    return params;
  }

  public static <T> void doImportBulkRequestWithList(final OptimizeOpenSearchClient osClient,
                                                     final String importItemName,
                                                     final List<T> entityCollection,
                                                     final Function<T, BulkOperation> addDtoToRequestConsumer,
                                                     final Boolean retryRequestIfNestedDocLimitReached,
                                                     final String indexName) {
    if (entityCollection.isEmpty()) {
      log.warn("Cannot perform bulk request with empty collection of {}.", importItemName);
    } else {
      final BulkRequest.Builder bulkReqBuilder = new BulkRequest.Builder()
        .index(indexName);
      List<BulkOperation> operations = entityCollection.stream().map(addDtoToRequestConsumer).toList();
      doBulkRequest(osClient, bulkReqBuilder, operations, importItemName, retryRequestIfNestedDocLimitReached);
    }
  }

  public static Script createDefaultScript(final String inlineUpdateScript) {
    return createDefaultScriptWithPrimitiveParams(inlineUpdateScript, Collections.emptyMap());
  }

  private static void doBulkRequest(final OptimizeOpenSearchClient osClient,
                                    final BulkRequest.Builder bulkReqBuilder,
                                    final List<BulkOperation> operations,
                                    final String itemName,
                                    final boolean retryRequestIfNestedDocLimitReached) {
    if (retryRequestIfNestedDocLimitReached) {
      doBulkRequestWithNestedDocHandling(osClient, bulkReqBuilder, operations, itemName);
    } else {
      doBulkRequestWithoutRetries(osClient, bulkReqBuilder, operations, itemName);
    }
  }

  private static void doBulkRequestWithNestedDocHandling(final OptimizeOpenSearchClient osClient,
                                                         final BulkRequest.Builder bulkReqBuilder,
                                                         final List<BulkOperation> operations,
                                                         final String itemName) {
    if (!operations.isEmpty()) {
      log.info("Executing bulk request on {} items of {}", operations.size(), itemName);

      final String errorMessage = String.format("There were errors while performing a bulk on %s.", itemName);
      BulkResponse bulkResponse = osClient.bulk(bulkReqBuilder.operations(operations), errorMessage);
      if (bulkResponse.errors()) {
        final Set<String> failedNestedDocLimitItemIds = bulkResponse.items()
          .stream()
          .filter(operation -> Objects.nonNull(operation.error()))
          // TODO OPT-7352 we need to check validate whether this is a valid way to check for nested document errors
          .filter(operation -> operation.error().reason().contains(NESTED_DOC_LIMIT_MESSAGE))
          .map(BulkResponseItem::id)
          .collect(Collectors.toSet());
        if (!failedNestedDocLimitItemIds.isEmpty()) {
          log.warn("There were failures while performing bulk on {} due to the nested document limit being reached." +
                     " Removing {} failed items and retrying", itemName, failedNestedDocLimitItemIds.size());
          operations.removeIf(request -> failedNestedDocLimitItemIds.contains(typeByBulkOperation(request).id()));
          if (!operations.isEmpty()) {
            doBulkRequestWithNestedDocHandling(osClient, bulkReqBuilder, operations, itemName);
          }
        } else {
          throw new OptimizeRuntimeException(String.format(
            "There were failures while performing bulk on %s.",
            itemName
          ));
        }
      }
    } else {
      log.debug("Bulk request on {} not executed because it contains no actions.", itemName);
    }
  }

  private static String getHintForErrorMsg(final boolean containsNestedDocumentLimitErrorMessage) {
    if (containsNestedDocumentLimitErrorMessage) {
      // exception potentially related to nested object limit
      return "If you are experiencing failures due to too many nested documents, try carefully increasing the " +
        "configured nested object limit or enabling the skipping of " +
        "documents that have reached this limit during import (import.skipDataAfterNestedDocLimitReached). " +
        "See Optimize documentation for details.";
    }
    return "";
  }

  private static BulkOperationBase typeByBulkOperation(final BulkOperation bulkOperation) {
    if (bulkOperation.isCreate()) {
      return bulkOperation.create();
    } else if (bulkOperation.isIndex()) {
      return bulkOperation.index();
    } else if (bulkOperation.isDelete()) {
      return bulkOperation.delete();
    } else if (bulkOperation.isUpdate()) {
      return bulkOperation.update();
    }
    throw new OptimizeRuntimeException(String.format(
      "The bulk operation with kind [%s] is not a supported operation.",
      bulkOperation._kind()
    ));
  }

  private static void doBulkRequestWithoutRetries(final OptimizeOpenSearchClient osClient,
                                                  final BulkRequest.Builder bulkReqBuilder,
                                                  final List<BulkOperation> operations,
                                                  final String itemName) {
    if (!operations.isEmpty()) {
      final String errorMessage = String.format("There were errors while performing a bulk on %s.", itemName);
      BulkResponse bulkResponse = osClient.bulk(bulkReqBuilder.operations(operations), errorMessage);
      if (bulkResponse.errors()) {
        final boolean isReachedNestedDocLimit = bulkResponse.items()
          .stream()
          .filter(operation -> Objects.nonNull(operation.error()))
          .anyMatch(operation -> operation.error().reason().contains(NESTED_DOC_LIMIT_MESSAGE));
        throw new OptimizeRuntimeException(String.format(
          "There were failures while performing bulk on %s.%n%s",
          itemName,
          getHintForErrorMsg(isReachedNestedDocLimit)
        ));
      }
    } else {
      log.debug("Bulk request on {} not executed because it contains no actions.", itemName);
    }
  }

}
