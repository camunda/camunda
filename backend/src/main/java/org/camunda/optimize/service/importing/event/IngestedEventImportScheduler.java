/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.event;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.AbstractScheduledService;
import org.camunda.optimize.service.events.stateprocessing.EventStateProcessingService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.IngestedEventImportConfiguration;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@AllArgsConstructor
@Slf4j
@Component
public class IngestedEventImportScheduler extends AbstractScheduledService {
  private final ConfigurationService configurationService;
  private final EventStateProcessingService eventStateProcessingService;
  private final EventProcessInstanceImportMediatorManager instanceImportMediatorManager;
  private final EventProcessInstanceIndexManager eventBasedProcessIndexManager;
  private final PublishStateUpdateService publishStateUpdateService;
  private final EventProcessDefinitionImportService eventProcessDefinitionImportService;

  @PostConstruct
  public void init() {
    if (getEventBasedImportConfiguration().isEnabled()) {
      startImportScheduling();
    }
  }

  @PreDestroy
  public synchronized void stopImportScheduling() {
    log.info("Stop scheduling ingested event import.");
    stopScheduling();
  }

  public synchronized void startImportScheduling() {
    log.info("Scheduling ingested event import.");
    startScheduling();
  }

  @Override
  protected void run() {
    runImportCycle();
  }

  public Future<Void> runImportCycle() {
    if (!eventStateProcessingService.isCurrentlyProcessingEvents()) {
      eventStateProcessingService.processUncountedEvents();
    }
    eventBasedProcessIndexManager.syncAvailableIndices();
    eventBasedProcessIndexManager.cleanupIndexes();
    instanceImportMediatorManager.refreshMediators();
    publishStateUpdateService.updateEventProcessPublishStates();
    eventProcessDefinitionImportService.syncPublishedEventProcessDefinitions();
    final CompletableFuture<?>[] importTaskFutures = instanceImportMediatorManager.getActiveMediators()
      .stream()
      .filter(EventProcessInstanceImportMediator::canImport)
      .map(eventProcessInstanceImportMediator -> {
        final CompletableFuture<Void> importCompletedFuture =
          eventBasedProcessIndexManager.registerIndexUsageAndReturnCompletableHook(eventProcessInstanceImportMediator);
        eventProcessInstanceImportMediator.importNextPage(importCompletedFuture);
        return importCompletedFuture;
      })
      .toArray(CompletableFuture[]::new);

    return CompletableFuture.allOf(importTaskFutures);
  }

  @Override
  protected Trigger getScheduleTrigger() {
    return new PeriodicTrigger(getEventBasedImportConfiguration().getImportIntervalInSec(), TimeUnit.SECONDS);
  }

  private IngestedEventImportConfiguration getEventBasedImportConfiguration() {
    return configurationService.getIngestedEventImportConfiguration();
  }
}
