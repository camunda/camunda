/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.reader;

import static org.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.camunda.optimize.service.db.os.externalcode.client.dsl.AggregationDSL.termAggregation;
import static org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.matchAll;
import static org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.sourceExcluded;
import static org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.stringTerms;
import static org.camunda.optimize.service.util.InstanceIndexUtil.isInstanceIndexNotFoundException;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.reader.DefinitionInstanceReader;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class DefinitionInstanceReaderOS extends DefinitionInstanceReader {
  final OptimizeIndexNameService indexNameService;
  final OptimizeOpenSearchClient osClient;

  @Override
  public Set<String> getAllExistingDefinitionKeys(
      final DefinitionType type, final Set<String> instanceIds) {
    final Query idQuery =
        CollectionUtils.isEmpty(instanceIds)
            ? matchAll()
            : stringTerms(resolveInstanceIdFieldForType(type), instanceIds);
    final String defKeyAggName = "definitionKeyAggregation";
    final Aggregation definitionKeyAgg =
        termAggregation(resolveDefinitionKeyFieldForType(type), MAX_RESPONSE_SIZE_LIMIT)
            ._toAggregation();
    final SearchRequest.Builder requestBuilder =
        new SearchRequest.Builder()
            .index(
                indexNameService.getOptimizeIndexAliasForIndex(resolveIndexMultiAliasForType(type)))
            .aggregations(defKeyAggName, definitionKeyAgg)
            .query(idQuery)
            .source(sourceExcluded());

    try {
      return osClient
          .getRichOpenSearchClient()
          .doc()
          .searchAggregationsUnsafe(requestBuilder)
          .get(defKeyAggName)
          .sterms()
          .buckets()
          .array()
          .stream()
          .map(StringTermsBucket::key)
          .collect(Collectors.toSet());
    } catch (IOException e) {
      throw new OptimizeRuntimeException(
          String.format("Was not able to retrieve definition keys for instances of type %s", type),
          e);
    } catch (OpenSearchException e) {
      if (isInstanceIndexNotFoundException(type, e)) {
        log.info(
            "Was not able to retrieve definition keys for instances because no {} instance indices exist. "
                + "Returning empty set.",
            type);
        return Collections.emptySet();
      }
      throw e;
    }
  }
}
