package org.camunda.optimize.service.engine.importing;

import org.camunda.optimize.service.engine.importing.service.mediator.EngineImportMediator;
import org.camunda.optimize.service.engine.importing.service.mediator.StoreIndexesEngineImportMediator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class EngineImportScheduler extends Thread {

  private Logger logger = LoggerFactory.getLogger(getClass());

  private List<EngineImportMediator> importMediators;
  private List<EngineImportMediator> sleepyMediators;

  private volatile boolean isEnabled = true;

  public EngineImportScheduler(
      List<EngineImportMediator> importMediators
  ) {
    this.importMediators = importMediators;
    this.sleepyMediators = filterSleepyFactories(importMediators);
  }

  private List<EngineImportMediator> filterSleepyFactories(List<EngineImportMediator> mediators) {
    ArrayList result = new ArrayList();

    for (EngineImportMediator factory : mediators) {
      if (!factory.getClass().equals(StoreIndexesEngineImportMediator.class)) {
        result.add(factory);
      }
    }
    return result;
  }

  public void disable() {
    logger.debug("Scheduler is disabled and will soon shut down");
    isEnabled = false;
  }

  public void enable() {
    logger.debug("Scheduler was enabled and will soon start scheduling the import");
    isEnabled = true;
  }

  @Override
  public void run() {
    while (isEnabled) {
      logger.debug("Schedule next round!");
      try {
        scheduleNextRound();
      } catch (Exception e) {
        logger.error("Could not schedule next import round!", e);
      }
    }
  }

  private List<EngineImportMediator> obtainActiveMediators() {
    return importMediators
        .stream()
        .filter(EngineImportMediator::canImport)
        .collect(Collectors.toList());
  }

  public void scheduleUntilImportIsFinished() {
    List<EngineImportMediator> currentImportRound = obtainActiveMediators();
    while (!nothingToBeImported(currentImportRound)) {
      scheduleCurrentImportRound(currentImportRound);
      currentImportRound = obtainActiveMediators();
    }
  }

  public void scheduleNextRound() {
    List<EngineImportMediator> currentImportRound = obtainActiveMediators();
    if (nothingToBeImported(currentImportRound)) {
      doBackoff();
    } else {
      scheduleCurrentImportRound(currentImportRound);
    }
  }

  private boolean nothingToBeImported(List currentImportRound) {
    return currentImportRound.isEmpty();
  }

  private void doBackoff() {
    long timeToSleep = calculateTimeToSleep();
    try {
      logger.debug("No imports to schedule. Scheduler is sleeping for [{}] ms.", timeToSleep);
      Thread.sleep(timeToSleep);
    } catch (InterruptedException e) {
      logger.error("Scheduler was interrupted while sleeping.", e);
    }
  }

  private long calculateTimeToSleep() {
    long timeToSleepInMs = sleepyMediators
        .stream()
        .map(EngineImportMediator::getBackoffTimeInMs)
        .min(Long::compare)
        .orElse(5000L);

    return timeToSleepInMs;
  }

  private void scheduleCurrentImportRound(List<EngineImportMediator> currentImportRound) {
    String mediators = currentImportRound.stream()
        .map(c -> c.getClass().getSimpleName())
        .reduce((a,b) -> a + ", " + b).get();
      logger.debug("Scheduling import round for {}", mediators);
    for (EngineImportMediator engineImportMediator : currentImportRound) {
      try {
        engineImportMediator.importNextPage();
      } catch (Exception e) {
        logger.error("Was not able to execute import of [{}]", engineImportMediator.getClass().getSimpleName(), e);
      }
    }
  }

  public boolean isEnabled() {
    return this.isEnabled;
  }
}
