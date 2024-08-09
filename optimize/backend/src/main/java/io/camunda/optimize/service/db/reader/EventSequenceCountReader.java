/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.EVENT_SEQUENCE_COUNT_INDEX_PREFIX;
import static io.camunda.optimize.service.db.schema.index.events.EventSequenceCountIndex.N_GRAM_FIELD;
import static io.camunda.optimize.service.db.schema.index.events.EventSequenceCountIndex.SOURCE_EVENT;

import io.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import io.camunda.optimize.dto.optimize.query.event.sequence.EventCountResponseDto;
import io.camunda.optimize.dto.optimize.query.event.sequence.EventSequenceCountDto;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public interface EventSequenceCountReader {

  String GROUP_AGG = EventCountResponseDto.Fields.group;
  String SOURCE_AGG = EventCountResponseDto.Fields.source;
  String EVENT_NAME_AGG = EventCountResponseDto.Fields.eventName;
  String COMPOSITE_EVENT_NAME_SOURCE_AND_GROUP_AGGREGATION =
      "compositeEventNameSourceAndGroupAggregation";
  String COUNT_AGG = EventCountResponseDto.Fields.count;
  String KEYWORD_ANALYZER = "keyword";

  List<EventSequenceCountDto> getEventSequencesWithSourceInIncomingOrTargetInOutgoing(
      final List<EventTypeDto> incomingEvents, final List<EventTypeDto> outgoingEvents);

  List<EventCountResponseDto> getEventCountsForSearchTerm(
      final List<String> groups, final String searchTerm);

  Set<String> getIndexSuffixesForCurrentSequenceCountIndices();

  List<EventSequenceCountDto> getEventSequencesContainingBothEventTypes(
      final EventTypeDto firstEventTypeDto, final EventTypeDto secondEventTypeDto);

  List<EventSequenceCountDto> getAllSequenceCounts();

  default String getIndexName(final String indexKey) {
    return EVENT_SEQUENCE_COUNT_INDEX_PREFIX + indexKey;
  }

  default String getNgramSearchField(final String searchFieldName) {
    return getNestedField(SOURCE_EVENT, searchFieldName) + "." + N_GRAM_FIELD;
  }

  default String getNestedField(final String property, final String searchFieldName) {
    return property + "." + searchFieldName;
  }

  default List<EventCountResponseDto> getEventCountsForAllExternalEventsUsingSearchTerm(
      final String searchTerm) {
    return getEventCountsForSearchTerm(Collections.emptyList(), searchTerm);
  }

  default List<EventCountResponseDto> getEventCountsForExternalGroupsUsingSearchTerm(
      final List<String> groups, final String searchTerm) {
    return getEventCountsForSearchTerm(groups, searchTerm);
  }
}
