package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.impl.PaginatedImportService;
import org.camunda.optimize.service.importing.index.ImportIndexHandler;
import org.camunda.optimize.service.importing.job.schedule.IdleImportScheduleJob;
import org.camunda.optimize.service.importing.job.schedule.ImportScheduleJob;
import org.camunda.optimize.service.importing.job.schedule.PageBasedImportScheduleJob;
import org.camunda.optimize.service.importing.job.schedule.ScheduleJobFactory;
import org.camunda.optimize.service.importing.provider.ImportServiceProvider;
import org.camunda.optimize.service.importing.provider.IndexHandlerProvider;
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
  protected IndexHandlerProvider indexHandlerProvider;

  @Autowired
  protected BackoffService backoffService;

  private boolean enabled = true;
  private boolean skipBackoffToCheckForNewDataInEngine = true;

  private LocalDateTime lastReset = LocalDateTime.now();

  public void scheduleNewImportRound() {
    logger.debug("Scheduling import of all paginated types");
    this.importScheduleJobs.addAll(scheduleJobFactory.createPagedJobs());
  }

  @Override
  public void run() {
    while (isEnabled()) {
      checkAndResetImportIndexing();
      logger.debug("Executing import round");
      executeNextJob();
      logger.debug("Finished import round");
    }
  }

  protected void checkAndResetImportIndexing() {
    long castToLong = Double.valueOf(configurationService.getImportResetInterval()).longValue();
    LocalDateTime resetDueDate = lastReset.plus(castToLong, ChronoUnit.HOURS);
    if (LocalDateTime.now().isAfter(resetDueDate)) {
      logger.debug("Reset due date is due. Resetting the import indexes of the import services!");
      for (ImportIndexHandler importIndexHandler : indexHandlerProvider.getAllHandlers()) {
        importIndexHandler.resetImportIndex();
      }
      lastReset = LocalDateTime.now();
    }
  }

  @Override
  public void start() {
    logger.info("starting import scheduler thread");
    this.scheduleNewImportRound();
    super.start();
    this.setName(SCHEDULER_NAME);
  }

  protected void executeNextJob() {
    if( hasStillJobsToExecute()) {
      ImportScheduleJob toExecute = getNextToExecute();
      executeGivenJob(toExecute);
    } else {
      backoffIfPossibleAndScheduleNewRound();
    }
  }

  private void backoffIfPossibleAndScheduleNewRound() {
    if(skipBackoffToCheckForNewDataInEngine) {
      for (ImportIndexHandler importIndexHandler : indexHandlerProvider.getAllHandlers()) {
        importIndexHandler.restartImportCycle();
      }
      skipBackoffToCheckForNewDataInEngine = false;
    } else {
      backoffService.backoffAndSleep();
      skipBackoffToCheckForNewDataInEngine = true;
    }
    this.scheduleNewImportRound();
  }

  public boolean hasStillJobsToExecute() {
    return !importScheduleJobs.isEmpty();
  }

  public ImportScheduleJob getNextToExecute() {
    ImportScheduleJob toExecute;
    if (hasStillJobsToExecute()) {
      toExecute = importScheduleJobs.poll();
    } else {
      toExecute = new IdleImportScheduleJob();
    }
    return toExecute;
  }

  private void executeGivenJob(ImportScheduleJob toExecute) {
    boolean engineHasStillNewData = false;
    try {

      ImportResult importResult = toExecute.execute();
      engineHasStillNewData = handleIndexes(importResult, toExecute);
      if (engineHasStillNewData) {
        postProcess(toExecute, importResult);
      } else if (!engineHasStillNewData && toExecute.isPageBased()) {
        sleepAndReschedule(engineHasStillNewData, toExecute);
      }

    } catch (RejectedExecutionException e) {
      //nothing bad happened, we just have a lot of data to import
      //next step is sleep
      if (logger.isDebugEnabled()) {
        logger.debug("import jobs capacity exceeded");
        sleepAndReschedule(engineHasStillNewData, toExecute);
      }
    } catch (OptimizeException op) {
      // is thrown if there is a connection problem for instance
      sleepAndReschedule(engineHasStillNewData, toExecute);
    } catch (Exception e) {
      if (logger.isDebugEnabled()) {
        logger.debug("error while executing import job", e);
      }
    }
  }

  public boolean handleIndexes(ImportResult importResult, ImportScheduleJob toExecute) {
    boolean engineHasStillNewData = importResult.getEngineHasStillNewData();
    if (toExecute.isPageBased()) {
      ImportIndexHandler importIndexHandler = indexHandlerProvider.getIndexHandler(
          importResult.getElasticSearchType(), importResult.getIndexHandlerType()
      );

      if (!engineHasStillNewData) {
        engineHasStillNewData = importIndexHandler.adjustIndexWhenNoResultsFound(engineHasStillNewData);
      }

      importIndexHandler.moveImportIndex(importResult.getSearchedSize());
      importIndexHandler.persistImportIndexToElasticsearch();
    }

    return engineHasStillNewData;
  }

  /**
   * The job might create additional information for creation of other jobs based on it.
   * An example is import of HPI based on information obtained from HAI.
   */
  public void postProcess(ImportScheduleJob toExecute, ImportResult importResult) {
    if (importResult.getIdsToFetch() != null) {
      importScheduleJobs.addAll(scheduleJobFactory.createIndexedScheduleJobs(importResult.getIdsToFetch()));
    }

    if (toExecute.isPageBased()) {
      PageBasedImportScheduleJob typeCastedJob = (PageBasedImportScheduleJob) toExecute;
      rescheduleBasedOnPages(
          importResult.getEngineHasStillNewData(),
          typeCastedJob
      );
    }
  }

  /**
   * Handle rescheduling of currently executed job based on pages that were fetched
   * from the engine and overall progress.
   */
  private void rescheduleBasedOnPages(boolean engineHasStillNewData, PageBasedImportScheduleJob toExecute) {
    if (engineHasStillNewData) {
      String elasticsearchType = toExecute.getImportService().getElasticsearchType();
      logger.debug(
          "Processed a page during data import run of [{}], scheduling one more run",
          elasticsearchType
      );
      backoffService.resetBackoff(toExecute);
      importScheduleJobs.add(scheduleJobFactory.createPagedJob(elasticsearchType));
    }
  }

  private void sleepAndReschedule(boolean engineHasStillNewData, ImportScheduleJob toExecute) {
    if (toExecute != null) {
      backoffService.handleSleep(engineHasStillNewData, toExecute);

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
    if (!hasStillJobsToExecute()) {
      this.scheduleNewImportRound();
    }
  }

  /**
   * Check if there is at least one job which does not have backoff time set.
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

  public LocalDateTime getLastReset() {
    return lastReset;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void disable() {
    this.enabled = false;
  }

  public boolean isSkipBackoffToCheckForNewDataInEngine() {
    return skipBackoffToCheckForNewDataInEngine;
  }

  public void setSkipBackoffToCheckForNewDataInEngine(boolean skipBackoffToCheckForNewDataInEngine) {
    this.skipBackoffToCheckForNewDataInEngine = skipBackoffToCheckForNewDataInEngine;
  }

  public void clearQueue() {
    this.importScheduleJobs.clear();
  }
}
