/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.events;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.service.es.reader.ExternalEventReader;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

@AllArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class ExternalEventByGroupsFetcherService implements EventFetcherService<EventDto> {

  private final List<String> groups;

  private final ExternalEventReader externalEventReader;

  @Override
  public List<EventDto> getEventsIngestedAfter(final Long eventTimestamp, final int limit) {
    return externalEventReader.getEventsIngestedAfterForGroups(eventTimestamp, limit, groups);
  }

  @Override
  public List<EventDto> getEventsIngestedAt(final Long eventTimestamp) {
    return externalEventReader.getEventsIngestedAtForGroups(eventTimestamp, groups);
  }

}
