/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.reader.EventSequenceCountReader;
import org.camunda.optimize.service.es.reader.EventTraceStateReader;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex;
import org.camunda.optimize.service.es.schema.index.events.EventTraceStateIndex;
import org.camunda.optimize.service.es.writer.EventSequenceCountWriter;
import org.camunda.optimize.service.es.writer.EventTraceStateWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
public class EventTraceStateServiceFactory {
  private final OptimizeElasticsearchClient esClient;
  private final ElasticSearchSchemaManager elasticSearchSchemaManager;
  private final ObjectMapper objectMapper;
  private final ConfigurationService configurationService;

  public EventTraceStateService createEventTraceStateService(final String eventSuffix) {
    return new EventTraceStateService(
      createEventTraceStateWriter(eventSuffix),
      createEventTraceStateReader(eventSuffix),
      createEventSequenceCountWriter(eventSuffix),
      createEventSequenceCountReader(eventSuffix)
    );
  }

  private EventSequenceCountReader createEventSequenceCountReader(final String indexKey) {
    elasticSearchSchemaManager.createIndexIfMissing(esClient, new EventSequenceCountIndex(indexKey));
    return new EventSequenceCountReader(indexKey, esClient, objectMapper, configurationService);
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
