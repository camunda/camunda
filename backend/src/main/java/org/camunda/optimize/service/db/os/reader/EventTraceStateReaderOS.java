/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventTraceStateDto;
import org.camunda.optimize.service.db.reader.EventTraceStateReader;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@Slf4j
public class EventTraceStateReaderOS implements EventTraceStateReader {

  private final String indexKey;
  private final OptimizeOpenSearchClient osClient;
  private final ObjectMapper objectMapper;

  @Override
  public List<EventTraceStateDto> getEventTraceStateForTraceIds(final List<String> traceIds) {
    //todo will be handled in the OPT-7230
    return new ArrayList<>();
  }

  @Override
  public List<EventTraceStateDto> getTracesContainingAtLeastOneEventFromEach(final List<EventTypeDto> startEvents,
                                                                             final List<EventTypeDto> endEvents,
                                                                             final int maxResultsSize) {
    //todo will be handled in the OPT-7230
    return null;
  }

  @Override
  public List<EventTraceStateDto> getTracesWithTraceIdIn(final List<String> traceIds) {
    //todo will be handled in the OPT-7230
    return null;
  }
  
}
