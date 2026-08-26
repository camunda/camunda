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
package io.camunda.client.impl.worker;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.BackoffSupplier;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.client.api.worker.JobWorkerMetrics;
import io.camunda.client.impl.Loggers;
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
import java.util.function.Consumer;
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
 * {@code remainingJobs} with the {@code activationThreshold}. If the executor refuses a job, the
 * worker frees up that job's capacity immediately, so that a refused job never takes up capacity
 * for good. Each job's capacity is freed up exactly once, whether the job ran or was refused.
 *
 * <p>If a poll fails with an error response, a retry is scheduled with a delay using the {@code
 * retryDelaySupplier} to ask for a new {@code pollInterval}. By default, this retry delay supplier
 * is the {@link ExponentialBackoff}. This default is also used as a fallback for the user provided
 * backoff. On the next success, the {@code pollInterval} is reset to its original value.
 */
public final class JobWorkerImpl implements JobWorker, Closeable {

  public static final String ERROR_MSG =
      "Expected to handle received job with key {}, but the worker reached maximum capacity (maxJobsActive). "
          + "The job is made available again right away, so that it can be picked up by another worker. "
          + "If this issue persists, make sure to either scale your workers, threads, increase maxJobsActive or reduce the load you want to work on. ";
  private static final BackoffSupplier DEFAULT_BACKOFF_SUPPLIER =
      JobWorkerBuilderImpl.DEFAULT_BACKOFF_SUPPLIER;
  private static final Logger LOG = Loggers.JOB_WORKER_LOGGER;
  private static final String RETURN_JOB_ERROR_MSG =
      "The worker had no capacity to handle this job, so it was returned to the broker.";
  private static final String SUPPLY_RETRY_DELAY_FAILURE_MESSAGE =
      "Expected to supply retry delay, but an exception was thrown. Falling back to default backoff supplier";
  // job queue state
  private final int maxJobsActive;
  private final int activationThreshold;
  private final AtomicInteger remainingJobs;

