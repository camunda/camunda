package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.exceptions.OptimizeException;
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

  @Autowired
  protected ImportProgressReporter importProgressReporter;

  protected long backoffCounter = STARTING_BACKOFF;

  private boolean enabled = true;

  private LocalDateTime lastReset = LocalDateTime.now();

  public void scheduleProcessEngineImport() {
    logger.debug("Scheduling import of all types");
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
      logger.debug("Executing import round");
      executeJob();
      logger.debug("Finished import round. \n");
    }
  }

  protected void checkAndResetImportIndexing() {
    long castToLong = Double.valueOf(configurationService.getImportResetInterval()).longValue();
    LocalDateTime resetDueDate = lastReset.plus(castToLong, ChronoUnit.HOURS);
    if (LocalDateTime.now().isAfter(resetDueDate)) {
      logger.debug("Reset due date is due. Resetting the import indexes of the import services!");
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
    int pagesPassed = 0;

    if (!importScheduleJobs.isEmpty()) {
      ImportScheduleJob toExecute = importScheduleJobs.poll();
      int startIndex = toExecute.getImportService().getImportStartIndex();
      int endIndex = 0;
      try {
        pagesPassed = toExecute.execute();
        endIndex = toExecute.getImportService().getImportStartIndex();
      } catch (RejectedExecutionException e) {
        //nothing bad happened, we just have a lot of data to import
        //next step is sleep
        if (logger.isDebugEnabled()) {
          logger.debug("import jobs capacity exceeded");
          sleepAndReschedule(pagesPassed, toExecute);
        }
      } catch (OptimizeException op) {
        sleepAndReschedule(pagesPassed, toExecute);
      } catch (Exception e) {
        if (logger.isDebugEnabled()) {
          logger.debug("error while executing import job", e);
        }
      }
      if (pagesPassed > 0) {
        logger.debug(
            "Processed [{}] pages during data import run of [{}], scheduling one more run",
            pagesPassed,
            toExecute.getImportService().getElasticsearchType()
        );
        importScheduleJobs.add(toExecute);
        backoffCounter = STARTING_BACKOFF;
      } if (pagesPassed == 0 && (endIndex - startIndex != 0)) {
        logger.debug(
            "Index of [{}] is [{}]",
            toExecute.getImportService().getElasticsearchType(),
            toExecute.getImportService().getImportStartIndex()
        );
        importScheduleJobs.add(toExecute);
      }
    }

    if (importScheduleJobs.isEmpty()) {
      sleepAndReschedule(pagesPassed);
    }
  }

  private void sleepAndReschedule(int pagesPassed, ImportScheduleJob toExecute) {
    backoffCounter = calculateBackoff(pagesPassed);
    long interval = configurationService.getImportHandlerWait();

    try {
      long sleepTime = interval * backoffCounter;
      logDebugSleepInformation(sleepTime, toExecute);
      Thread.currentThread().sleep(sleepTime);
    } catch (InterruptedException e) {
      logger.warn("Import handler is interrupted while sleeping between import jobs", e);
    }

    //just in case someone manually added a job
    if (importScheduleJobs.isEmpty()) {
      this.scheduleProcessEngineImport();
    }
  }

  private void logDebugSleepInformation(long sleepTime, ImportScheduleJob toExecute) {
    if (toExecute != null) {
      logger.debug(
          "Cant schedule import of [{}], sleeping for [{}] ms",
          toExecute.getImportService().getElasticsearchType(),
          sleepTime
      );
    } else {
      logger.debug(
          "No data for import detected, sleeping for [{}] ms",
          sleepTime
      );
    }
  }

  protected void sleepAndReschedule(int pagesPassed) {
    this.sleepAndReschedule(pagesPassed, null);
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
