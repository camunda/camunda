/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.camunda.optimize.service.db.reader.EventSequenceCountReader;
import org.camunda.optimize.service.db.reader.EventTraceStateReader;
import org.camunda.optimize.service.db.writer.EventSequenceCountWriter;
import org.camunda.optimize.service.db.writer.EventTraceStateWriter;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.reader.EventSequenceCountReaderES;
import org.camunda.optimize.service.es.reader.EventTraceStateReaderES;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndexES;
import org.camunda.optimize.service.es.schema.index.events.EventTraceStateIndexES;
import org.camunda.optimize.service.es.writer.EventSequenceCountWriterES;
import org.camunda.optimize.service.es.writer.EventTraceStateWriterES;
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

  //TODO will be handled properly with https://jira.camunda.com/browse/OPT-7244
  private EventSequenceCountReader createEventSequenceCountReader(final String indexKey) {
    elasticSearchSchemaManager.createIndexIfMissing(esClient, new EventSequenceCountIndexES(indexKey));
    return new EventSequenceCountReaderES(indexKey, esClient, objectMapper, configurationService);
  }

  //TODO will be handled properly with https://jira.camunda.com/browse/OPT-7244
  private EventSequenceCountWriter createEventSequenceCountWriter(final String indexKey) {
    elasticSearchSchemaManager.createIndexIfMissing(esClient, new EventSequenceCountIndexES(indexKey));
    return new EventSequenceCountWriterES(indexKey, esClient, objectMapper);
  }

  //TODO will be handled properly with https://jira.camunda.com/browse/OPT-7244
  private EventTraceStateReader createEventTraceStateReader(final String indexKey) {
    elasticSearchSchemaManager.createIndexIfMissing(esClient, new EventTraceStateIndexES(indexKey));
    return new EventTraceStateReaderES(indexKey, esClient, objectMapper);
  }

  //TODO will be handled properly with https://jira.camunda.com/browse/OPT-7244
  private EventTraceStateWriter createEventTraceStateWriter(final String indexKey) {
    elasticSearchSchemaManager.createIndexIfMissing(esClient, new EventTraceStateIndexES(indexKey));
    return new EventTraceStateWriterES(indexKey, esClient, objectMapper);
  }

}
