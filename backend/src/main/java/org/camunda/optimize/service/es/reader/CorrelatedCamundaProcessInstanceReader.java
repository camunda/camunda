/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.event.autogeneration.CorrelatableProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.autogeneration.CorrelationValueDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceConfigDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceType;
import org.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.ParsedTopHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.BUSINESS_KEY;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.TENANT_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.util.DefinitionVersionHandlingUtil.isDefinitionVersionSetToAll;
import static org.camunda.optimize.service.util.DefinitionVersionHandlingUtil.isDefinitionVersionSetToLatest;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static org.camunda.optimize.service.util.InstanceIndexUtil.isInstanceIndexNotFoundException;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameField;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableValueField;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.elasticsearch.core.TimeValue.timeValueSeconds;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.functionScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;
import static org.elasticsearch.search.aggregations.AggregationBuilders.topHits;

@RequiredArgsConstructor
@Component
@Slf4j
public class CorrelatedCamundaProcessInstanceReader {

  private static final String EVENT_SOURCE_AGG = "eventSourceAgg";
  private static final String BUCKET_HITS_AGG = "bucketHitsAgg";
  private static final String[] CORRELATABLE_FIELDS = {BUSINESS_KEY, VARIABLES};
  private static final int MAX_HITS = 100;

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;
  private final ConfigurationService configurationService;

  private final ProcessDefinitionReader processDefinitionReader;

  public List<String> getCorrelationValueSampleForEventSources(final List<CamundaEventSourceEntryDto> eventSources) {
    final List<CamundaEventSourceEntryDto> camundaSources = eventSources.stream()
      .filter(eventSource -> EventSourceType.CAMUNDA.equals(eventSource.getSourceType()))
      .collect(Collectors.toList());
    if (camundaSources.isEmpty()) {
      log.debug("There are no Camunda sources to fetch sample correlation values for");
      return Collections.emptyList();
    }

    log.debug("Fetching sample of correlation values for {} event sources", camundaSources.size());

    final BoolQueryBuilder completedInstanceQuery = boolQuery().must(existsQuery(END_DATE));
    final BoolQueryBuilder matchesSourceQuery = boolQuery().minimumShouldMatch(1);
    camundaSources.forEach(source -> matchesSourceQuery.should(queryForEventSourceInstances(source)));

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(boolQuery()
               .filter(completedInstanceQuery)
               .filter(matchesSourceQuery)
               .must(functionScoreQuery(matchAllQuery(), ScoreFunctionBuilders.randomFunction())))
      .size(0);

    SearchRequest searchRequest = new SearchRequest(getInstanceIndexNames(eventSources)).source(searchSourceBuilder);
    addCorrelationValuesAggregation(searchSourceBuilder, camundaSources);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (IOException e) {
      String reason = "Was not able to fetch sample correlation values";
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    } catch (ElasticsearchStatusException e) {
      if (isInstanceIndexNotFoundException(PROCESS, e)) {
        log.warn("Was not able to fetch sample correlation values because no instance indices exist. " +
                   "Returning empty list.");
        return Collections.emptyList();
      }
      throw e;
    }
    Aggregations aggregations = searchResponse.getAggregations();
    return extractCorrelationValues(aggregations, camundaSources);
  }

