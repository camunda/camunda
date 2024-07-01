/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository;

import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.BUSINESS_KEY;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;
import static io.camunda.optimize.service.db.schema.index.events.EventIndex.EVENT_NAME;
import static io.camunda.optimize.service.db.schema.index.events.EventIndex.GROUP;
import static io.camunda.optimize.service.db.schema.index.events.EventIndex.N_GRAM_FIELD;
import static io.camunda.optimize.service.db.schema.index.events.EventIndex.SOURCE;
import static io.camunda.optimize.service.db.schema.index.events.EventIndex.TIMESTAMP;
import static io.camunda.optimize.service.db.schema.index.events.EventIndex.TRACE_ID;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;

import com.google.common.collect.ImmutableMap;
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
import io.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceConfigDto;
import io.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceEntryDto;
import io.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import io.camunda.optimize.dto.optimize.rest.Page;
import io.camunda.optimize.service.db.schema.ScriptData;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

public interface EventRepository {

  String MIN_AGG = "min";
  String MAX_AGG = "max";
  String KEYWORD_ANALYZER = "keyword";

  Map<String, String> sortableFieldLookup =
      ImmutableMap.of(
          EventDto.Fields.group.toLowerCase(), GROUP,
          EventDto.Fields.source.toLowerCase(), SOURCE,
          EventDto.Fields.eventName.toLowerCase(), EVENT_NAME,
          EventDto.Fields.traceId.toLowerCase(), TRACE_ID,
          EventDto.Fields.timestamp.toLowerCase(), TIMESTAMP);

  String EVENT_GROUP_AGG = "eventGroupAggregation";
  String LOWERCASE_GROUP_AGG = "lowercaseGroupAggregation";
  String GROUP_COMPOSITE_AGG = "compositeAggregation";

  String EVENT_SOURCE_AGG = "eventSourceAgg";
  String BUCKET_HITS_AGG = "bucketHitsAgg";
  String[] CORRELATABLE_FIELDS = {BUSINESS_KEY, VARIABLES};
  int MAX_HITS = 100;

  void upsertEvents(List<EventDto> eventDtos);

  void deleteEventsOlderThan(OffsetDateTime timestamp, String deletedItemIdentifier);

  void deleteEventsWithIdsIn(List<String> eventIdsToDelete, String deletedItemIdentifier);

  List<EventDto> getEventsIngestedAfter(Long ingestTimestamp, int limit);

  List<EventDto> getEventsIngestedAfterForGroups(
      Long ingestTimestamp, int limit, List<String> groups);

  Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>> getMinAndMaxIngestedTimestamps();

  List<EventDto> getEventsIngestedAtForGroups(Long ingestTimestamp, List<String> groups);

  List<EventDto> getEventsIngestedAt(Long ingestTimestamp);

  Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>> getMinAndMaxIngestedTimestampsForGroups(
      List<String> groups);

  Page<DeletableEventDto> getEventsForRequest(EventSearchRequestDto eventSearchRequestDto);

  List<String> getEventGroups(EventGroupRequestDto eventGroupRequestDto);

  List<String> getCorrelationValueSampleForEventSources(
      List<CamundaEventSourceEntryDto> camundaSources);

  List<CorrelatableProcessInstanceDto> getCorrelatableInstancesForSources(
      List<CamundaEventSourceEntryDto> camundaSources, List<String> correlationValues);

  enum TimeRangeRequest {
    AT,
    BETWEEN,
    AFTER
  }

