/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.schema.IndexSettingsBuilder;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.DecisionInstanceType.DECISION_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.type.DecisionInstanceType.DECISION_DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.type.DecisionInstanceType.INPUTS;
import static org.camunda.optimize.service.es.schema.type.DecisionInstanceType.OUTPUTS;
import static org.camunda.optimize.service.util.DecisionVariableHelper.getVariableIdField;
import static org.camunda.optimize.service.util.DecisionVariableHelper.getVariableValueField;
import static org.camunda.optimize.service.util.DecisionVariableHelper.getVariableValueFieldForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.wildcardQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

@Component
public class DecisionVariableReader {

  private static final Logger logger = LoggerFactory.getLogger(DecisionVariableReader.class);

  private static final String FILTERED_VARIABLES_AGGREGATION = "filteredVariables";
  private static final String VALUE_AGGREGATION = "values";
  private static final String VARIABLE_VALUE_NGRAM = "nGramField";
  private static final String VARIABLE_VALUE_LOWERCASE = "lowercaseField";

  private RestHighLevelClient esClient;

  @Autowired
  public DecisionVariableReader(final RestHighLevelClient esClient) {
    this.esClient = esClient;
  }

  public List<String> getInputVariableValues(final String decisionDefinitionKey,
                                             final String decisionDefinitionVersion,
                                             final String variableId,
                                             final VariableType variableType,
                                             final String valueFilter) {
    logger.debug(
      "Fetching input variable values for decision definition with key [{}] and version [{}]",
      decisionDefinitionKey,
      decisionDefinitionVersion
    );

    return getVariableValues(
      decisionDefinitionKey, decisionDefinitionVersion, variableId, variableType, valueFilter, INPUTS
    );
  }

  public List<String> getOutputVariableValues(final String decisionDefinitionKey,
                                              final String decisionDefinitionVersion,
                                              final String variableId,
                                              final VariableType variableType,
                                              final String valueFilter) {
    logger.debug(
      "Fetching output variable values for decision definition with key [{}] and version [{}]",
      decisionDefinitionKey,
      decisionDefinitionVersion
    );

    return getVariableValues(
      decisionDefinitionKey, decisionDefinitionVersion, variableId, variableType, valueFilter, OUTPUTS
    );
  }

  private List<String> getVariableValues(final String decisionDefinitionKey,
                                         final String decisionDefinitionVersion,
                                         final String variableId,
                                         final VariableType variableType,
                                         final String valueFilter,
                                         final String variablesPath) {
    final BoolQueryBuilder query = buildDecisionDefinitionBaseQuery(decisionDefinitionKey, decisionDefinitionVersion);

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .aggregation(getVariableValueAggregation(variableId, variablesPath, variableType, valueFilter))
      .size(0);

    final SearchRequest searchRequest = new SearchRequest(getOptimizeIndexAliasForType(DECISION_INSTANCE_TYPE))
      .types(DECISION_INSTANCE_TYPE)
      .source(searchSourceBuilder);

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final Aggregations aggregations = searchResponse.getAggregations();

      return extractVariableValues(aggregations, variablesPath);
    } catch (IOException e) {
      final String reason = String.format(
        "Was not able to fetch values for %s variable [%s] ", variableType, variableId
      );
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  private BoolQueryBuilder buildDecisionDefinitionBaseQuery(final String decisionDefinitionKey,
                                                            final String decisionDefinitionVersion) {
    BoolQueryBuilder query = QueryBuilders.boolQuery()
      .must(QueryBuilders.termsQuery(DECISION_DEFINITION_KEY, decisionDefinitionKey));

    if (!ReportConstants.ALL_VERSIONS.equals(decisionDefinitionVersion)) {
      query = query
        .must(QueryBuilders.termsQuery(DECISION_DEFINITION_VERSION, decisionDefinitionVersion));
    }
    return query;
  }

  private List<String> extractVariableValues(final Aggregations aggregations, final String variableFieldLabel) {
    Nested variablesFromType = aggregations.get(variableFieldLabel);
    Filter filteredVariables = variablesFromType.getAggregations().get(FILTERED_VARIABLES_AGGREGATION);
    Terms valueTerms = filteredVariables.getAggregations().get(VALUE_AGGREGATION);
    List<String> allValues = new ArrayList<>();
    for (Terms.Bucket valueBucket : valueTerms.getBuckets()) {
      allValues.add(valueBucket.getKeyAsString());
    }
    return allValues;
  }

  private AggregationBuilder getVariableValueAggregation(final String variableId,
                                                         final String variablePath,
                                                         final VariableType variableType,
                                                         final String valueFilter) {
    final TermsAggregationBuilder collectAllVariableValues =
      terms(VALUE_AGGREGATION)
        .field(getVariableValueFieldForType(variablePath, variableType))
        .size(10_000)
        .order(BucketOrder.key(true));

    final FilterAggregationBuilder filterForVariableWithGivenIdAndPrefix = getVariableValueFilterAggregation(
      variableId, variablePath, valueFilter
    );

    return nested(variablePath, variablePath)
      .subAggregation(
        filterForVariableWithGivenIdAndPrefix
          .subAggregation(collectAllVariableValues)
      );
  }

  private FilterAggregationBuilder getVariableValueFilterAggregation(final String variableId,
                                                                     final String variablePath,
                                                                     final String valueFilter) {
    final BoolQueryBuilder filterQuery = boolQuery()
      .must(termQuery(getVariableIdField(variablePath), variableId));

    addValueFilter(variablePath, valueFilter, filterQuery);

    return filter(FILTERED_VARIABLES_AGGREGATION, filterQuery);
  }

  private void addValueFilter(final String variablePath, final String valueFilter, final BoolQueryBuilder filterQuery) {
    if (!(valueFilter == null) && !valueFilter.isEmpty()) {
      final String lowerCaseValue = valueFilter.toLowerCase();
      QueryBuilder filter = (lowerCaseValue.length() > IndexSettingsBuilder.MAX_GRAM)
          /*
            using the slow wildcard query for uncommonly large filter strings (> 10 chars)
          */
        ? wildcardQuery(getValueSearchField(variablePath, VARIABLE_VALUE_LOWERCASE), buildWildcardQuery(lowerCaseValue))
          /*
            using Elasticsearch nGrams to filter for strings < 10 chars,
            because it's fast but increasing the number of chars makes the index bigger
          */
        : termQuery(getValueSearchField(variablePath, VARIABLE_VALUE_NGRAM), lowerCaseValue);

      filterQuery.must(filter);
    }
  }

  private String getValueSearchField(final String variablePath, final String searchFieldName) {
    return getVariableValueField(variablePath) + "." + searchFieldName;
  }

  private String buildWildcardQuery(final String valueFilter) {
    return "*" + valueFilter + "*";
  }
}
