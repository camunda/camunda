/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.event.mediator;

import io.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventDto;
import io.camunda.optimize.service.db.events.EventFetcherService;
import io.camunda.optimize.service.importing.TimestampBasedImportIndexHandler;
import io.camunda.optimize.service.importing.TimestampBasedImportMediator;
import io.camunda.optimize.service.importing.engine.mediator.MediatorRank;
import io.camunda.optimize.service.importing.event.service.EventTraceImportService;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class EventTraceImportMediator
    extends TimestampBasedImportMediator<
        TimestampBasedImportIndexHandler<TimestampBasedImportIndexDto>, EventDto> {

  private final EventFetcherService eventService;

  public EventTraceImportMediator(
      final EventFetcherService eventService,
      final TimestampBasedImportIndexHandler<TimestampBasedImportIndexDto> importIndexHandler,
      final EventTraceImportService importService,
      final ConfigurationService configurationService,
      final BackoffCalculator idleBackoffCalculator) {
    super(configurationService, idleBackoffCalculator, importIndexHandler, importService);
    this.eventService = eventService;
  }

  @Override
  protected OffsetDateTime getTimestamp(final EventDto eventDto) {
    return OffsetDateTime.ofInstant(
        Instant.ofEpochMilli(eventDto.getIngestionTimestamp()), ZoneId.systemDefault());
  }

  @Override
  protected List<EventDto> getEntitiesNextPage() {
    return eventService.getEventsIngestedAfter(
        importIndexHandler.getTimestampOfLastEntity().toInstant().toEpochMilli(), getMaxPageSize());
  }

  @Override
  protected List<EventDto> getEntitiesLastTimestamp() {
    return eventService.getEventsIngestedAt(
        importIndexHandler.getTimestampOfLastEntity().toInstant().toEpochMilli());
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
