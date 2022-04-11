/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.AbstractScheduledService;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.importing.event.mediator.PersistEventIndexHandlerStateMediator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.EventBasedProcessConfiguration;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@AllArgsConstructor
@Slf4j
@Component
public class EventTraceStateProcessingScheduler extends AbstractScheduledService {
  private final ConfigurationService configurationService;

  private final EventTraceImportMediatorManager eventTraceImportMediatorManager;
  @Getter
  private final PersistEventIndexHandlerStateMediator eventProcessingProgressMediator;

  @PostConstruct
  public void init() {
    if (getEventBasedProcessConfiguration().getEventImport().isEnabled()) {
      startScheduling();
    }
  }

  public Future<Void> runImportRound() {
    return runImportRound(false);
  }

  public Future<Void> runImportRound(final boolean forceImport) {
    final List<ImportMediator> allImportMediators = getImportMediators();
    final List<ImportMediator> currentImportRound = allImportMediators
      .stream()
      .filter(mediator -> forceImport || mediator.canImport())
      .collect(Collectors.toList());

    if (log.isDebugEnabled()) {
      log.debug(
        "Scheduling import round for {}",
        currentImportRound.stream()
          .map(mediator1 -> mediator1.getClass().getSimpleName())
          .collect(Collectors.joining(","))
      );
    }

    final CompletableFuture<?>[] importTaskFutures = currentImportRound
      .stream()
      .map(ImportMediator::runImport)
      .toArray(CompletableFuture[]::new);

    if (importTaskFutures.length == 0 && !forceImport) {
      doBackoff(allImportMediators);
    }

    return CompletableFuture.allOf(importTaskFutures);
  }

  public List<ImportMediator> getImportMediators() {
    return Stream.concat(
      Stream.of(eventProcessingProgressMediator),
      eventTraceImportMediatorManager.getEventTraceImportMediators().stream()
    ).collect(Collectors.toList());
  }

  @Override
  protected void run() {
    if (isScheduledToRun()) {
      runImportRound();
    }
  }

  @Override
  protected Trigger createScheduleTrigger() {
    return new PeriodicTrigger(0);
  }

  @Override
  public synchronized boolean startScheduling() {
    log.info("Scheduling event pre-aggregation.");
    return super.startScheduling();
  }

  @PreDestroy
  @Override
  public synchronized void stopScheduling() {
    log.info("Stop scheduling event pre-aggregation.");
    super.stopScheduling();
  }

  private void doBackoff(final List<ImportMediator> mediators) {
    long timeToSleep = mediators
      .stream()
      .map(ImportMediator::getBackoffTimeInMs)
      .min(Long::compare)
      .orElse(5000L);
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
