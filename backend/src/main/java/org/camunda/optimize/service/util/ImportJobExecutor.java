package org.camunda.optimize.service.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public abstract class ImportJobExecutor {

  private Logger logger = LoggerFactory.getLogger(getClass());

  @PostConstruct
  public void init() {
    startExecutingImportJobs();
  }

  private ThreadPoolExecutor importExecutor;

  public boolean isActive() {
    return importExecutor.getActiveCount() > 0;
  }

  public void executeImportJob(Runnable elasticsearchImportJob) throws InterruptedException {
    logger.debug(
        "{}: Currently active [{}] jobs and [{}] in queue",
        getClass().getSimpleName(),
        importExecutor.getActiveCount(),
        importExecutor.getQueue().size()
    );
    importExecutor.execute(elasticsearchImportJob);
  }

  public void startExecutingImportJobs() {
    if (importExecutor == null || importExecutor.isShutdown()) {
      BlockingQueue<Runnable> importJobsQueue = new ArrayBlockingQueue<>(getMaxQueueSize());
      String poolName = this.getClass().getSimpleName() + "-pool";
      importExecutor =
        new ThreadPoolExecutor(
          getExecutorThreadCount(),
          getExecutorThreadCount(),
          Long.MAX_VALUE,
          TimeUnit.DAYS,
          importJobsQueue,
          new NamedThreadFactory(poolName),
          new BlockCallerUntilExecutorHasCapacity()
        );
    }
  }

  /**
   * Number of threads that should be used in the thread pool executor.
   */
  protected abstract int getExecutorThreadCount();

  /**
   * Number of jobs that should be able to accumulate until new submission is blocked.
   */
  protected abstract int getMaxQueueSize();

  public void stopExecutingImportJobs() {
    // Ask the thread pool to finish and exit
    importExecutor.shutdown();

    // Waits for 1 minute to finish all currently executing jobs
    try {
      boolean timeElapsedBeforeTermination = !importExecutor.awaitTermination(60L, TimeUnit.SECONDS);
      if (timeElapsedBeforeTermination) {
        logger.warn("{}: Timeout during shutdown of import job executor! " +
          "The current running jobs could not end within 60 seconds after shutdown operation.",
          getClass().getSimpleName());
      }
    } catch (InterruptedException e) {
      logger.error("{}: Interrupted while shutting down the import job executor!",
        getClass().getSimpleName(),
        e
      );
    }
  }

  private class BlockCallerUntilExecutorHasCapacity implements RejectedExecutionHandler {
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
      // this will block if the queue is full
      if (!executor.isShutdown()) {
        try {
          logger.debug("{}: Max queue capacity is reach and, thus, can't schedule any new jobs." +
            "Caller needs to wait until there is new free spot.", super.getClass().getSimpleName());
          executor.getQueue().put(r);
          logger.debug("{}: Added job to queue. Caller can continue working on his tasks.",
            super.getClass().getSimpleName());
        } catch (InterruptedException e) {
          logger.error("{}: Interrupted while waiting to submit a new job to the job executor!",
            getClass().getSimpleName(),
            e
          );
        }
      }
    }
  }

}
