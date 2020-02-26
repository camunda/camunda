/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.eventprocess.mediator;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.EventDto;
import org.camunda.optimize.dto.optimize.query.event.EventImportSourceDto;
import org.camunda.optimize.service.events.EventFetcherService;
import org.camunda.optimize.service.importing.eventprocess.service.EventProcessInstanceImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
  @Getter
  private final EventImportSourceDto eventImportSourceDto;
  private final ConfigurationService configurationService;
  private final EventFetcherService eventService;
  private final EventProcessInstanceImportService eventProcessInstanceImportService;

  private final BackoffCalculator idleBackoffCalculator;

  private Long lastImportedEntityTimestamp = 0L;

  public EventProcessInstanceImportMediator(final String publishedProcessStateId,
                                            final EventImportSourceDto eventImportSourceDto,
                                            final ConfigurationService configurationService,
                                            final EventFetcherService eventService,
                                            final EventProcessInstanceImportService eventProcessInstanceImportService) {
    this.publishedProcessStateId = publishedProcessStateId;
    this.eventImportSourceDto = eventImportSourceDto;
    this.configurationService = configurationService;
    this.eventService = eventService;
    this.eventProcessInstanceImportService = eventProcessInstanceImportService;
    this.idleBackoffCalculator = new BackoffCalculator(configurationService);
    this.lastImportedEntityTimestamp = eventImportSourceDto.getLastImportedEventTimestamp().toInstant().toEpochMilli();
  }

  public void importNextPage(final CompletableFuture<Void> importCompleted) {
    try {
      final List<EventDto> lastTimeStampEvents = eventService.getEventsIngestedAt(lastImportedEntityTimestamp);
      final List<EventDto> nextPageEvents = eventService.getEventsIngestedAfter(
        lastImportedEntityTimestamp,
        getMaxPageSize()
      );

      final AtomicLong currentPageLastEntityTimestamp = new AtomicLong(lastImportedEntityTimestamp);
      if (!nextPageEvents.isEmpty()) {
        idleBackoffCalculator.resetBackoff();
        currentPageLastEntityTimestamp.set(nextPageEvents.get(nextPageEvents.size() - 1).getIngestionTimestamp());

        List<EventDto> eventDtosToImport = Stream.concat(lastTimeStampEvents.stream(), nextPageEvents.stream())
          .collect(Collectors.toList());

        eventProcessInstanceImportService.executeImport(eventDtosToImport, () -> {
          eventImportSourceDto.setLastImportedEventTimestamp(OffsetDateTime.ofInstant(Instant.ofEpochMilli(
            currentPageLastEntityTimestamp.get()), ZoneId.systemDefault()));
          importCompleted.complete(null);
        });
        lastImportedEntityTimestamp = currentPageLastEntityTimestamp.get();
      } else {
        calculateIdleSleepTime();
        importCompleted.complete(null);
      }
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
    return configurationService.getEventImportConfiguration().getMaxPageSize();
  }

  public void shutdown() {
    eventProcessInstanceImportService.shutdown();
  }

}
