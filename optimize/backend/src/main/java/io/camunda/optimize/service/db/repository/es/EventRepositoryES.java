/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import static io.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static io.camunda.optimize.dto.optimize.query.sorting.SortOrder.DESC;
import static io.camunda.optimize.service.db.DatabaseConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.EVENT_PROCESS_MAPPING_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.EVENT_PROCESS_PUBLISH_STATE_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.EXTERNAL_EVENTS_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.MAX_GRAM;
import static io.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static io.camunda.optimize.service.db.DatabaseConstants.SORT_NULLS_FIRST;
import static io.camunda.optimize.service.db.DatabaseConstants.SORT_NULLS_LAST;
import static io.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil.createDefaultScriptWithPrimitiveParams;
import static io.camunda.optimize.service.db.schema.index.AbstractDefinitionIndex.DEFINITION_KEY;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_XML;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.BUSINESS_KEY;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.END_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_KEY;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_VERSION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.TENANT_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;
import static io.camunda.optimize.service.db.schema.index.events.EventIndex.EVENT_NAME;
import static io.camunda.optimize.service.db.schema.index.events.EventIndex.GROUP;
import static io.camunda.optimize.service.db.schema.index.events.EventIndex.INGESTION_TIMESTAMP;
import static io.camunda.optimize.service.db.schema.index.events.EventIndex.SOURCE;
import static io.camunda.optimize.service.db.schema.index.events.EventIndex.TIMESTAMP;
import static io.camunda.optimize.service.db.schema.index.events.EventIndex.TRACE_ID;
import static io.camunda.optimize.service.util.DefinitionVersionHandlingUtil.isDefinitionVersionSetToAll;
import static io.camunda.optimize.service.util.DefinitionVersionHandlingUtil.isDefinitionVersionSetToLatest;
import static io.camunda.optimize.service.util.InstanceIndexUtil.isInstanceIndexNotFoundException;
import static io.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameField;
import static io.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableValueField;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.elasticsearch.core.TimeValue.timeValueSeconds;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.functionScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.prefixQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;
import static org.elasticsearch.search.aggregations.AggregationBuilders.topHits;
import static org.elasticsearch.search.sort.SortOrder.ASC;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.IdentityDto;
import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.event.DeletableEventDto;
import io.camunda.optimize.dto.optimize.query.event.EventGroupRequestDto;
import io.camunda.optimize.dto.optimize.query.event.EventSearchRequestDto;
import io.camunda.optimize.dto.optimize.query.event.autogeneration.CorrelatableProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.event.autogeneration.CorrelationValueDto;
import io.camunda.optimize.dto.optimize.query.event.process.CamundaActivityEventDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessDefinitionDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessRoleRequestDto;
import io.camunda.optimize.dto.optimize.query.event.process.db.DbEventProcessMappingDto;
import io.camunda.optimize.dto.optimize.query.event.process.db.DbEventProcessPublishStateDto;
import io.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceConfigDto;
import io.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceEntryDto;
import io.camunda.optimize.dto.optimize.rest.Page;
import io.camunda.optimize.dto.optimize.rest.sorting.SortRequestDto;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.es.ElasticsearchCompositeAggregationScroller;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil;
import io.camunda.optimize.service.db.es.schema.index.events.CamundaActivityEventIndexES;
import io.camunda.optimize.service.db.es.schema.index.events.EventIndexES;
import io.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.repository.EventRepository;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import io.camunda.optimize.service.db.schema.ScriptData;
import io.camunda.optimize.service.db.schema.index.events.CamundaActivityEventIndex;
import io.camunda.optimize.service.db.schema.index.events.EventIndex;
import io.camunda.optimize.service.db.schema.index.events.EventProcessMappingIndex;
import io.camunda.optimize.service.db.schema.index.events.EventProcessPublishStateIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.IdGenerator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeValuesSourceBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.TermsValuesSourceBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.ParsedSingleValueNumericMetricsAggregation;
import org.elasticsearch.search.aggregations.metrics.ParsedTopHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class EventRepositoryES implements EventRepository {
  private final OptimizeElasticsearchClient esClient;
  private final DateTimeFormatter formatter;
  private final ObjectMapper objectMapper;
  private final ConfigurationService configurationService;
  private final DateTimeFormatter dateTimeFormatter;
  private final ProcessDefinitionReader processDefinitionReader;

  @Override
  public void upsertEvents(List<EventDto> eventDtos) {
    final BulkRequest bulkRequest = new BulkRequest();
    for (EventDto eventDto : eventDtos) {
      bulkRequest.add(createEventUpsert(eventDto));
    }

    if (bulkRequest.numberOfActions() > 0) {
      try {
        final BulkResponse bulkResponse = esClient.bulk(bulkRequest);
        if (bulkResponse.hasFailures()) {
          final String errorMessage =
              String.format(
                  "There were failures while writing events. Received error message: %s",
                  bulkResponse.buildFailureMessage());
          throw new OptimizeRuntimeException(errorMessage);
        }
      } catch (IOException e) {
        final String errorMessage = "There were errors while writing events.";
        log.error(errorMessage, e);
        throw new OptimizeRuntimeException(errorMessage, e);
      }
    }
  }

  @Override
  public void deleteEventsOlderThan(
      final OffsetDateTime timestamp, final String deletedItemIdentifier) {

    final BoolQueryBuilder filterQuery =
        boolQuery()
            .filter(rangeQuery(EventIndex.TIMESTAMP).lt(dateTimeFormatter.format(timestamp)));

    ElasticsearchWriterUtil.tryDeleteByQueryRequest(
        esClient,
        filterQuery,
        deletedItemIdentifier,
        false,
        // use wildcarded index name to catch all indices that exist after potential rollover
        esClient
            .getIndexNameService()
            .getOptimizeIndexNameWithVersionWithWildcardSuffix(new EventIndexES()));
  }

  @Override
  public void deleteEventsWithIdsIn(
      final List<String> eventIdsToDelete, final String deletedItemIdentifier) {

    final BoolQueryBuilder filterQuery =
        boolQuery().filter(termsQuery(EventIndex.ID, eventIdsToDelete));
    ElasticsearchWriterUtil.tryDeleteByQueryRequest(
        esClient,
        filterQuery,
        deletedItemIdentifier,
        true,
        // use wildcarded index name to catch all indices that exist after potential rollover
        esClient
            .getIndexNameService()
            .getOptimizeIndexNameWithVersionWithWildcardSuffix(new EventIndexES()));
  }

  @Override
  public void deleteByProcessInstanceIds(
      final String definitionKey, final List<String> processInstanceIds) {
    final BoolQueryBuilder filterQuery =
        boolQuery()
            .filter(termsQuery(CamundaActivityEventIndex.PROCESS_INSTANCE_ID, processInstanceIds));

    ElasticsearchWriterUtil.tryDeleteByQueryRequest(
        esClient,
        filterQuery,
        String.format("camunda activity events of %d process instances", processInstanceIds.size()),
        false,
        // use wildcarded index name to catch all indices that exist after potential rollover
        esClient
            .getIndexNameService()
            .getOptimizeIndexNameWithVersionWithWildcardSuffix(
                new CamundaActivityEventIndexES(definitionKey)));
  }

  @Override
  public List<CamundaActivityEventDto> getPageOfEventsForDefinitionKeySortedByTimestamp(
      final String definitionKey,
      final Pair<Long, Long> timestampRange,
      final int limit,
      final TimeRangeRequest mode) {
    final RangeQueryBuilder timestampQuery;
    if (mode.equals(TimeRangeRequest.AT)) {
      timestampQuery =
          rangeQuery(CamundaActivityEventIndex.TIMESTAMP)
              .lte(formatter.format(convertToOffsetDateTime(timestampRange.getLeft())))
              .gte(formatter.format(convertToOffsetDateTime(timestampRange.getRight())));
    } else if (mode.equals(TimeRangeRequest.AFTER)) {
      timestampQuery =
          rangeQuery(CamundaActivityEventIndex.TIMESTAMP)
              .gt(formatter.format(convertToOffsetDateTime(timestampRange.getLeft())));
    } else {
      timestampQuery =
          rangeQuery(CamundaActivityEventIndex.TIMESTAMP)
              .gt(formatter.format(convertToOffsetDateTime(timestampRange.getLeft())))
              .lt(formatter.format(convertToOffsetDateTime(timestampRange.getRight())));
    }
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(timestampQuery)
            .sort(SortBuilders.fieldSort(CamundaActivityEventIndex.TIMESTAMP).order(ASC))
            .size(limit);

    final SearchRequest searchRequest =
        new SearchRequest(CamundaActivityEventIndex.constructIndexName(definitionKey))
            .source(searchSourceBuilder);

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest);
      return ElasticsearchReaderUtil.mapHits(
          searchResponse.getHits(), CamundaActivityEventDto.class, objectMapper);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Was not able to retrieve camunda activity events!", e);
    }
  }

  @Override
  public Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>>
      getMinAndMaxIngestedTimestampsForDefinition(final String processDefinitionKey) {
    log.debug("Fetching min and max timestamp for ingested camunda events");

    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(matchAllQuery())
            .fetchSource(false)
            .aggregation(
                AggregationBuilders.min(MIN_AGG)
                    .field(CamundaActivityEventIndex.TIMESTAMP)
                    .format(OPTIMIZE_DATE_FORMAT))
            .aggregation(
                AggregationBuilders.max(MAX_AGG)
                    .field(CamundaActivityEventIndex.TIMESTAMP)
                    .format(OPTIMIZE_DATE_FORMAT))
            .size(0);

    try {
      final String indexName = CamundaActivityEventIndex.constructIndexName(processDefinitionKey);
      final boolean indexExists = esClient.exists(new GetIndexRequest(indexName));
      if (indexExists) {
        final SearchResponse searchResponse =
            esClient.search(new SearchRequest(indexName).source(searchSourceBuilder));
        return ImmutablePair.of(
            extractTimestampForAggregation(searchResponse.getAggregations().get(MIN_AGG)),
            extractTimestampForAggregation(searchResponse.getAggregations().get(MAX_AGG)));
      } else {
        log.debug("{} Index does not exist", indexName);
        return ImmutablePair.of(Optional.empty(), Optional.empty());
      }
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(
          "Was not able to retrieve min and max camunda activity ingestion timestamps!", e);
    }
  }

  @Override
  public Optional<EventProcessMappingDto> getEventProcessMapping(
      final String eventProcessMappingId) {
    final GetRequest getRequest =
        new GetRequest(EVENT_PROCESS_MAPPING_INDEX_NAME).id(eventProcessMappingId);

    final GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest);
    } catch (IOException e) {
      final String reason =
          String.format("Could not fetch event-based process with id [%s].", eventProcessMappingId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    EventProcessMappingDto result = null;
    if (getResponse.isExists()) {
      try {
        result =
            objectMapper
                .readValue(getResponse.getSourceAsString(), DbEventProcessMappingDto.class)
                .toEventProcessMappingDto();
      } catch (IOException e) {
        String reason =
            "Could not deserialize information for event-based process with ID: "
                + eventProcessMappingId;
        log.error(
            "Was not able to retrieve event-based process with id [{}] from Elasticsearch. Reason: {}",
            eventProcessMappingId,
            reason);
        throw new OptimizeRuntimeException(reason, e);
      }
    }

    return Optional.ofNullable(result);
  }

  @Override
  public List<EventProcessMappingDto> getAllEventProcessMappingsOmitXml() {
    String[] fieldsToExclude = new String[] {DbEventProcessMappingDto.Fields.xml};
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(matchAllQuery())
            .size(LIST_FETCH_LIMIT)
            .fetchSource(null, fieldsToExclude);
    final SearchRequest searchRequest =
        new SearchRequest(EVENT_PROCESS_MAPPING_INDEX_NAME)
            .source(searchSourceBuilder)
            .scroll(
                timeValueSeconds(
                    configurationService
                        .getElasticSearchConfiguration()
                        .getScrollTimeoutInSeconds()));

    final SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest);
    } catch (IOException e) {
      log.error("Was not able to retrieve event-based processes!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve event-based processes!", e);
    }

    return ElasticsearchReaderUtil.retrieveAllScrollResults(
            scrollResp,
            DbEventProcessMappingDto.class,
            objectMapper,
            esClient,
            configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds())
        .stream()
        .map(DbEventProcessMappingDto::toEventProcessMappingDto)
        .toList();
  }

  @Override
  public List<EventProcessRoleRequestDto<IdentityDto>> getEventProcessRoles(
      final String eventProcessMappingId) {
    final GetRequest getRequest =
        new GetRequest(EVENT_PROCESS_MAPPING_INDEX_NAME)
            .id(eventProcessMappingId)
            .fetchSourceContext(
                new FetchSourceContext(true, new String[] {EventProcessMappingIndex.ROLES}, null));

    final GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest);
    } catch (IOException e) {
      final String reason =
          String.format(
              "Could not fetch roles for event-based process with id [%s].", eventProcessMappingId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    List<EventProcessRoleRequestDto<IdentityDto>> result = Collections.emptyList();
    if (getResponse.isExists()) {
      try {
        result =
            objectMapper
                .readValue(getResponse.getSourceAsString(), DbEventProcessMappingDto.class)
                .getRoles();
      } catch (IOException e) {
        final String reason =
            "Could not deserialize information for event-based process with id: "
                + eventProcessMappingId;
        log.error(
            "Was not able to retrieve roles for event-based process with id [{}] from Elasticsearch. Reason: {}",
            eventProcessMappingId,
            reason);
        throw new OptimizeRuntimeException(reason, e);
      }
    }
    return result;
  }

  @Override
  public IdResponseDto createEventProcessPublishState(
      final EventProcessPublishStateDto eventProcessPublishStateDto) {
    String id = eventProcessPublishStateDto.getId();
    IndexResponse indexResponse;
    try {
      final IndexRequest request =
          new IndexRequest(EVENT_PROCESS_PUBLISH_STATE_INDEX_NAME)
              .id(id)
              .source(
                  objectMapper.writeValueAsString(
                      DbEventProcessPublishStateDto.fromEventProcessPublishStateDto(
                          eventProcessPublishStateDto)),
                  XContentType.JSON)
              .setRefreshPolicy(IMMEDIATE);
      indexResponse = esClient.index(request);
    } catch (IOException e) {
      final String errorMessage =
          String.format(
              "There was a problem while writing the event process publish state [%s].", id);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    if (!indexResponse.getResult().equals(IndexResponse.Result.CREATED)) {
      final String errorMessage =
          String.format("Could not write event process publish state [%s].", id);
      throw new OptimizeRuntimeException(errorMessage);
    }
    return new IdResponseDto(id);
  }

  @Override
  public boolean markAsDeletedAllEventProcessPublishStatesForEventProcessMappingId(
      final String eventProcessMappingId,
      final String updateItem,
      final ScriptData scriptData,
      final String idFieldName,
      final String indexName) {
    final Script updateScript =
        createDefaultScriptWithPrimitiveParams(scriptData.scriptString(), scriptData.params());
    return ElasticsearchWriterUtil.tryUpdateByQueryRequest(
        esClient,
        updateItem,
        updateScript,
        termQuery(idFieldName, eventProcessMappingId),
        indexName);
  }

  @Override
  public void markAsDeletedPublishStatesForEventProcessMappingIdExcludingPublishStateId(
      final String eventProcessMappingId,
      final String updateItem,
      final ScriptData scriptData,
      final String indexName,
      final String publishStateIdToExclude) {
    final Script updateScript =
        createDefaultScriptWithPrimitiveParams(scriptData.scriptString(), scriptData.params());
    final BoolQueryBuilder query =
        boolQuery()
            .must(
                termQuery(EventProcessPublishStateIndex.PROCESS_MAPPING_ID, eventProcessMappingId))
            .mustNot(termQuery(EventProcessPublishStateIndex.ID, publishStateIdToExclude));

    ElasticsearchWriterUtil.tryUpdateByQueryRequest(
        esClient, updateItem, updateScript, query, indexName);
  }

  @Override
  public Optional<EventProcessPublishStateDto> getEventProcessPublishStateByEventProcessId(
      final String eventProcessMappingId) {
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(
                boolQuery()
                    .must(
                        termQuery(
                            EventProcessPublishStateIndex.PROCESS_MAPPING_ID,
                            eventProcessMappingId))
                    .must(termQuery(EventProcessPublishStateIndex.DELETED, false)))
            .sort(
                SortBuilders.fieldSort(EventProcessPublishStateIndex.PUBLISH_DATE_TIME)
                    .order(SortOrder.DESC))
            .size(1);
    final SearchRequest searchRequest =
        new SearchRequest(EVENT_PROCESS_PUBLISH_STATE_INDEX_NAME).source(searchSourceBuilder);

    final SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (IOException e) {
      final String reason =
          String.format(
              "Could not fetch event process publish state with id [%s].", eventProcessMappingId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    EventProcessPublishStateDto result = null;
    if (searchResponse.getHits().getTotalHits().value > 0) {
      try {
        result =
            objectMapper
                .readValue(
                    searchResponse.getHits().getAt(0).getSourceAsString(),
                    DbEventProcessPublishStateDto.class)
                .toEventProcessPublishStateDto();
      } catch (IOException e) {
        String reason =
            "Could not deserialize information for event process publish state with id: "
                + eventProcessMappingId;
        log.error(
            "Was not able to retrieve event process publish state with id [{}]. Reason: {}",
            eventProcessMappingId,
            reason);
        throw new OptimizeRuntimeException(reason, e);
      }
    }

    return Optional.ofNullable(result);
  }

  @Override
  public void updateEntry(String indexName, String entityId, ScriptData script) {
    esClient.update(indexName, entityId, script);
  }

  @Override
  public List<EventProcessPublishStateDto> getAllEventProcessPublishStatesWithDeletedState(
      final boolean deleted) {
    final TermQueryBuilder query = termQuery(EventProcessPublishStateIndex.DELETED, deleted);
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder().query(query).size(LIST_FETCH_LIMIT);
    final SearchRequest searchRequest =
        new SearchRequest(EVENT_PROCESS_PUBLISH_STATE_INDEX_NAME)
            .source(searchSourceBuilder)
            .scroll(
                timeValueSeconds(
                    configurationService
                        .getElasticSearchConfiguration()
                        .getScrollTimeoutInSeconds()));

    final SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest);
    } catch (IOException e) {
      throw new OptimizeRuntimeException(
          "Was not able to retrieve event process publish states!", e);
    }

    return ElasticsearchReaderUtil.retrieveAllScrollResults(
            scrollResp,
            DbEventProcessPublishStateDto.class,
            objectMapper,
            esClient,
            configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds())
        .stream()
        .map(DbEventProcessPublishStateDto::toEventProcessPublishStateDto)
        .collect(Collectors.toList());
  }

  @Override
  public Optional<EventProcessDefinitionDto> getEventProcessDefinitionByKeyOmitXml(
      final String eventProcessDefinitionKey) {
    final BoolQueryBuilder query =
        QueryBuilders.boolQuery().must(termQuery(DEFINITION_KEY, eventProcessDefinitionKey));

    SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder().query(query).size(1).fetchSource(null, PROCESS_DEFINITION_XML);

    SearchRequest searchRequest =
        new SearchRequest(EVENT_PROCESS_DEFINITION_INDEX_NAME).source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (IOException e) {
      final String reason =
          String.format(
              "Could not fetch event-based process definition with key [%s].",
              eventProcessDefinitionKey);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (searchResponse.getHits().getHits().length == 0) {
      return Optional.empty();
    }

    SearchHit hit = searchResponse.getHits().getAt(0);
    final String sourceAsString = hit.getSourceAsString();
    try {
      final EventProcessDefinitionDto definitionDto =
          objectMapper.readValue(sourceAsString, EventProcessDefinitionDto.class);
      return Optional.of(definitionDto);
    } catch (JsonProcessingException e) {
      final String reason =
          "It was not possible to deserialize a hit from Elasticsearch!"
              + " Hit response from Elasticsearch: "
              + sourceAsString;
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason);
    }
  }

  @Override
  public List<EventProcessDefinitionDto> getAllEventProcessDefinitionsOmitXml() {
    String[] fieldsToExclude = new String[] {DbEventProcessMappingDto.Fields.xml};
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder().size(LIST_FETCH_LIMIT).fetchSource(null, fieldsToExclude);
    final SearchRequest searchRequest =
        new SearchRequest(EVENT_PROCESS_DEFINITION_INDEX_NAME)
            .source(searchSourceBuilder)
            .scroll(
                timeValueSeconds(
                    configurationService
                        .getElasticSearchConfiguration()
                        .getScrollTimeoutInSeconds()));

    final SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Was not able to retrieve event-based processes!", e);
    }

    return ElasticsearchReaderUtil.retrieveAllScrollResults(
        scrollResp,
        EventProcessDefinitionDto.class,
        objectMapper,
        esClient,
        configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds());
  }

  @Override
  public List<EventDto> getEventsIngestedAfter(final Long ingestTimestamp, final int limit) {
    final RangeQueryBuilder timestampQuery = rangeQuery(INGESTION_TIMESTAMP).gt(ingestTimestamp);
    return getPageOfEventsSortedByIngestionTimestamp(timestampQuery, limit);
  }

  @Override
  public List<EventDto> getEventsIngestedAfterForGroups(
      final Long ingestTimestamp, final int limit, final List<String> groups) {
    log.debug(
        "Fetching events that where ingested after {} for groups {}", ingestTimestamp, groups);

    final BoolQueryBuilder query =
        buildGroupFilterQuery(groups).must(rangeQuery(INGESTION_TIMESTAMP).gt(ingestTimestamp));

    return getPageOfEventsSortedByIngestionTimestamp(query, limit);
  }

  @Override
  public List<EventDto> getEventsIngestedAt(final Long ingestTimestamp) {
    log.debug("Fetching events that where ingested at {}", ingestTimestamp);

    final RangeQueryBuilder timestampQuery =
        rangeQuery(INGESTION_TIMESTAMP).lte(ingestTimestamp).gte(ingestTimestamp);

    return getPageOfEventsSortedByIngestionTimestamp(timestampQuery, MAX_RESPONSE_SIZE_LIMIT);
  }

  @Override
  public List<EventDto> getEventsIngestedAtForGroups(
      final Long ingestTimestamp, final List<String> groups) {
    log.debug("Fetching events that where ingested at {} for groups {}", ingestTimestamp, groups);

    final BoolQueryBuilder query =
        buildGroupFilterQuery(groups)
            .must(rangeQuery(INGESTION_TIMESTAMP).lte(ingestTimestamp).gte(ingestTimestamp));

    return getPageOfEventsSortedByIngestionTimestamp(query, MAX_RESPONSE_SIZE_LIMIT);
  }

  @Override
  public Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>> getMinAndMaxIngestedTimestamps() {
    log.debug("Fetching min and max timestamp for ingested external events");
    return getMinAndMaxIngestedTimestampsForQuery(matchAllQuery());
  }

  @Override
  public Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>>
      getMinAndMaxIngestedTimestampsForGroups(final List<String> groups) {
    log.debug("Fetching min and max timestamp for ingested external events in groups: {}", groups);
    return getMinAndMaxIngestedTimestampsForQuery(buildGroupFilterQuery(groups));
  }

  @Override
  public Page<DeletableEventDto> getEventsForRequest(
      final EventSearchRequestDto eventSearchRequestDto) {

    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(getSearchQueryForEventRequest(eventSearchRequestDto))
            .from(eventSearchRequestDto.getPaginationRequestDto().getOffset())
            .size(eventSearchRequestDto.getPaginationRequestDto().getLimit());
    getSortForEventRequest(eventSearchRequestDto.getSortRequestDto())
        .ifPresent(searchSourceBuilder::sort);
    // add secondary sort order
    searchSourceBuilder.sort(SortBuilders.fieldSort(TIMESTAMP).order(SortOrder.DESC));

    final SearchRequest searchRequest =
        new SearchRequest(EXTERNAL_EVENTS_INDEX_NAME).source(searchSourceBuilder);
    try {
      return toPage(eventSearchRequestDto, esClient.search(searchRequest));
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Was not able to retrieve events!", e);
    }
  }

  @Override
  public List<String> getEventGroups(final EventGroupRequestDto eventGroupRequestDto) {
    final String searchTerm = eventGroupRequestDto.getSearchTerm();
    AbstractQueryBuilder<?> query;
    if (searchTerm == null) {
      query = matchAllQuery();
    } else if (searchTerm.length() > MAX_GRAM) {
      query = boolQuery().must(prefixQuery(GROUP, searchTerm));
    } else {
      query =
          boolQuery()
              .must(
                  matchQuery(getNgramSearchField(GROUP), searchTerm.toLowerCase())
                      .analyzer(KEYWORD_ANALYZER));
    }

    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(query)
            .aggregation(buildCompositeGroupAggregation(eventGroupRequestDto))
            .size(0);

    final SearchRequest searchRequest =
        new SearchRequest(EXTERNAL_EVENTS_INDEX_NAME).source(searchSourceBuilder);
    List<String> groups = new ArrayList<>();
    ElasticsearchCompositeAggregationScroller.create()
        .setEsClient(esClient)
        .setSearchRequest(searchRequest)
        .setPathToAggregation(GROUP_COMPOSITE_AGG)
        .setCompositeBucketConsumer(
            bucket -> groups.add((String) (bucket.getKey().get(EVENT_GROUP_AGG))))
        .consumePage();
    return groups;
  }

  @Override
  public List<String> getCorrelationValueSampleForEventSources(
      List<CamundaEventSourceEntryDto> camundaSources) {
    final BoolQueryBuilder completedInstanceQuery = boolQuery().must(existsQuery(END_DATE));
    final BoolQueryBuilder matchesSourceQuery = boolQuery().minimumShouldMatch(1);
    camundaSources.forEach(
        source -> matchesSourceQuery.should(queryForEventSourceInstances(source)));

    SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(
                boolQuery()
                    .filter(completedInstanceQuery)
                    .filter(matchesSourceQuery)
                    .must(
                        functionScoreQuery(
                            matchAllQuery(), ScoreFunctionBuilders.randomFunction())))
            .size(0);

    SearchRequest searchRequest =
        new SearchRequest(getInstanceIndexNames(camundaSources)).source(searchSourceBuilder);
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
        log.warn(
            "Was not able to fetch sample correlation values because no instance indices exist. "
                + "Returning empty list.");
        return Collections.emptyList();
      }
      throw e;
    }
    Aggregations aggregations = searchResponse.getAggregations();
    return extractCorrelationValues(aggregations, camundaSources);
  }

  @Override
  public List<CorrelatableProcessInstanceDto> getCorrelatableInstancesForSources(
      List<CamundaEventSourceEntryDto> camundaSources, List<String> correlationValues) {
    final BoolQueryBuilder completedInstanceQuery = boolQuery().must(existsQuery(END_DATE));
    final BoolQueryBuilder matchesSourceQuery = boolQuery().minimumShouldMatch(1);
    camundaSources.forEach(
        eventSource ->
            matchesSourceQuery.should(
                queryForEventSourceInstancesWithCorrelationValues(eventSource, correlationValues)));

    SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(
                boolQuery()
                    .filter(completedInstanceQuery)
                    .filter(matchesSourceQuery)
                    .must(
                        functionScoreQuery(
                            matchAllQuery(), ScoreFunctionBuilders.randomFunction())))
            .size(MAX_RESPONSE_SIZE_LIMIT);
    SearchRequest searchRequest =
        new SearchRequest(getInstanceIndexNames(camundaSources))
            .source(searchSourceBuilder)
            .scroll(
                timeValueSeconds(
                    configurationService
                        .getElasticSearchConfiguration()
                        .getScrollTimeoutInSeconds()));

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (IOException e) {
      String reason = "Was not able to fetch instances for correlation values";
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    } catch (ElasticsearchStatusException e) {
      if (isInstanceIndexNotFoundException(PROCESS, e)) {
        log.info(
            "Was not able to fetch instances for correlation values because no instance indices exist. "
                + "Returning empty list.");
        return Collections.emptyList();
      }
      throw e;
    }
    return ElasticsearchReaderUtil.retrieveAllScrollResults(
        searchResponse,
        CorrelatableProcessInstanceDto.class,
        objectMapper,
        esClient,
        configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds());
  }

  private List<String> extractCorrelationValues(
      final Aggregations aggregations, final List<CamundaEventSourceEntryDto> eventSources) {
    final Terms valuesByProcessDefinition = aggregations.get(EVENT_SOURCE_AGG);
    List<String> correlationValues = new ArrayList<>();
    for (Terms.Bucket eventSourceBucket : valuesByProcessDefinition.getBuckets()) {
      CamundaEventSourceEntryDto eventSourceForCurrentBucket =
          eventSources.stream()
              .filter(
                  source ->
                      source
                          .getConfiguration()
                          .getProcessDefinitionKey()
                          .equals(eventSourceBucket.getKeyAsString()))
              .findFirst()
              .orElseThrow(
                  () ->
                      new OptimizeRuntimeException(
                          String.format(
                              "Could not find event source for bucket with key %s when sampling for correlation values",
                              eventSourceBucket.getKeyAsString())));
      ParsedTopHits topHits = eventSourceBucket.getAggregations().get(BUCKET_HITS_AGG);
      for (SearchHit hit : topHits.getHits().getHits()) {
        try {
          final CorrelationValueDto correlationValueDto =
              objectMapper.readValue(hit.getSourceAsString(), CorrelationValueDto.class);
          Optional<String> correlationValueToAdd =
              extractCorrelationValue(eventSourceForCurrentBucket, correlationValueDto);
          if (correlationValueToAdd.isPresent()) {
            correlationValues.add(correlationValueToAdd.get());
          } else {
            log.warn(
                "Could not find correlation value to use in sample from {}", correlationValueDto);
          }
        } catch (IOException e) {
          final String reason =
              String.format(
                  "It was not possible to deserialize a hit to class %s from from Elasticsearch: %s",
                  CorrelationValueDto.class.getSimpleName(), hit.getSourceAsString());
          log.error(reason, e);
          throw new OptimizeRuntimeException(reason);
        }
      }
    }
    return correlationValues;
  }

  private void addCorrelationValuesAggregation(
      final SearchSourceBuilder searchSourceBuilder,
      final List<CamundaEventSourceEntryDto> eventSources) {
    TermsAggregationBuilder correlationValuesAggregation =
        terms(EVENT_SOURCE_AGG)
            .field(PROCESS_DEFINITION_KEY)
            .size(eventSources.size())
            .subAggregation(
                // We use top hits only to access the documents in the bucket, which will be random
                // rather than scored
                topHits(BUCKET_HITS_AGG).size(MAX_HITS).fetchSource(CORRELATABLE_FIELDS, null));
    searchSourceBuilder.aggregation(correlationValuesAggregation);
  }

  private BoolQueryBuilder queryForEventSourceInstances(
      final CamundaEventSourceEntryDto eventSource) {
    return boolQuery().filter(versionsQuery(eventSource)).filter(tenantsQuery(eventSource));
  }

  private BoolQueryBuilder queryForEventSourceInstancesWithCorrelationValues(
      final CamundaEventSourceEntryDto eventSource, final List<String> correlationValues) {
    final BoolQueryBuilder eventSourceQuery =
        boolQuery()
            .filter(
                termQuery(
                    PROCESS_DEFINITION_KEY,
                    eventSource.getConfiguration().getProcessDefinitionKey()))
            .filter(versionsQuery(eventSource))
            .filter(tenantsQuery(eventSource));
    if (eventSource.getConfiguration().isTracedByBusinessKey()) {
      eventSourceQuery.filter(termsQuery(BUSINESS_KEY, correlationValues));
    } else {
      eventSourceQuery.filter(
          nestedQuery(
              VARIABLES,
              boolQuery()
                  .filter(
                      termQuery(
                          getNestedVariableNameField(),
                          eventSource.getConfiguration().getTraceVariable()))
                  .filter(termsQuery(getNestedVariableValueField(), correlationValues)),
              ScoreMode.None));
    }
    return eventSourceQuery;
  }

  private BoolQueryBuilder versionsQuery(final CamundaEventSourceEntryDto eventSource) {
    final CamundaEventSourceConfigDto eventSourceConfig = eventSource.getConfiguration();
    final BoolQueryBuilder versionQuery = boolQuery();
    if (isDefinitionVersionSetToLatest(eventSourceConfig.getVersions())) {
      versionQuery.must(
          termQuery(
              PROCESS_DEFINITION_VERSION,
              processDefinitionReader.getLatestVersionToKey(
                  eventSource.getConfiguration().getProcessDefinitionKey())));
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
    final List<String> nonNullTenants =
        eventSourceConfig.getTenants().stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    if (!nonNullTenants.isEmpty()) {
      tenantQuery.should(termsQuery(TENANT_ID, nonNullTenants));
    }
    return tenantQuery;
  }

  private BoolQueryBuilder buildGroupFilterQuery(final List<String> groups) {
    final BoolQueryBuilder groupsQuery = boolQuery();
    final List<String> nonNullGroups =
        groups.stream().filter(Objects::nonNull).collect(Collectors.toList());
    final boolean includeNull = groups.size() > nonNullGroups.size();
    final BoolQueryBuilder groupFilterQuery = boolQuery().minimumShouldMatch(1);
    if (!nonNullGroups.isEmpty()) {
      groupFilterQuery.should(termsQuery(GROUP, nonNullGroups));
    }
    if (includeNull) {
      groupFilterQuery.should(boolQuery().mustNot(existsQuery(GROUP)));
    }
    if (!CollectionUtils.isEmpty(groupFilterQuery.should())) {
      groupsQuery.filter().add(groupFilterQuery);
    }
    return groupsQuery;
  }

  private Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>>
      getMinAndMaxIngestedTimestampsForQuery(AbstractQueryBuilder<?> query) {
    SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(query)
            .fetchSource(false)
            .aggregation(AggregationBuilders.min(MIN_AGG).field(INGESTION_TIMESTAMP))
            .aggregation(AggregationBuilders.max(MAX_AGG).field(INGESTION_TIMESTAMP))
            .size(0);

    try {
      final SearchResponse searchResponse =
          esClient.search(
              new SearchRequest(EXTERNAL_EVENTS_INDEX_NAME).source(searchSourceBuilder));
      return ImmutablePair.of(
          extractTimestampForAggregation(searchResponse.getAggregations().get(MIN_AGG)),
          extractTimestampForAggregation(searchResponse.getAggregations().get(MAX_AGG)));
    } catch (IOException e) {
      throw new OptimizeRuntimeException(
          "Was not able to retrieve min and max event ingestion timestamps!", e);
    }
  }

  private CompositeAggregationBuilder buildCompositeGroupAggregation(
      final EventGroupRequestDto eventGroupRequestDto) {
    // We aggregate on the group name to return to user and the lower case name so we can sort
    // properly
    List<CompositeValuesSourceBuilder<?>> eventGroupsAndLowercaseGroups = new ArrayList<>();
    eventGroupsAndLowercaseGroups.add(
        new TermsValuesSourceBuilder(LOWERCASE_GROUP_AGG)
            .field(GROUP + "." + DefaultIndexMappingCreator.LOWERCASE)
            .order(ASC)
            .missingBucket(true));
    eventGroupsAndLowercaseGroups.add(
        new TermsValuesSourceBuilder(EVENT_GROUP_AGG).field(GROUP).order(ASC).missingBucket(true));
    return new CompositeAggregationBuilder(GROUP_COMPOSITE_AGG, eventGroupsAndLowercaseGroups)
        .size(
            Math.min(
                eventGroupRequestDto.getLimit(),
                configurationService.getElasticSearchConfiguration().getAggregationBucketLimit()));
  }

  private Page<DeletableEventDto> toPage(
      final EventSearchRequestDto eventSearchRequestDto, final SearchResponse searchResponse) {
    final List<EventDto> eventsForRequest =
        ElasticsearchReaderUtil.mapHits(searchResponse.getHits(), EventDto.class, objectMapper);

    long totalHits;
    if (Objects.isNull(searchResponse.getHits().getTotalHits())) {
      log.warn("Could not extract the total hits from SearchResponse");
      totalHits = 0;
    } else {
      totalHits = searchResponse.getHits().getTotalHits().value;
    }
    return new Page<>(
        eventSearchRequestDto.getPaginationRequestDto().getOffset(),
        eventSearchRequestDto.getPaginationRequestDto().getLimit(),
        totalHits,
        eventSearchRequestDto
            .getSortRequestDto()
            .getSortBy()
            .orElse(DeletableEventDto.Fields.timestamp),
        eventSearchRequestDto.getSortRequestDto().getSortOrder().orElse(DESC),
        eventsForRequest.stream().map(DeletableEventDto::from).collect(Collectors.toList()));
  }

  private QueryBuilder getSearchQueryForEventRequest(
      final EventSearchRequestDto eventSearchRequestDto) {
    final String searchTerm = eventSearchRequestDto.getSearchTerm();
    if (eventSearchRequestDto.getSearchTerm() == null) {
      return matchAllQuery();
    }

    if (searchTerm.length() > MAX_GRAM) {
      return boolQuery()
          .minimumShouldMatch(1)
          .should(prefixQuery(GROUP, searchTerm))
          .should(prefixQuery(SOURCE, searchTerm))
          .should(prefixQuery(EVENT_NAME, searchTerm))
          .should(prefixQuery(TRACE_ID, searchTerm));
    }

    return boolQuery()
        .should(
            QueryBuilders.multiMatchQuery(
                    searchTerm.toLowerCase(),
                    getNgramSearchField(GROUP),
                    getNgramSearchField(SOURCE),
                    getNgramSearchField(EVENT_NAME),
                    getNgramSearchField(TRACE_ID))
                .analyzer(KEYWORD_ANALYZER));
  }

  private Optional<FieldSortBuilder> getSortForEventRequest(final SortRequestDto sortRequestDto) {
    final Optional<String> sortByOpt = sortRequestDto.getSortBy();
    if (sortByOpt.isPresent()) {
      FieldSortBuilder fieldSortBuilder =
          SortBuilders.fieldSort(convertToIndexSortField(sortByOpt.get()));
      sortRequestDto
          .getSortOrder()
          .ifPresent(
              order -> {
                // This makes sure that nullable fields respect the sort order
                if (io.camunda.optimize.dto.optimize.query.sorting.SortOrder.ASC.equals(order)) {
                  fieldSortBuilder
                      .order(SortOrder.fromString(order.toString()))
                      .missing(SORT_NULLS_FIRST);
                } else {
                  fieldSortBuilder
                      .order(SortOrder.fromString(order.toString()))
                      .missing(SORT_NULLS_LAST);
                }
              });
      return Optional.of(fieldSortBuilder);
    }
    return Optional.empty();
  }

  private Optional<OffsetDateTime> extractTimestampForAggregation(
      final ParsedSingleValueNumericMetricsAggregation aggregation) {
    String dateAsStr = aggregation.getValueAsString();
    return parseDateString(dateAsStr, formatter);
  }

  private List<EventDto> getPageOfEventsSortedByIngestionTimestamp(
      final AbstractQueryBuilder<?> query, final int limit) {
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(query)
            .sort(SortBuilders.fieldSort(INGESTION_TIMESTAMP).order(ASC))
            .size(limit);

    final SearchRequest searchRequest =
        new SearchRequest(EXTERNAL_EVENTS_INDEX_NAME).source(searchSourceBuilder);

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest);
      return ElasticsearchReaderUtil.mapHits(
          searchResponse.getHits(), EventDto.class, objectMapper);
    } catch (IOException e) {
      throw new OptimizeRuntimeException(
          "Was not able to retrieve ingested events by timestamp!", e);
    }
  }

  private UpdateRequest createEventUpsert(final EventDto eventDto) {
    return new UpdateRequest()
        .index(DatabaseConstants.EXTERNAL_EVENTS_INDEX_NAME)
        .id(IdGenerator.getNextId())
        .doc(objectMapper.convertValue(eventDto, Map.class))
        .docAsUpsert(true);
  }
}
