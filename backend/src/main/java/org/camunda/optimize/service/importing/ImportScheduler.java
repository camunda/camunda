package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.impl.PaginatedImportService;
import org.camunda.optimize.service.importing.job.schedule.ImportScheduleJob;
import org.camunda.optimize.service.importing.job.schedule.PageBasedImportScheduleJob;
import org.camunda.optimize.service.importing.job.schedule.ScheduleJobFactory;
import org.camunda.optimize.service.importing.provider.ImportServiceProvider;
import org.camunda.optimize.service.util.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
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
  protected ImportJobExecutor importJobExecutor;

  @Autowired
  protected ScheduleJobFactory scheduleJobFactory;

  @Autowired
  protected ImportServiceProvider importServiceProvider;

  protected HashMap<String,Long> jobBackoffCounters = new HashMap<>();

  protected long generalBackoffCounter = STARTING_BACKOFF;

  private boolean enabled = true;

  private LocalDateTime lastReset = LocalDateTime.now();

  public void scheduleProcessEngineImport() {
    logger.debug("Scheduling import of all paginated types");
    this.importScheduleJobs.addAll(scheduleJobFactory.createPagedJobs());
  }

  @Override
  public void run() {
    while (isEnabled()) {
      checkAndResetImportIndexing();
      logger.debug("Executing import round");
      executeJob();
      logger.debug("Finished import round");
    }
  }

  protected void checkAndResetImportIndexing() {
    long castToLong = Double.valueOf(configurationService.getImportResetInterval()).longValue();
    LocalDateTime resetDueDate = lastReset.plus(castToLong, ChronoUnit.HOURS);
    if (LocalDateTime.now().isAfter(resetDueDate)) {
      logger.debug("Reset due date is due. Resetting the import indexes of the import services!");
      for (PaginatedImportService importService : importServiceProvider.getPagedServices()) {
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
      try {

        ImportResult importResult = toExecute.execute();
        pagesPassed = importResult.getPagesPassed();
        if (pagesPassed != 0) {
          postProcess(toExecute, importResult);
        } else if (pagesPassed == 0) {
          sleepAndReschedule(pagesPassed, toExecute);
        }

      } catch (RejectedExecutionException e) {
        //nothing bad happened, we just have a lot of data to import
        //next step is sleep
        if (logger.isDebugEnabled()) {
          logger.debug("import jobs capacity exceeded");
          sleepAndReschedule(pagesPassed, toExecute);
        }
      } catch (OptimizeException op) {
        // is thrown if there is a connection problem for instance
        sleepAndReschedule(pagesPassed, toExecute);
      } catch (Exception e) {
        if (logger.isDebugEnabled()) {
          logger.debug("error while executing import job", e);
        }
      }

    }

    if (importScheduleJobs.isEmpty()) {
      this.backoffAndSleep();
      sleepAndReschedule(pagesPassed);
    }
  }

  /**
   * The job might create additional information for creation of other jobs based on it.
   * An example is import of HPI based on information obtained from HAI.
   *
   * @param toExecute
   * @param importResult
   */
  private void postProcess(ImportScheduleJob toExecute, ImportResult importResult) {
    if (importResult.getIdsToFetch() != null) {
      importScheduleJobs.addAll(scheduleJobFactory.createIndexedScheduleJobs(importResult.getIdsToFetch()));
    }

    if (toExecute.isPageBased()) {
      PageBasedImportScheduleJob typeCastedJob = (PageBasedImportScheduleJob) toExecute;
      rescheduleBasedOnPages(
          importResult.getPagesPassed(),
          typeCastedJob,
          typeCastedJob.getIndexBeforeExecution(),
          typeCastedJob.getIndexAfterExecution()
      );
    }
  }

  /**
   * Handle rescheduling of currently executed job based on pages that were fetched
   * from the engine and overall progress.
   *
   * @param pagesPassed
   * @param toExecute
   * @param startIndex
   * @param endIndex
   */
  private void rescheduleBasedOnPages(int pagesPassed, PageBasedImportScheduleJob toExecute, int startIndex, int endIndex) {
    if (pagesPassed > 0) {
      logger.debug(
          "Processed [{}] pages during data import run of [{}], scheduling one more run",
          pagesPassed,
          toExecute.getImportService().getElasticsearchType()
      );
      importScheduleJobs.add(toExecute);
      this.setJobBackoff(toExecute.getImportService().getElasticsearchType(), STARTING_BACKOFF);
      this.generalBackoffCounter = STARTING_BACKOFF;
    }
    if (pagesPassed == 0 && (endIndex - startIndex != 0)) {
      logger.debug(
          "Index of [{}] is [{}]",
          toExecute.getImportService().getElasticsearchType(),
          toExecute.getImportService().getImportStartIndex()
      );
      importScheduleJobs.add(toExecute);
    }
  }

  private void sleepAndReschedule(int pagesPassed, ImportScheduleJob toExecute) {
    if (toExecute != null) {
      long sleepTime = calculateSleepTime(pagesPassed, toExecute);

      if (sleepTime > 0) {
        toExecute.setTimeToExecute(LocalDateTime.now().plus(sleepTime, ChronoUnit.MILLIS));
      } else {
        toExecute.setTimeToExecute(null);
      }

      try {
        this.importScheduleJobs.put(toExecute);
      } catch (InterruptedException e) {
        logger.debug("Can't reschedule job", e);
      }

      if (allJobsAreBackingOff()) {
        backoffAndSleep();
      }
    }


    //just in case someone manually added a job
    if (importScheduleJobs.isEmpty()) {
      this.scheduleProcessEngineImport();
    }
  }

  private void backoffAndSleep() {
    try {
      if (this.generalBackoffCounter < configurationService.getMaximumBackoff()) {
        this.generalBackoffCounter = this.generalBackoffCounter + 1;
      }
      Thread.sleep(configurationService.getGeneralBackoff() * this.generalBackoffCounter);
    } catch (InterruptedException e) {
      logger.error("Interrupting backoff", e);
    }
  }

  /**
   * Calculate sleep time for specific import job based on current progress
   * of import.
   *
   * @param pagesPassed
   * @param toExecute
   * @return
   */
  private long calculateSleepTime(int pagesPassed, ImportScheduleJob toExecute) {
    long jobBackoff = calculateJobBackoff(pagesPassed, toExecute);
    long interval = configurationService.getImportHandlerWait();
    long sleepTime = interval * jobBackoff;
    logDebugSleepInformation(sleepTime, toExecute);
    return sleepTime;
  }

  /**
   * Check if there is at least one job which does not have backoff time set.
   * @return
   */
  private boolean allJobsAreBackingOff() {
    boolean result = true;
    for (ImportScheduleJob job : this.importScheduleJobs) {
      if (job.getTimeToExecute() == null) {
        result = false;
        break;
      }
    }
    return result;
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

  protected long calculateJobBackoff(int pagesPassed, ImportScheduleJob toExecute) {
    long result;
    Long jobBackoff = getBackoffCounter(toExecute.getImportService().getElasticsearchType());
    if (pagesPassed == 0) {
      if (jobBackoff < configurationService.getMaximumBackoff()) {
        result = jobBackoff + 1;
        this.setJobBackoff(toExecute.getImportService().getElasticsearchType(), result);
      } else {
        result = jobBackoff;
      }
    } else {
      result = STARTING_BACKOFF;
    }
    return result;
  }

  protected void setJobBackoff (String job, Long backoff) {
    this.jobBackoffCounters.put(job,backoff);
  }

  protected void resetBackoffCounters() {
    for (Map.Entry e : this.jobBackoffCounters.entrySet()) {
      e.setValue(STARTING_BACKOFF);
    }
    this.generalBackoffCounter = STARTING_BACKOFF;
  }

  public LocalDateTime getLastReset() {
    return lastReset;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void disable() {
    this.enabled = false;
  }

  public Long getBackoffCounter(String elasticsearchType) {
    Long jobBackoff = this.jobBackoffCounters.get(elasticsearchType);
    if (jobBackoff == null) {
      jobBackoff = STARTING_BACKOFF;
      this.setJobBackoff(elasticsearchType, STARTING_BACKOFF);
    }
    return jobBackoff;
  }

  public long getGeneralBackoffCounter() {
    return generalBackoffCounter;
  }
}
