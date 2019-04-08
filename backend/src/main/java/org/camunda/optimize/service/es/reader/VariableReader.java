/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.variable.VariableRetrievalDto;
import org.camunda.optimize.service.es.schema.IndexSettingsBuilder;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.ProcessVariableHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
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
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
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
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.DATE_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.PROCESS_DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.STRING_VARIABLES;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getAllVariableTypeFieldLabels;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameFieldLabel;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableValueFieldLabel;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.prefixQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.wildcardQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

@Component
public class VariableReader {

  private final Logger logger = LoggerFactory.getLogger(VariableReader.class);

  private static final String FILTER_FOR_NAME_AGGREGATION = "filterForName";
  private static final String FILTERED_VARIABLES_AGGREGATION = "filteredVariables";
  private static final String NAMES_AGGREGATION = "names";
  private static final String VALUE_AGGREGATION = "values";
  private static final String STRING_VARIABLE_VALUE_NGRAM = "nGramField";
  private static final String STRING_VARIABLE_VALUE_LOWERCASE = "lowercaseField";

  private RestHighLevelClient esClient;
  private ConfigurationService configurationService;

  @Autowired
  public VariableReader(RestHighLevelClient esClient, ConfigurationService configurationService) {
    this.esClient = esClient;
    this.configurationService = configurationService;
  }

  public List<VariableRetrievalDto> getVariables(String processDefinitionKey,
                                                 String processDefinitionVersion,
                                                 String namePrefix) {
    logger.debug("Fetching variables for process definition with key [{}] and version [{}]",
      processDefinitionKey,
      processDefinitionVersion);

    BoolQueryBuilder query = buildProcessDefinitionBaseQuery(processDefinitionKey, processDefinitionVersion);

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .size(0);
    SearchRequest searchRequest =
      new SearchRequest(getOptimizeIndexAliasForType(PROC_INSTANCE_TYPE))
        .types(PROC_INSTANCE_TYPE)
        .source(searchSourceBuilder);


    addVariableAggregation(searchSourceBuilder, namePrefix);
    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format(
        "Was not able to fetch variables process definition with key [%s] and version [%s]",
        processDefinitionKey,
        processDefinitionVersion
      );
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    Aggregations aggregations = searchResponse.getAggregations();
    return extractVariables(aggregations);
  }

  private BoolQueryBuilder buildProcessDefinitionBaseQuery(String processDefinitionKey,
                                                           String processDefinitionVersion) {
    BoolQueryBuilder query;
    query =
      QueryBuilders.boolQuery()
        .must(QueryBuilders.termsQuery(PROCESS_DEFINITION_KEY, processDefinitionKey));

    if (!ReportConstants.ALL_VERSIONS.equals(processDefinitionVersion)) {
      query = query
        .must(QueryBuilders.termsQuery(PROCESS_DEFINITION_VERSION, processDefinitionVersion));
    }
    return query;
  }

  private List<VariableRetrievalDto> extractVariables(Aggregations aggregations) {
    List<VariableRetrievalDto> getVariablesResponseList = new ArrayList<>();
    for (String variableFieldLabel : ProcessVariableHelper.getAllVariableTypeFieldLabels()) {
      getVariablesResponseList.addAll(extractVariablesFromType(aggregations, variableFieldLabel));
    }
    return getVariablesResponseList;
  }

  private List<VariableRetrievalDto> extractVariablesFromType(Aggregations aggregations, String variableFieldLabel) {
    Nested variables = aggregations.get(variableFieldLabel);
    Filter filteredVariables = variables.getAggregations().get(FILTERED_VARIABLES_AGGREGATION);
    Terms nameTerms = filteredVariables.getAggregations().get(NAMES_AGGREGATION);
    List<VariableRetrievalDto> responseDtoList = new ArrayList<>();
    for (Terms.Bucket nameBucket : nameTerms.getBuckets()) {
      VariableRetrievalDto response = new VariableRetrievalDto();
      response.setName(nameBucket.getKeyAsString());
      response.setType(ProcessVariableHelper.fieldLabelToVariableType(variableFieldLabel));
      responseDtoList.add(response);
    }
    return responseDtoList;
  }

  private void addVariableAggregation(SearchSourceBuilder requestBuilder, String namePrefix) {
    String securedNamePrefix = namePrefix == null ? "" : namePrefix;
    for (String variableFieldLabel : getAllVariableTypeFieldLabels()) {
      FilterAggregationBuilder filterAllVariablesWithCertainPrefixInName = filter(
        FILTERED_VARIABLES_AGGREGATION,
        prefixQuery(getNestedVariableNameFieldLabel(variableFieldLabel), securedNamePrefix)
      );
      TermsAggregationBuilder collectAllVariableNames = terms(NAMES_AGGREGATION)
        .field(getNestedVariableNameFieldLabel(variableFieldLabel))
        .size(10_000)
        .order(BucketOrder.key(true));
      NestedAggregationBuilder checkoutVariables = nested(variableFieldLabel, variableFieldLabel);

      requestBuilder
        .aggregation(
          checkoutVariables
            .subAggregation(
              filterAllVariablesWithCertainPrefixInName
                .subAggregation(
                  collectAllVariableNames
                )
            )
        );
    }
  }

