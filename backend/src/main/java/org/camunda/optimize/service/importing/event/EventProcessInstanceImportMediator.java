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
import org.camunda.optimize.service.events.EventService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
@Slf4j
public class EventProcessInstanceImportMediator {

  @Getter
  private final String publishedProcessStateId;
  private final ConfigurationService configurationService;
  private final EventService eventService;
  private final EventProcessInstanceImportService eventProcessInstanceImportService;

  private final BackoffCalculator idleBackoffCalculator;

  private Long lastImportedTimestamp = 0L;
  private AtomicLong lastPersistedTimestamp = new AtomicLong();

  public EventProcessInstanceImportMediator(final String publishedProcessStateId,
                                            final ConfigurationService configurationService,
                                            final EventService eventService,
                                            final Long lastImportedTimestamp,
                                            final EventProcessInstanceImportService eventProcessInstanceImportService) {
    this.publishedProcessStateId = publishedProcessStateId;
    this.configurationService = configurationService;
    this.eventService = eventService;
    this.eventProcessInstanceImportService = eventProcessInstanceImportService;
    this.lastImportedTimestamp = lastImportedTimestamp;
    this.lastPersistedTimestamp.set(lastImportedTimestamp);
    this.idleBackoffCalculator = new BackoffCalculator(configurationService);
  }

  public void importNextPage(final CompletableFuture<Void> importCompleted) {
    try {
      final List<EventDto> lastTimeStampEvents = eventService.getEventsIngestedAt(lastImportedTimestamp);
      final List<EventDto> nextPageEvents = eventService.getEventsIngestedAfter(lastImportedTimestamp, getMaxPageSize());

      final AtomicLong currentPageLastEntityTimestamp = new AtomicLong(lastImportedTimestamp);
      if (!nextPageEvents.isEmpty()) {
        idleBackoffCalculator.resetBackoff();
        currentPageLastEntityTimestamp.set(nextPageEvents.get(nextPageEvents.size() - 1).getIngestionTimestamp());
      } else {
        calculateIdleSleepTime();
      }

      final List<EventDto> eventDtosToImport = Stream
        .concat(lastTimeStampEvents.stream(), nextPageEvents.stream())
        .collect(Collectors.toList());

      eventProcessInstanceImportService.executeImport(eventDtosToImport, () -> {
        lastPersistedTimestamp.set(currentPageLastEntityTimestamp.get());
        importCompleted.complete(null);
      });

      lastImportedTimestamp = currentPageLastEntityTimestamp.get();
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

  public Long getLastPersistedTimestamp() {
    return lastPersistedTimestamp.get();
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
