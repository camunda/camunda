/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.events;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.event.EventDto;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

@AllArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CamundaEventFetcherService implements EventFetcherService {

  private final CamundaEventService camundaEventService;
  private final String definitionKey;

  @Override
  public List<EventDto> getEventsIngestedAfter(final Long eventTimestamp, final int limit) {
    return camundaEventService.getCamundaEventsForDefinitionAfter(definitionKey, eventTimestamp, limit);
  }

  @Override
  public List<EventDto> getEventsIngestedAt(final Long eventTimestamp) {
    return camundaEventService.getCamundaEventsForDefinitionAt(definitionKey, eventTimestamp);
  }

}
