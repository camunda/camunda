/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.events;

import io.camunda.optimize.dto.optimize.query.event.sequence.OrderedEventDto;
import io.camunda.optimize.service.db.events.EventFetcherService;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CamundaTraceableEventFetcherService implements EventFetcherService<OrderedEventDto> {

  private final CamundaEventService camundaEventService;
  private final String definitionKey;

  @Override
  public List<OrderedEventDto> getEventsIngestedAfter(final Long eventTimestamp, final int limit) {
    return camundaEventService.getTraceableCamundaEventsForDefinitionAfter(
        definitionKey, eventTimestamp, limit);
  }

  @Override
  public List<OrderedEventDto> getEventsIngestedAt(final Long eventTimestamp) {
    return camundaEventService.getTraceableCamundaEventsForDefinitionAt(
        definitionKey, eventTimestamp);
  }
}
