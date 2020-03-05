/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.eventprocess.mediator;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.EventDto;
import org.camunda.optimize.service.events.EventFetcherService;
import org.camunda.optimize.service.importing.TimestampBasedImportMediator;
import org.camunda.optimize.service.importing.eventprocess.handler.EventProcessInstanceImportSourceIndexHandler;
import org.camunda.optimize.service.importing.eventprocess.service.EventProcessInstanceImportService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class EventProcessInstanceImportMediator
  extends TimestampBasedImportMediator<EventProcessInstanceImportSourceIndexHandler, EventDto> {

  @Getter
  private final String publishedProcessStateId;
  private final EventFetcherService eventService;

  public EventProcessInstanceImportMediator(final String publishedProcessStateId,
                                            final EventProcessInstanceImportSourceIndexHandler importSourceIndexHandler,
                                            final EventFetcherService eventService,
                                            final EventProcessInstanceImportService eventProcessInstanceImportService) {
    this.publishedProcessStateId = publishedProcessStateId;
    this.importIndexHandler = importSourceIndexHandler;
    this.eventService = eventService;
    this.importService = eventProcessInstanceImportService;
  }

  @Override
  protected void init() {
    // noop
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