  default OffsetDateTime convertToOffsetDateTime(final Long eventTimestamp) {
    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(eventTimestamp), ZoneId.systemDefault());
  }

  void deleteByProcessInstanceIds(
      final String definitionKey, final List<String> processInstanceIds);

  List<CamundaActivityEventDto> getPageOfEventsForDefinitionKeySortedByTimestamp(
      final String definitionKey,
      final Pair<Long, Long> timestampRange,
      final int limit,
      final TimeRangeRequest mode);

  default List<CamundaActivityEventDto> getPageOfEventsForDefinitionKeySortedByTimestamp(
      final String definitionKey,
      final Long timestamp,
      final int limit,
      final TimeRangeRequest mode) {
    if (mode.equals(TimeRangeRequest.AT)) {
      return getPageOfEventsForDefinitionKeySortedByTimestamp(
          definitionKey, Pair.of(timestamp, timestamp), limit, mode);
    } else if (mode.equals(TimeRangeRequest.AFTER)) {
      return getPageOfEventsForDefinitionKeySortedByTimestamp(
          definitionKey, Pair.of(timestamp, Long.MAX_VALUE), limit, mode);
    } else {
      throw new IllegalArgumentException(
          "When using the between mode, you need to provide a pair of timestamps");
    }
  }

  default String getNgramSearchField(final String searchFieldName) {
    return searchFieldName + "." + N_GRAM_FIELD;
  }

  default String convertToIndexSortField(final String providedField) {
    if (sortableFieldLookup.containsKey(providedField.toLowerCase())) {
      return sortableFieldLookup.get(providedField.toLowerCase());
    } else {
      throw new OptimizeRuntimeException(
          "Could not extract event sort field from " + providedField);
    }
  }

  Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>>
      getMinAndMaxIngestedTimestampsForDefinition(final String processDefinitionKey);

  Optional<EventProcessMappingDto> getEventProcessMapping(final String eventProcessMappingId);

  List<EventProcessMappingDto> getAllEventProcessMappingsOmitXml();

  List<EventProcessRoleRequestDto<IdentityDto>> getEventProcessRoles(
      final String eventProcessMappingId);

  IdResponseDto createEventProcessPublishState(
      final EventProcessPublishStateDto eventProcessPublishStateDto);

  boolean markAsDeletedAllEventProcessPublishStatesForEventProcessMappingId(
      final String eventProcessMappingId,
      final String updateItem,
      final ScriptData scriptData,
      final String idFieldName,
      final String indexName);

  void markAsDeletedPublishStatesForEventProcessMappingIdExcludingPublishStateId(
      final String eventProcessMappingId,
      final String updateItem,
      final ScriptData scriptData,
      final String indexName,
      final String publishStateIdToExclude);

  Optional<EventProcessPublishStateDto> getEventProcessPublishStateByEventProcessId(
      String eventProcessMappingId);

  void updateEntry(final String indexName, final String entityId, final ScriptData script);

  List<EventProcessPublishStateDto> getAllEventProcessPublishStatesWithDeletedState(
      boolean deleted);

  Optional<EventProcessDefinitionDto> getEventProcessDefinitionByKeyOmitXml(
      String eventProcessDefinitionKey);

  List<EventProcessDefinitionDto> getAllEventProcessDefinitionsOmitXml();

  default Optional<OffsetDateTime> parseDateString(
      String dateAsStr, final DateTimeFormatter formatter) {
    try {
      return Optional.of(
          OffsetDateTime.ofInstant(Instant.parse(dateAsStr), ZoneId.systemDefault()));
    } catch (DateTimeParseException e1) {
      try {
        // if parsing fails, try to parse as offset format (e.g., 2024-05-23T10:30:39.651+0000)
        return Optional.of(
            OffsetDateTime.ofInstant(
                OffsetDateTime.parse(dateAsStr, formatter).toInstant(), ZoneId.systemDefault()));
      } catch (DateTimeParseException e2) {
        return Optional.empty();
      }
    }
  }

  default String[] getInstanceIndexNames(final List<CamundaEventSourceEntryDto> eventSources) {
    return eventSources.stream()
        .map(
            source ->
                getProcessInstanceIndexAliasName(
                    source.getConfiguration().getProcessDefinitionKey()))
        .toArray(String[]::new);
  }

  default Optional<String> extractCorrelationValue(
      CamundaEventSourceEntryDto eventSourceForCurrentBucket,
      CorrelationValueDto correlationValueDto) {
    Optional<String> correlationValueToAdd;
    final CamundaEventSourceConfigDto eventSourceConfig =
        eventSourceForCurrentBucket.getConfiguration();
    if (eventSourceConfig.isTracedByBusinessKey()) {
      correlationValueToAdd = Optional.ofNullable(correlationValueDto.getBusinessKey());
    } else if (eventSourceConfig.getTraceVariable() != null) {
      final Map<String, SimpleProcessVariableDto> variablesByName =
          correlationValueDto.getVariables().stream()
              .collect(Collectors.toMap(SimpleProcessVariableDto::getName, Function.identity()));
      correlationValueToAdd =
          variablesByName.get(eventSourceConfig.getTraceVariable()).getValue().stream().findFirst();
    } else {
      throw new OptimizeRuntimeException(
          "Cannot get variable sample values for event source with no tracing variable");
    }
    return correlationValueToAdd;
  }
}
