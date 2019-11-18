/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.engine.importing.service.ImportObserver;
import org.camunda.optimize.service.engine.importing.service.mediator.EngineImportMediator;
import org.camunda.optimize.service.engine.importing.service.mediator.ScrollBasedImportMediator;
import org.camunda.optimize.service.util.ImportJobExecutor;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;


@Slf4j
public class EngineImportScheduler extends Thread {

  private List<EngineImportMediator> importMediators;

  private List<ImportObserver> importObservers = Collections.synchronizedList(new LinkedList<>());

  private String engineAlias;
  private volatile boolean isEnabled = true;
  private boolean isImporting = false;

  public EngineImportScheduler(List<EngineImportMediator> importMediators,
                               String engineAlias) {
    this.importMediators = importMediators;
    this.engineAlias = engineAlias;
  }

  public void subscribe(ImportObserver importObserver) {
    importObservers.add(importObserver);
  }

  public void unsubscribe(ImportObserver importObserver) {
    importObservers.remove(importObserver);
  }

  public void disable() {
    log.debug("Scheduler for engine {} is disabled", engineAlias);
    isEnabled = false;
  }

  public void enable() {
    log.debug("Scheduler for engine {} was enabled and will soon start scheduling the import", engineAlias);
    isEnabled = true;
  }

  public void shutdown() {
    log.debug("Scheduler for engine {} will shutdown.", engineAlias);
    getImportMediators().forEach(mediator -> mediator.getImportJobExecutor().stopExecutingImportJobs());
  }

  @Override
  public void run() {
    while (isEnabled) {
      log.debug("Schedule next round!");
      try {
        scheduleNextRound();
      } catch (Exception e) {
        log.error("Could not schedule next import round!", e);
      }
    }
  }

  private List<EngineImportMediator> obtainActiveMediators() {
    return importMediators
      .stream()
      .filter(EngineImportMediator::canImport)
      .collect(Collectors.toList());
  }

  public void scheduleNextRoundScrollBasedOnly() {
    final List<EngineImportMediator> currentImportRound = obtainActiveMediators()
      .stream()
      .filter(e -> e instanceof ScrollBasedImportMediator)
      .collect(Collectors.toList());
    scheduleCurrentImportRound(currentImportRound);
  }

  public void scheduleNextRound() {
    List<EngineImportMediator> currentImportRound = obtainActiveMediators();
    if (nothingToBeImported(currentImportRound)) {
      this.isImporting = false;
      if (!hasActiveImportJobs()) {
        notifyThatImportIsIdle();
      }
      doBackoff();
    } else {
      this.isImporting = true;
      notifyThatImportIsInProgress();
      scheduleCurrentImportRound(currentImportRound);
    }
  }

  private boolean hasActiveImportJobs() {
    return importMediators
      .stream()
      .map(EngineImportMediator::getImportJobExecutor)
      .anyMatch(ImportJobExecutor::isActive);
  }

  private void notifyThatImportIsInProgress() {
    importObservers.forEach(o -> o.importInProgress(engineAlias));
  }

  private void notifyThatImportIsIdle() {
    importObservers.forEach(o -> o.importIsIdle(engineAlias));
  }

  private boolean nothingToBeImported(List currentImportRound) {
    return currentImportRound.isEmpty();
  }

  private void doBackoff() {
    long timeToSleep = calculateTimeToSleep();
    try {
      log.debug("No imports to schedule. Scheduler is sleeping for [{}] ms.", timeToSleep);
      Thread.sleep(timeToSleep);
    } catch (InterruptedException e) {
      log.error("Scheduler was interrupted while sleeping.", e);
    }
  }

  private long calculateTimeToSleep() {
    return importMediators
      .stream()
      .map(EngineImportMediator::getBackoffTimeInMs)
      .min(Long::compare)
      .orElse(5000L);
  }

  private void scheduleCurrentImportRound(List<EngineImportMediator> currentImportRound) {
    String mediators = currentImportRound.stream()
      .map(c -> c.getClass().getSimpleName())
      .reduce((a, b) -> a + ", " + b).orElse("");
    log.debug("Scheduling import round for {}", mediators);
    for (EngineImportMediator engineImportMediator : currentImportRound) {
      try {
        engineImportMediator.importNextPage();
      } catch (Exception e) {
        log.error("Was not able to execute import of [{}]", engineImportMediator.getClass().getSimpleName(), e);
      }
    }
  }

  public boolean isEnabled() {
    return this.isEnabled;
  }

  public String getEngineAlias() {
    return engineAlias;
  }

  public boolean isImporting() {
    return isImporting || hasActiveImportJobs();
  }

  public List<EngineImportMediator> getImportMediators() {
    return importMediators;
  }
}
