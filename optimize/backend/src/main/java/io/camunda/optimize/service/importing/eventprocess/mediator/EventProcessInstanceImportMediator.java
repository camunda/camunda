/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.eventprocess.mediator;

import io.camunda.optimize.dto.optimize.query.event.process.EventDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessEventDto;
import io.camunda.optimize.service.db.events.EventFetcherService;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.importing.TimestampBasedImportMediator;
import io.camunda.optimize.service.importing.engine.mediator.MediatorRank;
import io.camunda.optimize.service.importing.engine.service.ImportService;
import io.camunda.optimize.service.importing.eventprocess.handler.EventProcessInstanceImportSourceIndexHandler;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class EventProcessInstanceImportMediator<T extends EventProcessEventDto>
    extends TimestampBasedImportMediator<EventProcessInstanceImportSourceIndexHandler, T> {

  @Getter private final String publishedProcessStateId;
  private final EventFetcherService<T> eventFetcherService;

  public EventProcessInstanceImportMediator(
      final String publishedProcessStateId,
      final EventProcessInstanceImportSourceIndexHandler importSourceIndexHandler,
      final EventFetcherService<T> eventFetcherService,
      final ImportService<T> eventProcessEventImportService,
      final ConfigurationService configurationService,
      final BackoffCalculator idleBackoffCalculator) {
    super(
        configurationService,
        idleBackoffCalculator,
        importSourceIndexHandler,
        eventProcessEventImportService);
    this.publishedProcessStateId = publishedProcessStateId;
    this.eventFetcherService = eventFetcherService;
  }

  @Override
  protected OffsetDateTime getTimestamp(final T eventProcessEventDto) {
    if (eventProcessEventDto instanceof EventDto) {
      return OffsetDateTime.ofInstant(
          Instant.ofEpochMilli(((EventDto) eventProcessEventDto).getIngestionTimestamp()),
          ZoneId.systemDefault());
    } else {
      throw new OptimizeRuntimeException("Cannot read import timestamp for unsupported entity");
    }
  }

  @Override
  protected List<T> getEntitiesNextPage() {
    return eventFetcherService.getEventsIngestedAfter(
        importIndexHandler.getTimestampOfLastEntity().toInstant().toEpochMilli(), getMaxPageSize());
  }

  @Override
  protected List<T> getEntitiesLastTimestamp() {
    return eventFetcherService.getEventsIngestedAt(
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
