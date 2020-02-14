/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.EventDto;
import org.camunda.optimize.service.EventTraceStateService;
import org.camunda.optimize.service.events.ExternalEventService;
import org.camunda.optimize.service.importing.TimestampBasedImportMediator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExternalEventTraceImportMediator
  extends TimestampBasedImportMediator<ExternalEventTraceImportIndexHandler, EventDto> {

  private ExternalEventService eventFetcherService;

  private final ConfigurationService configurationService;
  private final EventTraceStateService eventTraceStateService;

  @PostConstruct
  @Override
  protected void init() {
    importIndexHandler = importIndexHandlerRegistry.getExternalEventTraceImportIndexHandler();
    eventFetcherService = beanFactory.getBean(ExternalEventService.class);
    importService = new ExternalEventTraceImportService(elasticsearchImportJobExecutor, eventTraceStateService);
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
    return eventFetcherService.getEventsIngestedAfter(
      importIndexHandler.getTimestampOfLastEntity().toInstant().toEpochMilli(), getMaxPageSize()
    );
  }

  @Override
  protected List<EventDto> getEntitiesLastTimestamp() {
    return eventFetcherService.getEventsIngestedAt(
      importIndexHandler.getTimestampOfLastEntity().toInstant().toEpochMilli()
    );
  }

}
