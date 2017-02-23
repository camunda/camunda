package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.util.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.ArrayBlockingQueue;

public class ImportScheduler extends Thread {
  private final Logger logger = LoggerFactory.getLogger(ImportScheduler.class);

  protected final ArrayBlockingQueue<ImportScheduleJob> importScheduleJobs = new ArrayBlockingQueue<>(3);
  
  @Autowired
  protected ConfigurationService configurationService;

  @Autowired
  protected ImportServiceProvider importServiceProvider;

  protected long backoffCounter = 0;

  public void scheduleProcessEngineImport() {
    ImportScheduleJob job = new ImportScheduleJob();
    job.setImportServiceProvider(importServiceProvider);
    this.importScheduleJobs.add(job);
  }

  @Override
  public void run() {
    while (true) {
      executeJob();
    }
  }

  @Override
  public void start() {
    logger.info("starting import scheduler thread");
    super.start();
  }

  protected void executeJob() {
    logger.debug("executing import round");
    int pagesPassed = 0;

    if (!importScheduleJobs.isEmpty()) {
      ImportScheduleJob toExecute = importScheduleJobs.poll();
      pagesPassed = toExecute.execute();
      if (pagesPassed > 0) {
        logger.debug("Processed [" + pagesPassed + "] pages during data import run, scheduling one more run");
        this.scheduleProcessEngineImport();
        backoffCounter = calculateBackoff(pagesPassed);
      }
    }

    if (importScheduleJobs.isEmpty()) {
      sleepAndReschedule(pagesPassed);
    }
  }

  protected void sleepAndReschedule(int pagesPassed) {
    backoffCounter = calculateBackoff(pagesPassed);
    long interval = configurationService.getImportHandlerWait();

    try {
      long sleepTime = interval * backoffCounter;
      logger.debug("No data for import detected, sleeping for [" + sleepTime + "] ms");
      Thread.currentThread().sleep(sleepTime);
    } catch (InterruptedException e) {
      logger.warn("Import handler is interrupted while sleeping between import jobs", e);
    }

    //just in case someone manually added a job
    if (importScheduleJobs.isEmpty()) {
      this.scheduleProcessEngineImport();
    }
  }

  protected long calculateBackoff(int pagesPassed) {
    long result;
    if (pagesPassed == 0) {
      if (backoffCounter < configurationService.getMaximumBackoff()) {
        result = backoffCounter + 1;
      } else {
        result = backoffCounter;
      }
    } else {
      result = 1;
    }
    return result;
  }

  protected long getBackoffCounter() {
    return backoffCounter;
  }

  protected void resetBackoffCounter() {
    this.backoffCounter = 0;
  }
}
