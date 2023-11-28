/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.schema.index.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.camunda.optimize.service.db.events.EventSequenceCountReaderFactory;
import org.camunda.optimize.service.db.reader.EventSequenceCountReader;
import org.camunda.optimize.service.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.os.reader.EventSequenceCountReaderOS;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Conditional(OpenSearchCondition.class)
public class EventSequenceCountReaderFactoryOS implements EventSequenceCountReaderFactory {

  private final OptimizeOpenSearchClient osClient;
  private final ObjectMapper objectMapper;
  private final ConfigurationService configurationService;

  @Override
  public EventSequenceCountReader createEventSequenceCountReader(final String eventSuffix) {
    return new EventSequenceCountReaderOS(
      eventSuffix,
      osClient,
      objectMapper,
      configurationService
    );
  }

}
