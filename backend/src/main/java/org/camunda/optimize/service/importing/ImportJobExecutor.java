package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.importing.job.ImportJob;
import org.camunda.optimize.service.util.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ImportJobExecutor {

  @Autowired
  private ConfigurationService configurationService;

  private Logger logger = LoggerFactory.getLogger(ImportJobExecutor.class);

  private ExecutorService importExecutor;

  private int queueSize = 100;
  private int corePoolSize = 2;
  private int maxPoolSize = 2;

  @PostConstruct
  public void init() {
    queueSize = configurationService.getMaxJobQueueSize();
    corePoolSize = configurationService.getImportExecutorThreadCount();
    maxPoolSize = configurationService.getImportExecutorThreadCount();
    startExecutingImportJobs();
  }

  public void executeImportJob(ImportJob importJob) throws InterruptedException {
    importExecutor.execute(importJob);
  }

  public void startExecutingImportJobs() {
    if (importExecutor==null || importExecutor.isShutdown()) {
      BlockingQueue<Runnable> importJobsQueue = new ArrayBlockingQueue<>(queueSize);
      importExecutor = new ThreadPoolExecutor(corePoolSize, maxPoolSize, Long.MAX_VALUE, TimeUnit.DAYS, importJobsQueue);
    }
  }

  public void stopExecutingImportJobs() {
    // Ask the thread pool to finish and exit
    importExecutor.shutdown();

    // Waits for 1 minute to finish all currently executing jobs
    try {
      boolean timeElapsedBeforeTermination = !importExecutor.awaitTermination(60L, TimeUnit.SECONDS);
      if(timeElapsedBeforeTermination) {
        logger.warn("Timeout during shutdown of import job executor! " +
          "The current running jobs could not end within 60 seconds after shutdown operation.");
      }
    } catch (InterruptedException e) {
      logger.error("Interrupted while shutting down the import job executor!", e);
    }
  }

}
