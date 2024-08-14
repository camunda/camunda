/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.eventprocess.mediator;

import static io.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

import io.camunda.optimize.dto.optimize.query.event.process.EventImportSourceDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessEventDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;
import io.camunda.optimize.dto.optimize.query.event.process.source.EventSourceType;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.EventProcessInstanceWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.importing.engine.service.ImportService;
import io.camunda.optimize.service.importing.eventprocess.handler.EventProcessInstanceImportSourceIndexHandler;
import io.camunda.optimize.service.importing.eventprocess.service.EventProcessInstanceImportService;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventProcessInstanceImportMediatorFactory {

  private final BeanFactory beanFactory;

  private final ConfigurationService configurationService;

  private final EventProcessInstanceWriter eventProcessInstanceWriter;
  private final EventFetcherFactory eventFetcherFactory;
  private final DatabaseClient databaseClient;

  @SuppressWarnings(UNCHECKED_CAST)
  public <T extends EventProcessEventDto>
      List<EventProcessInstanceImportMediator<T>> createEventProcessInstanceMediators(
          final EventProcessPublishStateDto publishedStateDto) {
    return publishedStateDto.getEventImportSources().stream()
        .map(
            importSource ->
                (EventProcessInstanceImportMediator<T>)
                    beanFactory.getBean(
                        EventProcessInstanceImportMediator.class,
                        publishedStateDto.getId(),
                        new EventProcessInstanceImportSourceIndexHandler(
                            configurationService, importSource),
                        eventFetcherFactory.createEventFetcherForEventImportSource(importSource),
                        createImportService(publishedStateDto, importSource),
                        configurationService,
                        new BackoffCalculator(configurationService)))
        .collect(Collectors.toList());
  }

  private ImportService<? extends EventProcessEventDto> createImportService(
      final EventProcessPublishStateDto eventProcessPublishStateDto,
      final EventImportSourceDto eventSourceEntryDto) {
    final EventProcessInstanceImportService eventProcessInstanceImportService =
        createEventProcessInstanceImportService(eventProcessPublishStateDto);
    if (EventSourceType.EXTERNAL.equals(eventSourceEntryDto.getEventImportSourceType())) {
      return eventProcessInstanceImportService;
    } else {
      throw new OptimizeRuntimeException(
          String.format(
              "Cannot create mediator for Event Source Type: %s",
              eventSourceEntryDto.getEventImportSourceType()));
    }
  }

  private EventProcessInstanceImportService createEventProcessInstanceImportService(
      final EventProcessPublishStateDto eventProcessPublishStateDto) {
    return new EventProcessInstanceImportService(
        configurationService,
        eventProcessPublishStateDto,
        eventProcessInstanceWriter,
        databaseClient);
  }
}
