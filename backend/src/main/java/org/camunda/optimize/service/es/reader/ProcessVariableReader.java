/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableValueRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableNameDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.IndexSettingsBuilder;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.ProcessVariableHelper;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
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
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.DATE_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.STRING_VARIABLES;
import static org.camunda.optimize.service.util.DefinitionQueryUtil.createDefinitionQuery;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getAllVariableTypeFieldLabels;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameFieldLabel;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableValueFieldLabel;
import static org.camunda.optimize.service.util.ProcessVariableHelper.variableTypeToFieldLabel;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.prefixQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.wildcardQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

@RequiredArgsConstructor
@Component
@Slf4j
public class ProcessVariableReader {

  private static final String FILTER_FOR_NAME_AGGREGATION = "filterForName";
  private static final String FILTERED_VARIABLES_AGGREGATION = "filteredVariables";
  private static final String NAMES_AGGREGATION = "names";
  private static final String VALUE_AGGREGATION = "values";
  private static final String STRING_VARIABLE_VALUE_NGRAM = "nGramField";
  private static final String STRING_VARIABLE_VALUE_LOWERCASE = "lowercaseField";

  private final OptimizeElasticsearchClient esClient;
  private final ProcessDefinitionReader processDefinitionReader;

  public List<VariableNameDto> getVariableNames(ProcessVariableNameRequestDto requestDto) {
    log.debug(
      "Fetching variable names for process definition with key [{}] and versions [{}]",
      requestDto.getProcessDefinitionKey(),
      requestDto.getProcessDefinitionVersions()
    );

    BoolQueryBuilder query =
      createDefinitionQuery(
        requestDto.getProcessDefinitionKey(),
        requestDto.getProcessDefinitionVersions(),
        requestDto.getTenantIds(),
        new ProcessInstanceType(),
        processDefinitionReader::getLatestVersionToKey
      );

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .size(0);
    SearchRequest searchRequest = new SearchRequest(PROC_INSTANCE_TYPE)
      .types(PROC_INSTANCE_TYPE)
      .source(searchSourceBuilder);

    addVariableNameAggregation(searchSourceBuilder, requestDto);
    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format(
        "Was not able to fetch variable names for process definition with key [%s] and versions [%s]",
        requestDto.getProcessDefinitionKey(),
        requestDto.getProcessDefinitionVersions()
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    Aggregations aggregations = searchResponse.getAggregations();
    return extractVariableNames(aggregations);
  }

  private List<VariableNameDto> extractVariableNames(Aggregations aggregations) {
    List<VariableNameDto> getVariablesResponseList = new ArrayList<>();
    for (String variableFieldLabel : ProcessVariableHelper.getAllVariableTypeFieldLabels()) {
      getVariablesResponseList.addAll(extractVariableNamesFromType(aggregations, variableFieldLabel));
    }
    return getVariablesResponseList;
  }

  private List<VariableNameDto> extractVariableNamesFromType(Aggregations aggregations, String variableFieldLabel) {
    Nested variables = aggregations.get(variableFieldLabel);
    Filter filteredVariables = variables.getAggregations().get(FILTERED_VARIABLES_AGGREGATION);
    Terms nameTerms = filteredVariables.getAggregations().get(NAMES_AGGREGATION);
    List<VariableNameDto> responseDtoList = new ArrayList<>();
    for (Terms.Bucket nameBucket : nameTerms.getBuckets()) {
      VariableNameDto response = new VariableNameDto();
      response.setName(nameBucket.getKeyAsString());
      response.setType(ProcessVariableHelper.fieldLabelToVariableType(variableFieldLabel));
      responseDtoList.add(response);
    }
    return responseDtoList;
  }

  private void addVariableNameAggregation(SearchSourceBuilder requestBuilder, ProcessVariableNameRequestDto requestDto) {
    String securedNamePrefix = requestDto.getNamePrefix() == null ? "" : requestDto.getNamePrefix();
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

  public List<String> getVariableValues(ProcessVariableValueRequestDto requestDto) {
    log.debug(
      "Fetching variable values for process definition with key [{}] and versions [{}]",
      requestDto.getProcessDefinitionKey(),
      requestDto.getProcessDefinitionVersions()
    );

    BoolQueryBuilder query =
      createDefinitionQuery(
        requestDto.getProcessDefinitionKey(),
        requestDto.getProcessDefinitionVersions(),
        requestDto.getTenantIds(),
        new ProcessInstanceType(),
        processDefinitionReader::getLatestVersionToKey
      );

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .aggregation(getVariableValueAggregation(requestDto))
      .size(0);
    SearchRequest searchRequest = new SearchRequest(PROC_INSTANCE_TYPE)
      .types(PROC_INSTANCE_TYPE)
      .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format(
        "Was not able to fetch variable values for variable [%s] and type [%s]",
        requestDto.getName(),
        requestDto.getType()
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    Aggregations aggregations = searchResponse.getAggregations();
    return extractVariableValues(aggregations, requestDto);
  }

  private List<String> extractVariableValues(Aggregations aggregations, ProcessVariableValueRequestDto requestDto) {
    String variableFieldLabel = variableTypeToFieldLabel(requestDto.getType());
    Nested variablesFromType = aggregations.get(variableFieldLabel);
    Filter filteredVariables = variablesFromType.getAggregations().get(FILTER_FOR_NAME_AGGREGATION);
    Terms valueTerms = filteredVariables.getAggregations().get(VALUE_AGGREGATION);
    List<String> allValues = new ArrayList<>();
    for (Terms.Bucket valueBucket : valueTerms.getBuckets()) {
      allValues.add(valueBucket.getKeyAsString());
    }
    int lastIndex = Math.min(allValues.size(), requestDto.getResultOffset() + requestDto.getNumResults());
    return allValues.subList(requestDto.getResultOffset(), lastIndex);
  }

  private AggregationBuilder getVariableValueAggregation(ProcessVariableValueRequestDto requestDto) {
    String variableFieldLabel = variableTypeToFieldLabel(requestDto.getType());
    Integer size = Math.min(requestDto.getResultOffset() + requestDto.getNumResults(), MAX_RESPONSE_SIZE_LIMIT);
    TermsAggregationBuilder collectAllVariableValues =
      terms(VALUE_AGGREGATION)
        .field(getNestedVariableValueFieldLabel(variableFieldLabel))
        .size(size)
        .order(BucketOrder.key(true));

    if (DATE_VARIABLES.equals(variableFieldLabel)) {
      collectAllVariableValues.format(OPTIMIZE_DATE_FORMAT);
    }
    FilterAggregationBuilder filterForVariableWithGivenNameAndPrefix =
      getVariableValueFilterAggregation(requestDto.getName(), variableFieldLabel, requestDto.getValueFilter());
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
