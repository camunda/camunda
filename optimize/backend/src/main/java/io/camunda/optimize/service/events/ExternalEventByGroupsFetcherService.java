/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.events;

import io.camunda.optimize.dto.optimize.query.event.process.EventDto;
import io.camunda.optimize.service.db.events.EventFetcherService;
import io.camunda.optimize.service.db.reader.ExternalEventReader;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

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
