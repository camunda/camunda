package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.importing.job.ImportJob;
import org.camunda.optimize.service.util.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ImportJobExecutor {

  @Autowired
  private ConfigurationService configurationService;

  private Logger logger = LoggerFactory.getLogger(ImportJobExecutor.class);

  private ThreadPoolExecutor importExecutor;

  private int queueSize = 100;
  private int corePoolSize = 2;
  private int maxPoolSize = 2;

  private List<Future> submittedJobs;

  @PostConstruct
  public void init() {
    queueSize = configurationService.getMaxJobQueueSize();
    corePoolSize = configurationService.getImportExecutorThreadCount();
    maxPoolSize = configurationService.getImportExecutorThreadCount();
    startExecutingImportJobs();
  }

  public boolean isActive() {
    return importExecutor.getActiveCount() > 0;
  }

  public void executeImportJob(ImportJob importJob) throws InterruptedException {
    logger.debug(
        "Currently active [{}] jobs and [{}] in queue",
        importExecutor.getActiveCount(),
        importExecutor.getQueue().size()
    );
    Future submitted = importExecutor.submit(importJob);

    //used for testing only
    if (submittedJobs != null) {
      submittedJobs.add(submitted);
    }
  }

  public void startExecutingImportJobs() {
    submittedJobs = new ArrayList<>();

    if (importExecutor == null || importExecutor.isShutdown()) {
      BlockingQueue<Runnable> importJobsQueue = new ArrayBlockingQueue<>(queueSize);
      importExecutor = new ThreadPoolExecutor(corePoolSize, maxPoolSize, Long.MAX_VALUE, TimeUnit.DAYS, importJobsQueue);
    }
  }

  public void stopExecutingImportJobs() {
    // Ask the thread pool to finish and exit
    importExecutor.shutdown();

    // Waits for 1 minute to finish all currently executing jobs
    try {
      for (Future f : submittedJobs) {
        while (!f.isDone()) {
          Thread.sleep(1000L);
        }
      }

      boolean timeElapsedBeforeTermination = !importExecutor.awaitTermination(60L, TimeUnit.SECONDS);
      if (timeElapsedBeforeTermination) {
        logger.warn("Timeout during shutdown of import job executor! " +
            "The current running jobs could not end within 60 seconds after shutdown operation.");
      }
    } catch (InterruptedException e) {
      logger.error("Interrupted while shutting down the import job executor!", e);
    }
  }

}
