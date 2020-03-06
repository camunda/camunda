/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.event.mediator;

import org.camunda.optimize.dto.optimize.query.event.EventDto;
import org.camunda.optimize.service.EventTraceStateService;
import org.camunda.optimize.service.events.EventFetcherService;
import org.camunda.optimize.service.importing.TimestampBasedImportIndexHandler;
import org.camunda.optimize.service.importing.TimestampBasedImportMediator;
import org.camunda.optimize.service.importing.event.service.EventTraceImportService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class EventTraceImportMediator
  extends TimestampBasedImportMediator<TimestampBasedImportIndexHandler, EventDto> {

  private final EventFetcherService eventService;
  private final EventTraceStateService eventTraceStateService;

  public EventTraceImportMediator(final EventFetcherService eventService,
                                  final TimestampBasedImportIndexHandler importIndexHandler,
                                  final EventTraceStateService eventTraceStateService) {
    this.eventService = eventService;
    this.importIndexHandler = importIndexHandler;
    this.eventTraceStateService = eventTraceStateService;
  }

  @Override
  protected void init() {
    this.importService = new EventTraceImportService(elasticsearchImportJobExecutor, eventTraceStateService);
  }

  @Override
  public int getMaxPageSize() {
    return configurationService.getEventImportConfiguration().getMaxPageSize();
  }

  @Override
  protected OffsetDateTime getTimestamp(final EventDto eventDto) {
    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(eventDto.getIngestionTimestamp()), ZoneId.systemDefault());
  }

  @Override
  protected List<EventDto> getEntitiesNextPage() {
    return eventService.getEventsIngestedAfter(
      importIndexHandler.getTimestampOfLastEntity().toInstant().toEpochMilli(),
      getMaxPageSize()
    );
  }

  @Override
  protected List<EventDto> getEntitiesLastTimestamp() {
    return eventService.getEventsIngestedAt(
      importIndexHandler.getTimestampOfLastEntity().toInstant().toEpochMilli()
    );
  }
}
