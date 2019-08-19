/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableValueRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.IndexSettingsBuilder;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
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

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.util.DecisionVariableHelper.getVariableValueField;
import static org.camunda.optimize.service.util.DefinitionQueryUtil.createDefinitionQuery;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameField;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableTypeField;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableValueFieldForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
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

  private static final String FILTERED_VARIABLES_AGGREGATION = "filteredVariables";
  private static final String VALUE_AGGREGATION = "values";
  private static final String VARIABLE_VALUE_NGRAM = "nGramField";
  private static final String VARIABLE_VALUE_LOWERCASE = "lowercaseField";
  private static final String VARIABLE_NAME_BUCKET_AGGREGATION = "names";
  private static final String VARIABLE_TYPE_AGGREGATION = "variableTypeAggregation";

  private final OptimizeElasticsearchClient esClient;
  private final ProcessDefinitionReader processDefinitionReader;

  public List<ProcessVariableNameResponseDto> getVariableNames(ProcessVariableNameRequestDto requestDto) {
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
        new ProcessInstanceIndex(),
        processDefinitionReader::getLatestVersionToKey
      );

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .size(0);
    SearchRequest searchRequest = new SearchRequest(PROCESS_INSTANCE_INDEX_NAME)
      .types(PROCESS_INSTANCE_INDEX_NAME)
      .source(searchSourceBuilder);

    addVariableNameAggregation(searchSourceBuilder, requestDto);
    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format(
        "Was not able to fetch process variable names for definition with key [%s] and versions [%s]",
        requestDto.getProcessDefinitionKey(),
        requestDto.getProcessDefinitionVersions()
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    Aggregations aggregations = searchResponse.getAggregations();
    return extractVariableNames(aggregations);
  }

  private List<ProcessVariableNameResponseDto> extractVariableNames(Aggregations aggregations) {
    Nested variables = aggregations.get(VARIABLES);
    Filter filteredVariables = variables.getAggregations().get(FILTERED_VARIABLES_AGGREGATION);
    Terms nameTerms = filteredVariables.getAggregations().get(VARIABLE_NAME_BUCKET_AGGREGATION);
    List<ProcessVariableNameResponseDto> responseDtoList = new ArrayList<>();
    for (Terms.Bucket variableNameBucket : nameTerms.getBuckets()) {
      Terms variableTypes = variableNameBucket.getAggregations().get(VARIABLE_TYPE_AGGREGATION);
      for (Terms.Bucket variableTypeBucket : variableTypes.getBuckets()) {
        ProcessVariableNameResponseDto response = new ProcessVariableNameResponseDto();
        response.setName(variableNameBucket.getKeyAsString());
        response.setType(VariableType.getTypeForId(variableTypeBucket.getKeyAsString()));
        responseDtoList.add(response);
      }
    }
    return responseDtoList;
  }

  private void addVariableNameAggregation(SearchSourceBuilder requestBuilder, ProcessVariableNameRequestDto requestDto) {
    String securedNamePrefix = requestDto.getNamePrefix() == null ? "" : requestDto.getNamePrefix();
    FilterAggregationBuilder filterAllVariablesWithCertainPrefixInName = filter(
      FILTERED_VARIABLES_AGGREGATION,
      prefixQuery(getNestedVariableNameField(), securedNamePrefix)
    );
    TermsAggregationBuilder collectVariableNameBuckets = terms(VARIABLE_NAME_BUCKET_AGGREGATION)
      .field(getNestedVariableNameField())
      .size(10_000)
      .order(BucketOrder.key(true));
    //  a variable is unique by its name and type. Therefore we have to collect all possible types as well
    TermsAggregationBuilder collectPossibleVariableTypes = terms(VARIABLE_TYPE_AGGREGATION)
      .field(getNestedVariableTypeField())
      .size(10_000);

    NestedAggregationBuilder checkoutVariables = nested(VARIABLES, VARIABLES);

    requestBuilder
      .aggregation(
        checkoutVariables
          .subAggregation(
            filterAllVariablesWithCertainPrefixInName
              .subAggregation(
                collectVariableNameBuckets
                  .subAggregation(
                    collectPossibleVariableTypes
                  )
              )
          )
      );
  }


  // ----------------------------

  public List<String> getVariableValues(final ProcessVariableValueRequestDto requestDto) {
    log.debug(
      "Fetching input variable values for process definition with key [{}] and versions [{}]",
      requestDto.getProcessDefinitionKey(),
      requestDto.getProcessDefinitionVersions()
    );

    final BoolQueryBuilder query =
      createDefinitionQuery(
        requestDto.getProcessDefinitionKey(),
        requestDto.getProcessDefinitionVersions(),
        requestDto.getTenantIds(),
        new ProcessInstanceIndex(),
        processDefinitionReader::getLatestVersionToKey
      );

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .aggregation(getVariableValueAggregation(requestDto))
      .size(0);

    final SearchRequest searchRequest = new SearchRequest(PROCESS_INSTANCE_INDEX_NAME)
      .types(PROCESS_INSTANCE_INDEX_NAME)
      .source(searchSourceBuilder);

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final Aggregations aggregations = searchResponse.getAggregations();

      return extractVariableValues(aggregations, requestDto);
    } catch (IOException e) {
      final String reason = String.format(
        "Was not able to fetch values for variable [%s] with type [%s] ",
        requestDto.getName(),
        requestDto.getType().getId()
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  private List<String> extractVariableValues(final Aggregations aggregations,
                                             final ProcessVariableValueRequestDto requestDto) {
    Nested variablesFromType = aggregations.get(VARIABLES);
    Filter filteredVariables = variablesFromType.getAggregations().get(FILTERED_VARIABLES_AGGREGATION);
    Terms valueTerms = filteredVariables.getAggregations().get(VALUE_AGGREGATION);
    List<String> allValues = new ArrayList<>();
    for (Terms.Bucket valueBucket : valueTerms.getBuckets()) {
      allValues.add(valueBucket.getKeyAsString());
    }
    int lastIndex = Math.min(allValues.size(), requestDto.getResultOffset() + requestDto.getNumResults());
    return allValues.subList(requestDto.getResultOffset(), lastIndex);
  }

  private AggregationBuilder getVariableValueAggregation(final ProcessVariableValueRequestDto requestDto) {
    final TermsAggregationBuilder collectAllVariableValues =
      terms(VALUE_AGGREGATION)
        .field(getNestedVariableValueFieldForType(requestDto.getType()))
        .size(MAX_RESPONSE_SIZE_LIMIT)
        .order(BucketOrder.key(true));

    final FilterAggregationBuilder filterForVariableWithGivenIdAndPrefix =
      getVariableValueFilterAggregation(requestDto.getName(), requestDto.getType(), requestDto.getValueFilter());

    return nested(VARIABLES, VARIABLES)
      .subAggregation(
        filterForVariableWithGivenIdAndPrefix
          .subAggregation(collectAllVariableValues)
      );
  }

  private FilterAggregationBuilder getVariableValueFilterAggregation(final String variableName,
                                                                     final VariableType type, final String valueFilter) {
    final BoolQueryBuilder filterQuery = boolQuery()
      .must(termQuery(ProcessVariableHelper.getNestedVariableNameField(), variableName))
      .must(termQuery(getNestedVariableTypeField(), type.getId()));

    addValueFilter(type, valueFilter, filterQuery);

    return filter(FILTERED_VARIABLES_AGGREGATION, filterQuery);
  }

  private void addValueFilter(final VariableType variableType,
                              final String valueFilter, final BoolQueryBuilder filterQuery) {
    boolean isStringVariable = VariableType.STRING.equals(variableType);
    boolean valueFilterIsConfigured = !(valueFilter == null) && !valueFilter.isEmpty();
    if (isStringVariable && valueFilterIsConfigured) {
      final String lowerCaseValue = valueFilter.toLowerCase();
      QueryBuilder filter = (lowerCaseValue.length() > IndexSettingsBuilder.MAX_GRAM)
          /*
            using the slow wildcard query for uncommonly large filter strings (> 10 chars)
          */
        ? wildcardQuery(getValueSearchField(VARIABLE_VALUE_LOWERCASE), buildWildcardQuery(lowerCaseValue))
          /*
            using Elasticsearch nGrams to filter for strings < 10 chars,
            because it's fast but increasing the number of chars makes the index bigger
          */
        : termQuery(getValueSearchField(VARIABLE_VALUE_NGRAM), lowerCaseValue);

      filterQuery.must(filter);
    }
  }

  private String getValueSearchField(final String searchFieldName) {
    return getVariableValueField(VARIABLES) + "." + searchFieldName;
  }

  private String buildWildcardQuery(final String valueFilter) {
    return "*" + valueFilter + "*";
  }

}
