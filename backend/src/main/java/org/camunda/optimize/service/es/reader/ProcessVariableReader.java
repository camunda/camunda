/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.reader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import org.camunda.optimize.dto.optimize.query.variable.LabelDto;
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
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
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
import java.util.Map;
import java.util.stream.Collectors;

import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.LOWERCASE_FIELD;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.N_GRAM_FIELD;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.util.InstanceIndexUtil.isInstanceIndexNotFoundException;
import static org.camunda.optimize.service.util.ProcessVariableHelper.buildWildcardQuery;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameField;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableTypeField;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableValueFieldForType;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getValueSearchField;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;
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
  private static final String INDEX_AGGREGATION = "_index";
  private static final String PROCESS_INSTANCE_INDEX_NAME_SUBSECTION =
    "-" + ElasticsearchConstants.PROCESS_INSTANCE_INDEX_PREFIX;

  private final OptimizeElasticsearchClient esClient;
  private final ProcessDefinitionReader processDefinitionReader;
  private final ConfigurationService configurationService;
  private final VariableLabelReader variableLabelReader;

  public List<ProcessVariableNameResponseDto> getVariableNames(ProcessVariableNameRequestDto requestDto) {
    log.debug(
      "Fetching variable names for process definition with key [{}] and versions [{}]",
      requestDto.getProcessDefinitionKey(),
      requestDto.getProcessDefinitionVersions()
    );

    return getVariableNames(Collections.singletonList(requestDto));
  }

  public List<ProcessVariableNameResponseDto> getVariableNames(final List<ProcessVariableNameRequestDto> variableNameRequests) {
    final List<ProcessVariableNameRequestDto> validNameRequests = variableNameRequests
      .stream()
      .filter(request -> request.getProcessDefinitionKey() != null)
      .filter(request -> !CollectionUtils.isEmpty(request.getProcessDefinitionVersions()))
      .collect(Collectors.toList());
    if (validNameRequests.isEmpty()) {
      log.debug(
        "Cannot fetch variable names as no valid variable requests are provided. " +
          "Variable requests must include definition key and version.");
      return Collections.emptyList();
    }

    List<String> processDefinitionKeys = validNameRequests.stream()
      .map(ProcessVariableNameRequestDto::getProcessDefinitionKey)
      .distinct()
      .collect(Collectors.toList());
    Map<String, DefinitionVariableLabelsDto> definitionLabelsDtos =
      variableLabelReader.getVariableLabelsByKey(processDefinitionKeys);

    BoolQueryBuilder query = boolQuery();
    validNameRequests.forEach(request ->
                                query.should(DefinitionQueryUtil.createDefinitionQuery(
                                  request.getProcessDefinitionKey(),
                                  request.getProcessDefinitionVersions(),
                                  request.getTenantIds(),
                                  new ProcessInstanceIndex(request.getProcessDefinitionKey()),
                                  processDefinitionReader::getLatestVersionToKey
                                )));
    return getVariableNamesForInstancesMatchingQuery(query, definitionLabelsDtos);
  }

  public List<ProcessVariableNameResponseDto> getVariableNamesForInstancesMatchingQuery(final BoolQueryBuilder baseQuery,
                                                                                        final Map<String,
                                                                                          DefinitionVariableLabelsDto> definitionLabelsDtos) {
    List<CompositeValuesSourceBuilder<?>> variableNameAndTypeTerms = new ArrayList<>();
    variableNameAndTypeTerms.add(new TermsValuesSourceBuilder(NAME_AGGREGATION)
                                   .field(getNestedVariableNameField()));
    variableNameAndTypeTerms.add(new TermsValuesSourceBuilder(TYPE_AGGREGATION)
                                   .field(getNestedVariableTypeField()));
    variableNameAndTypeTerms.add(new TermsValuesSourceBuilder(INDEX_AGGREGATION)
                                   .field(INDEX_AGGREGATION));

    CompositeAggregationBuilder varNameAndTypeAgg =
      new CompositeAggregationBuilder(VAR_NAME_AND_TYPE_COMPOSITE_AGG, variableNameAndTypeTerms)
        .size(configurationService.getEsAggregationBucketLimit());

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(baseQuery)
      .aggregation(nested(VARIABLES, VARIABLES).subAggregation(varNameAndTypeAgg))
      .size(0);

    SearchRequest searchRequest = new SearchRequest(PROCESS_INSTANCE_MULTI_ALIAS)
      .source(searchSourceBuilder);

    List<ProcessVariableNameResponseDto> variableNames = new ArrayList<>();
    CompositeAggregationScroller.create()
      .setEsClient(esClient)
      .setSearchRequest(searchRequest)
      .setPathToAggregation(VARIABLES, VAR_NAME_AND_TYPE_COMPOSITE_AGG)
      .setCompositeBucketConsumer(bucket -> variableNames.add(extractVariableNameAndLabel(
        bucket,
        definitionLabelsDtos
      )))
      .consumeAllPages();
    return filterVariableNameResults(variableNames);
  }

  private List<ProcessVariableNameResponseDto> filterVariableNameResults(final List<ProcessVariableNameResponseDto> variableNames) {
    // Exclude object variables from this result as they are only visible in raw data reports.
    // Additionally, in case variables have the same name, type and label across definitions
    // then we eliminate duplicates and we display the variable as one.
    return variableNames.stream()
      .distinct()
      .filter(varName -> !VariableType.OBJECT.equals(varName.getType()))
      .collect(Collectors.toList());
  }

  private ProcessVariableNameResponseDto extractVariableNameAndLabel(final ParsedComposite.ParsedBucket bucket,
                                                                     final Map<String, DefinitionVariableLabelsDto> definitionLabelsByKey) {
    final String processDefinitionKey = extractProcessDefinitionKeyFromIndexName(((String) bucket.getKey()
      .get(INDEX_AGGREGATION)));
    final String variableName = (String) (bucket.getKey()).get(NAME_AGGREGATION);
    final String variableType = (String) (bucket.getKey().get(TYPE_AGGREGATION));
    String labelValue = null;
    if (processDefinitionKey != null && definitionLabelsByKey.containsKey(processDefinitionKey)) {
      final List<LabelDto> labels = definitionLabelsByKey.get(processDefinitionKey).getLabels();
      for (LabelDto label : labels) {
        if (label.getVariableName().equals(variableName) && label.getVariableType()
          .toString()
          .equalsIgnoreCase(variableType)) {
          labelValue = label.getVariableLabel();
        }
      }
    }

    return new ProcessVariableNameResponseDto(
      variableName,
      VariableType.getTypeForId(variableType),
      labelValue
    );
  }

  public String extractProcessDefinitionKeyFromIndexName(final String indexName) {
    int firstIndex = indexName.indexOf(PROCESS_INSTANCE_INDEX_NAME_SUBSECTION);
    int lastIndex = indexName.lastIndexOf(PROCESS_INSTANCE_INDEX_NAME_SUBSECTION);
    if (firstIndex != lastIndex) {
      log.warn("Skipping fetching variables for process definition with index name: {}.", indexName);
      return null;
    }

    final int processDefKeyStartIndex = firstIndex + PROCESS_INSTANCE_INDEX_NAME_SUBSECTION.length();
    final int processDefKeyEndIndex = indexName.lastIndexOf("_v" + ProcessInstanceIndex.VERSION);
    return indexName.substring(processDefKeyStartIndex, processDefKeyEndIndex);
  }

  public List<String> getVariableValues(final ProcessVariableValuesQueryDto requestDto) {
    final List<ProcessVariableSourceDto> processVariableSources = requestDto.getProcessVariableSources()
      .stream()
      .filter(source -> !CollectionUtils.isEmpty(source.getProcessDefinitionVersions()))
      .collect(Collectors.toList());
    if (processVariableSources.isEmpty()) {
      log.debug("Cannot fetch variable values for process definition with missing versions.");
      return Collections.emptyList();
    }

    log.debug("Fetching input variable values from sources [{}]", processVariableSources);

    final BoolQueryBuilder query = boolQuery();
    processVariableSources
      .forEach(source -> {
        query.should(DefinitionQueryUtil.createDefinitionQuery(
          source.getProcessDefinitionKey(),
          source.getProcessDefinitionVersions(),
          source.getTenantIds(),
          new ProcessInstanceIndex(source.getProcessDefinitionKey()),
          processDefinitionReader::getLatestVersionToKey
        ));
        if (source.getProcessInstanceId() != null) {
          query.must(termQuery(ProcessInstanceIndex.PROCESS_INSTANCE_ID, source.getProcessInstanceId()));
        }
      });

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .aggregation(getVariableValueAggregation(requestDto))
      .size(0);

    final SearchRequest searchRequest =
      new SearchRequest(PROCESS_INSTANCE_MULTI_ALIAS).source(searchSourceBuilder);

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest);
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
        log.info("Was not able to fetch variable values because no instance indices exist. Returning empty list.");
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

}
