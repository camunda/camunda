/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventTraceStateDto;
import org.camunda.optimize.service.db.writer.EventTraceStateWriter;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;

import java.util.List;

@AllArgsConstructor
@Slf4j
public class EventTraceStateWriterOS implements EventTraceStateWriter {

  private final String indexKey;
  private final OptimizeOpenSearchClient osClient;
  private final ObjectMapper objectMapper;

  @Override
  public void upsertEventTraceStates(final List<EventTraceStateDto> eventTraceStateDtos) {
    //todo will be handled in the OPT-7230
  }

}
