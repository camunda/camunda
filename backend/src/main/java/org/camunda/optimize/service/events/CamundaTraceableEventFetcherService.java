/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.events;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.event.sequence.OrderedEventDto;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

@AllArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CamundaTraceableEventFetcherService implements EventFetcherService<OrderedEventDto> {

  private final CamundaEventService camundaEventService;
  private final String definitionKey;

  @Override
  public List<OrderedEventDto> getEventsIngestedAfter(final Long eventTimestamp, final int limit) {
    return camundaEventService.getTraceableCamundaEventsForDefinitionAfter(definitionKey, eventTimestamp, limit);
  }

  @Override
  public List<OrderedEventDto> getEventsIngestedAt(final Long eventTimestamp) {
    return camundaEventService.getTraceableCamundaEventsForDefinitionAt(definitionKey, eventTimestamp);
  }

}
