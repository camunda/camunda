/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import static io.camunda.optimize.dto.optimize.DefinitionType.DECISION;
import static io.camunda.optimize.service.db.DatabaseConstants.EXTERNAL_PROCESS_VARIABLE_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.MAX_GRAM;
import static io.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.DatabaseConstants.VARIABLE_LABEL_INDEX_NAME;
import static io.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil.createDefaultScriptWithSpecificDtoParams;
import static io.camunda.optimize.service.db.schema.index.ExternalProcessVariableIndex.INGESTION_TIMESTAMP;
import static io.camunda.optimize.service.db.schema.index.VariableUpdateInstanceIndex.TIMESTAMP;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableClauseIdField;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableValueFieldForType;
import static io.camunda.optimize.service.util.DefinitionQueryUtilES.createDefinitionQuery;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getDecisionInstanceIndexAliasName;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static io.camunda.optimize.service.util.InstanceIndexUtil.isInstanceIndexNotFoundException;
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
import io.camunda.optimize.dto.optimize.query.variable.VariableUpdateInstanceDto;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil;
import io.camunda.optimize.service.db.es.schema.index.DecisionInstanceIndexES;
import io.camunda.optimize.service.db.es.schema.index.ExternalProcessVariableIndexES;
import io.camunda.optimize.service.db.es.schema.index.VariableUpdateInstanceIndexES;
import io.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil;
import io.camunda.optimize.service.db.reader.DecisionDefinitionReader;
import io.camunda.optimize.service.db.repository.VariableRepository;
import io.camunda.optimize.service.db.repository.script.ProcessInstanceScriptFactory;
import io.camunda.optimize.service.db.schema.ScriptData;
import io.camunda.optimize.service.db.schema.index.VariableUpdateInstanceIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
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
            .aggregation(getVariableValueAggregation(requestDto, variablesPath))
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

  private AggregationBuilder getVariableValueAggregation(
      final DecisionVariableValueRequestDto requestDto, final String variablePath) {
    final TermsAggregationBuilder collectAllVariableValues =
        terms(VALUE_AGGREGATION)
            .field(getVariableValueFieldForType(variablePath, requestDto.getVariableType()))
            .size(MAX_RESPONSE_SIZE_LIMIT)
            .order(BucketOrder.key(true));

    final FilterAggregationBuilder filterForVariableWithGivenIdAndPrefix =
        getVariableValueFilterAggregation(
            requestDto.getVariableId(), variablePath, requestDto.getValueFilter());

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
