/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.os;

import static io.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static io.camunda.optimize.dto.optimize.query.sorting.SortOrder.ASC;
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
import static io.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.lt;
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
import io.camunda.optimize.service.db.os.OpenSearchCompositeAggregationScroller;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.externalcode.client.dsl.AggregationDSL;
import io.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL;
import io.camunda.optimize.service.db.os.externalcode.client.dsl.RequestDSL;
import io.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchDocumentOperations;
import io.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchDocumentOperations.AggregatedResult;
import io.camunda.optimize.service.db.os.reader.OpensearchReaderUtil;
import io.camunda.optimize.service.db.os.schema.index.events.CamundaActivityEventIndexOS;
import io.camunda.optimize.service.db.os.schema.index.events.EventIndexOS;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.repository.EventRepository;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.db.schema.ScriptData;
import io.camunda.optimize.service.db.schema.index.events.CamundaActivityEventIndex;
import io.camunda.optimize.service.db.schema.index.events.EventIndex;
import io.camunda.optimize.service.db.schema.index.events.EventProcessMappingIndex;
import io.camunda.optimize.service.db.schema.index.events.EventProcessPublishStateIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.IdGenerator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldSort;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.AggregationBuilders;
import org.opensearch.client.opensearch._types.aggregations.CompositeAggregation;
import org.opensearch.client.opensearch._types.aggregations.CompositeAggregationSource;
import org.opensearch.client.opensearch._types.aggregations.MaxAggregation;
import org.opensearch.client.opensearch._types.aggregations.MinAggregation;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;
import org.opensearch.client.opensearch._types.aggregations.TopHitsAggregate;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode;
import org.opensearch.client.opensearch._types.query_dsl.FunctionScore;
import org.opensearch.client.opensearch._types.query_dsl.FunctionScoreQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.MultiMatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.RandomScoreFunction;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.IndexRequest.Builder;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.SourceConfig;
import org.opensearch.client.opensearch.core.search.SourceFilter;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(OpenSearchCondition.class)
public class EventRepositoryOS implements EventRepository {
  private final OptimizeOpenSearchClient osClient;
  private final DateTimeFormatter formatter;
  private final ConfigurationService configurationService;
  private final DateTimeFormatter dateTimeFormatter;
  private final OptimizeIndexNameService indexNameService;
  private final ProcessDefinitionReader processDefinitionReader;

  @Override
  public void upsertEvents(List<EventDto> eventDtos) {
    final List<BulkOperation> bulkOperations =
        eventDtos.stream()
            .map(
                eventDto -> new BulkOperation.Builder().update(createEventUpsert(eventDto)).build())
            .toList();

    if (!bulkOperations.isEmpty()) {
      osClient.doBulkRequest(
          BulkRequest.Builder::new,
          bulkOperations,
          indexNameService.getOptimizeIndexAliasForIndex(
              DatabaseConstants.EXTERNAL_EVENTS_INDEX_NAME),
          false);
    }
  }

  @Override
  public void deleteEventsOlderThan(
      final OffsetDateTime timestamp, final String deletedItemIdentifier) {
    osClient.deleteByQueryTask(
        deletedItemIdentifier,
        lt(EventIndex.TIMESTAMP, dateTimeFormatter.format(timestamp)),
        false,
        // use wildcarded index name to catch all indices that exist after potential rollover
        osClient
            .getIndexNameService()
            .getOptimizeIndexNameWithVersionWithWildcardSuffix(new EventIndexOS()));
  }

  @Override
  public void deleteEventsWithIdsIn(
      final List<String> eventIdsToDelete, final String deletedItemIdentifier) {
    final Query filterQuery = QueryDSL.stringTerms(EventIndex.ID, eventIdsToDelete);
    osClient.deleteByQuery(
        filterQuery,
        true,
        // use wildcarded index name to catch all indices that exist after potential rollover
        osClient
            .getIndexNameService()
            .getOptimizeIndexNameWithVersionWithWildcardSuffix(new EventIndexOS()));
  }

  @Override
  public void deleteByProcessInstanceIds(
      final String definitionKey, final List<String> processInstanceIds) {
    final Query filterQuery =
        QueryDSL.stringTerms(CamundaActivityEventIndex.PROCESS_INSTANCE_ID, processInstanceIds);
    osClient.deleteByQueryTask(
        String.format("camunda activity events of %d process instances", processInstanceIds.size()),
        filterQuery,
        false,
        // use wildcarded index name to catch all indices that exist after potential rollover
        osClient
            .getIndexNameService()
            .getOptimizeIndexNameWithVersionWithWildcardSuffix(
                new CamundaActivityEventIndexOS(definitionKey)));
  }