  public List<CorrelatableProcessInstanceDto> getCorrelatableInstancesForSources(final List<CamundaEventSourceEntryDto> camundaSources,
                                                                                 final List<String> correlationValues) {
    if (camundaSources.isEmpty()) {
      log.debug("There are no Camunda sources to fetch correlated process instances for");
      return Collections.emptyList();
    }

    log.debug(
      "Fetching correlated process instances for correlation value sample size {} for {} event sources",
      correlationValues.size(),
      camundaSources.size()
    );

    final BoolQueryBuilder completedInstanceQuery = boolQuery().must(existsQuery(END_DATE));
    final BoolQueryBuilder matchesSourceQuery = boolQuery().minimumShouldMatch(1);
    camundaSources.forEach(eventSource -> matchesSourceQuery.should(queryForEventSourceInstancesWithCorrelationValues(
      eventSource,
      correlationValues
    )));

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(boolQuery()
               .filter(completedInstanceQuery)
               .filter(matchesSourceQuery)
               .must(functionScoreQuery(matchAllQuery(), ScoreFunctionBuilders.randomFunction())))
      .size(MAX_RESPONSE_SIZE_LIMIT);
    SearchRequest searchRequest = new SearchRequest(getInstanceIndexNames(camundaSources))
      .source(searchSourceBuilder)
      .scroll(timeValueSeconds(configurationService.getEsScrollTimeoutInSeconds()));

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (IOException e) {
      String reason = "Was not able to fetch instances for correlation values";
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    } catch (ElasticsearchStatusException e) {
      if (isInstanceIndexNotFoundException(PROCESS, e)) {
        log.info("Was not able to fetch instances for correlation values because no instance indices exist. " +
                   "Returning empty list.");
        return Collections.emptyList();
      }
      throw e;
    }
    return ElasticsearchReaderUtil.retrieveAllScrollResults(
      searchResponse,
      CorrelatableProcessInstanceDto.class,
      objectMapper,
      esClient,
      configurationService.getEsScrollTimeoutInSeconds()
    );
  }

  private List<String> extractCorrelationValues(final Aggregations aggregations,
                                                final List<CamundaEventSourceEntryDto> eventSources) {
    final Terms valuesByProcessDefinition = aggregations.get(EVENT_SOURCE_AGG);
    List<String> correlationValues = new ArrayList<>();
    for (Terms.Bucket eventSourceBucket : valuesByProcessDefinition.getBuckets()) {
      CamundaEventSourceEntryDto eventSourceForCurrentBucket = eventSources.stream()
        .filter(source -> source.getConfiguration()
          .getProcessDefinitionKey()
          .equals(eventSourceBucket.getKeyAsString()))
        .findFirst()
        .orElseThrow(() -> new OptimizeRuntimeException(String.format(
          "Could not find event source for bucket with key %s when sampling for correlation values",
          eventSourceBucket.getKeyAsString()
        )));
      ParsedTopHits topHits = eventSourceBucket.getAggregations().get(BUCKET_HITS_AGG);
      for (SearchHit hit : topHits.getHits().getHits()) {
        try {
          final CorrelationValueDto correlationValueDto = objectMapper.readValue(
            hit.getSourceAsString(),
            CorrelationValueDto.class
          );
          Optional<String> correlationValueToAdd;
          final CamundaEventSourceConfigDto eventSourceConfig = eventSourceForCurrentBucket.getConfiguration();
          if (eventSourceConfig.isTracedByBusinessKey()) {
            correlationValueToAdd = Optional.ofNullable(correlationValueDto.getBusinessKey());
          } else if (eventSourceConfig.getTraceVariable() != null) {
            final Map<String, SimpleProcessVariableDto> variablesByName = correlationValueDto.getVariables()
              .stream()
              .collect(Collectors.toMap(SimpleProcessVariableDto::getName, Function.identity()));
            correlationValueToAdd =
              variablesByName.get(eventSourceConfig.getTraceVariable()).getValue().stream().findFirst();
          } else {
            throw new OptimizeRuntimeException(
              "Cannot get variable sample values for event source with no tracing variable");
          }
          if (correlationValueToAdd.isPresent()) {
            correlationValues.add(correlationValueToAdd.get());
          } else {
            log.warn("Could not find correlation value to use in sample from {}", correlationValueDto);
          }
        } catch (IOException e) {
          final String reason = String.format(
            "It was not possible to deserialize a hit to class %s from from Elasticsearch: %s",
            CorrelationValueDto.class.getSimpleName(),
            hit.getSourceAsString()
          );
          log.error(reason, e);
          throw new OptimizeRuntimeException(reason);
        }

      }
    }
    return correlationValues;
  }

