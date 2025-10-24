/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.client.impl.worker;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.BackoffSupplier;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.camunda.zeebe.client.api.worker.JobWorkerMetrics;
import io.camunda.zeebe.client.impl.Loggers;
import java.io.Closeable;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;

/**
 * The job worker wants to have enough jobs to work on. Most of this class' implementation deals
 * with the scheduling of polling for new jobs.
 *
 * <p>In order to get an initial set of jobs to work on, the job worker will schedule a first poll
 * on construction. If a poll does not provide any new jobs, another poll is scheduled with a delay
 * using the {@code pollInterval}.
 *
 * <p>If a poll successfully provides jobs, the worker submits each job to the job handler. Every
 * time a job is completed, the worker checks if it still has enough jobs to work on. If not, it
 * will poll for new jobs. To determine what is considered enough jobs it compares its number of
 * {@code remainingJobs} with the {@code activationThreshold}.
 *
 * <p>If a poll fails with an error response, a retry is scheduled with a delay using the {@code
 * retryDelaySupplier} to ask for a new {@code pollInterval}. By default, this retry delay supplier
 * is the {@link ExponentialBackoff}. This default is also used as a fallback for the user provided
 * backoff. On the next success, the {@code pollInterval} is reset to its original value.
 */
public final class JobWorkerImpl implements JobWorker, Closeable {

  public static final String ERROR_MSG =
      "Expected to handle received job with key {}, but the worker reached maximum capacity (maxJobsActive). "
          + "The job activation timed out (controllable by timeout parameter). It will get reactivated shortly. "
          + "If this issue persist, make sure to either scale your workers, threads, increase maxJobsActive or reduce the load you want to work on. ";
  private static final BackoffSupplier DEFAULT_BACKOFF_SUPPLIER =
      JobWorkerBuilderImpl.DEFAULT_BACKOFF_SUPPLIER;
  private static final Logger LOG = Loggers.JOB_WORKER_LOGGER;
  private static final String SUPPLY_RETRY_DELAY_FAILURE_MESSAGE =
      "Expected to supply retry delay, but an exception was thrown. Falling back to default backoff supplier";
  // job queue state
  private final int maxJobsActive;
  private final int activationThreshold;
  private final AtomicInteger remainingJobs;

  // job execution facilities
  private final Executor executor;
  private final JobRunnableFactory jobHandlerFactory;
  private final long initialPollInterval;
  private final JobStreamer jobStreamer;
  private final BackoffSupplier backoffSupplier;
  private final JobWorkerMetrics metrics;

  // state synchronization
  private final AtomicBoolean acquiringJobs = new AtomicBoolean(true);
  private final AtomicReference<JobPoller> claimableJobPoller;
  private final AtomicBoolean isPollScheduled = new AtomicBoolean(false);

  private volatile long pollInterval;
  private final ScheduledExecutorService scheduledExecutorService;

  public JobWorkerImpl(
      final int maxJobsActive,
      final ScheduledExecutorService executor,
      final Duration pollInterval,
      final JobRunnableFactory jobHandlerFactory,
      final JobPoller jobPoller,
      final JobStreamer jobStreamer,
      final BackoffSupplier backoffSupplier,
      final JobWorkerMetrics metrics,
      final Executor jobExecutor) {
    this.maxJobsActive = maxJobsActive;
    activationThreshold = Math.round(maxJobsActive * 0.3f);
    remainingJobs = new AtomicInteger(0);

    this.executor = jobExecutor;
    scheduledExecutorService = executor;
    this.jobHandlerFactory = jobHandlerFactory;
    this.jobStreamer = jobStreamer;
    initialPollInterval = pollInterval.toMillis();
    this.backoffSupplier = backoffSupplier;
    this.metrics = metrics;

    claimableJobPoller = new AtomicReference<>(jobPoller);
    this.pollInterval = initialPollInterval;

    openStream();
    schedulePoll();
  }

  private void openStream() {
    jobStreamer.openStreamer(this::handleStreamedJob);
  }

  @Override
  public boolean isOpen() {
    return acquiringJobs.get();
  }

  @Override
  public boolean isClosed() {
    return !isOpen() && claimableJobPoller.get() != null && remainingJobs.get() <= 0;
  }

  @Override
  public void close() {
    acquiringJobs.set(false);
    jobStreamer.close();
  }

  /**
   * Schedules a poll for jobs with a delay of {@code pollInterval}. Does not schedule twice if a
   * poll is already scheduled.
   */
  private void schedulePoll() {
    if (isPollScheduled.compareAndSet(false, true)) {
      scheduledExecutorService.schedule(this::onScheduledPoll, pollInterval, TimeUnit.MILLISECONDS);
    }
  }

