/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.reader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableValueRequestDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.IndexSettingsBuilder;
import org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
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

import static org.camunda.optimize.dto.optimize.DefinitionType.DECISION;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.INPUTS;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.OUTPUTS;
import static org.camunda.optimize.service.util.DecisionVariableHelper.getVariableClauseIdField;
import static org.camunda.optimize.service.util.DecisionVariableHelper.getVariableValueField;
import static org.camunda.optimize.service.util.DecisionVariableHelper.getVariableValueFieldForType;
import static org.camunda.optimize.service.util.DefinitionQueryUtil.createDefinitionQuery;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getDecisionInstanceIndexAliasName;
import static org.camunda.optimize.service.util.InstanceIndexUtil.isInstanceIndexNotFoundException;
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
public class DecisionVariableReader {

  private static final String FILTERED_VARIABLES_AGGREGATION = "filteredVariables";
  private static final String VALUE_AGGREGATION = "values";
  private static final String VARIABLE_VALUE_NGRAM = "nGramField";
  private static final String VARIABLE_VALUE_LOWERCASE = "lowercaseField";

  private final OptimizeElasticsearchClient esClient;
  private final DecisionDefinitionReader decisionDefinitionReader;

  public List<DecisionVariableNameResponseDto> getInputVariableNames(final String decisionDefinitionKey,
                                                                     final List<String> decisionDefinitionVersions,
                                                                     final List<String> tenantIds) {
    if (decisionDefinitionVersions == null || decisionDefinitionVersions.isEmpty()) {
      log.debug("Cannot fetch output variable values for decision definition with missing versions.");
      return Collections.emptyList();
    }

    List<DecisionVariableNameResponseDto> decisionDefinitions = decisionDefinitionReader.getDecisionDefinition(
      decisionDefinitionKey,
      decisionDefinitionVersions,
      tenantIds
    ).orElseThrow(() -> new OptimizeRuntimeException(
      "Could not extract input variables. Requested decision definition not found!"))
      .getInputVariableNames();

    decisionDefinitions.forEach(definition -> {
      if (definition.getName() == null) {
        definition.setName(definition.getId());
      }
    });
    return decisionDefinitions;
  }

  public List<DecisionVariableNameResponseDto> getOutputVariableNames(final String decisionDefinitionKey,
                                                                      final List<String> decisionDefinitionVersions,
                                                                      final List<String> tenantIds) {
    if (decisionDefinitionVersions == null || decisionDefinitionVersions.isEmpty()) {
      return Collections.emptyList();
    } else {
      List<DecisionVariableNameResponseDto> decisionDefinitions = decisionDefinitionReader.getDecisionDefinition(
        decisionDefinitionKey,
        decisionDefinitionVersions,
        tenantIds
      ).orElseThrow(() -> new OptimizeRuntimeException(
        "Could not extract output variables. Requested decision definition not found!"))
        .getOutputVariableNames();

      decisionDefinitions.forEach(definition -> {
        if (definition.getName() == null) {
          definition.setName(definition.getId());
        }
      });
      return decisionDefinitions;
    }
  }

  public List<String> getInputVariableValues(final DecisionVariableValueRequestDto requestDto) {
    if (requestDto.getDecisionDefinitionVersions() == null || requestDto.getDecisionDefinitionVersions().isEmpty()) {
      log.debug("Cannot fetch input variable values for decision definition with missing versions.");
      return Collections.emptyList();
    }

    log.debug(
      "Fetching input variable values for decision definition with key [{}] and versions [{}]",
      requestDto.getDecisionDefinitionKey(),
      requestDto.getDecisionDefinitionVersions()
    );

    return getVariableValues(requestDto, INPUTS);
  }

  public List<String> getOutputVariableValues(final DecisionVariableValueRequestDto requestDto) {
    if (requestDto.getDecisionDefinitionVersions() == null || requestDto.getDecisionDefinitionVersions().isEmpty()) {
      log.debug("Cannot fetch output variable values for decision definition with missing versions.");
      return Collections.emptyList();
    }

    log.debug(
      "Fetching output variable values for decision definition with key [{}] and versions [{}]",
      requestDto.getDecisionDefinitionKey(),
      requestDto.getDecisionDefinitionVersions()
    );

    return getVariableValues(requestDto, OUTPUTS);
  }

