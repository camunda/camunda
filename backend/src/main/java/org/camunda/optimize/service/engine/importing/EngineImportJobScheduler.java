package org.camunda.optimize.service.engine.importing;

import org.camunda.optimize.service.engine.importing.job.factory.EngineImportJobFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class EngineImportJobScheduler extends Thread {

  private Logger logger = LoggerFactory.getLogger(getClass());

  private EngineImportJobExecutor executor;
  private List<EngineImportJobFactory> jobFactories;

  private volatile boolean isEnabled = true;

  public EngineImportJobScheduler(EngineImportJobExecutor executor,
                                  List<EngineImportJobFactory> jobFactories) {
    this.executor = executor;
    this.jobFactories = jobFactories;
  }

  public void disable() {
    logger.debug("Scheduler is disabled and will soon shut down");
    isEnabled = false;
  }

  public void enable() {
    logger.debug("Scheduler was enabled and will soon start scheduling jobs");
    isEnabled = true;
  }

  @Override
  public void run() {
    while (isEnabled) {
      logger.debug("Schedule next round!");
      scheduleNextRound();
    }
  }

  private List<Runnable> obtainNextJobRound() {
    return jobFactories
        .stream()
        .map(EngineImportJobFactory::getNextJob)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());
  }

  public void scheduleUntilCantCreateNewJobs() {
    List<Runnable> currentImportRound = obtainNextJobRound();
    while (!nothingToBeImported(currentImportRound)) {
      scheduleCurrentJobRound(currentImportRound);
      currentImportRound = obtainNextJobRound();
    }
  }

  public void scheduleNextRound() {
    List<Runnable> currentImportRound = obtainNextJobRound();
    if (nothingToBeImported(currentImportRound)) {
      doBackoff();
    } else {
      scheduleCurrentJobRound(currentImportRound);
    }
  }

  private boolean nothingToBeImported(List<Runnable> currentImportJobRound) {
    return currentImportJobRound.isEmpty();
  }

  private void doBackoff() {
    long timeToSleep = calculateTimeToSleep();
    try {
      logger.debug("No jobs to schedule. Scheduler is sleeping for [{}] ms.", timeToSleep);
      Thread.sleep(timeToSleep);
    } catch (InterruptedException e) {
      logger.error("Scheduler was interrupted while sleeping.", e);
    }
  }

  private long calculateTimeToSleep() {
    long timeToSleepInMs = jobFactories
        .stream()
        .map(EngineImportJobFactory::getBackoffTimeInMs)
        .min(Long::compare)
        .orElse(5000L);
    return timeToSleepInMs;
  }

  private void scheduleCurrentJobRound(List<Runnable> currentImportJobRound) {
    for (int ithJob = 0; ithJob < currentImportJobRound.size(); ithJob++) {
      Runnable currentJob = currentImportJobRound.get(ithJob);
      try {
        executor.executeImportJob(currentJob);
      } catch (InterruptedException e) {
        logger.error("Scheduler was interrupted while trying to schedule job. Scheduling the job once again.", e);
        currentImportJobRound.add(currentJob);
      }
    }
  }

  public boolean isEnabled() {
    return this.isEnabled;
  }
}
