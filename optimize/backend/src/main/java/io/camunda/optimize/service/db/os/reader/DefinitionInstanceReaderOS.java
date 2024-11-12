/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;
import static io.camunda.optimize.service.db.os.client.dsl.AggregationDSL.termAggregation;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.matchAll;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.sourceExcluded;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.stringTerms;
import static io.camunda.optimize.service.util.ExceptionUtil.isInstanceIndexNotFoundException;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.reader.DefinitionInstanceReader;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
@Conditional(OpenSearchCondition.class)
public class DefinitionInstanceReaderOS extends DefinitionInstanceReader {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(DefinitionInstanceReaderOS.class);
  final OptimizeIndexNameService indexNameService;
  final OptimizeOpenSearchClient osClient;

  public DefinitionInstanceReaderOS(
      final OptimizeIndexNameService indexNameService, final OptimizeOpenSearchClient osClient) {
    this.indexNameService = indexNameService;
    this.osClient = osClient;
  }

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
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(
          String.format("Was not able to retrieve definition keys for instances of type %s", type),
          e);
    } catch (final OpenSearchException e) {
      if (isInstanceIndexNotFoundException(type, e)) {
        LOG.info(
            "Was not able to retrieve definition keys for instances because no {} instance indices exist. "
                + "Returning empty set.",
            type);
        return Collections.emptySet();
      }
      throw e;
    }
  }
}
