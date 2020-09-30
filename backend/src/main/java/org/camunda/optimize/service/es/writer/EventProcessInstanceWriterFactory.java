/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.events.EventProcessInstanceIndex;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventProcessInstanceWriterFactory {
  private final ObjectMapper objectMapper;
  private final OptimizeElasticsearchClient elasticsearchClient;
  private final DateTimeFormatter dateTimeFormatter;

  public EventProcessInstanceWriter createEventProcessInstanceWriter(final EventProcessPublishStateDto processPublishStateDto) {
    return new EventProcessInstanceWriter(
      new EventProcessInstanceIndex(processPublishStateDto.getId()),
      elasticsearchClient,
      objectMapper,
      dateTimeFormatter
    );
  }

}
