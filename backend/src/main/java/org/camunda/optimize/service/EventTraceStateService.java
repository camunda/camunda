/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

package org.camunda.optimize.service;


import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.event.EventTraceStateDto;
import org.camunda.optimize.service.es.reader.EventTraceStateReader;
import org.camunda.optimize.service.es.writer.EventTraceStateWriter;
import org.springframework.stereotype.Component;

import java.util.List;

@AllArgsConstructor
@Component
public class EventTraceStateService {

  private final EventTraceStateWriter eventTraceStateWriter;
  private final EventTraceStateReader eventTraceStateReader;

  public List<EventTraceStateDto> getEventTraceStatesForIds(List<String> traceIds) {
    return eventTraceStateReader.getEventTraceStateForTraceIds(traceIds);
  }

  public void upsertEventStateTraces(final List<EventTraceStateDto> eventTraceStateDtos) {
    eventTraceStateWriter.upsertEventTraceStates(eventTraceStateDtos);
  }

}
