/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.reader;

import static org.camunda.optimize.service.db.schema.index.events.EventTraceStateIndex.EVENT_TRACE;

import java.util.List;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventTraceStateDto;
import org.camunda.optimize.service.db.schema.index.events.EventTraceStateIndex;

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