  @Override
  public List<CamundaActivityEventDto> getPageOfEventsForDefinitionKeySortedByTimestamp(
      final String definitionKey,
      final Pair<Long, Long> timestampRange,
      final int limit,
      final TimeRangeRequest mode) {
    final Query timestampQuery;
    if (mode.equals(TimeRangeRequest.AT)) {
      timestampQuery =
          QueryDSL.gteLte(
              CamundaActivityEventIndex.TIMESTAMP,
              formatter.format(convertToOffsetDateTime(timestampRange.getLeft())),
              formatter.format(convertToOffsetDateTime(timestampRange.getRight())));
    } else if (mode.equals(TimeRangeRequest.AFTER)) {
      timestampQuery =
          QueryDSL.gt(
              CamundaActivityEventIndex.TIMESTAMP,
              formatter.format(convertToOffsetDateTime(timestampRange.getLeft())));
    } else {
      timestampQuery =
          QueryDSL.gtLt(
              CamundaActivityEventIndex.TIMESTAMP,
              formatter.format(convertToOffsetDateTime(timestampRange.getLeft())),
              formatter.format(convertToOffsetDateTime(timestampRange.getRight())));
    }

    final SearchRequest.Builder searchRequest =
        RequestDSL.searchRequestBuilder(CamundaActivityEventIndex.constructIndexName(definitionKey))
            .query(timestampQuery)
            .sort(
                new SortOptions.Builder()
                    .field(
                        new FieldSort.Builder()
                            .field(CamundaActivityEventIndex.TIMESTAMP)
                            .order(SortOrder.Asc)
                            .build())
                    .build())
            .size(limit);

    return osClient
        .getRichOpenSearchClient()
        .doc()
        .searchValues(searchRequest, CamundaActivityEventDto.class);
  }

  @Override
  public Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>>
      getMinAndMaxIngestedTimestampsForDefinition(final String processDefinitionKey) {
    log.debug("Fetching min and max timestamp for ingested camunda events");
    final SearchRequest.Builder request =
        RequestDSL.searchRequestBuilder(
                CamundaActivityEventIndex.constructIndexName(processDefinitionKey))
            .query(QueryDSL.matchAll())
            .aggregations(
                MIN_AGG,
                AggregationBuilders.min()
                    .field(CamundaActivityEventIndex.TIMESTAMP)
                    .format(OPTIMIZE_DATE_FORMAT)
                    .build()
                    ._toAggregation())
            .aggregations(
                MAX_AGG,
                AggregationBuilders.max()
                    .field(CamundaActivityEventIndex.TIMESTAMP)
                    .format(OPTIMIZE_DATE_FORMAT)
                    .build()
                    ._toAggregation())
            .size(0);

    final String indexName = CamundaActivityEventIndex.constructIndexName(processDefinitionKey);
    final boolean indexExists = osClient.getRichOpenSearchClient().index().indexExists(indexName);
    if (indexExists) {
      final Map<String, Aggregate> searchResponse =
          osClient.getRichOpenSearchClient().doc().searchAggregations(request);
      return ImmutablePair.of(
          extractTimestampForAggregation(searchResponse.get(MIN_AGG).min().value()),
          extractTimestampForAggregation(searchResponse.get(MAX_AGG).max().value()));
    } else {
      log.debug("{} Index does not exist", indexName);
      return ImmutablePair.of(Optional.empty(), Optional.empty());
    }
  }

  @Override
  public Optional<EventProcessMappingDto> getEventProcessMapping(
      final String eventProcessMappingId) {
    final String errorMessage =
        String.format("Could not fetch event-based process with id [%s].", eventProcessMappingId);
    GetResponse<DbEventProcessMappingDto> response =
        osClient.get(
            EVENT_PROCESS_MAPPING_INDEX_NAME,
            eventProcessMappingId,
            DbEventProcessMappingDto.class,
            errorMessage);

    return response.found() && response.source() != null
        ? Optional.of(response.source().toEventProcessMappingDto())
        : Optional.empty();
  }

  @Override
  public List<EventProcessMappingDto> getAllEventProcessMappingsOmitXml() {
    final SearchRequest.Builder searchBuilder =
        createSearchRequestForEventOmitXml(EVENT_PROCESS_MAPPING_INDEX_NAME);

    final OpenSearchDocumentOperations.AggregatedResult<Hit<DbEventProcessMappingDto>> scrollResp;
    try {
      scrollResp = osClient.retrieveAllScrollResults(searchBuilder, DbEventProcessMappingDto.class);
    } catch (final IOException e) {
      log.error("Was not able to retrieve event-based processes!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve event-based processes!", e);
    }
    return OpensearchReaderUtil.extractAggregatedResponseValues(scrollResp).stream()
        .map(DbEventProcessMappingDto::toEventProcessMappingDto)
        .toList();
  }

