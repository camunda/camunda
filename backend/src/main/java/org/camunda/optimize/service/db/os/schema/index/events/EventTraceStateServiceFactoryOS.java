/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.schema.index.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.camunda.optimize.service.db.events.EventTraceStateServiceFactory;
import org.camunda.optimize.service.db.reader.EventSequenceCountReader;
import org.camunda.optimize.service.db.reader.EventTraceStateReader;
import org.camunda.optimize.service.db.writer.EventSequenceCountWriter;
import org.camunda.optimize.service.db.writer.EventTraceStateWriter;
import org.camunda.optimize.service.events.EventTraceStateService;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.os.reader.EventSequenceCountReaderOS;
import org.camunda.optimize.service.db.os.reader.EventTraceStateReaderOS;
import org.camunda.optimize.service.db.os.writer.EventTraceStateWriterOS;
import org.camunda.optimize.service.db.os.schema.OpenSearchSchemaManager;
import org.camunda.optimize.service.db.os.writer.EventSequenceCountWriterOS;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
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
      createEventSequenceCountReader(eventSuffix)
    );
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

