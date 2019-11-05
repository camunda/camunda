/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantIdsDto;
import org.camunda.optimize.dto.optimize.query.definition.TenantIdWithDefinitionsDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.StrictIndexMappingCreator;
import org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DEFINITION_NAME;
import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DEFINITION_TENANT_ID;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

@AllArgsConstructor
@Slf4j
@Component
public class DefinitionReader {
  private static final String TENANT_AGGREGATION = "tenants";
  private static final String DEFINITION_TYPE_AGGREGATION = "definitionType";
  private static final String DEFINITION_KEY_AGGREGATION = "definitionKey";
  private static final String NAME_AGGREGATION = "definitionName";
  private static final String[] ALL_DEFINITION_INDEXES =
    {PROCESS_DEFINITION_INDEX_NAME, DECISION_DEFINITION_INDEX_NAME};
  private static final String TENANT_NOT_DEFINED_VALUE = "null";

  private final OptimizeElasticsearchClient esClient;

  public Optional<DefinitionWithTenantIdsDto> getDefinition(final DefinitionType type, final String key) {
    if (type == null || key == null) {
      return Optional.empty();
    }

    final BoolQueryBuilder query = QueryBuilders.boolQuery()
      .must(termQuery(DEFINITION_KEY, key));

    return getDefinitionWithTenantIdsDtos(query, new String[]{getIndexNameForType(type)}).stream().findFirst();
  }

  public List<DefinitionWithTenantIdsDto> getDefinitions() {
    return getDefinitionWithTenantIdsDtos(QueryBuilders.matchAllQuery(), ALL_DEFINITION_INDEXES);
  }

