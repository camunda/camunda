/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.schema.index.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.service.db.events.EventTraceStateServiceFactory;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.reader.EventSequenceCountReaderOS;
import io.camunda.optimize.service.db.os.reader.EventTraceStateReaderOS;
import io.camunda.optimize.service.db.os.schema.OpenSearchSchemaManager;
import io.camunda.optimize.service.db.os.writer.EventSequenceCountWriterOS;
import io.camunda.optimize.service.db.os.writer.EventTraceStateWriterOS;
import io.camunda.optimize.service.db.reader.EventSequenceCountReader;
import io.camunda.optimize.service.db.reader.EventTraceStateReader;
import io.camunda.optimize.service.db.writer.EventSequenceCountWriter;
import io.camunda.optimize.service.db.writer.EventTraceStateWriter;
import io.camunda.optimize.service.events.EventTraceStateService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Conditional(OpenSearchCondition.class)
public class EventTraceStateServiceFactoryOS implements EventTraceStateServiceFactory {

  private final OptimizeOpenSearchClient osClient;
  private final OpenSearchSchemaManager openSearchSchemaManager;
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
    openSearchSchemaManager.createIndexIfMissing(osClient, new EventSequenceCountIndexOS(indexKey));
    return new EventSequenceCountReaderOS(indexKey, osClient, objectMapper, configurationService);
  }

  private EventSequenceCountWriter createEventSequenceCountWriter(final String indexKey) {
    openSearchSchemaManager.createIndexIfMissing(osClient, new EventSequenceCountIndexOS(indexKey));
    return new EventSequenceCountWriterOS(indexKey, osClient, objectMapper);
  }

  private EventTraceStateReader createEventTraceStateReader(final String indexKey) {
    openSearchSchemaManager.createIndexIfMissing(osClient, new EventTraceStateIndexOS(indexKey));
    return new EventTraceStateReaderOS(indexKey, osClient, objectMapper);
  }

  private EventTraceStateWriter createEventTraceStateWriter(final String indexKey) {
    openSearchSchemaManager.createIndexIfMissing(osClient, new EventTraceStateIndexOS(indexKey));
    return new EventTraceStateWriterOS(indexKey, osClient, objectMapper);
  }
}
