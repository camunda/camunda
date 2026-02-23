/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.appint.dispatch;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the {@link Dispatcher} interface that manages the execution of jobs with a
 * specified maximum number of concurrent jobs. It uses a single-threaded executor to execute jobs
 * sequentially and a semaphore to limit the number of jobs in flight. If a job fails, it is retried
 * until executed successfully.
 */
public class DispatcherImpl implements Dispatcher {

  private final Logger log = LoggerFactory.getLogger(getClass().getPackageName());

  // Single-threaded executor to execute jobs sequentially so events are processed in order
  private final ExecutorService executorService = Executors.newSingleThreadExecutor();
  private final Semaphore semaphore;
  private final int maxJobsInFlight;

  public DispatcherImpl(final int maxJobsInFlight) {
    this.maxJobsInFlight = maxJobsInFlight;
    semaphore = new Semaphore(maxJobsInFlight);
  }

  @Override
  public void dispatch(final Runnable job) {
    try {
      semaphore.acquire();
      log.trace("Dispatching job {}", job);
      executorService.execute(() -> executeJob(job));
    } catch (final InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean isActive() {
    return semaphore.availablePermits() != maxJobsInFlight;
  }

  /**
   * Executes the given job and releases a permit from the semaphore upon completion. If an
   * exception occurs during execution, the job is retried until it succeeds.
   *
   * @param job, the job to execute
   */
  private void executeJob(final Runnable job) {
    boolean success = false;
    while (!success) {
      try {
        job.run();
        success = true;
        semaphore.release();
      } catch (final Exception e) {
        log.debug("Failed to run the job. Restarting.", e);
      }
    }
  }

  @Override
  public void close() {
    try {
      executorService.shutdown();
      executorService.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);
    } catch (final InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
