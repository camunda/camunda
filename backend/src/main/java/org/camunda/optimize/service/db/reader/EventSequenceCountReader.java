/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.reader;

import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventCountResponseDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventSequenceCountDto;

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
    final List<EventTypeDto> incomingEvents,
    final List<EventTypeDto> outgoingEvents);

  List<EventCountResponseDto> getEventCountsForAllExternalEventsUsingSearchTerm(final String searchTerm);

  List<EventCountResponseDto> getEventCountsForExternalGroupsUsingSearchTerm(final List<String> groups,
                                                                             final String searchTerm);

  List<EventCountResponseDto> getEventCountsForCamundaSources(final List<CamundaEventSourceEntryDto> camundaSources);

  Set<String> getIndexSuffixesForCurrentSequenceCountIndices();

  List<EventSequenceCountDto> getEventSequencesContainingBothEventTypes(final EventTypeDto firstEventTypeDto,
                                                                        final EventTypeDto secondEventTypeDto);

  List<EventSequenceCountDto> getAllSequenceCounts();

}