  // job execution facilities
  private final Executor executor;
  private final JobClient jobClient;
  private final JobRunnableFactory jobHandlerFactory;
  private final long initialPollInterval;
  private final JobStreamer jobStreamer;
  private final BackoffSupplier backoffSupplier;
  private final BackoffSupplier streamNoJobsBackoffSupplier;
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
      final JobClient jobClient,
      final JobRunnableFactory jobHandlerFactory,
      final JobPoller jobPoller,
      final JobStreamer jobStreamer,
      final BackoffSupplier backoffSupplier,
      final BackoffSupplier streamNoJobsBackoffSupplier,
      final JobWorkerMetrics metrics,
      final Executor jobExecutor) {
    this.maxJobsActive = maxJobsActive;
    activationThreshold = Math.round(maxJobsActive * 0.3f);
    remainingJobs = new AtomicInteger(0);

    this.executor = jobExecutor;
    this.jobClient = jobClient;
    scheduledExecutorService = executor;
    this.jobHandlerFactory = jobHandlerFactory;
    this.jobStreamer = jobStreamer;
    initialPollInterval = pollInterval.toMillis();
    this.backoffSupplier = backoffSupplier;
    this.streamNoJobsBackoffSupplier = streamNoJobsBackoffSupplier;
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
    final int actualRemainingJobs = remainingJobs.get();

    if (jobStreamer.isOpen() && activatedJobs == 0) {
      // to keep polling requests to a minimum, if streaming is enabled, and the response is empty,
      // we back off on poll success responses.
      backoff(jobPoller, streamNoJobsBackoffSupplier);
      LOG.trace("No jobs to activate via polling, will backoff and poll in {}", pollInterval);
    } else {
      pollInterval = initialPollInterval;
      // Normally the jobs just activated go on to free their own capacity as they finish, and each
      // one that does asks for another poll. A job the executor refused gives its capacity back
      // while this poll is still running, though, so the poll it asks for finds the poller still
      // taken and is dropped. Asking again here, now that the poller is free, is what keeps a
      // worker going that would otherwise sit still until the jobs it did take are done.
      if (shouldPoll(actualRemainingJobs)) {
        schedulePoll();
      }
    }
  }

  private void onPollError(final JobPoller jobPoller, final Throwable error) {
    backoff(jobPoller, backoffSupplier);
    LOG.debug(
        "Failed to activate jobs due to {}, delay retry for {} ms",
        error.getMessage(),
        pollInterval);
  }

  private void backoff(final JobPoller jobPoller, final BackoffSupplier backoffSupplier) {
    getPollInterval(backoffSupplier);
    releaseJobPoller(jobPoller);
    schedulePoll();
  }

  private void getPollInterval(final BackoffSupplier backoffSupplier) {
    final long prevInterval = pollInterval;
    try {
      pollInterval = backoffSupplier.supplyRetryDelay(prevInterval);
    } catch (final Exception e) {
      LOG.warn(SUPPLY_RETRY_DELAY_FAILURE_MESSAGE, e);
      pollInterval = DEFAULT_BACKOFF_SUPPLIER.supplyRetryDelay(prevInterval);
    }
  }

  private void handleJob(final ActivatedJob job) {
    // Take a capacity slot for this job before handing it over, and give it back right away if
    // the executor does not take the job. Taking capacity for the whole response at once would
    // also count the jobs that were rejected, and that capacity would never be given back.
    remainingJobs.incrementAndGet();
    // The executor may run the job on the calling thread and still report it as refused, in which
    // case the job both ran and was refused and the two paths below are taken for the same job.
    // The flag makes sure the slot taken above is given back only once, as giving it back twice
    // would let the worker ask the broker for more jobs than it is allowed to run at a time.
    final AtomicBoolean capacityHeld = new AtomicBoolean(true);
    if (!handleActivatedJob(job, () -> handleJobFinished(capacityHeld), this::returnJobToBroker)) {
      releaseCapacity(capacityHeld);
    }
  }

  private void handleStreamedJob(final ActivatedJob job) {
    handleActivatedJob(job, this::handleStreamJobFinished, this::leaveStreamedJobToBroker);
  }

  /**
   * Hands the job over to the executor that runs the job handler.
   *
   * @param onRefused what to do with a job the executor would not take, which differs between a job
   *     the worker asked for and one the broker pushed to it
   * @return true if the executor took the job, in which case the given finalizer is guaranteed to
   *     run once the handler is done
   */
  private boolean handleActivatedJob(
      final ActivatedJob job, final Runnable finalizer, final Consumer<ActivatedJob> onRefused) {
    metrics.jobActivated(1);
    try {
      executor.execute(jobHandlerFactory.create(job, finalizer));
      return true;
    } catch (final RejectedExecutionException e) {
      if (isClosed()) {
        return false;
      }

      if (scheduledExecutorService.isShutdown() || scheduledExecutorService.isTerminated()) {
        LOG.warn("Underlying executor was closed before the worker. Closing the worker now.", e);
        close();
        return false;
      }

      LOG.warn(ERROR_MSG, job.getKey(), e);
      onRefused.accept(job);
      return false;
    }
  }

  /**
   * Fails the job without using up a retry, so that the broker offers it again right away instead
   * of holding it back until its timeout expires.
   *
   * <p>Never throws: a job that could not be handed back simply stays out of reach until its
   * timeout expires, which is where it would have been anyway. Letting a failure out of here would
   * stop the worker from handling the rest of the jobs it just activated.
   */
  private void returnJobToBroker(final ActivatedJob job) {
    try {
      jobClient
          .newFailCommand(job)
          .retries(job.getRetries())
          .errorMessage(RETURN_JOB_ERROR_MSG)
          .send()
          .exceptionally(
              error -> {
                logFailedReturn(job, error);
                return null;
              });
    } catch (final RuntimeException e) {
      // sending can also fail on the spot, for example once the client starts shutting down
      logFailedReturn(job, e);
    }
  }

  /**
   * Leaves a refused streamed job to the broker. The broker yields a job whose push to a stream
   * fails, which makes it activatable again just as quickly as a fail command from here would.
   * Sending one anyway would only race that yield, and the broker's version is the sounder of the
   * two: it cannot be made stale by the job being activated again in the meantime.
   */
  private void leaveStreamedJobToBroker(final ActivatedJob job) {
    LOG.debug(
        "Job with key {} was refused. Leaving it to the broker, which offers it again once the "
            + "push to this worker fails.",
        job.getKey());
  }

  private void logFailedReturn(final ActivatedJob job, final Throwable error) {
    LOG.debug(
        "Failed to return job with key {} to the broker. It stays out of reach until its "
            + "timeout expires.",
        job.getKey(),
        error);
  }

  private void handleJobFinished(final AtomicBoolean capacityHeld) {
    releaseCapacity(capacityHeld);
    metrics.jobHandled(1);
  }

  /**
   * Gives back the capacity slot taken for a job, and polls again if there is room for more. Does
   * nothing if the slot was already given back.
   */
  private void releaseCapacity(final AtomicBoolean capacityHeld) {
    if (!capacityHeld.compareAndSet(true, false)) {
      return;
    }
    final int actualRemainingJobs = remainingJobs.decrementAndGet();
    if (!isPollScheduled.get() && shouldPoll(actualRemainingJobs)) {
      tryPoll();
    }
  }

  private void handleStreamJobFinished() {
    metrics.jobHandled(1);
  }
}
