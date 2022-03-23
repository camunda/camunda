/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public abstract class ImportJobExecutor {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  private final String name;

  protected ImportJobExecutor(final String name) {
    this.name = name;
  }

  public void shutdown() {
    stopExecutingImportJobs();
  }

  private ThreadPoolExecutor importExecutor;

  public boolean isActive() {
    return importExecutor.getActiveCount() > 0 || !importExecutor.getQueue().isEmpty();
  }

  public void executeImportJob(final Runnable elasticsearchImportJob) {
    logger.debug(
      "{}: Currently active [{}] jobs and [{}] in queue of job type [{}]",
      getClass().getSimpleName(),
      importExecutor.getActiveCount(),
      importExecutor.getQueue().size(),
      elasticsearchImportJob.getClass().getSimpleName()
    );
    importExecutor.execute(elasticsearchImportJob);
  }

  public void startExecutingImportJobs() {
    if (importExecutor == null || importExecutor.isShutdown()) {
      final BlockingQueue<Runnable> importJobsQueue = new ArrayBlockingQueue<>(getMaxQueueSize());
      importExecutor =
        new ThreadPoolExecutor(
          getExecutorThreadCount(),
          getExecutorThreadCount(),
          Long.MAX_VALUE,
          TimeUnit.DAYS,
          importJobsQueue,
          new ThreadFactoryBuilder().setNameFormat("ImportJobExecutor-pool-" + this.name + "-%d").build(),
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
    importExecutor.shutdownNow();

    // Waits for 1 minute to finish all currently executing jobs
    try {
      boolean timeElapsedBeforeTermination = !importExecutor.awaitTermination(60L, TimeUnit.SECONDS);
      if (timeElapsedBeforeTermination) {
        logger.warn(
          "{}: Timeout during shutdown of import job executor! " +
            "The current running jobs could not end within 60 seconds after shutdown operation.",
          getClass().getSimpleName()
        );
      }
    } catch (InterruptedException e) {
      logger.error(
        "{}: Interrupted while shutting down the import job executor!",
        getClass().getSimpleName(),
        e
      );
    }
  }

  private class BlockCallerUntilExecutorHasCapacity implements RejectedExecutionHandler {
    public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
      // this will block if the queue is full
      if (!executor.isShutdown()) {
        try {
          logger.debug(
            "{}: Max queue capacity is reached and, thus, can't schedule any new jobs." +
              "Caller needs to wait until there is new free spot. Job class [{}].",
            super.getClass().getSimpleName(),
            runnable.getClass().getSimpleName()
          );
          executor.getQueue().put(runnable);
          logger.debug(
            "{}: Added job to queue. Caller can continue working on his tasks.",
            super.getClass().getSimpleName()
          );
        } catch (InterruptedException e) {
          logger.error(
            "{}: Interrupted while waiting to submit a new job to the job executor!", getClass().getSimpleName(), e
          );
          Thread.currentThread().interrupt();
        }
      }
    }
  }

}
