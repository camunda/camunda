/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import static io.camunda.optimize.dto.optimize.query.sorting.SortOrder.DESC;
import static io.camunda.optimize.service.db.DatabaseConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.EVENT_PROCESS_MAPPING_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.EVENT_PROCESS_PUBLISH_STATE_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.EXTERNAL_EVENTS_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.MAX_GRAM;
import static io.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.SORT_NULLS_FIRST;
import static io.camunda.optimize.service.db.DatabaseConstants.SORT_NULLS_LAST;
import static io.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil.createDefaultScriptWithPrimitiveParams;
import static io.camunda.optimize.service.db.schema.index.AbstractDefinitionIndex.DEFINITION_KEY;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_XML;
import static io.camunda.optimize.service.db.schema.index.events.EventIndex.EVENT_NAME;
import static io.camunda.optimize.service.db.schema.index.events.EventIndex.GROUP;
import static io.camunda.optimize.service.db.schema.index.events.EventIndex.INGESTION_TIMESTAMP;
import static io.camunda.optimize.service.db.schema.index.events.EventIndex.SOURCE;
import static io.camunda.optimize.service.db.schema.index.events.EventIndex.TIMESTAMP;
import static io.camunda.optimize.service.db.schema.index.events.EventIndex.TRACE_ID;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.elasticsearch.core.TimeValue.timeValueSeconds;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.prefixQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.search.sort.SortOrder.ASC;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.IdentityDto;
import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.event.DeletableEventDto;
import io.camunda.optimize.dto.optimize.query.event.EventGroupRequestDto;
import io.camunda.optimize.dto.optimize.query.event.EventSearchRequestDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessDefinitionDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessRoleRequestDto;
import io.camunda.optimize.dto.optimize.query.event.process.db.DbEventProcessMappingDto;
import io.camunda.optimize.dto.optimize.query.event.process.db.DbEventProcessPublishStateDto;
import io.camunda.optimize.dto.optimize.rest.Page;
import io.camunda.optimize.dto.optimize.rest.sorting.SortRequestDto;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.es.ElasticsearchCompositeAggregationScroller;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil;
import io.camunda.optimize.service.db.es.schema.index.events.EventIndexES;
import io.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil;
import io.camunda.optimize.service.db.repository.EventRepository;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import io.camunda.optimize.service.db.schema.ScriptData;
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
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeValuesSourceBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.TermsValuesSourceBuilder;
import org.elasticsearch.search.aggregations.metrics.ParsedSingleValueNumericMetricsAggregation;
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

  @Override
  public void upsertEvents(final List<EventDto> eventDtos) {
    final BulkRequest bulkRequest = new BulkRequest();
    for (final EventDto eventDto : eventDtos) {
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
      } catch (final IOException e) {
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
  public Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>> getMinAndMaxIngestedTimestamps() {
    log.debug("Fetching min and max timestamp for ingested external events");
    return getMinAndMaxIngestedTimestampsForQuery(matchAllQuery());
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
  public List<EventDto> getEventsIngestedAt(final Long ingestTimestamp) {
    log.debug("Fetching events that where ingested at {}", ingestTimestamp);

    final RangeQueryBuilder timestampQuery =
        rangeQuery(INGESTION_TIMESTAMP).lte(ingestTimestamp).gte(ingestTimestamp);

    return getPageOfEventsSortedByIngestionTimestamp(timestampQuery, MAX_RESPONSE_SIZE_LIMIT);
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
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Was not able to retrieve events!", e);
    }
  }

  @Override
  public List<String> getEventGroups(final EventGroupRequestDto eventGroupRequestDto) {
    final String searchTerm = eventGroupRequestDto.getSearchTerm();
    final AbstractQueryBuilder<?> query;
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
    final List<String> groups = new ArrayList<>();
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
  public Optional<EventProcessMappingDto> getEventProcessMapping(
      final String eventProcessMappingId) {
    final GetRequest getRequest =
        new GetRequest(EVENT_PROCESS_MAPPING_INDEX_NAME).id(eventProcessMappingId);

    final GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest);
    } catch (final IOException e) {
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
      } catch (final IOException e) {
        final String reason =
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
    final String[] fieldsToExclude = new String[] {DbEventProcessMappingDto.Fields.xml};
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
    } catch (final IOException e) {
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
    } catch (final IOException e) {
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
      } catch (final IOException e) {
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
    final String id = eventProcessPublishStateDto.getId();
    final IndexResponse indexResponse;
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
    } catch (final IOException e) {
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
    } catch (final IOException e) {
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
      } catch (final IOException e) {
        final String reason =
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
  public void updateEntry(final String indexName, final String entityId, final ScriptData script) {
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
    } catch (final IOException e) {
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

    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder().query(query).size(1).fetchSource(null, PROCESS_DEFINITION_XML);

    final SearchRequest searchRequest =
        new SearchRequest(EVENT_PROCESS_DEFINITION_INDEX_NAME).source(searchSourceBuilder);

    final SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (final IOException e) {
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

    final SearchHit hit = searchResponse.getHits().getAt(0);
    final String sourceAsString = hit.getSourceAsString();
    try {
      final EventProcessDefinitionDto definitionDto =
          objectMapper.readValue(sourceAsString, EventProcessDefinitionDto.class);
      return Optional.of(definitionDto);
    } catch (final JsonProcessingException e) {
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
    final String[] fieldsToExclude = new String[] {DbEventProcessMappingDto.Fields.xml};
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
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Was not able to retrieve event-based processes!", e);
    }

    return ElasticsearchReaderUtil.retrieveAllScrollResults(
        scrollResp,
        EventProcessDefinitionDto.class,
        objectMapper,
        esClient,
        configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds());
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
      getMinAndMaxIngestedTimestampsForQuery(final AbstractQueryBuilder<?> query) {
    final SearchSourceBuilder searchSourceBuilder =
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
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(
          "Was not able to retrieve min and max event ingestion timestamps!", e);
    }
  }

  private CompositeAggregationBuilder buildCompositeGroupAggregation(
      final EventGroupRequestDto eventGroupRequestDto) {
    // We aggregate on the group name to return to user and the lower case name so we can sort
    // properly
    final List<CompositeValuesSourceBuilder<?>> eventGroupsAndLowercaseGroups = new ArrayList<>();
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

    final long totalHits;
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
      final FieldSortBuilder fieldSortBuilder =
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
    final String dateAsStr = aggregation.getValueAsString();
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
    } catch (final IOException e) {
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
