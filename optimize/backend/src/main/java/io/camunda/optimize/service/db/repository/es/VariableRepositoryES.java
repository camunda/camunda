/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import static io.camunda.optimize.dto.optimize.DefinitionType.DECISION;
import static io.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static io.camunda.optimize.dto.optimize.ReportConstants.APPLIED_TO_ALL_DEFINITIONS;
import static io.camunda.optimize.service.db.DatabaseConstants.EXTERNAL_PROCESS_VARIABLE_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.MAX_GRAM;
import static io.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.db.DatabaseConstants.VARIABLE_LABEL_INDEX_NAME;
import static io.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil.createDefaultScriptWithSpecificDtoParams;
import static io.camunda.optimize.service.db.schema.index.AbstractInstanceIndex.LOWERCASE_FIELD;
import static io.camunda.optimize.service.db.schema.index.AbstractInstanceIndex.N_GRAM_FIELD;
import static io.camunda.optimize.service.db.schema.index.ExternalProcessVariableIndex.INGESTION_TIMESTAMP;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;
import static io.camunda.optimize.service.db.schema.index.VariableUpdateInstanceIndex.TIMESTAMP;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableClauseIdField;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableValueFieldForType;
import static io.camunda.optimize.service.util.DefinitionQueryUtilES.createDefinitionQuery;
import static io.camunda.optimize.service.util.ExceptionUtil.isInstanceIndexNotFoundException;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getDecisionInstanceIndexAliasName;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static io.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameField;
import static io.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableTypeField;
import static io.camunda.optimize.util.LogUtil.sanitizeLogMessage;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.elasticsearch.core.TimeValue.timeValueSeconds;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.index.query.QueryBuilders.wildcardQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;
import static org.elasticsearch.search.sort.SortOrder.ASC;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.variable.DecisionVariableValueRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import io.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessToQueryDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableSourceDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableValuesQueryDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.dto.optimize.query.variable.VariableUpdateInstanceDto;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.es.ElasticsearchCompositeAggregationScroller;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.filter.ProcessQueryFilterEnhancer;
import io.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil;
import io.camunda.optimize.service.db.es.schema.index.DecisionInstanceIndexES;
import io.camunda.optimize.service.db.es.schema.index.ExternalProcessVariableIndexES;
import io.camunda.optimize.service.db.es.schema.index.ProcessInstanceIndexES;
import io.camunda.optimize.service.db.es.schema.index.VariableUpdateInstanceIndexES;
import io.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.db.reader.DecisionDefinitionReader;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.repository.VariableRepository;
import io.camunda.optimize.service.db.repository.script.ProcessInstanceScriptFactory;
import io.camunda.optimize.service.db.schema.ScriptData;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import io.camunda.optimize.service.db.schema.index.VariableUpdateInstanceIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.DefinitionQueryUtilES;
import io.camunda.optimize.service.util.InstanceIndexUtil;
import io.camunda.optimize.service.util.ProcessVariableHelper;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.script.Script;
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
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class VariableRepositoryES implements VariableRepository {
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;
  private final ConfigurationService configurationService;
  private final DateTimeFormatter dateTimeFormatter;
  private final DecisionDefinitionReader decisionDefinitionReader;
  private final ProcessQueryFilterEnhancer processQueryFilterEnhancer;
  private final ProcessDefinitionReader processDefinitionReader;

  @Override
  public void deleteVariableDataByProcessInstanceIds(
      final String processDefinitionKey, final List<String> processInstanceIds) {
    final BulkRequest bulkRequest = new BulkRequest().setRefreshPolicy(IMMEDIATE);
    processInstanceIds.forEach(
        id ->
            bulkRequest.add(
                new UpdateRequest(getProcessInstanceIndexAliasName(processDefinitionKey), id)
                    .script(new Script(ProcessInstanceScriptFactory.createVariableClearScript()))
                    .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)));
    esClient.doBulkRequest(
        bulkRequest, getProcessInstanceIndexAliasName(processDefinitionKey), false);
  }

  @Override
  public void upsertVariableLabel(
      final String variableLabelIndexName,
      final DefinitionVariableLabelsDto definitionVariableLabelsDto,
      final ScriptData scriptData) {
    final Script updateEntityScript =
        createDefaultScriptWithSpecificDtoParams(
            scriptData.scriptString(), scriptData.params(), objectMapper);
    try {
      final UpdateRequest updateRequest =
          new UpdateRequest()
              .index(variableLabelIndexName)
              .id(definitionVariableLabelsDto.getDefinitionKey().toLowerCase(Locale.ENGLISH))
              .upsert(
                  objectMapper.writeValueAsString(definitionVariableLabelsDto), XContentType.JSON)
              .script(updateEntityScript)
              .setRefreshPolicy(IMMEDIATE)
              .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

      esClient.update(updateRequest);
    } catch (final IOException e) {
      final String errorMessage =
          String.format(
              "Was not able to update the variable labels for the process definition with id: [%s]",
              definitionVariableLabelsDto.getDefinitionKey());
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (final ElasticsearchStatusException e) {
      final String errorMessage =
          String.format(
              "Was not able to update the variable labels for the process definition with id: [%s] due to an Elasticsearch"
                  + " exception",
              definitionVariableLabelsDto.getDefinitionKey());
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  @Override
  public void deleteVariablesForDefinition(
      final String variableLabelIndexName, final String processDefinitionKey) {
    final DeleteRequest request =
        new DeleteRequest(variableLabelIndexName)
            .id(processDefinitionKey)
            .setRefreshPolicy(IMMEDIATE);

    try {
      esClient.delete(request);
    } catch (final IOException e) {
      final String errorMessage =
          String.format(
              "Could not delete variable label document with id [%s]. ", processDefinitionKey);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  @Override
  public void deleteByProcessInstanceIds(final List<String> processInstanceIds) {
    final BoolQueryBuilder filterQuery =
        boolQuery()
            .filter(
                termsQuery(VariableUpdateInstanceIndex.PROCESS_INSTANCE_ID, processInstanceIds));

    ElasticsearchWriterUtil.tryDeleteByQueryRequest(
        esClient,
        filterQuery,
        String.format("variable updates of %d process instances", processInstanceIds.size()),
        false,
        // use wildcarded index name to catch all indices that exist after potential rollover
        esClient
            .getIndexNameService()
            .getOptimizeIndexNameWithVersionWithWildcardSuffix(
                new VariableUpdateInstanceIndexES()));
  }

  @Override
  public Map<String, DefinitionVariableLabelsDto> getVariableLabelsByKey(
      final List<String> processDefinitionKeys) {
    final MultiGetRequest multiGetRequest = new MultiGetRequest();
    processDefinitionKeys.forEach(
        processDefinitionKey ->
            multiGetRequest.add(
                new MultiGetRequest.Item(
                    VARIABLE_LABEL_INDEX_NAME, processDefinitionKey.toLowerCase(Locale.ENGLISH))));
    try {
      return Arrays.stream(esClient.mget(multiGetRequest).getResponses())
          .map(this::extractDefinitionLabelsDto)
          .flatMap(Optional::stream)
          .peek(
              label -> label.setDefinitionKey(label.getDefinitionKey().toLowerCase(Locale.ENGLISH)))
          .collect(
              Collectors.toMap(DefinitionVariableLabelsDto::getDefinitionKey, Function.identity()));
    } catch (final IOException e) {
      final String errorMessage =
          String.format(
              "There was an error while fetching documents from the variable label index with keys %s.",
              processDefinitionKeys);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  @Override
  public List<VariableUpdateInstanceDto> getVariableInstanceUpdatesForProcessInstanceIds(
      final Set<String> processInstanceIds) {

    final BoolQueryBuilder query =
        boolQuery()
            .must(termsQuery(VariableUpdateInstanceIndex.PROCESS_INSTANCE_ID, processInstanceIds));

    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(query)
            .sort(SortBuilders.fieldSort(TIMESTAMP).order(ASC))
            .size(MAX_RESPONSE_SIZE_LIMIT);
    final SearchRequest searchRequest =
        new SearchRequest(DatabaseConstants.VARIABLE_UPDATE_INSTANCE_INDEX_NAME)
            .source(searchSourceBuilder)
            .scroll(
                timeValueSeconds(
                    configurationService
                        .getElasticSearchConfiguration()
                        .getScrollTimeoutInSeconds()));

    final SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (final IOException e) {
      log.error("Was not able to retrieve variable instance updates!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve variable instance updates!", e);
    }

    return ElasticsearchReaderUtil.retrieveAllScrollResults(
        searchResponse,
        VariableUpdateInstanceDto.class,
        objectMapper,
        esClient,
        configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds());
  }

  @Override
  public void writeExternalProcessVariables(
      final List<ExternalProcessVariableDto> variables, final String itemName) {
    final BulkRequest bulkRequest = new BulkRequest();
    variables.forEach(variable -> addInsertExternalVariableRequest(bulkRequest, variable));

    esClient.doBulkRequest(
        bulkRequest,
        itemName,
        false // there are no nested documents in the externalProcessVariableIndex
        );
  }

  @Override
  public void deleteExternalVariablesIngestedBefore(
      final OffsetDateTime timestamp, final String deletedItemIdentifier) {

    final BoolQueryBuilder filterQuery =
        boolQuery()
            .filter(
                rangeQuery(ExternalProcessVariableDto.Fields.ingestionTimestamp)
                    .lt(dateTimeFormatter.format(timestamp)));

    ElasticsearchWriterUtil.tryDeleteByQueryRequest(
        esClient,
        filterQuery,
        deletedItemIdentifier,
        false,
        // use wildcarded index name to catch all indices that exist after potential rollover
        esClient
            .getIndexNameService()
            .getOptimizeIndexNameWithVersionWithWildcardSuffix(
                new ExternalProcessVariableIndexES()));
  }

  @Override
  public List<ExternalProcessVariableDto> getVariableUpdatesIngestedAfter(
      final Long ingestTimestamp, final int limit) {
    final RangeQueryBuilder timestampQuery = rangeQuery(INGESTION_TIMESTAMP).gt(ingestTimestamp);
    return getPageOfVariablesSortedByIngestionTimestamp(timestampQuery, limit);
  }

  @Override
  public List<ExternalProcessVariableDto> getVariableUpdatesIngestedAt(final Long ingestTimestamp) {
    final RangeQueryBuilder timestampQuery =
        rangeQuery(INGESTION_TIMESTAMP).lte(ingestTimestamp).gte(ingestTimestamp);
    return getPageOfVariablesSortedByIngestionTimestamp(timestampQuery, MAX_RESPONSE_SIZE_LIMIT);
  }

  @Override
  public List<String> getDecisionVariableValues(
      final DecisionVariableValueRequestDto requestDto, final String variablesPath) {
    final BoolQueryBuilder query =
        createDefinitionQuery(
            requestDto.getDecisionDefinitionKey(),
            requestDto.getDecisionDefinitionVersions(),
            requestDto.getTenantIds(),
            new DecisionInstanceIndexES(requestDto.getDecisionDefinitionKey()),
            decisionDefinitionReader::getLatestVersionToKey);

    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(query)
            .aggregation(getDecisionVariableValueAggregation(requestDto, variablesPath))
            .size(0);

    final SearchRequest searchRequest =
        new SearchRequest(getDecisionInstanceIndexAliasName(requestDto.getDecisionDefinitionKey()))
            .source(searchSourceBuilder);

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest);
      final Aggregations aggregations = searchResponse.getAggregations();

      return extractVariableValues(aggregations, requestDto, variablesPath);
    } catch (final IOException e) {
      final String reason =
          String.format(
              "Was not able to fetch values for variable [%s] with type [%s] ",
              requestDto.getVariableId(), requestDto.getVariableType());
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    } catch (final ElasticsearchStatusException e) {
      if (isInstanceIndexNotFoundException(DECISION, e)) {
        log.info(
            "Was not able to fetch variable values because no instance index with alias {} exists. "
                + "Returning empty list.",
            getDecisionInstanceIndexAliasName(requestDto.getDecisionDefinitionKey()));
        return Collections.emptyList();
      }
      throw e;
    }
  }

  @Override
  public List<ProcessVariableNameResponseDto> getVariableNames(
      final ProcessVariableNameRequestDto variableNameRequest,
      final List<ProcessToQueryDto> validNameRequests,
      final List<String> processDefinitionKeys,
      final Map<String, DefinitionVariableLabelsDto> definitionLabelsDtos) {
    BoolQueryBuilder query = boolQuery().minimumShouldMatch(1);
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
    List<CompositeValuesSourceBuilder<?>> variableNameAndTypeTerms = new ArrayList<>();
    variableNameAndTypeTerms.add(
        new TermsValuesSourceBuilder(NAME_AGGREGATION).field(getNestedVariableNameField()));
    variableNameAndTypeTerms.add(
        new TermsValuesSourceBuilder(TYPE_AGGREGATION).field(getNestedVariableTypeField()));
    variableNameAndTypeTerms.add(
        new TermsValuesSourceBuilder(INDEX_AGGREGATION).field(INDEX_AGGREGATION));

    CompositeAggregationBuilder varNameAndTypeAgg =
        new CompositeAggregationBuilder(VAR_NAME_AND_TYPE_COMPOSITE_AGG, variableNameAndTypeTerms)
            .size(configurationService.getElasticSearchConfiguration().getAggregationBucketLimit());

    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(baseQuery)
            .aggregation(nested(VARIABLES, VARIABLES).subAggregation(varNameAndTypeAgg))
            .size(0);

    String[] indicesToTarget =
        processDefinitionKeysToTarget.stream()
            .map(InstanceIndexUtil::getProcessInstanceIndexAliasName)
            .toArray(String[]::new);
    SearchRequest searchRequest = new SearchRequest(indicesToTarget).source(searchSourceBuilder);

    List<ProcessVariableNameResponseDto> variableNames = new ArrayList<>();
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
  public List<String> getVariableValues(
      final ProcessVariableValuesQueryDto requestDto,
      final List<ProcessVariableSourceDto> processVariableSources) {
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
    } catch (IOException e) {
      final String reason =
          String.format(
              "Was not able to fetch values for variable [%s] with type [%s] ",
              requestDto.getName(), requestDto.getType().getId());
      log.error(sanitizeLogMessage(reason), e);
      throw new OptimizeRuntimeException(reason, e);
    } catch (ElasticsearchStatusException e) {
      if (isInstanceIndexNotFoundException(PROCESS, e)) {
        log.info(
            "Was not able to fetch variable values because no instance indices exist. Returning empty list.");
        return Collections.emptyList();
      }
      throw e;
    }
  }

  private ProcessVariableNameResponseDto extractVariableNameAndLabel(
      final ParsedComposite.ParsedBucket bucket,
      final Map<String, DefinitionVariableLabelsDto> definitionLabelsByKey) {
    final String processDefinitionKey =
        extractProcessDefinitionKeyFromIndexName(((String) bucket.getKey().get(INDEX_AGGREGATION)));
    final String variableName = (String) (bucket.getKey().get(NAME_AGGREGATION));
    final String variableType = (String) (bucket.getKey().get(TYPE_AGGREGATION));
    return processVariableNameResponseDtoFrom(
        definitionLabelsByKey, processDefinitionKey, variableName, variableType);
  }

  private List<String> extractVariableValues(
      final Aggregations aggregations, final ProcessVariableValuesQueryDto requestDto) {
    return extractVariableValues(
        aggregations, requestDto.getResultOffset(), requestDto.getNumResults(), VARIABLES);
  }

  private List<String> extractVariableValues(
      final Aggregations aggregations,
      final DecisionVariableValueRequestDto requestDto,
      final String variableFieldLabel) {
    final Nested variablesFromType = aggregations.get(variableFieldLabel);
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

  private List<String> extractVariableValues(
      final Aggregations aggregations,
      final Integer resultOffset,
      final Integer numResults,
      final String variableFieldLabel) {
    final Nested variablesFromType = aggregations.get(variableFieldLabel);
    final Filter filteredVariables =
        variablesFromType.getAggregations().get(FILTERED_VARIABLES_AGGREGATION);
    final Terms valueTerms = filteredVariables.getAggregations().get(VALUE_AGGREGATION);
    final List<String> allValues = new ArrayList<>();
    for (final Terms.Bucket valueBucket : valueTerms.getBuckets()) {
      allValues.add(valueBucket.getKeyAsString());
    }
    int lastIndex = Math.min(allValues.size(), resultOffset + numResults);
    return allValues.subList(resultOffset, lastIndex);
  }

  private AggregationBuilder getDecisionVariableValueAggregation(
      final DecisionVariableValueRequestDto requestDto, final String variablePath) {
    final FilterAggregationBuilder filterForVariableWithGivenIdAndPrefix =
        getVariableValueFilterAggregation(
            requestDto.getVariableId(), variablePath, requestDto.getValueFilter());
    return getVariableValuesAggregation(
        variablePath, requestDto.getVariableType(), filterForVariableWithGivenIdAndPrefix);
  }

  private AggregationBuilder getVariableValueAggregation(
      final ProcessVariableValuesQueryDto requestDto) {
    final FilterAggregationBuilder filterForVariableWithGivenIdAndPrefix =
        getProcessVariableValueFilterAggregation(
            requestDto.getName(), requestDto.getType(), requestDto.getValueFilter());
    return getVariableValuesAggregation(
        VARIABLES, requestDto.getType(), filterForVariableWithGivenIdAndPrefix);
  }

  private AggregationBuilder getVariableValuesAggregation(
      final String variablePath,
      final VariableType variableType,
      final FilterAggregationBuilder filterForVariableWithGivenIdAndPrefix) {
    final TermsAggregationBuilder collectAllVariableValues =
        terms(VALUE_AGGREGATION)
            .field(getVariableValueFieldForType(variablePath, variableType))
            .size(MAX_RESPONSE_SIZE_LIMIT)
            .order(BucketOrder.key(true));

    return nested(variablePath, variablePath)
        .subAggregation(
            filterForVariableWithGivenIdAndPrefix.subAggregation(collectAllVariableValues));
  }

  private FilterAggregationBuilder getVariableValueFilterAggregation(
      final String variableId, final String variablePath, final String valueFilter) {
    final BoolQueryBuilder filterQuery =
        boolQuery().must(termQuery(getVariableClauseIdField(variablePath), variableId));

    addValueFilter(variablePath, valueFilter, filterQuery);

    return filter(FILTERED_VARIABLES_AGGREGATION, filterQuery);
  }

  private FilterAggregationBuilder getProcessVariableValueFilterAggregation(
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
    boolean isStringVariable = VariableType.STRING.equals(variableType);
    boolean valueFilterIsConfigured = valueFilter != null && !valueFilter.isEmpty();
    if (isStringVariable && valueFilterIsConfigured) {
      final String lowerCaseValue = valueFilter.toLowerCase(Locale.ENGLISH);
      QueryBuilder filter =
          (lowerCaseValue.length() > MAX_GRAM)
              /*
                using the slow wildcard query for uncommonly large filter strings (> 10 chars)
              */
              ? wildcardQuery(
                  ProcessVariableHelper.getValueSearchField(LOWERCASE_FIELD),
                  buildWildcardQuery(lowerCaseValue))
              /*
                using Elasticsearch ngrams to filter for strings < 10 chars,
                because it's fast but increasing the number of chars makes the index bigger
              */
              : termQuery(ProcessVariableHelper.getValueSearchField(N_GRAM_FIELD), lowerCaseValue);

      filterQuery.must(filter);
    }
  }

  private void addValueFilter(
      final String variablePath, final String valueFilter, final BoolQueryBuilder filterQuery) {
    if (valueFilter != null && !valueFilter.isEmpty()) {
      final String lowerCaseValue = valueFilter.toLowerCase(Locale.ENGLISH);
      final QueryBuilder filter =
          (lowerCaseValue.length() > MAX_GRAM)
              /*
                using the slow wildcard query for uncommonly large filter strings (> 10 chars)
              */
              ? wildcardQuery(
                  getValueSearchField(variablePath, VARIABLE_VALUE_LOWERCASE),
                  buildWildcardQuery(lowerCaseValue))
              /*
                using Elasticsearch ngrams to filter for strings < 10 chars,
                because it's fast but increasing the number of chars makes the index bigger
              */
              : termQuery(getValueSearchField(variablePath, VARIABLE_VALUE_NGRAM), lowerCaseValue);

      filterQuery.must(filter);
    }
  }

  private List<ExternalProcessVariableDto> getPageOfVariablesSortedByIngestionTimestamp(
      final AbstractQueryBuilder<?> query, final int limit) {
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(query)
            .sort(SortBuilders.fieldSort(INGESTION_TIMESTAMP).order(ASC))
            .size(limit);

    final SearchRequest searchRequest =
        new SearchRequest(EXTERNAL_PROCESS_VARIABLE_INDEX_NAME).source(searchSourceBuilder);

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest);
      return ElasticsearchReaderUtil.mapHits(
          searchResponse.getHits(), ExternalProcessVariableDto.class, objectMapper);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(
          "Was not able to retrieve ingested variables by timestamp!", e);
    }
  }

  private Optional<DefinitionVariableLabelsDto> extractDefinitionLabelsDto(
      final MultiGetItemResponse multiGetItemResponse) {
    return Optional.ofNullable(multiGetItemResponse.getResponse().getSourceAsString())
        .map(
            json -> {
              try {
                return objectMapper.readValue(
                    multiGetItemResponse.getResponse().getSourceAsString(),
                    DefinitionVariableLabelsDto.class);
              } catch (final IOException e) {
                throw new OptimizeRuntimeException("Failed parsing response: " + json, e);
              }
            });
  }

  private void addInsertExternalVariableRequest(
      final BulkRequest bulkRequest, final ExternalProcessVariableDto externalVariable) {
    try {
      bulkRequest.add(
          new IndexRequest(EXTERNAL_PROCESS_VARIABLE_INDEX_NAME)
              .source(objectMapper.writeValueAsString(externalVariable), XContentType.JSON));
    } catch (final JsonProcessingException e) {
      log.warn(
          "Could not serialize external process variable: {}. This variable will not be ingested.",
          externalVariable,
          e);
    }
  }
}
