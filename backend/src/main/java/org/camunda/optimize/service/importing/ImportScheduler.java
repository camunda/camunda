package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.status.ImportProgressReporter;
import org.camunda.optimize.service.util.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;

public class ImportScheduler extends Thread {
  protected static final long STARTING_BACKOFF = 0;
  public static final String SCHEDULER_NAME = "ImportScheduler-Thread";
  private final Logger logger = LoggerFactory.getLogger(ImportScheduler.class);

  protected final LinkedBlockingQueue<ImportScheduleJob> importScheduleJobs = new LinkedBlockingQueue<>();
  
  @Autowired
  protected ConfigurationService configurationService;

  @Autowired
  protected ImportServiceProvider importServiceProvider;

  @Autowired
  protected ImportJobExecutor importJobExecutor;

  protected long backoffCounter = STARTING_BACKOFF;

  private boolean enabled = true;

  private LocalDateTime lastReset = LocalDateTime.now();

  public void scheduleProcessEngineImport() {
    for (ImportService service : importServiceProvider.getServices()) {
      ImportScheduleJob job = new ImportScheduleJob();
      job.setImportService(service);
      this.importScheduleJobs.add(job);
    }
  }

  @Override
  public void run() {
    while (isEnabled()) {
      checkAndResetImportIndexing();
      executeJob();
    }
  }

  protected void checkAndResetImportIndexing() {
    long castToLong = Double.valueOf(configurationService.getImportResetInterval()).longValue();
    LocalDateTime resetDueDate = lastReset.plus(castToLong, ChronoUnit.HOURS);
    if (LocalDateTime.now().isAfter(resetDueDate)) {
      for (ImportService importService : importServiceProvider.getServices()) {
        importService.resetImportStartIndex();
      }
      lastReset = LocalDateTime.now();
    }
  }

  @Override
  public void start() {
    logger.info("starting import scheduler thread");
    this.scheduleProcessEngineImport();
    super.start();
    this.setName(SCHEDULER_NAME);
  }



  protected void executeJob() {
    logger.debug("executing import round");
    int pagesPassed = 0;

    if (!importScheduleJobs.isEmpty()) {
      ImportScheduleJob toExecute = importScheduleJobs.poll();
      try {
        pagesPassed = toExecute.execute();
      } catch (RejectedExecutionException e) {
        //nothing bad happened, we just have a lot of data to import
        //next step is sleep
        if (logger.isDebugEnabled()) {
          logger.debug("import jobs capacity exceeded");
        }
      } catch (Exception e) {
        if (logger.isDebugEnabled()) {
          logger.debug("error while executing import job", e);
        }
      }
      if (pagesPassed > 0) {
        logger.debug("Processed [" + pagesPassed + "] pages during data import run, scheduling one more run");
        importScheduleJobs.add(toExecute);
        backoffCounter = STARTING_BACKOFF;
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
      result = STARTING_BACKOFF;
    }
    return result;
  }

  protected long getBackoffCounter() {
    return backoffCounter;
  }

  protected void resetBackoffCounter() {
    this.backoffCounter = STARTING_BACKOFF;
  }

  public LocalDateTime getLastReset() {
    return lastReset;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void disable () {
    this.enabled = false;
  }
}