  @Override
  public List<EventProcessRoleRequestDto<IdentityDto>> getEventProcessRoles(
      final String eventProcessMappingId) {
    GetRequest.Builder getRequest =
        new GetRequest.Builder()
            .sourceIncludes(EventProcessMappingIndex.ROLES)
            .index(EVENT_PROCESS_MAPPING_INDEX_NAME)
            .id(eventProcessMappingId);

    final String errorMessage =
        String.format(
            "Could not fetch roles for event-based process with id [%s].", eventProcessMappingId);

    GetResponse<DbEventProcessMappingDto> response =
        osClient.get(getRequest, DbEventProcessMappingDto.class, errorMessage);

    List<EventProcessRoleRequestDto<IdentityDto>> result = Collections.emptyList();
    if (response.found() && response.source() != null) {
      result = response.source().getRoles();
    }
    return result;
  }

  @Override
  public IdResponseDto createEventProcessPublishState(
      final EventProcessPublishStateDto eventProcessPublishStateDto) {
    String id = eventProcessPublishStateDto.getId();
    Builder<Object> indexRequest =
        RequestDSL.indexRequestBuilder(EVENT_PROCESS_PUBLISH_STATE_INDEX_NAME)
            .id(id)
            .document(
                DbEventProcessPublishStateDto.fromEventProcessPublishStateDto(
                    eventProcessPublishStateDto));
    IndexResponse response = osClient.getRichOpenSearchClient().doc().index(indexRequest);
    if (!response.result().equals(Result.Created)) {
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
    return osClient.updateByQuery(
            indexName,
            QueryDSL.term(idFieldName, eventProcessMappingId),
            QueryDSL.script(scriptData.scriptString(), scriptData.params()))
        > 0L;
  }

  @Override
  public void markAsDeletedPublishStatesForEventProcessMappingIdExcludingPublishStateId(
      final String eventProcessMappingId,
      final String updateItem,
      final ScriptData scriptData,
      final String indexName,
      final String publishStateIdToExclude) {
    Query mustMatchQuery =
        QueryDSL.term(EventProcessPublishStateIndex.PROCESS_MAPPING_ID, eventProcessMappingId);
    Query mustNotMatchQuery =
        QueryDSL.not(QueryDSL.term(EventProcessPublishStateIndex.ID, publishStateIdToExclude));
    Query finalQuery = QueryDSL.and(mustMatchQuery, mustNotMatchQuery);
    osClient.updateByQuery(
        indexName, finalQuery, QueryDSL.script(scriptData.scriptString(), scriptData.params()));
  }

  @Override
  public Optional<EventProcessPublishStateDto> getEventProcessPublishStateByEventProcessId(
      final String eventProcessMappingId) {
    Query query =
        QueryDSL.and(
            QueryDSL.term(EventProcessPublishStateIndex.PROCESS_MAPPING_ID, eventProcessMappingId),
            QueryDSL.term(EventProcessPublishStateIndex.DELETED, false));
    SearchRequest.Builder searchRequest =
        RequestDSL.searchRequestBuilder(EVENT_PROCESS_PUBLISH_STATE_INDEX_NAME)
            .query(query)
            .sort(
                new SortOptions.Builder()
                    .field(
                        new FieldSort.Builder()
                            .order(SortOrder.Desc)
                            .field(EventProcessPublishStateIndex.PUBLISH_DATE_TIME)
                            .build())
                    .build())
            .size(1);
    final String errorMessage =
        String.format(
            "Could not fetch event process publish state with id [%s].", eventProcessMappingId);
    SearchResponse<DbEventProcessPublishStateDto> response =
        osClient.search(searchRequest, DbEventProcessPublishStateDto.class, errorMessage);
    if (!response.hits().hits().isEmpty() && response.hits().hits().get(0).source() != null) {
      return Optional.of(response.hits().hits().get(0).source().toEventProcessPublishStateDto());
    }
    return Optional.empty();
  }

  @Override
  public List<EventProcessPublishStateDto> getAllEventProcessPublishStatesWithDeletedState(
      final boolean deleted) {
    Query query = QueryDSL.term(EventProcessPublishStateIndex.DELETED, deleted);
    SearchRequest.Builder searchRequest =
        RequestDSL.searchRequestBuilder(EVENT_PROCESS_PUBLISH_STATE_INDEX_NAME)
            .query(query)
            .size(LIST_FETCH_LIMIT)
            .scroll(
                RequestDSL.time(
                    String.valueOf(
                        configurationService
                            .getOpenSearchConfiguration()
                            .getScrollTimeoutInSeconds())));
    final OpenSearchDocumentOperations.AggregatedResult<Hit<DbEventProcessPublishStateDto>>
        scrollResp;
    try {
      scrollResp =
          osClient.retrieveAllScrollResults(searchRequest, DbEventProcessPublishStateDto.class);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(
          "Was not able to retrieve event process publish states!", e);
    }
    return OpensearchReaderUtil.extractAggregatedResponseValues(scrollResp).stream()
        .map(DbEventProcessPublishStateDto::toEventProcessPublishStateDto)
        .collect(Collectors.toList());
  }

  @Override
  public void updateEntry(String indexName, String entityId, ScriptData script) {
    osClient.update(indexName, entityId, script);
  }

  @Override
  public Optional<EventProcessDefinitionDto> getEventProcessDefinitionByKeyOmitXml(
      final String eventProcessDefinitionKey) {
    final Query query = QueryDSL.term(DEFINITION_KEY, eventProcessDefinitionKey);

    final SourceConfig searchSourceBuilder =
        new SourceConfig.Builder()
            .filter(new SourceFilter.Builder().excludes(PROCESS_DEFINITION_XML).build())
            .build();

    SearchRequest.Builder searchRequest =
        new SearchRequest.Builder()
            .index(EVENT_PROCESS_DEFINITION_INDEX_NAME)
            .query(query)
            .source(searchSourceBuilder)
            .size(1);

    final String errorMessage =
        String.format(
            "Could not fetch event-based process definition with key [%s].",
            eventProcessDefinitionKey);

    SearchResponse<EventProcessDefinitionDto> searchResponse =
        osClient.search(searchRequest, EventProcessDefinitionDto.class, errorMessage);

    if (!searchResponse.hits().hits().isEmpty()) {
      return Optional.ofNullable(searchResponse.hits().hits().get(0).source());
    }
    return Optional.empty();
  }

  @Override
  public List<EventProcessDefinitionDto> getAllEventProcessDefinitionsOmitXml() {
    final SearchRequest.Builder searchBuilder =
        createSearchRequestForEventOmitXml(EVENT_PROCESS_DEFINITION_INDEX_NAME);

    final OpenSearchDocumentOperations.AggregatedResult<Hit<EventProcessDefinitionDto>> scrollResp;
    try {
      scrollResp =
          osClient.retrieveAllScrollResults(searchBuilder, EventProcessDefinitionDto.class);
    } catch (final IOException e) {
      log.error("Was not able to retrieve event-based processes!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve event-based processes!", e);
    }
    return OpensearchReaderUtil.extractAggregatedResponseValues(scrollResp).stream().toList();
  }

  @Override
  public List<EventDto> getEventsIngestedAfter(final Long ingestTimestamp, final int limit) {
    final Query timestampQuery = QueryDSL.gt(INGESTION_TIMESTAMP, ingestTimestamp);
    return getPageOfEventsSortedByIngestionTimestamp(timestampQuery, limit);
  }

  @Override
  public List<EventDto> getEventsIngestedAfterForGroups(
      final Long ingestTimestamp, final int limit, final List<String> groups) {
    final Query query =
        QueryDSL.and(
            QueryDSL.gt(INGESTION_TIMESTAMP, ingestTimestamp), buildGroupFilterQuery(groups));
    return getPageOfEventsSortedByIngestionTimestamp(query, limit);
  }

  @Override
  public List<EventDto> getEventsIngestedAt(final Long ingestTimestamp) {
    final Query timestampQuery =
        QueryDSL.gteLte(INGESTION_TIMESTAMP, ingestTimestamp, ingestTimestamp);
    return getPageOfEventsSortedByIngestionTimestamp(timestampQuery, MAX_RESPONSE_SIZE_LIMIT);
  }

  @Override
  public List<EventDto> getEventsIngestedAtForGroups(
      final Long ingestTimestamp, final List<String> groups) {
    final Query query =
        QueryDSL.and(
            buildGroupFilterQuery(groups),
            QueryDSL.gteLte(INGESTION_TIMESTAMP, ingestTimestamp, ingestTimestamp));
    return getPageOfEventsSortedByIngestionTimestamp(query, MAX_RESPONSE_SIZE_LIMIT);
  }

  @Override
  public Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>> getMinAndMaxIngestedTimestamps() {
    return getMinAndMaxIngestedTimestampsForQuery(QueryDSL.matchAll());
  }

  @Override
  public Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>>
      getMinAndMaxIngestedTimestampsForGroups(final List<String> groups) {
    return getMinAndMaxIngestedTimestampsForQuery(buildGroupFilterQuery(groups));
  }

  @Override
  public Page<DeletableEventDto> getEventsForRequest(
      final EventSearchRequestDto eventSearchRequestDto) {

    final SearchRequest.Builder searchRequest =
        new SearchRequest.Builder()
            .index(EXTERNAL_EVENTS_INDEX_NAME)
            .query(getSearchQueryForEventRequest(eventSearchRequestDto))
            .from(eventSearchRequestDto.getPaginationRequestDto().getOffset())
            .size(eventSearchRequestDto.getPaginationRequestDto().getLimit());
    getSortForEventRequest(eventSearchRequestDto.getSortRequestDto())
        .ifPresent(searchRequest::sort);
    // add secondary sort order
    searchRequest.sort(
        new SortOptions.Builder()
            .field(new FieldSort.Builder().field(TIMESTAMP).order(SortOrder.Desc).build())
            .build());

    return toPage(
        eventSearchRequestDto,
        osClient.search(searchRequest, EventDto.class, "Was not able to retrieve events!"));
  }

  @Override
  public List<String> getEventGroups(final EventGroupRequestDto eventGroupRequestDto) {
    final String searchTerm = eventGroupRequestDto.getSearchTerm();
    Query query;
    if (searchTerm == null) {
      query = QueryDSL.matchAll();
    } else if (searchTerm.length() > MAX_GRAM) {
      query = QueryDSL.prefix(GROUP, searchTerm);
    } else {
      query =
          new MatchQuery.Builder()
              .field(getNgramSearchField(GROUP))
              .query(FieldValue.of(searchTerm.toLowerCase()))
              .analyzer(KEYWORD_ANALYZER)
              .build()
              .toQuery();
    }

    List<String> groups = new ArrayList<>();
    OpenSearchCompositeAggregationScroller.create()
        .setClient(osClient)
        .query(query)
        .index(List.of(EXTERNAL_EVENTS_INDEX_NAME))
        .aggregations(
            Collections.singletonMap(
                GROUP_COMPOSITE_AGG,
                buildCompositeGroupAggregation(eventGroupRequestDto)._toAggregation()))
        .size(0)
        .setPathToAggregation(GROUP_COMPOSITE_AGG)
        .setCompositeBucketConsumer(
            bucket -> groups.add((bucket.key().get(EVENT_GROUP_AGG)).to(String.class)))
        .consumePage();
    return groups;
  }

  @Override
  public List<String> getCorrelationValueSampleForEventSources(
      List<CamundaEventSourceEntryDto> camundaSources) {
    final Query completedInstanceQuery = QueryDSL.exists(END_DATE);
    final BoolQuery.Builder matchesSourceQuery = new BoolQuery.Builder().minimumShouldMatch("1");
    camundaSources.forEach(
        source -> matchesSourceQuery.should(queryForEventSourceInstances(source)));

    SearchRequest.Builder searchRequest =
        new SearchRequest.Builder()
            .index(Arrays.asList(getInstanceIndexNames(camundaSources)))
            .query(
                new BoolQuery.Builder()
                    .filter(completedInstanceQuery)
                    .filter(matchesSourceQuery.build().toQuery())
                    .must(createFunctionScoreQuery())
                    .build()
                    .toQuery())
            .size(0);

    addCorrelationValuesAggregation(searchRequest, camundaSources);

    SearchResponse<String> searchResponse =
        osClient.search(
            searchRequest, String.class, "Was not able to fetch sample correlation values");
    return extractCorrelationValues(searchResponse.aggregations(), camundaSources);
  }

  private static Query createFunctionScoreQuery() {
    Query functionScoreQuery =
        new FunctionScoreQuery.Builder()
            .query(QueryDSL.matchAll())
            .functions(
                new FunctionScore.Builder()
                    .randomScore(new RandomScoreFunction.Builder().build())
                    .build())
            .build()
            .toQuery();
    return functionScoreQuery;
  }

  @Override
  public List<CorrelatableProcessInstanceDto> getCorrelatableInstancesForSources(
      List<CamundaEventSourceEntryDto> camundaSources, List<String> correlationValues) {
    final Query completedInstanceQuery = QueryDSL.exists(END_DATE);
    final BoolQuery.Builder matchesSourceQuery = new BoolQuery.Builder().minimumShouldMatch("1");
    camundaSources.forEach(
        eventSource ->
            matchesSourceQuery.should(
                queryForEventSourceInstancesWithCorrelationValues(eventSource, correlationValues)));

    SearchRequest.Builder searchRequest =
        new SearchRequest.Builder()
            .index(Arrays.asList(getInstanceIndexNames(camundaSources)))
            .query(
                new BoolQuery.Builder()
                    .filter(completedInstanceQuery)
                    .filter(matchesSourceQuery.build().toQuery())
                    .must(createFunctionScoreQuery())
                    .build()
                    .toQuery())
            .size(MAX_RESPONSE_SIZE_LIMIT)
            .scroll(
                RequestDSL.time(
                    String.valueOf(
                        configurationService
                            .getOpenSearchConfiguration()
                            .getScrollTimeoutInSeconds())));

    try {
      AggregatedResult<Hit<CorrelatableProcessInstanceDto>> scrollResp =
          osClient.retrieveAllScrollResults(searchRequest, CorrelatableProcessInstanceDto.class);
      return OpensearchReaderUtil.extractAggregatedResponseValues(scrollResp).stream().toList();
    } catch (IOException e) {
      String reason = "Was not able to fetch instances for correlation values";
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    } catch (RuntimeException e) {
      if (isInstanceIndexNotFoundException(PROCESS, e)) {
        log.info(
            "Was not able to fetch instances for correlation values because no instance indices exist. "
                + "Returning empty list.");
        return Collections.emptyList();
      }
      throw e;
    }
  }

  private List<String> extractCorrelationValues(
      final Map<String, Aggregate> aggregations,
      final List<CamundaEventSourceEntryDto> eventSources) {
    final Aggregate valuesByProcessDefinition = aggregations.get(EVENT_SOURCE_AGG);
    List<String> correlationValues = new ArrayList<>();
    for (StringTermsBucket eventSourceBucket :
        valuesByProcessDefinition.sterms().buckets().array()) {
      CamundaEventSourceEntryDto eventSourceForCurrentBucket =
          eventSources.stream()
              .filter(
                  source ->
                      source
                          .getConfiguration()
                          .getProcessDefinitionKey()
                          .equals(eventSourceBucket.key()))
              .findFirst()
              .orElseThrow(
                  () ->
                      new OptimizeRuntimeException(
                          String.format(
                              "Could not find event source for bucket with key %s when sampling for correlation values",
                              eventSourceBucket.key())));
      TopHitsAggregate topHits = eventSourceBucket.aggregations().get(BUCKET_HITS_AGG).topHits();
      for (Hit<JsonData> hit : topHits.hits().hits()) {
        final CorrelationValueDto correlationValueDto = hit.source().to(CorrelationValueDto.class);
        Optional<String> correlationValueToAdd =
            extractCorrelationValue(eventSourceForCurrentBucket, correlationValueDto);
        if (correlationValueToAdd.isPresent()) {
          correlationValues.add(correlationValueToAdd.get());
        } else {
          log.warn(
              "Could not find correlation value to use in sample from {}", correlationValueDto);
        }
      }
    }
    return correlationValues;
  }

  private void addCorrelationValuesAggregation(
      final SearchRequest.Builder searchSourceBuilder,
      final List<CamundaEventSourceEntryDto> eventSources) {
    TermsAggregation correlationValuesAggregation =
        new TermsAggregation.Builder()
            .field(PROCESS_DEFINITION_KEY)
            .size(eventSources.size())
            .build();
    Map<String, Aggregation> subAggregations = new HashMap<>();
    // We use top hits only to access the documents in the bucket, which will be random
    // rather than scored
    subAggregations.put(
        BUCKET_HITS_AGG,
        AggregationDSL.topHitsAggregation(List.of(CORRELATABLE_FIELDS), MAX_HITS)._toAggregation());
    searchSourceBuilder.aggregations(
        Map.of(
            EVENT_SOURCE_AGG,
            AggregationDSL.withSubaggregations(correlationValuesAggregation, subAggregations)));
  }

  private Query queryForEventSourceInstances(final CamundaEventSourceEntryDto eventSource) {
    return new BoolQuery.Builder()
        .filter(versionsQuery(eventSource))
        .filter(tenantsQuery(eventSource))
        .build()
        .toQuery();
  }

  private Query queryForEventSourceInstancesWithCorrelationValues(
      final CamundaEventSourceEntryDto eventSource, final List<String> correlationValues) {
    final BoolQuery.Builder eventSourceQuery =
        new BoolQuery.Builder()
            .filter(
                QueryDSL.term(
                    PROCESS_DEFINITION_KEY,
                    eventSource.getConfiguration().getProcessDefinitionKey()))
            .filter(versionsQuery(eventSource))
            .filter(tenantsQuery(eventSource));
    if (eventSource.getConfiguration().isTracedByBusinessKey()) {
      eventSourceQuery.filter(QueryDSL.stringTerms(BUSINESS_KEY, correlationValues));
    } else {
      eventSourceQuery.filter(
          QueryDSL.nested(
              VARIABLES,
              new BoolQuery.Builder()
                  .filter(
                      QueryDSL.term(
                          getNestedVariableNameField(),
                          eventSource.getConfiguration().getTraceVariable()))
                  .filter(QueryDSL.stringTerms(getNestedVariableValueField(), correlationValues))
                  .build()
                  .toQuery(),
              ChildScoreMode.None));
    }
    return eventSourceQuery.build().toQuery();
  }

  private Query versionsQuery(final CamundaEventSourceEntryDto eventSource) {
    final CamundaEventSourceConfigDto eventSourceConfig = eventSource.getConfiguration();
    final BoolQuery.Builder versionQuery = new BoolQuery.Builder();
    if (isDefinitionVersionSetToLatest(eventSourceConfig.getVersions())) {
      versionQuery.must(
          QueryDSL.term(
              PROCESS_DEFINITION_VERSION,
              processDefinitionReader.getLatestVersionToKey(
                  eventSource.getConfiguration().getProcessDefinitionKey())));
    } else if (!isDefinitionVersionSetToAll(eventSourceConfig.getVersions())) {
      versionQuery.must(
          QueryDSL.stringTerms(PROCESS_DEFINITION_VERSION, eventSourceConfig.getVersions()));
    } else if (eventSourceConfig.getVersions().isEmpty()) {
      versionQuery.mustNot(QueryDSL.exists(PROCESS_DEFINITION_VERSION));
    }
    return versionQuery.build().toQuery();
  }

  private Query tenantsQuery(final CamundaEventSourceEntryDto eventSource) {
    final CamundaEventSourceConfigDto eventSourceConfig = eventSource.getConfiguration();
    final BoolQuery.Builder tenantQuery = new BoolQuery.Builder();
    if (eventSourceConfig.getTenants().contains(null) || eventSourceConfig.getTenants().isEmpty()) {
      tenantQuery.should(QueryDSL.not(QueryDSL.exists(TENANT_ID)));
    }
    final List<String> nonNullTenants =
        eventSourceConfig.getTenants().stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    if (!nonNullTenants.isEmpty()) {
      tenantQuery.should(QueryDSL.stringTerms(TENANT_ID, nonNullTenants));
    }
    return tenantQuery.build().toQuery();
  }

  private Query buildGroupFilterQuery(final List<String> groups) {
    final List<String> nonNullGroups =
        groups.stream().filter(Objects::nonNull).collect(Collectors.toList());
    final boolean includeNull = groups.size() > nonNullGroups.size();
    final BoolQuery.Builder groupFilterQuery = new BoolQuery.Builder().minimumShouldMatch("1");
    boolean filterActive = false;
    if (!nonNullGroups.isEmpty()) {
      groupFilterQuery.should(QueryDSL.stringTerms(GROUP, nonNullGroups));
      filterActive = true;
    }
    if (includeNull) {
      groupFilterQuery.should(QueryDSL.not(QueryDSL.exists(GROUP)));
      filterActive = true;
    }
    if (filterActive) {
      return new BoolQuery.Builder().filter(groupFilterQuery.build().toQuery()).build().toQuery();
    } else {
      return QueryDSL.matchAll();
    }
  }

  private Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>>
      getMinAndMaxIngestedTimestampsForQuery(Query query) {

    final SourceConfig searchSourceBuilder = new SourceConfig.Builder().fetch(false).build();

    HashMap<String, Aggregation> aggs = new HashMap<>();
    aggs.put(
        MIN_AGG, new MinAggregation.Builder().field(INGESTION_TIMESTAMP).build()._toAggregation());
    aggs.put(
        MAX_AGG, new MaxAggregation.Builder().field(INGESTION_TIMESTAMP).build()._toAggregation());
    SearchRequest.Builder searchRequest =
        new SearchRequest.Builder()
            .query(query)
            .index(EXTERNAL_EVENTS_INDEX_NAME)
            .source(searchSourceBuilder)
            .aggregations(aggs)
            .size(0);

    SearchResponse<EventDto> searchResponse =
        osClient.search(
            searchRequest,
            EventDto.class,
            "Was not able to retrieve min and max event ingestion timestamps!");
    return ImmutablePair.of(
        extractTimestampForAggregation(searchResponse.aggregations().get(MIN_AGG).min().value()),
        extractTimestampForAggregation(searchResponse.aggregations().get(MAX_AGG).max().value()));
  }

  private CompositeAggregation buildCompositeGroupAggregation(
      final EventGroupRequestDto eventGroupRequestDto) {
    // We aggregate on the group name to return to user and the lower case name so we can sort
    // properly
    List<Map<String, CompositeAggregationSource>> eventGroupsAndLowercaseGroups = new ArrayList<>();
    eventGroupsAndLowercaseGroups.add(
        Map.of(
            LOWERCASE_GROUP_AGG,
            AggregationDSL.compositeTermsAggregationSource(
                new TermsAggregation.Builder()
                    .field(GROUP + "." + DefaultIndexMappingCreator.LOWERCASE)
                    .missingBucket(true)
                    .build())));
    eventGroupsAndLowercaseGroups.add(
        Map.of(
            EVENT_GROUP_AGG,
            AggregationDSL.compositeTermsAggregationSource(
                new TermsAggregation.Builder().field(GROUP).missingBucket(true).build())));
    int size =
        Math.min(
            eventGroupRequestDto.getLimit(),
            configurationService.getOpenSearchConfiguration().getAggregationBucketLimit());
    return new CompositeAggregation.Builder()
        .size(size)
        .sources(eventGroupsAndLowercaseGroups)
        .build();
  }

  private Page<DeletableEventDto> toPage(
      final EventSearchRequestDto eventSearchRequestDto,
      final SearchResponse<EventDto> searchResponse) {

    long totalHits;
    final List<EventDto> eventsForRequest;
    if (Objects.isNull(searchResponse.hits().total())) {
      log.warn("Could not extract the total hits from SearchResponse");
      totalHits = 0;
      eventsForRequest = Collections.emptyList();
    } else {
      totalHits = searchResponse.hits().total().value();
      eventsForRequest = searchResponse.hits().hits().stream().map(Hit::source).toList();
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

  private Query getSearchQueryForEventRequest(final EventSearchRequestDto eventSearchRequestDto) {
    final String searchTerm = eventSearchRequestDto.getSearchTerm();
    if (eventSearchRequestDto.getSearchTerm() == null) {
      return QueryDSL.matchAll();
    }

    if (searchTerm.length() > MAX_GRAM) {
      return QueryDSL.or(
          "1",
          QueryDSL.prefix(GROUP, searchTerm),
          QueryDSL.prefix(SOURCE, searchTerm),
          QueryDSL.prefix(EVENT_NAME, searchTerm),
          QueryDSL.prefix(TRACE_ID, searchTerm));
    }

    return new MultiMatchQuery.Builder()
        .fields(
            getNgramSearchField(GROUP),
            getNgramSearchField(SOURCE),
            getNgramSearchField(EVENT_NAME),
            getNgramSearchField(TRACE_ID))
        .query(searchTerm.toLowerCase())
        .analyzer(KEYWORD_ANALYZER)
        .build()
        .toQuery();
  }

  private Optional<SortOptions> getSortForEventRequest(final SortRequestDto sortRequestDto) {
    final Optional<String> sortByOpt = sortRequestDto.getSortBy();
    if (sortByOpt.isPresent()) {
      FieldSort.Builder fieldSortBuilder =
          new FieldSort.Builder().field(convertToIndexSortField(sortByOpt.get()));

      sortRequestDto
          .getSortOrder()
          .ifPresent(
              order -> {
                // This makes sure that nullable fields respect the sort order
                if (io.camunda.optimize.dto.optimize.query.sorting.SortOrder.ASC.equals(order)) {
                  fieldSortBuilder
                      .order(optimizeToOpenSearchSortOrder(order))
                      .missing(FieldValue.of(SORT_NULLS_FIRST));
                } else {
                  fieldSortBuilder
                      .order(optimizeToOpenSearchSortOrder(order))
                      .missing(FieldValue.of(SORT_NULLS_LAST));
                }
              });
      return Optional.of(new SortOptions.Builder().field(fieldSortBuilder.build()).build());
    }
    return Optional.empty();
  }

  private SortOrder optimizeToOpenSearchSortOrder(
      io.camunda.optimize.dto.optimize.query.sorting.SortOrder order) {
    if (order.equals(DESC)) {
      return SortOrder.Desc;
    }
    if (order.equals(ASC)) {
      return SortOrder.Asc;
    }
    throw new OptimizeRuntimeException("Unexpected sort order: " + order);
  }

  private List<EventDto> getPageOfEventsSortedByIngestionTimestamp(
      final Query query, final int limit) {

    final SearchRequest.Builder searchRequest =
        new SearchRequest.Builder()
            .index(EXTERNAL_EVENTS_INDEX_NAME)
            .query(query)
            .size(limit)
            .sort(
                new SortOptions.Builder()
                    .field(
                        new FieldSort.Builder()
                            .field(INGESTION_TIMESTAMP)
                            .order(SortOrder.Asc)
                            .build())
                    .build());
    SearchResponse<EventDto> searchResponse =
        osClient.search(
            searchRequest,
            EventDto.class,
            "Was not able to retrieve ingested events by timestamp!");

    return searchResponse.hits().hits().stream().map(Hit::source).toList();
  }

  private SearchRequest.Builder createSearchRequestForEventOmitXml(
      String eventProcessDefinitionIndexName) {
    List<String> fieldsToExclude = List.of(DbEventProcessMappingDto.Fields.xml);

    final SourceConfig searchSourceBuilder =
        new SourceConfig.Builder()
            .filter(new SourceFilter.Builder().excludes(fieldsToExclude).build())
            .build();

    return new SearchRequest.Builder()
        .index(eventProcessDefinitionIndexName)
        .query(QueryDSL.matchAll())
        .source(searchSourceBuilder)
        .size(LIST_FETCH_LIMIT)
        .scroll(
            RequestDSL.time(
                String.valueOf(
                    configurationService
                        .getOpenSearchConfiguration()
                        .getScrollTimeoutInSeconds())));
  }

  private Optional<OffsetDateTime> extractTimestampForAggregation(final String dateAsStr) {
    return parseDateString(dateAsStr, formatter);
  }

  private Optional<OffsetDateTime> extractTimestampForAggregation(final Double timestamp) {
    try {
      Instant instant = Instant.ofEpochMilli(timestamp.longValue());
      return Optional.of(OffsetDateTime.ofInstant(instant, ZoneId.systemDefault()));
    } catch (final Exception ex) {
      log.warn(
          "Unexpected error processing the {} camunda activity ingestion timestamp.", timestamp);
      return Optional.empty();
    }
  }

  private UpdateOperation<EventDto> createEventUpsert(final EventDto eventDto) {
    return new UpdateOperation.Builder<EventDto>()
        .index(
            indexNameService.getOptimizeIndexAliasForIndex(
                DatabaseConstants.EXTERNAL_EVENTS_INDEX_NAME))
        .id(IdGenerator.getNextId())
        .document(eventDto)
        .docAsUpsert(true)
        .build();
  }
}
