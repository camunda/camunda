/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.EventDto;
import org.camunda.optimize.service.es.reader.EventReader;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
@Slf4j
public class EventProcessInstanceImportMediator {

  @Getter
  private final String publishedProcessStateId;
  private final ConfigurationService configurationService;
  private final EventReader eventReader;
  private final EventProcessInstanceImportService eventProcessInstanceImportService;

  private final BackoffCalculator idleBackoffCalculator;

  private Long lastTimestamp = 0L;

  public EventProcessInstanceImportMediator(final String publishedProcessStateId,
                                            final ConfigurationService configurationService,
                                            final EventReader eventReader,
                                            final EventProcessInstanceImportService eventProcessInstanceImportService) {
    this.publishedProcessStateId = publishedProcessStateId;
    this.configurationService = configurationService;
    this.eventReader = eventReader;
    this.eventProcessInstanceImportService = eventProcessInstanceImportService;

    this.idleBackoffCalculator = new BackoffCalculator(configurationService);
  }

  public void importNextPage(final CompletableFuture<Void> importCompleted) {
    try {
      final List<EventDto> lastTimeStampEvents = eventReader.getEventsIngestedAt(lastTimestamp);
      final List<EventDto> nextPageEvents = eventReader.getEventsIngestedAfter(lastTimestamp, getMaxPageSize());

      if (!nextPageEvents.isEmpty()) {
        idleBackoffCalculator.resetBackoff();
        lastTimestamp = nextPageEvents.get(nextPageEvents.size() - 1).getIngestionTimestamp();
      } else {
        calculateIdleSleepTime();
      }

      final List<EventDto> eventDtosToImport = Stream
        .concat(lastTimeStampEvents.stream(), nextPageEvents.stream())
        .collect(Collectors.toList());

      eventProcessInstanceImportService.executeImport(eventDtosToImport, () -> importCompleted.complete(null));
    } catch (Exception e) {
      log.error("Failure during event process import.", e);
      importCompleted.complete(null);
    }
  }

  public boolean canImport() {
    boolean canImportNewPage = idleBackoffCalculator.isReadyForNextRetry();
    log.debug("can import next page [{}]", canImportNewPage);
    return canImportNewPage;
  }

  private void calculateIdleSleepTime() {
    final long sleepTime = idleBackoffCalculator.calculateSleepTime();
    log.debug("Was not able to produce a new job, sleeping for [{}] ms", sleepTime);
  }

  private int getMaxPageSize() {
    return configurationService.getIngestedEventImportConfiguration().getMaxPageSize();
  }

  public void shutdown() {
    eventProcessInstanceImportService.shutdown();
  }

}