  /** Frees up the scheduler and polls for new jobs. */
  private void onScheduledPoll() {
    isPollScheduled.set(false);
    final int actualRemainingJobs = remainingJobs.get();
    if (shouldPoll(actualRemainingJobs)) {
      tryPoll();
    }
  }

  private boolean shouldPoll(final int remainingJobs) {
    return acquiringJobs.get() && remainingJobs <= activationThreshold;
  }

  private void tryPoll() {
    tryClaimJobPoller()
        .ifPresent(
            poller -> {
              try {
                poll(poller);
              } catch (final Exception error) {
                LOG.warn("Unexpected failure to activate jobs", error);
                onPollError(poller, error);
              }
            });
  }

  /**
   * @return an optional job poller if not already in use, otherwise an empty optional
   */
  private Optional<JobPoller> tryClaimJobPoller() {
    return Optional.ofNullable(claimableJobPoller.getAndSet(null));
  }

  /** Release the job poller for the next try to poll */
  private void releaseJobPoller(final JobPoller jobPoller) {
    claimableJobPoller.set(jobPoller);
  }

  private void poll(final JobPoller jobPoller) {
    // check the condition again within the critical section
    // to avoid race conditions that would let us exceed the buffer size
    final int actualRemainingJobs = remainingJobs.get();
    if (!shouldPoll(actualRemainingJobs)) {
      LOG.trace("Expected to activate for jobs, but still enough remain. Reschedule poll.");
      releaseJobPoller(jobPoller);
      schedulePoll();
      return;
    }
    final int maxJobsToActivate = maxJobsActive - actualRemainingJobs;
    jobPoller.poll(
        maxJobsToActivate,
        this::handleJob,
        activatedJobs -> onPollSuccess(jobPoller, activatedJobs),
        error -> onPollError(jobPoller, error),
        this::isOpen);
  }

  private void onPollSuccess(final JobPoller jobPoller, final int activatedJobs) {
    // first release, then lookup remaining jobs, to allow handleJobFinished() to poll
    releaseJobPoller(jobPoller);
    final int actualRemainingJobs = remainingJobs.addAndGet(activatedJobs);

    if (jobStreamer.isOpen() && activatedJobs == 0) {
      // to keep polling requests to a minimum, if streaming is enabled, and the response is empty,
      // we back off on poll success responses.
      backoff(jobPoller);
      LOG.trace("No jobs to activate via polling, will backoff and poll in {}", pollInterval);
    } else {
      pollInterval = initialPollInterval;
      if (actualRemainingJobs <= 0) {
        schedulePoll();
      }
      // if jobs were activated, then successive polling happens due to handleJobFinished
    }
  }

  private void onPollError(final JobPoller jobPoller, final Throwable error) {
    backoff(jobPoller);
    LOG.debug(
        "Failed to activate jobs due to {}, delay retry for {} ms",
        error.getMessage(),
        pollInterval);
  }

  private void backoff(final JobPoller jobPoller) {
    getPollInterval();
    releaseJobPoller(jobPoller);
    schedulePoll();
  }

  private void getPollInterval() {
    final long prevInterval = pollInterval;
    try {
      pollInterval = backoffSupplier.supplyRetryDelay(prevInterval);
    } catch (final Exception e) {
      LOG.warn(SUPPLY_RETRY_DELAY_FAILURE_MESSAGE, e);
      pollInterval = DEFAULT_BACKOFF_SUPPLIER.supplyRetryDelay(prevInterval);
    }
  }

  private void handleJob(final ActivatedJob job) {
    handleActivatedJob(job, this::handleJobFinished);
  }

  private void handleStreamedJob(final ActivatedJob job) {
    handleActivatedJob(job, this::handleStreamJobFinished);
  }

  private void handleActivatedJob(final ActivatedJob job, final Runnable finalizer) {
    metrics.jobActivated(1);
    try {
      executor.execute(jobHandlerFactory.create(job, finalizer));
    } catch (final RejectedExecutionException e) {
      if (isClosed()) {
        return;
      }

      if (scheduledExecutorService.isShutdown() || scheduledExecutorService.isTerminated()) {
        LOG.warn("Underlying executor was closed before the worker. Closing the worker now.", e);
        close();
        return;
      }

      LOG.warn(ERROR_MSG, job.getKey(), e);
    }
  }

  private void handleJobFinished() {
    final int actualRemainingJobs = remainingJobs.decrementAndGet();
    if (!isPollScheduled.get() && shouldPoll(actualRemainingJobs)) {
      tryPoll();
    }
    metrics.jobHandled(1);
  }

  private void handleStreamJobFinished() {
    metrics.jobHandled(1);
  }
}
