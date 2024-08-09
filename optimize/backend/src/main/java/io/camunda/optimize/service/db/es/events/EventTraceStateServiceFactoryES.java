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
import io.camunda.optimize.service.db.es.reader.EventTraceStateReaderES;
import io.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
import io.camunda.optimize.service.db.es.schema.index.events.EventSequenceCountIndexES;
import io.camunda.optimize.service.db.es.schema.index.events.EventTraceStateIndexES;
import io.camunda.optimize.service.db.es.writer.EventSequenceCountWriterES;
import io.camunda.optimize.service.db.es.writer.EventTraceStateWriterES;
import io.camunda.optimize.service.db.events.EventTraceStateServiceFactory;
import io.camunda.optimize.service.db.reader.EventSequenceCountReader;
import io.camunda.optimize.service.db.reader.EventTraceStateReader;
import io.camunda.optimize.service.db.writer.EventSequenceCountWriter;
import io.camunda.optimize.service.db.writer.EventTraceStateWriter;
import io.camunda.optimize.service.events.EventTraceStateService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Conditional(ElasticSearchCondition.class)
public class EventTraceStateServiceFactoryES implements EventTraceStateServiceFactory {

  private final OptimizeElasticsearchClient esClient;
  private final ElasticSearchSchemaManager elasticSearchSchemaManager;
  private final ObjectMapper objectMapper;
  private final ConfigurationService configurationService;

  @Override
  public EventTraceStateService createEventTraceStateService(final String eventSuffix) {
    return new EventTraceStateService(
        createEventTraceStateWriter(eventSuffix),
        createEventTraceStateReader(eventSuffix),
        createEventSequenceCountWriter(eventSuffix),
        createEventSequenceCountReader(eventSuffix));
  }

  private EventSequenceCountReader createEventSequenceCountReader(final String indexKey) {
    elasticSearchSchemaManager.createIndexIfMissing(
        esClient, new EventSequenceCountIndexES(indexKey));
    return new EventSequenceCountReaderES(indexKey, esClient, objectMapper, configurationService);
  }

  private EventSequenceCountWriter createEventSequenceCountWriter(final String indexKey) {
    elasticSearchSchemaManager.createIndexIfMissing(
        esClient, new EventSequenceCountIndexES(indexKey));
    return new EventSequenceCountWriterES(indexKey, esClient, objectMapper);
  }

  private EventTraceStateReader createEventTraceStateReader(final String indexKey) {
    elasticSearchSchemaManager.createIndexIfMissing(esClient, new EventTraceStateIndexES(indexKey));
    return new EventTraceStateReaderES(indexKey, esClient, objectMapper);
  }

  private EventTraceStateWriter createEventTraceStateWriter(final String indexKey) {
    elasticSearchSchemaManager.createIndexIfMissing(esClient, new EventTraceStateIndexES(indexKey));
    return new EventTraceStateWriterES(indexKey, esClient, objectMapper);
  }
}