  public List<TenantIdWithDefinitionsDto> getDefinitionsGroupedByTenant() {
    // four levels of aggregations
    // 4. group by name (should only be one)
    final TermsAggregationBuilder nameAggregation =
      terms(NAME_AGGREGATION)
        .field(DEFINITION_NAME)
        .size(MAX_RESPONSE_SIZE_LIMIT)
        .size(1);
    // 3. group by key
    final TermsAggregationBuilder keyAggregation = terms(DEFINITION_KEY_AGGREGATION)
      .field(DEFINITION_KEY)
      .size(MAX_RESPONSE_SIZE_LIMIT)
      .subAggregation(nameAggregation);
    // 2. group by _index (type)
    final TermsAggregationBuilder typeAggregation = terms(DEFINITION_TYPE_AGGREGATION)
      .field("_index")
      .subAggregation(keyAggregation);
    // 1. group by tenant
    final TermsAggregationBuilder tenantsAggregation =
      terms(TENANT_AGGREGATION)
        .field(DEFINITION_TENANT_ID)
        .size(MAX_RESPONSE_SIZE_LIMIT)
        // put `null` values (default tenant) into a dedicated bucket
        .missing(TENANT_NOT_DEFINED_VALUE)
        .order(BucketOrder.key(true))
        .subAggregation(typeAggregation);

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(QueryBuilders.matchAllQuery());
    searchSourceBuilder.aggregation(tenantsAggregation);
    searchSourceBuilder.size(0);

    final SearchRequest searchRequest = new SearchRequest(ALL_DEFINITION_INDEXES).source(searchSourceBuilder);
    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final Terms tenantResult = searchResponse.getAggregations().get(TENANT_AGGREGATION);
      return tenantResult.getBuckets().stream()
        .map(tenantBucket -> {
          // convert not defined bucket back to a `null` id
          final String tenantId = TENANT_NOT_DEFINED_VALUE.equalsIgnoreCase(tenantBucket.getKeyAsString())
            ? null
            : tenantBucket.getKeyAsString();
          final Terms typeAggregationResult = tenantBucket.getAggregations().get(DEFINITION_TYPE_AGGREGATION);
          final List<SimpleDefinitionDto> definitionDtos = typeAggregationResult.getBuckets()
            .stream()
            .flatMap(typeBucket -> {
              final DefinitionType definitionType = resolveDefinitionTypeFromIndexAlias(typeBucket.getKeyAsString());
              final Terms keyAggregationResult = typeBucket.getAggregations().get(DEFINITION_KEY_AGGREGATION);
              return keyAggregationResult.getBuckets().stream()
                .map(keyBucket -> {
                  final Terms nameResult = keyBucket.getAggregations().get(NAME_AGGREGATION);
                  final String definitionName = nameResult.getBuckets().stream()
                    .findFirst()
                    .map(Terms.Bucket::getKeyAsString)
                    .orElse(null);
                  return new SimpleDefinitionDto(keyBucket.getKeyAsString(), definitionName, definitionType);
                });
            })
            .collect(Collectors.toList());
          return new TenantIdWithDefinitionsDto(tenantId, definitionDtos);
        })
        .collect(Collectors.toList());
    } catch (IOException e) {
      final String reason = "Was not able to fetch definitions.";
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  private String getIndexNameForType(final DefinitionType type) {
    switch (type) {
      case PROCESS:
        return PROCESS_DEFINITION_INDEX_NAME;
      case DECISION:
        return DECISION_DEFINITION_INDEX_NAME;
      default:
        throw new OptimizeRuntimeException("Unsupported definition type:" + type);
    }
  }

  private List<DefinitionWithTenantIdsDto> getDefinitionWithTenantIdsDtos(final QueryBuilder filterQuery,
                                                                          final String[] definitionIndexNames) {
    // three levels of aggregations
    // 3.1 group by tenant
    final TermsAggregationBuilder tenantsAggregation =
      terms(TENANT_AGGREGATION)
        .field(DEFINITION_TENANT_ID)
        .size(MAX_RESPONSE_SIZE_LIMIT)
        // put `null` values (default tenant) into a dedicated bucket
        .missing(TENANT_NOT_DEFINED_VALUE)
        .order(BucketOrder.key(true));
    // 3.2 group by name (should only be one)
    final TermsAggregationBuilder nameAggregation =
      terms(NAME_AGGREGATION)
        .field(DEFINITION_NAME)
        .size(MAX_RESPONSE_SIZE_LIMIT)
        .size(1);
    // 2. group by key
    final TermsAggregationBuilder keyAggregation = terms(DEFINITION_KEY_AGGREGATION)
      .field(DEFINITION_KEY)
      .size(MAX_RESPONSE_SIZE_LIMIT)
      .subAggregation(tenantsAggregation)
      .subAggregation(nameAggregation);
    // 1. group by _index (type)
    final TermsAggregationBuilder typeAggregation = terms(DEFINITION_TYPE_AGGREGATION)
      .field("_index")
      .subAggregation(keyAggregation);

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(filterQuery);
    searchSourceBuilder.aggregation(typeAggregation);
    searchSourceBuilder.size(0);

    final SearchRequest searchRequest = new SearchRequest(definitionIndexNames).source(searchSourceBuilder);
    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final Terms typeAggregationResult = searchResponse.getAggregations().get(DEFINITION_TYPE_AGGREGATION);
      return typeAggregationResult.getBuckets().stream()
        .flatMap(typeBucket -> {
          final DefinitionType definitionType = resolveDefinitionTypeFromIndexAlias(typeBucket.getKeyAsString());
          final Terms keyAggregationResult = typeBucket.getAggregations().get(DEFINITION_KEY_AGGREGATION);
          return keyAggregationResult.getBuckets().stream().map(keyBucket -> {
            final Terms tenantResult = keyBucket.getAggregations().get(TENANT_AGGREGATION);
            final Terms nameResult = keyBucket.getAggregations().get(NAME_AGGREGATION);
            return new DefinitionWithTenantIdsDto(
              keyBucket.getKeyAsString(),
              nameResult.getBuckets().stream().findFirst().map(Terms.Bucket::getKeyAsString).orElse(null),
              definitionType,
              tenantResult.getBuckets().stream()
                .map(Terms.Bucket::getKeyAsString)
                // convert null bucket back to a `null` id
                .map(tenantId -> TENANT_NOT_DEFINED_VALUE.equalsIgnoreCase(tenantId) ? null : tenantId)
                .collect(Collectors.toList())
            );
          });
        })
        .collect(Collectors.toList());
    } catch (IOException e) {
      final String reason = "Was not able to fetch definitions.";
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  private DefinitionType resolveDefinitionTypeFromIndexAlias(String indexName) {
    if (indexName.equals(getOptimizeIndexNameForIndex(new ProcessDefinitionIndex()))) {
      return DefinitionType.PROCESS;
    } else if (indexName.equals(getOptimizeIndexNameForIndex(new DecisionDefinitionIndex()))) {
      return DefinitionType.DECISION;
    } else {
      throw new OptimizeRuntimeException("Unexpected definition index name: " + indexName);
    }
  }

  private String getOptimizeIndexNameForIndex(final StrictIndexMappingCreator index) {
    return esClient.getIndexNameService().getVersionedOptimizeIndexNameForIndexMapping(index);
  }
}
