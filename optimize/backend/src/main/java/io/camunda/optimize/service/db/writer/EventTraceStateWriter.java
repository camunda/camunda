/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.writer;

import io.camunda.optimize.dto.optimize.query.event.sequence.EventTraceStateDto;
import io.camunda.optimize.service.db.schema.index.events.EventTraceStateIndex;
import java.util.List;

public interface EventTraceStateWriter {

  void upsertEventTraceStates(final List<EventTraceStateDto> eventTraceStateDtos);

  default String updateScript() {
    return """
              for (def tracedEvent : params.eventTrace) {
                  ctx._source.eventTrace.removeIf(event -> event.eventId.equals(tracedEvent.eventId));
              }
              ctx._source.eventTrace.addAll(params.eventTrace);
            """;
  }

  default String getIndexName(String indexKey) {
    return EventTraceStateIndex.constructIndexName(indexKey);
  }
}