  private void addCorrelationValuesAggregation(final SearchSourceBuilder searchSourceBuilder,
                                               final List<CamundaEventSourceEntryDto> eventSources) {
    TermsAggregationBuilder correlationValuesAggregation = terms(EVENT_SOURCE_AGG)
      .field(PROCESS_DEFINITION_KEY)
      .size(eventSources.size())
      .subAggregation(
        // We use top hits only to access the documents in the bucket, which will be random rather than scored
        topHits(BUCKET_HITS_AGG)
          .size(MAX_HITS)
          .fetchSource(CORRELATABLE_FIELDS, null));
    searchSourceBuilder.aggregation(correlationValuesAggregation);
  }

  private BoolQueryBuilder queryForEventSourceInstances(final CamundaEventSourceEntryDto eventSource) {
    return boolQuery()
      .filter(versionsQuery(eventSource))
      .filter(tenantsQuery(eventSource));
  }

  private BoolQueryBuilder queryForEventSourceInstancesWithCorrelationValues(final CamundaEventSourceEntryDto eventSource,
                                                                             final List<String> correlationValues) {
    final BoolQueryBuilder eventSourceQuery = boolQuery()
      .filter(termQuery(PROCESS_DEFINITION_KEY, eventSource.getConfiguration().getProcessDefinitionKey()))
      .filter(versionsQuery(eventSource))
      .filter(tenantsQuery(eventSource));
    if (eventSource.getConfiguration().isTracedByBusinessKey()) {
      eventSourceQuery.filter(termsQuery(BUSINESS_KEY, correlationValues));
    } else {
      eventSourceQuery.filter(
        nestedQuery(
          VARIABLES,
          boolQuery()
            .filter(termQuery(getNestedVariableNameField(), eventSource.getConfiguration().getTraceVariable()))
            .filter(termsQuery(getNestedVariableValueField(), correlationValues)),
          ScoreMode.None
        )
      );
    }
    return eventSourceQuery;
  }

  private BoolQueryBuilder versionsQuery(final CamundaEventSourceEntryDto eventSource) {
    final CamundaEventSourceConfigDto eventSourceConfig = eventSource.getConfiguration();
    final BoolQueryBuilder versionQuery = boolQuery();
    if (isDefinitionVersionSetToLatest(eventSourceConfig.getVersions())) {
      versionQuery.must(termQuery(
        PROCESS_DEFINITION_VERSION,
        processDefinitionReader.getLatestVersionToKey(eventSource.getConfiguration().getProcessDefinitionKey())
      ));
    } else if (!isDefinitionVersionSetToAll(eventSourceConfig.getVersions())) {
      versionQuery.must(termsQuery(PROCESS_DEFINITION_VERSION, eventSourceConfig.getVersions()));
    } else if (eventSourceConfig.getVersions().isEmpty()) {
      versionQuery.mustNot(existsQuery(PROCESS_DEFINITION_VERSION));
    }
    return versionQuery;
  }

  private BoolQueryBuilder tenantsQuery(final CamundaEventSourceEntryDto eventSource) {
    final CamundaEventSourceConfigDto eventSourceConfig = eventSource.getConfiguration();
    final BoolQueryBuilder tenantQuery = boolQuery();
    if (eventSourceConfig.getTenants().contains(null) || eventSourceConfig.getTenants().isEmpty()) {
      tenantQuery.should(boolQuery().mustNot(existsQuery(TENANT_ID)));
    }
    final List<String> nonNullTenants = eventSourceConfig.getTenants().
      stream().filter(Objects::nonNull).collect(Collectors.toList());
    if (!nonNullTenants.isEmpty()) {
      tenantQuery.should(termsQuery(TENANT_ID, nonNullTenants));
    }
    return tenantQuery;
  }

  private String[] getInstanceIndexNames(final List<CamundaEventSourceEntryDto> eventSources) {
    return eventSources.stream()
      .map(source -> getProcessInstanceIndexAliasName(source.getConfiguration().getProcessDefinitionKey()))
      .toArray(String[]::new);
  }

}
