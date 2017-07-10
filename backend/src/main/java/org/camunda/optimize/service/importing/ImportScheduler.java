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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;

public class ImportScheduler extends Thread {

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

  @Autowired
  protected BackoffService backoffService;

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


    ImportScheduleJob toExecute = getNextToExecute();

    if (toExecute != null) {
      pagesPassed = executeAndGetPagesPassed(pagesPassed, toExecute);
    }

    if (importScheduleJobs.isEmpty()) {
      backoffService.backoffAndSleep();
      sleepAndReschedule(pagesPassed);
    }
  }

  public ImportScheduleJob getNextToExecute() {
    ImportScheduleJob toExecute = null;
    if (!importScheduleJobs.isEmpty()) {
      toExecute = importScheduleJobs.poll();
    }
    return toExecute;
  }

  private int executeAndGetPagesPassed(int pagesPassed, ImportScheduleJob toExecute) {
    try {

      ImportResult importResult = toExecute.execute();
      pagesPassed = importResult.getPagesPassed();
      if (pagesPassed != 0) {
        postProcess(toExecute, importResult);
      } else if (pagesPassed == 0 && toExecute.isPageBased()) {
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
    return pagesPassed;
  }

  /**
   * The job might create additional information for creation of other jobs based on it.
   * An example is import of HPI based on information obtained from HAI.
   *
   * @param toExecute
   * @param importResult
   */
  public void postProcess(ImportScheduleJob toExecute, ImportResult importResult) {
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
      backoffService.resetBackoff(toExecute);
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
      backoffService.handleSleep(pagesPassed,toExecute);

      try {
        this.importScheduleJobs.put(toExecute);
      } catch (InterruptedException e) {
        logger.debug("Can't reschedule job", e);
      }

      if (allJobsAreBackingOff()) {
        backoffService.backoffAndSleep();
      }
    }


    //just in case someone manually added a job
    if (importScheduleJobs.isEmpty()) {
      this.scheduleProcessEngineImport();
    }
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

  protected void sleepAndReschedule(int pagesPassed) {
    this.sleepAndReschedule(pagesPassed, null);
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


}
