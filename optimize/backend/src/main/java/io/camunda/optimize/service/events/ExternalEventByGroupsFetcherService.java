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
import org.slf4j.Logger;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ExternalEventByGroupsFetcherService implements EventFetcherService<EventDto> {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(ExternalEventByGroupsFetcherService.class);
  private final List<String> groups;

  private final ExternalEventReader externalEventReader;

  public ExternalEventByGroupsFetcherService(
      final List<String> groups, final ExternalEventReader externalEventReader) {
    this.groups = groups;
    this.externalEventReader = externalEventReader;
  }

  @Override
  public List<EventDto> getEventsIngestedAfter(final Long eventTimestamp, final int limit) {
    return externalEventReader.getEventsIngestedAfterForGroups(eventTimestamp, limit, groups);
  }

  @Override
  public List<EventDto> getEventsIngestedAt(final Long eventTimestamp) {
    return externalEventReader.getEventsIngestedAtForGroups(eventTimestamp, groups);
  }
}
