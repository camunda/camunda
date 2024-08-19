/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static io.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static io.camunda.optimize.dto.optimize.ReportConstants.APPLIED_TO_ALL_DEFINITIONS;
import static io.camunda.optimize.service.db.DatabaseConstants.MAX_GRAM;
import static io.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.LOWERCASE_FIELD;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.N_GRAM_FIELD;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;
import static io.camunda.optimize.service.util.InstanceIndexUtil.isInstanceIndexNotFoundException;
import static io.camunda.optimize.service.util.ProcessVariableHelper.buildWildcardQuery;
import static io.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameField;
import static io.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableTypeField;
import static io.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableValueFieldForType;
import static io.camunda.optimize.service.util.ProcessVariableHelper.getValueSearchField;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.wildcardQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

import io.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import io.camunda.optimize.dto.optimize.query.variable.LabelDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessToQueryDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableSourceDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableValuesQueryDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.db.es.ElasticsearchCompositeAggregationScroller;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.filter.FilterContext;
import io.camunda.optimize.service.db.es.filter.ProcessQueryFilterEnhancer;
import io.camunda.optimize.service.db.es.schema.index.ProcessInstanceIndexES;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.reader.ProcessVariableReader;
import io.camunda.optimize.service.db.reader.VariableLabelReader;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.DefinitionQueryUtilES;
import io.camunda.optimize.service.util.InstanceIndexUtil;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
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
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessVariableReaderES implements ProcessVariableReader {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(ProcessVariableReaderES.class);
  private final OptimizeElasticsearchClient esClient;
  private final ProcessDefinitionReader processDefinitionReader;
  private final ConfigurationService configurationService;
  private final VariableLabelReader variableLabelReader;
  private final ProcessQueryFilterEnhancer processQueryFilterEnhancer;

  public ProcessVariableReaderES(
      final OptimizeElasticsearchClient esClient,
      final ProcessDefinitionReader processDefinitionReader,
      final ConfigurationService configurationService,
      final VariableLabelReader variableLabelReader,
      final ProcessQueryFilterEnhancer processQueryFilterEnhancer) {
    this.esClient = esClient;
    this.processDefinitionReader = processDefinitionReader;
    this.configurationService = configurationService;
    this.variableLabelReader = variableLabelReader;
    this.processQueryFilterEnhancer = processQueryFilterEnhancer;
  }

  @Override
  public List<ProcessVariableNameResponseDto> getVariableNames(
      final ProcessVariableNameRequestDto variableNameRequest) {
    final Map<String, List<String>> logEntries = new HashMap<>();
    variableNameRequest
        .getProcessesToQuery()
        .forEach(
            processToQuery -> {
              logEntries.put(
                  processToQuery.getProcessDefinitionKey(),
                  processToQuery.getProcessDefinitionVersions());
            });
    log.debug("Fetching variable names for {definitionKey=[versions]}: [{}]", logEntries);

    final List<ProcessToQueryDto> validNameRequests =
        variableNameRequest.getProcessesToQuery().stream()
            .filter(request -> request.getProcessDefinitionKey() != null)
            .filter(request -> !CollectionUtils.isEmpty(request.getProcessDefinitionVersions()))
            .toList();
    if (validNameRequests.isEmpty()) {
      log.debug(
          "Cannot fetch variable names as no valid variable requests are provided. "
              + "Variable requests must include definition key and version.");
      return Collections.emptyList();
    }

    final List<String> processDefinitionKeys =
        validNameRequests.stream()
            .map(ProcessToQueryDto::getProcessDefinitionKey)
            .distinct()
            .toList();

    final Map<String, DefinitionVariableLabelsDto> definitionLabelsDtos =
        variableLabelReader.getVariableLabelsByKey(processDefinitionKeys);

    final BoolQueryBuilder query = boolQuery().minimumShouldMatch(1);
    validNameRequests.forEach(
        request ->
            query.should(
                DefinitionQueryUtilES.createDefinitionQuery(
                    request.getProcessDefinitionKey(),
                    request.getProcessDefinitionVersions(),
                    request.getTenantIds(),
                    new ProcessInstanceIndexES(request.getProcessDefinitionKey()),
                    processDefinitionReader::getLatestVersionToKey)));

    processQueryFilterEnhancer.addFilterToQuery(
        query,
        variableNameRequest.getFilter().stream()
            .filter(filter -> filter.getAppliedTo().contains(APPLIED_TO_ALL_DEFINITIONS))
            .toList(),
        FilterContext.builder().timezone(variableNameRequest.getTimezone()).build());

    return getVariableNamesForInstancesMatchingQuery(
        processDefinitionKeys, query, definitionLabelsDtos);
  }

  @Override
  public List<ProcessVariableNameResponseDto> getVariableNamesForInstancesMatchingQuery(
      final List<String> processDefinitionKeysToTarget,
      final BoolQueryBuilder baseQuery,
      final Map<String, DefinitionVariableLabelsDto> definitionLabelsDtos) {
    final List<CompositeValuesSourceBuilder<?>> variableNameAndTypeTerms = new ArrayList<>();
    variableNameAndTypeTerms.add(
        new TermsValuesSourceBuilder(NAME_AGGREGATION).field(getNestedVariableNameField()));
    variableNameAndTypeTerms.add(
        new TermsValuesSourceBuilder(TYPE_AGGREGATION).field(getNestedVariableTypeField()));
    variableNameAndTypeTerms.add(
        new TermsValuesSourceBuilder(INDEX_AGGREGATION).field(INDEX_AGGREGATION));

    final CompositeAggregationBuilder varNameAndTypeAgg =
        new CompositeAggregationBuilder(VAR_NAME_AND_TYPE_COMPOSITE_AGG, variableNameAndTypeTerms)
            .size(configurationService.getElasticSearchConfiguration().getAggregationBucketLimit());

    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(baseQuery)
            .aggregation(nested(VARIABLES, VARIABLES).subAggregation(varNameAndTypeAgg))
            .size(0);

    final String[] indicesToTarget =
        processDefinitionKeysToTarget.stream()
            .map(InstanceIndexUtil::getProcessInstanceIndexAliasName)
            .toArray(String[]::new);
    final SearchRequest searchRequest =
        new SearchRequest(indicesToTarget).source(searchSourceBuilder);

    final List<ProcessVariableNameResponseDto> variableNames = new ArrayList<>();
    ElasticsearchCompositeAggregationScroller.create()
        .setEsClient(esClient)
        .setSearchRequest(searchRequest)
        .setPathToAggregation(VARIABLES, VAR_NAME_AND_TYPE_COMPOSITE_AGG)
        .setCompositeBucketConsumer(
            bucket -> variableNames.add(extractVariableNameAndLabel(bucket, definitionLabelsDtos)))
        .consumeAllPages();
    return filterVariableNameResults(variableNames);
  }

  @Override
  public String extractProcessDefinitionKeyFromIndexName(final String indexName) {
    final int firstIndex = indexName.indexOf(PROCESS_INSTANCE_INDEX_NAME_SUBSECTION);
    final int lastIndex = indexName.lastIndexOf(PROCESS_INSTANCE_INDEX_NAME_SUBSECTION);
    if (firstIndex != lastIndex) {
      log.warn(
          "Skipping fetching variables for process definition with index name: {}.", indexName);
      return null;
    }

    final int processDefKeyStartIndex =
        firstIndex + PROCESS_INSTANCE_INDEX_NAME_SUBSECTION.length();
    final int processDefKeyEndIndex = indexName.lastIndexOf("_v" + ProcessInstanceIndex.VERSION);
    return indexName.substring(processDefKeyStartIndex, processDefKeyEndIndex);
  }

  @Override
  public List<String> getVariableValues(final ProcessVariableValuesQueryDto requestDto) {
    final List<ProcessVariableSourceDto> processVariableSources =
        requestDto.getProcessVariableSources().stream()
            .filter(source -> !CollectionUtils.isEmpty(source.getProcessDefinitionVersions()))
            .collect(Collectors.toList());
    if (processVariableSources.isEmpty()) {
      log.debug("Cannot fetch variable values for process definition with missing versions.");
      return Collections.emptyList();
    }

    log.debug("Fetching input variable values from sources [{}]", processVariableSources);

    final BoolQueryBuilder query = boolQuery();
    processVariableSources.forEach(
        source -> {
          query.should(
              DefinitionQueryUtilES.createDefinitionQuery(
                  source.getProcessDefinitionKey(),
                  source.getProcessDefinitionVersions(),
                  source.getTenantIds(),
                  new ProcessInstanceIndexES(source.getProcessDefinitionKey()),
                  processDefinitionReader::getLatestVersionToKey));
          if (source.getProcessInstanceId() != null) {
            query.must(
                termQuery(ProcessInstanceIndex.PROCESS_INSTANCE_ID, source.getProcessInstanceId()));
          }
        });

    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(query)
            .aggregation(getVariableValueAggregation(requestDto))
            .size(0);

    final SearchRequest searchRequest =
        new SearchRequest(PROCESS_INSTANCE_MULTI_ALIAS).source(searchSourceBuilder);

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest);
      final Aggregations aggregations = searchResponse.getAggregations();

      return extractVariableValues(aggregations, requestDto);
    } catch (final IOException e) {
      final String reason =
          String.format(
              "Was not able to fetch values for variable [%s] with type [%s] ",
              requestDto.getName(), requestDto.getType().getId());
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    } catch (final ElasticsearchStatusException e) {
      if (isInstanceIndexNotFoundException(PROCESS, e)) {
        log.info(
            "Was not able to fetch variable values because no instance indices exist. Returning empty list.");
        return Collections.emptyList();
      }
      throw e;
    }
  }

  private List<ProcessVariableNameResponseDto> filterVariableNameResults(
      final List<ProcessVariableNameResponseDto> variableNames) {
    // Exclude object variables from this result as they are only visible in raw data reports.
    // Additionally, in case variables have the same name, type and label across definitions
    // then we eliminate duplicates and we display the variable as one.
    return variableNames.stream()
        .distinct()
        .filter(varName -> !VariableType.OBJECT.equals(varName.getType()))
        .collect(Collectors.toList());
  }

  private ProcessVariableNameResponseDto extractVariableNameAndLabel(
      final ParsedComposite.ParsedBucket bucket,
      final Map<String, DefinitionVariableLabelsDto> definitionLabelsByKey) {
    final String processDefinitionKey =
        extractProcessDefinitionKeyFromIndexName(((String) bucket.getKey().get(INDEX_AGGREGATION)));
    final String variableName = (String) (bucket.getKey()).get(NAME_AGGREGATION);
    final String variableType = (String) (bucket.getKey().get(TYPE_AGGREGATION));
    String labelValue = null;
    if (processDefinitionKey != null && definitionLabelsByKey.containsKey(processDefinitionKey)) {
      final List<LabelDto> labels = definitionLabelsByKey.get(processDefinitionKey).getLabels();
      for (final LabelDto label : labels) {
        if (label.getVariableName().equals(variableName)
            && label.getVariableType().toString().equalsIgnoreCase(variableType)) {
          labelValue = label.getVariableLabel();
        }
      }
    }

    return new ProcessVariableNameResponseDto(
        variableName, VariableType.getTypeForId(variableType), labelValue);
  }

  private List<String> extractVariableValues(
      final Aggregations aggregations, final ProcessVariableValuesQueryDto requestDto) {
    final Nested variablesFromType = aggregations.get(VARIABLES);
    final Filter filteredVariables =
        variablesFromType.getAggregations().get(FILTERED_VARIABLES_AGGREGATION);
    final Terms valueTerms = filteredVariables.getAggregations().get(VALUE_AGGREGATION);
    final List<String> allValues = new ArrayList<>();
    for (final Terms.Bucket valueBucket : valueTerms.getBuckets()) {
      allValues.add(valueBucket.getKeyAsString());
    }
    final int lastIndex =
        Math.min(allValues.size(), requestDto.getResultOffset() + requestDto.getNumResults());
    return allValues.subList(requestDto.getResultOffset(), lastIndex);
  }

  private AggregationBuilder getVariableValueAggregation(
      final ProcessVariableValuesQueryDto requestDto) {
    final TermsAggregationBuilder collectAllVariableValues =
        terms(VALUE_AGGREGATION)
            .field(getNestedVariableValueFieldForType(requestDto.getType()))
            .size(MAX_RESPONSE_SIZE_LIMIT)
            .order(BucketOrder.key(true));

    final FilterAggregationBuilder filterForVariableWithGivenIdAndPrefix =
        getVariableValueFilterAggregation(
            requestDto.getName(), requestDto.getType(), requestDto.getValueFilter());

    return nested(VARIABLES, VARIABLES)
        .subAggregation(
            filterForVariableWithGivenIdAndPrefix.subAggregation(collectAllVariableValues));
  }

  private FilterAggregationBuilder getVariableValueFilterAggregation(
      final String variableName, final VariableType type, final String valueFilter) {
    final BoolQueryBuilder filterQuery =
        boolQuery()
            .must(termQuery(getNestedVariableNameField(), variableName))
            .must(termQuery(getNestedVariableTypeField(), type.getId()));

    addValueFilter(type, valueFilter, filterQuery);

    return filter(FILTERED_VARIABLES_AGGREGATION, filterQuery);
  }

  private void addValueFilter(
      final VariableType variableType,
      final String valueFilter,
      final BoolQueryBuilder filterQuery) {
    final boolean isStringVariable = VariableType.STRING.equals(variableType);
    final boolean valueFilterIsConfigured = valueFilter != null && !valueFilter.isEmpty();
    if (isStringVariable && valueFilterIsConfigured) {
      final String lowerCaseValue = valueFilter.toLowerCase(Locale.ENGLISH);
      final QueryBuilder filter =
          (lowerCaseValue.length() > MAX_GRAM)
              /*
                using the slow wildcard query for uncommonly large filter strings (> 10 chars)
              */
              ? wildcardQuery(
                  getValueSearchField(LOWERCASE_FIELD), buildWildcardQuery(lowerCaseValue))
              /*
                using Elasticsearch ngrams to filter for strings < 10 chars,
                because it's fast but increasing the number of chars makes the index bigger
              */
              : termQuery(getValueSearchField(N_GRAM_FIELD), lowerCaseValue);

      filterQuery.must(filter);
    }
  }
}
