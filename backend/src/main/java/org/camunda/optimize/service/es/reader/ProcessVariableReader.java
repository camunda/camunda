/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableSourceDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableValuesQueryDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.CompositeAggregationScroller;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.IndexSettingsBuilder;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.DefinitionQueryUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeValuesSourceBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.ParsedComposite;
import org.elasticsearch.search.aggregations.bucket.composite.TermsValuesSourceBuilder;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.service.es.schema.index.InstanceType.LOWERCASE_FIELD;
import static org.camunda.optimize.service.es.schema.index.InstanceType.N_GRAM_FIELD;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasNames;
import static org.camunda.optimize.service.util.InstanceIndexUtil.isInstanceIndexNotFoundException;
import static org.camunda.optimize.service.util.ProcessVariableHelper.buildWildcardQuery;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameField;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableTypeField;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableValueFieldForType;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getValueSearchField;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
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
  private static final String NAME_AGGREGATION = "variableNameAggregation";
  private static final String TYPE_AGGREGATION = "variableTypeAggregation";
  private static final String VALUE_AGGREGATION = "values";
  private static final String VAR_NAME_AND_TYPE_COMPOSITE_AGG = "varNameAndTypeCompositeAgg";

  private final OptimizeElasticsearchClient esClient;
  private final ProcessDefinitionReader processDefinitionReader;
  private final ConfigurationService configurationService;

  public List<ProcessVariableNameResponseDto> getVariableNames(ProcessVariableNameRequestDto requestDto) {
    if (requestDto.getProcessDefinitionVersions() == null || requestDto.getProcessDefinitionVersions().isEmpty()) {
      log.debug("Cannot fetch variable names for process definition with missing versions.");
      return Collections.emptyList();
    }

    log.debug(
      "Fetching variable names for process definition with key [{}] and versions [{}]",
      requestDto.getProcessDefinitionKey(),
      requestDto.getProcessDefinitionVersions()
    );

    return getVariableNames(Collections.singletonList(requestDto));
  }

  public List<ProcessVariableNameResponseDto> getVariableNames(final List<ProcessVariableNameRequestDto> variableNameRequests) {
    if (variableNameRequests.isEmpty()) {
      log.debug("Cannot fetch variable names as no variable requests are provided.");
      return Collections.emptyList();
    }

    BoolQueryBuilder query = boolQuery();
    variableNameRequests.stream()
      .filter(request -> request.getProcessDefinitionKey() != null)
      .filter(request -> !CollectionUtils.isEmpty(request.getProcessDefinitionVersions()))
      .forEach(request -> query.should(DefinitionQueryUtil.createDefinitionQuery(
        request.getProcessDefinitionKey(),
        request.getProcessDefinitionVersions(),
        request.getTenantIds(),
        new ProcessInstanceIndex(request.getProcessDefinitionKey()),
        processDefinitionReader::getLatestVersionToKey
      )));

    List<CompositeValuesSourceBuilder<?>> variableNameAndTypeTerms = new ArrayList<>();
    variableNameAndTypeTerms.add(new TermsValuesSourceBuilder(NAME_AGGREGATION)
                                   .field(getNestedVariableNameField()));
    variableNameAndTypeTerms.add(new TermsValuesSourceBuilder(TYPE_AGGREGATION)
                                   .field(getNestedVariableTypeField()));

    CompositeAggregationBuilder varNameAndTypeAgg =
      new CompositeAggregationBuilder(VAR_NAME_AND_TYPE_COMPOSITE_AGG, variableNameAndTypeTerms)
        .size(configurationService.getEsAggregationBucketLimit());

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .aggregation(nested(VARIABLES, VARIABLES).subAggregation(varNameAndTypeAgg))
      .size(0);
    SearchRequest searchRequest = new SearchRequest(getInstanceIndicesFromVariableRequests(variableNameRequests))
      .source(searchSourceBuilder);

    List<ProcessVariableNameResponseDto> variableNames = new ArrayList<>();
    CompositeAggregationScroller.create()
      .setEsClient(esClient)
      .setSearchRequest(searchRequest)
      .setPathToAggregation(VARIABLES, VAR_NAME_AND_TYPE_COMPOSITE_AGG)
      .setCompositeBucketConsumer(bucket -> variableNames.add(extractVariableName(bucket)))
      .consumeAllPages();
    return variableNames;
  }

  private ProcessVariableNameResponseDto extractVariableName(final ParsedComposite.ParsedBucket bucket) {
    final String variableName = (String) (bucket.getKey()).get(NAME_AGGREGATION);
    final String variableType = (String) (bucket.getKey().get(TYPE_AGGREGATION));
    return new ProcessVariableNameResponseDto(
      variableName,
      VariableType.getTypeForId(variableType)
    );
  }

  public List<String> getVariableValues(final ProcessVariableValuesQueryDto requestDto) {
    final List<ProcessVariableSourceDto> processVariableSources = requestDto.getProcessVariableSources()
      .stream()
      .filter(source -> !CollectionUtils.isEmpty(source.getProcessDefinitionVersions()))
      .collect(Collectors.toList());
    if (processVariableSources.isEmpty()) {
      log.debug("Cannot fetch variable names for process definition with missing versions.");
      return Collections.emptyList();
    }

    log.debug("Fetching input variable values from sources [{}]", processVariableSources);

    final BoolQueryBuilder query = boolQuery();
    processVariableSources.forEach(source -> query.should(
      DefinitionQueryUtil.createDefinitionQuery(
        source.getProcessDefinitionKey(),
        source.getProcessDefinitionVersions(),
        source.getTenantIds(),
        new ProcessInstanceIndex(source.getProcessDefinitionKey()),
        processDefinitionReader::getLatestVersionToKey
      ))
    );

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .aggregation(getVariableValueAggregation(requestDto))
      .size(0);

    final SearchRequest searchRequest =
      new SearchRequest(getInstanceIndicesFromVariableSources(requestDto.getProcessVariableSources()))
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
    } catch (ElasticsearchStatusException e) {
      if (isInstanceIndexNotFoundException(PROCESS, e)) {
        return Collections.emptyList();
      }
      throw e;
    }
  }

  private List<String> extractVariableValues(final Aggregations aggregations,
                                             final ProcessVariableValuesQueryDto requestDto) {
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

  private AggregationBuilder getVariableValueAggregation(final ProcessVariableValuesQueryDto requestDto) {
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
                                                                     final VariableType type,
                                                                     final String valueFilter) {
    final BoolQueryBuilder filterQuery = boolQuery()
      .must(termQuery(getNestedVariableNameField(), variableName))
      .must(termQuery(getNestedVariableTypeField(), type.getId()));

    addValueFilter(type, valueFilter, filterQuery);

    return filter(FILTERED_VARIABLES_AGGREGATION, filterQuery);
  }

  private void addValueFilter(final VariableType variableType,
                              final String valueFilter,
                              final BoolQueryBuilder filterQuery) {
    boolean isStringVariable = VariableType.STRING.equals(variableType);
    boolean valueFilterIsConfigured = valueFilter != null && !valueFilter.isEmpty();
    if (isStringVariable && valueFilterIsConfigured) {
      final String lowerCaseValue = valueFilter.toLowerCase();
      QueryBuilder filter = (lowerCaseValue.length() > IndexSettingsBuilder.MAX_GRAM)
          /*
            using the slow wildcard query for uncommonly large filter strings (> 10 chars)
          */
        ? wildcardQuery(getValueSearchField(LOWERCASE_FIELD), buildWildcardQuery(lowerCaseValue))
          /*
            using Elasticsearch ngrams to filter for strings < 10 chars,
            because it's fast but increasing the number of chars makes the index bigger
          */
        : termQuery(getValueSearchField(N_GRAM_FIELD), lowerCaseValue);

      filterQuery.must(filter);
    }
  }

  private String[] getInstanceIndicesFromVariableSources(final List<ProcessVariableSourceDto> processVariableSources) {
    final Set<String> definitionKeys = processVariableSources.stream()
      .map(ProcessVariableSourceDto::getProcessDefinitionKey)
      .collect(toSet());
    return getProcessInstanceIndexAliasNames(definitionKeys);
  }

  private String[] getInstanceIndicesFromVariableRequests(final List<ProcessVariableNameRequestDto> processVariableNameRequestDtos) {
    final Set<String> definitionKeys = processVariableNameRequestDtos.stream()
      .filter(Objects::nonNull)
      .map(ProcessVariableNameRequestDto::getProcessDefinitionKey)
      .collect(toSet());
    return getProcessInstanceIndexAliasNames(definitionKeys);
  }

}