  public List<String> getVariableValues(String processDefinitionKey,
                                        String processDefinitionVersion,
                                        String name,
                                        String type,
                                        String valueFilter) {
    logger.debug("Fetching variable values for process definition with key [{}] and version [{}]",
      processDefinitionKey,
      processDefinitionVersion);

    String variableFieldLabel = ProcessVariableHelper.variableTypeToFieldLabel(type);

    BoolQueryBuilder query = buildProcessDefinitionBaseQuery(processDefinitionKey, processDefinitionVersion);

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .aggregation(getVariableValueAggregation(name, variableFieldLabel, valueFilter))
      .size(0);
    SearchRequest searchRequest =
      new SearchRequest(getOptimizeIndexAliasForType(PROC_INSTANCE_TYPE))
        .types(PROC_INSTANCE_TYPE)
        .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format(
        "Was not able to fetch variable values for variable [%s] and type [%s]",
        name,
        type
      );
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    Aggregations aggregations = searchResponse.getAggregations();
    return extractVariableValues(aggregations, variableFieldLabel);
  }

  private List<String> extractVariableValues(Aggregations aggregations, String variableFieldLabel) {
    Nested variablesFromType = aggregations.get(variableFieldLabel);
    Filter filteredVariables = variablesFromType.getAggregations().get(FILTER_FOR_NAME_AGGREGATION);
    Terms valueTerms = filteredVariables.getAggregations().get(VALUE_AGGREGATION);
    List<String> allValues = new ArrayList<>();
    for (Terms.Bucket valueBucket : valueTerms.getBuckets()) {
      allValues.add(valueBucket.getKeyAsString());
    }
    return allValues;
  }

  private AggregationBuilder getVariableValueAggregation(String name, String variableFieldLabel, String valueFilter) {
    TermsAggregationBuilder collectAllVariableValues =
      terms(VALUE_AGGREGATION)
        .field(getNestedVariableValueFieldLabel(variableFieldLabel))
        .size(10_000)
        .order(BucketOrder.key(true));

    if (DATE_VARIABLES.equals(variableFieldLabel)) {
      collectAllVariableValues.format(OPTIMIZE_DATE_FORMAT);
    }
    FilterAggregationBuilder filterForVariableWithGivenNameAndPrefix =
      getVariableValueFilterAggregation(name, variableFieldLabel, valueFilter);
    NestedAggregationBuilder checkoutVariables =
      nested(variableFieldLabel, variableFieldLabel);

    return
      checkoutVariables
        .subAggregation(
          filterForVariableWithGivenNameAndPrefix
            .subAggregation(
              collectAllVariableValues
            )
        );
  }

  private FilterAggregationBuilder getVariableValueFilterAggregation(String name,
                                                                     String variableFieldLabel,
                                                                     String valueFilter) {
    BoolQueryBuilder filterQuery = boolQuery()
        .must(termQuery(getNestedVariableNameFieldLabel(variableFieldLabel), name));
    addValueFilter(variableFieldLabel, valueFilter, filterQuery);
    return filter(
      FILTER_FOR_NAME_AGGREGATION,
      filterQuery
    );
  }

  private void addValueFilter(String variableFieldLabel, String valueFilter, BoolQueryBuilder filterQuery) {
    if (!(valueFilter == null) && !valueFilter.isEmpty() && STRING_VARIABLES.equals(variableFieldLabel)) {
      valueFilter = valueFilter.toLowerCase();
      QueryBuilder filter = (valueFilter.length() > IndexSettingsBuilder.MAX_GRAM)
          /*
            using the slow wildcard query for uncommonly large filter strings (> 10 chars)
          */
          ? wildcardQuery(
              getMultiFieldName(variableFieldLabel, STRING_VARIABLE_VALUE_LOWERCASE),
              buildWildcardQuery(valueFilter)
          )
          /*
            using Elasticsearch nGrams to filter for strings < 10 chars,
            because it's fast but increasing the number of chars makes the index bigger
          */
          : termQuery(
                  getMultiFieldName(variableFieldLabel, STRING_VARIABLE_VALUE_NGRAM),
                  valueFilter
          );

      filterQuery.must(filter);
    }
  }

  private String getMultiFieldName(String variableFieldLabel, String fieldName) {
    return getNestedVariableValueFieldLabel(variableFieldLabel) + "." + fieldName;
  }

  private String buildWildcardQuery(String valueFilter) {
    return "*" + valueFilter + "*";
  }
}
