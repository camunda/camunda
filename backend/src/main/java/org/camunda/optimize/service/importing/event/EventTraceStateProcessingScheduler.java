/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.AbstractScheduledService;
import org.camunda.optimize.service.importing.EngineImportMediator;
import org.camunda.optimize.service.importing.event.mediator.PersistEventIndexHandlerStateMediator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.EventBasedProcessConfiguration;
import org.camunda.optimize.service.util.configuration.EventImportConfiguration;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

  @PreDestroy
  @Override
  public synchronized void stopScheduling() {
    log.info("Stop scheduling event pre-aggregation.");
    super.stopScheduling();
  }

  @Override
  public synchronized boolean startScheduling() {
    log.info("Scheduling event pre-aggregation.");
    return super.startScheduling();
  }

  @Override
  protected Trigger getScheduleTrigger() {
    return new PeriodicTrigger(getEventImportConfiguration().getImportIntervalInSec(), TimeUnit.SECONDS);
  }

  @Override
  protected void run() {
    runImportRound();
  }

  public Future<Void> runImportRound() {
    return runImportRound(false);
  }

  public Future<Void> runImportRound(final boolean forceImport) {
    final CompletableFuture<?>[] importTaskFutures = getImportMediators()
      .stream()
      .filter(mediator -> forceImport || mediator.canImport())
      .map(EngineImportMediator::runImport)
      .toArray(CompletableFuture[]::new);

    return CompletableFuture.allOf(importTaskFutures);
  }

  public List<EngineImportMediator> getImportMediators() {
    return Stream.concat(
      Stream.of(eventProcessingProgressMediator),
      eventTraceImportMediatorManager.getEventTraceImportMediators().stream()
    ).collect(Collectors.toList());
  }

  private EventBasedProcessConfiguration getEventBasedProcessConfiguration() {
    return configurationService.getEventBasedProcessConfiguration();
  }

  private EventImportConfiguration getEventImportConfiguration() {
    return configurationService.getEventImportConfiguration();
  }
}
