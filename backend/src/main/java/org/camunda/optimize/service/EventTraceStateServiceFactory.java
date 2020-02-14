/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.reader.EventTraceStateReader;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex;
import org.camunda.optimize.service.es.schema.index.events.EventTraceStateIndex;
import org.camunda.optimize.service.es.writer.EventSequenceCountWriter;
import org.camunda.optimize.service.es.writer.EventTraceStateWriter;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
public class EventTraceStateServiceFactory {
  private final OptimizeElasticsearchClient esClient;
  private final ElasticSearchSchemaManager elasticSearchSchemaManager;
  private final ObjectMapper objectMapper;

  public EventTraceStateService createEventTraceStateService(final String eventSuffix) {
    return new EventTraceStateService(
      createEventTraceStateWriter(eventSuffix),
      createEventTraceStateReader(eventSuffix),
      createEventSequenceCountWriter(eventSuffix)
    );
  }

  private EventSequenceCountWriter createEventSequenceCountWriter(final String indexKey) {
    elasticSearchSchemaManager.createIndexIfMissing(esClient, new EventSequenceCountIndex(indexKey));
    return new EventSequenceCountWriter(indexKey, esClient, objectMapper);
  }

  private EventTraceStateReader createEventTraceStateReader(final String indexKey) {
    elasticSearchSchemaManager.createIndexIfMissing(esClient, new EventTraceStateIndex(indexKey));
    return new EventTraceStateReader(indexKey, esClient, objectMapper);
  }

  private EventTraceStateWriter createEventTraceStateWriter(final String indexKey) {
    elasticSearchSchemaManager.createIndexIfMissing(esClient, new EventTraceStateIndex(indexKey));
    return new EventTraceStateWriter(indexKey, esClient, objectMapper);
  }
}
