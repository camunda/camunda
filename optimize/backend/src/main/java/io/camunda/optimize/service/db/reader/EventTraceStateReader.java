/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import static io.camunda.optimize.service.db.schema.index.events.EventTraceStateIndex.EVENT_TRACE;

import io.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import io.camunda.optimize.dto.optimize.query.event.sequence.EventTraceStateDto;
import io.camunda.optimize.service.db.schema.index.events.EventTraceStateIndex;
import java.util.List;

public interface EventTraceStateReader {

  List<EventTraceStateDto> getEventTraceStateForTraceIds(List<String> traceIds);

  List<EventTraceStateDto> getTracesContainingAtLeastOneEventFromEach(
      final List<EventTypeDto> startEvents,
      final List<EventTypeDto> endEvents,
      final int maxResultsSize);

  List<EventTraceStateDto> getTracesWithTraceIdIn(final List<String> traceIds);

  default String getEventTraceNestedField(final String searchFieldName) {
    return EVENT_TRACE + "." + searchFieldName;
  }

  default String getIndexName(String indexKey) {
    return EventTraceStateIndex.constructIndexName(indexKey);
  }
}
