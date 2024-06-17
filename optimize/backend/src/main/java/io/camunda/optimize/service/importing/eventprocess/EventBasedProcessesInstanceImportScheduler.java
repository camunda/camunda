/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.eventprocess;

import static io.camunda.optimize.dto.optimize.query.event.process.EventProcessState.PUBLISH_PENDING;

import io.camunda.optimize.dto.optimize.query.event.process.EventProcessEventDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;
import io.camunda.optimize.service.AbstractScheduledService;
import io.camunda.optimize.service.db.EventProcessInstanceIndexManager;
import io.camunda.optimize.service.importing.ImportMediator;
import io.camunda.optimize.service.importing.eventprocess.mediator.EventProcessInstanceImportMediator;
import io.camunda.optimize.service.importing.eventprocess.service.EventProcessDefinitionImportService;
import io.camunda.optimize.service.importing.eventprocess.service.PublishStateUpdateService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.EventBasedProcessConfiguration;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Slf4j
@Component
public class EventBasedProcessesInstanceImportScheduler extends AbstractScheduledService {
  private final ConfigurationService configurationService;

  private final EventProcessInstanceImportMediatorManager instanceImportMediatorManager;
  private final EventProcessInstanceIndexManager eventBasedProcessIndexManager;
  private final PublishStateUpdateService publishStateUpdateService;
  private final EventProcessDefinitionImportService eventProcessDefinitionImportService;

  @PostConstruct
  public void init() {
    if (getEventBasedProcessConfiguration().getEventImport().isEnabled()) {
      startImportScheduling();
    }
  }

  public synchronized void startImportScheduling() {
    log.info("Scheduling ingested event import.");
    startScheduling();
  }

  @PreDestroy
  public synchronized void stopImportScheduling() {
    log.info("Stop scheduling ingested event import.");
    stopScheduling();
  }

  public Future<Void> runImportRound() {
    return runImportRound(false);
  }

  public Future<Void> runImportRound(final boolean forceImport) {
    eventBasedProcessIndexManager.syncAvailableIndices();
    instanceImportMediatorManager.refreshMediators();
    publishStateUpdateService.updateEventProcessPublishStates();
    eventProcessDefinitionImportService.syncPublishedEventProcessDefinitions();

    final Collection<EventProcessInstanceImportMediator<EventProcessEventDto>>
        allInstanceMediators = instanceImportMediatorManager.getActiveMediators();
    final List<EventProcessInstanceImportMediator> currentImportRound =
        allInstanceMediators.stream()
            .filter(mediator -> forceImport || mediator.canImport())
            .collect(Collectors.toList());

    if (log.isDebugEnabled()) {
      log.debug(
          "Scheduling import round for {}",
          currentImportRound.stream()
              .map(mediator -> mediator.getClass().getSimpleName())
              .collect(Collectors.joining(",")));
    }

    final CompletableFuture<?>[] importTaskFutures =
        currentImportRound.stream()
            .map(
                mediator -> {
                  final CompletableFuture<Void> indexUsageFinishedFuture =
                      eventBasedProcessIndexManager.registerIndexUsageAndReturnFinishedHandler(
                          mediator.getPublishedProcessStateId());
                  final CompletableFuture<Void> importCompleteFuture = mediator.runImport();
                  importCompleteFuture.whenComplete(
                      (aVoid, throwable) -> indexUsageFinishedFuture.complete(null));
                  return importCompleteFuture;
                })
            .toArray(CompletableFuture[]::new);

    Optional<EventProcessPublishStateDto> pendingPublish =
        eventBasedProcessIndexManager.getPublishedInstanceStates().stream()
            .filter(s -> s.getState().equals(PUBLISH_PENDING))
            .findFirst();

    if (importTaskFutures.length == 0 && !forceImport && !pendingPublish.isPresent()) {
      doBackoff(allInstanceMediators);
    }

    return CompletableFuture.allOf(importTaskFutures);
  }

  @Override
  protected void run() {
    if (isScheduledToRun()) {
      runImportRound();
    }
  }

  @Override
  protected Trigger createScheduleTrigger() {
    return new PeriodicTrigger(Duration.ZERO);
  }

  private void doBackoff(final Collection<? extends ImportMediator> mediators) {
    long timeToSleep =
        mediators.stream().map(ImportMediator::getBackoffTimeInMs).min(Long::compare).orElse(5000L);
    try {
      log.debug("No imports to schedule. Scheduler is sleeping for [{}] ms.", timeToSleep);
      Thread.sleep(timeToSleep);
    } catch (InterruptedException e) {
      log.warn("Scheduler was interrupted while sleeping.", e);
      Thread.currentThread().interrupt();
    }
  }

  private EventBasedProcessConfiguration getEventBasedProcessConfiguration() {
    return configurationService.getEventBasedProcessConfiguration();
  }
}