  private List<String> getVariableValues(final DecisionVariableValueRequestDto requestDto,
                                         final String variablesPath) {
    final BoolQueryBuilder query = createDefinitionQuery(
      requestDto.getDecisionDefinitionKey(),
      requestDto.getDecisionDefinitionVersions(),
      requestDto.getTenantIds(),
      new DecisionInstanceIndex(requestDto.getDecisionDefinitionKey()),
      decisionDefinitionReader::getLatestVersionToKey
    );

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .aggregation(getVariableValueAggregation(requestDto, variablesPath))
      .size(0);

    final SearchRequest searchRequest =
      new SearchRequest(getDecisionInstanceIndexAliasName(requestDto.getDecisionDefinitionKey()))
        .source(searchSourceBuilder);

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest);
      final Aggregations aggregations = searchResponse.getAggregations();

      return extractVariableValues(aggregations, requestDto, variablesPath);
    } catch (IOException e) {
      final String reason = String.format(
        "Was not able to fetch values for variable [%s] with type [%s] ",
        requestDto.getVariableId(),
        requestDto.getVariableType()
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    } catch (ElasticsearchStatusException e) {
      if (isInstanceIndexNotFoundException(DECISION, e)) {
        log.info(
          "Was not able to fetch variable values because no instance index with alias {} exists. " +
            "Returning empty list.",
          getDecisionInstanceIndexAliasName(requestDto.getDecisionDefinitionKey())
        );
        return Collections.emptyList();
      }
      throw e;
    }
  }

  private List<String> extractVariableValues(final Aggregations aggregations,
                                             final DecisionVariableValueRequestDto requestDto,
                                             final String variableFieldLabel) {
    Nested variablesFromType = aggregations.get(variableFieldLabel);
    Filter filteredVariables = variablesFromType.getAggregations().get(FILTERED_VARIABLES_AGGREGATION);
    Terms valueTerms = filteredVariables.getAggregations().get(VALUE_AGGREGATION);
    List<String> allValues = new ArrayList<>();
    for (Terms.Bucket valueBucket : valueTerms.getBuckets()) {
      allValues.add(valueBucket.getKeyAsString());
    }
    int lastIndex = Math.min(allValues.size(), requestDto.getResultOffset() + requestDto.getNumResults());
    return allValues.subList(requestDto.getResultOffset(), lastIndex);
  }

  private AggregationBuilder getVariableValueAggregation(final DecisionVariableValueRequestDto requestDto,
                                                         final String variablePath) {
    final TermsAggregationBuilder collectAllVariableValues =
      terms(VALUE_AGGREGATION)
        .field(getVariableValueFieldForType(variablePath, requestDto.getVariableType()))
        .size(MAX_RESPONSE_SIZE_LIMIT)
        .order(BucketOrder.key(true));

    final FilterAggregationBuilder filterForVariableWithGivenIdAndPrefix = getVariableValueFilterAggregation(
      requestDto.getVariableId(), variablePath, requestDto.getValueFilter()
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
      .must(termQuery(getVariableClauseIdField(variablePath), variableId));

    addValueFilter(variablePath, valueFilter, filterQuery);

    return filter(FILTERED_VARIABLES_AGGREGATION, filterQuery);
  }

  private void addValueFilter(final String variablePath, final String valueFilter,
                              final BoolQueryBuilder filterQuery) {
    if (valueFilter != null && !valueFilter.isEmpty()) {
      final String lowerCaseValue = valueFilter.toLowerCase();
      QueryBuilder filter = (lowerCaseValue.length() > IndexSettingsBuilder.MAX_GRAM)
          /*
            using the slow wildcard query for uncommonly large filter strings (> 10 chars)
          */
        ? wildcardQuery(
        getValueSearchField(variablePath, VARIABLE_VALUE_LOWERCASE),
        buildWildcardQuery(lowerCaseValue)
      )
          /*
            using Elasticsearch ngrams to filter for strings < 10 chars,
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
