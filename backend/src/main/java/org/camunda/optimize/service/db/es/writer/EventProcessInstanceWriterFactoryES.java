/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;
import org.camunda.optimize.service.db.writer.EventProcessInstanceWriter;
import org.camunda.optimize.service.db.writer.EventProcessInstanceWriterFactory;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.es.schema.index.events.EventProcessInstanceIndexES;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class EventProcessInstanceWriterFactoryES implements EventProcessInstanceWriterFactory {

  private final ObjectMapper objectMapper;
  private final OptimizeElasticsearchClient elasticsearchClient;
  private final ConfigurationService configurationService;
  private final DateTimeFormatter dateTimeFormatter;

  @Override
  public EventProcessInstanceWriter createEventProcessInstanceWriter(final EventProcessPublishStateDto processPublishStateDto) {
    return new EventProcessInstanceWriterES(
      new EventProcessInstanceIndexES(processPublishStateDto.getId()),
      elasticsearchClient,
      configurationService,
      objectMapper,
      dateTimeFormatter
    );
  }

  @Override
  public EventProcessInstanceWriter createAllEventProcessInstanceWriter() {
    return new EventProcessInstanceWriterES(
      new EventProcessInstanceIndexES("*"),
      elasticsearchClient,
      configurationService,
      objectMapper,
      dateTimeFormatter
    );
  }

}
