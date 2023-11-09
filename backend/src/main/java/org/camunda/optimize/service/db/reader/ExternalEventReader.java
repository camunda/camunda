/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.reader;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.optimize.dto.optimize.query.event.DeletableEventDto;
import org.camunda.optimize.dto.optimize.query.event.EventGroupRequestDto;
import org.camunda.optimize.dto.optimize.query.event.EventSearchRequestDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.dto.optimize.rest.Page;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.camunda.optimize.service.db.schema.index.events.EventIndex.EVENT_NAME;
import static org.camunda.optimize.service.db.schema.index.events.EventIndex.GROUP;
import static org.camunda.optimize.service.db.schema.index.events.EventIndex.SOURCE;
import static org.camunda.optimize.service.db.schema.index.events.EventIndex.TIMESTAMP;
import static org.camunda.optimize.service.db.schema.index.events.EventIndex.TRACE_ID;

public interface ExternalEventReader {

  String MIN_AGG = "min";
  String MAX_AGG = "max";
  String KEYWORD_ANALYZER = "keyword";

  Map<String, String> sortableFieldLookup = ImmutableMap.of(
    EventDto.Fields.group.toLowerCase(), GROUP,
    EventDto.Fields.source.toLowerCase(), SOURCE,
    EventDto.Fields.eventName.toLowerCase(), EVENT_NAME,
    EventDto.Fields.traceId.toLowerCase(), TRACE_ID,
    EventDto.Fields.timestamp.toLowerCase(), TIMESTAMP
  );

  String EVENT_GROUP_AGG = "eventGroupAggregation";
  String LOWERCASE_GROUP_AGG = "lowercaseGroupAggregation";
  String GROUP_COMPOSITE_AGG = "compositeAggregation";

  List<EventDto> getEventsIngestedAfter(final Long ingestTimestamp, final int limit);

  List<EventDto> getEventsIngestedAfterForGroups(final Long ingestTimestamp, final int limit,
                                                 final List<String> groups);

  List<EventDto> getEventsIngestedAt(final Long ingestTimestamp);

  List<EventDto> getEventsIngestedAtForGroups(final Long ingestTimestamp, final List<String> groups);

  Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>> getMinAndMaxIngestedTimestamps();

  Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>> getMinAndMaxIngestedTimestampsForGroups(
    final List<String> groups);

  Page<DeletableEventDto> getEventsForRequest(final EventSearchRequestDto eventSearchRequestDto);

  List<String> getEventGroups(final EventGroupRequestDto eventGroupRequestDto);

}
