/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.reader.EventSequenceCountReaderES;
import io.camunda.optimize.service.db.events.EventSequenceCountReaderFactory;
import io.camunda.optimize.service.db.reader.EventSequenceCountReader;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Conditional(ElasticSearchCondition.class)
public class EventSequenceCountReaderFactoryES implements EventSequenceCountReaderFactory {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;
  private final ConfigurationService configurationService;

  @Override
  public EventSequenceCountReader createEventSequenceCountReader(final String eventSuffix) {
    return new EventSequenceCountReaderES(
        eventSuffix, esClient, objectMapper, configurationService);
  }
}
