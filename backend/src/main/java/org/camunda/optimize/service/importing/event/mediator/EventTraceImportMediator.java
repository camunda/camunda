/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.event.mediator;

import org.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.service.events.EventFetcherService;
import org.camunda.optimize.service.importing.TimestampBasedImportIndexHandler;
import org.camunda.optimize.service.importing.TimestampBasedImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.MediatorRank;
import org.camunda.optimize.service.importing.event.service.EventTraceImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
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
  extends TimestampBasedImportMediator<TimestampBasedImportIndexHandler<TimestampBasedImportIndexDto>, EventDto> {

  private final EventFetcherService eventService;

  public EventTraceImportMediator(final EventFetcherService eventService,
                                  final TimestampBasedImportIndexHandler<TimestampBasedImportIndexDto> importIndexHandler,
                                  final EventTraceImportService importService,
                                  final ConfigurationService configurationService,
                                  final BackoffCalculator idleBackoffCalculator) {
    super(configurationService, idleBackoffCalculator, importIndexHandler, importService);
    this.eventService = eventService;
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

  @Override
  public int getMaxPageSize() {
    return configurationService.getEventImportConfiguration().getMaxPageSize();
  }

  @Override
  public MediatorRank getRank() {
    return MediatorRank.INSTANCE;
  }

}
