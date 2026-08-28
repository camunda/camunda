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
import io.camunda.zeebe.client.api.worker.JobClient;
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
import java.util.function.BiConsumer;
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
 * backoff. On the next success, the {@code pollInterval} is reset to its original value. A poll
 * whose jobs the executor refused in full is treated the same way, since a worker that takes no job
 * at all has nothing to wait for and would otherwise poll as fast as the broker can answer.
 */
public final class JobWorkerImpl implements JobWorker, Closeable {

  public static final String ERROR_MSG =
      "Expected to handle received job with key {}, but the worker reached maximum capacity (maxJobsActive). "
          + "The job is made available again right away, so that it can be picked up by another worker. "
          + "If this issue persists, make sure to either scale your workers, threads, increase maxJobsActive or reduce the load you want to work on. ";
  private static final String STREAMED_ERROR_MSG =
      "Expected to handle received job with key {}, but the worker reached maximum capacity (maxJobsActive). "
          + "The job stays with this worker until its timeout expires, and only then is it offered to another worker. "
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
  private final AtomicInteger refusedJobsInPoll = new AtomicInteger(0);

  // job execution facilities
  private final Executor executor;
  private final JobClient jobClient;
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
      final JobClient jobClient,
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
    this.jobClient = jobClient;
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
    refusedJobsInPoll.set(0);
    jobPoller.poll(
        maxJobsToActivate,
        this::handleJob,
        activatedJobs -> onPollSuccess(jobPoller, activatedJobs),
        error -> onPollError(jobPoller, error),
        this::isOpen);
  }

  private void onPollSuccess(final JobPoller jobPoller, final int activatedJobs) {
    // Read the refusals before releasing the poller: once it is free, a job that finishes can start
    // the next poll, and that poll resets the count.
    final int refusedJobs = refusedJobsInPoll.get();
    // first release, then lookup remaining jobs, to allow handleJobFinished() to poll
    releaseJobPoller(jobPoller);
    final int actualRemainingJobs = remainingJobs.get();

    if (jobStreamer.isOpen() && activatedJobs == 0) {
      // to keep polling requests to a minimum, if streaming is enabled, and the response is empty,
      // we back off on poll success responses.
      backOffPolling();
      LOG.trace("No jobs to activate via polling, will backoff and poll in {}", pollInterval);
    } else if (activatedJobs > 0 && refusedJobs == activatedJobs && actualRemainingJobs <= 0) {
      // The executor took none of the jobs in this response and the worker has nothing left
      // running. Polling again right away would activate another batch, hand it straight back, and
      // repeat as fast as the broker can answer. Both halves of the condition are needed: a worker
      // that is keeping up can finish a whole response before this runs, which leaves no remaining
      // jobs either, and it is the refusals that tell the two apart.
      backOffPolling();
      LOG.debug(
          "The job handler executor took none of the {} jobs activated, will backoff and poll in {}",
          activatedJobs,
          pollInterval);
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
    backoff(jobPoller);
    LOG.debug(
        "Failed to activate jobs due to {}, delay retry for {} ms",
        error.getMessage(),
        pollInterval);
  }

  /**
   * Slows the next poll down and schedules it. Does not touch the job poller, so it is safe for a
   * caller that has already released it: releasing a poller twice can publish one that another
   * thread has since claimed and is polling with.
   */
  private void backOffPolling() {
    getPollInterval();
    schedulePoll();
  }

  /** Same, for a caller that is still holding the job poller. */
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
      if (releaseCapacity(capacityHeld)) {
        // Only a job whose slot was still held never ran, and only those say anything about
        // whether the executor is taking work. Counting them lets the poll that activated them
        // tell a response nobody took from one that is being worked through.
        refusedJobsInPoll.incrementAndGet();
      }
    }
  }

  private void handleStreamedJob(final ActivatedJob job) {
    handleActivatedJob(job, this::handleStreamJobFinished, this::leaveStreamedJobToBroker);
  }

  /**
   * Hands the job over to the executor that runs the job handler.
   *
   * @param onRefused what to do with a job the executor would not take, which differs between a job
   *     the worker asked for and one the broker pushed to it. It is also what tells the user about
   *     the refusal, since the two paths leave the job in very different places.
   * @return true if the executor took the job, in which case the given finalizer is guaranteed to
   *     run once the handler is done. A false answer does not mean the handler never ran: an
   *     executor may run the job on the calling thread and report it as refused all the same, so
   *     anything the caller does with a refused job has to cope with the job having run.
   */
  private boolean handleActivatedJob(
      final ActivatedJob job,
      final Runnable finalizer,
      final BiConsumer<ActivatedJob, RejectedExecutionException> onRefused) {
    metrics.jobActivated(1);
    // The executor may run the job on the calling thread and still report it as refused. Once the
    // handler has started, only it knows what became of the job, so the flag below keeps the
    // worker from stepping in afterwards.
    final AtomicBoolean handlerStarted = new AtomicBoolean(false);
    try {
      final Runnable jobRunnable = jobHandlerFactory.create(job, finalizer);
      executor.execute(
          () -> {
            handlerStarted.set(true);
            jobRunnable.run();
          });
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

      if (handlerStarted.get()) {
        LOG.debug(
            "Job with key {} ran on the calling thread even though the executor reported it as "
                + "refused. Leaving the job to the handler that ran it.",
            job.getKey(),
            e);
        return false;
      }

      metrics.jobRefused(1);
      onRefused.accept(job, e);
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
  private void returnJobToBroker(final ActivatedJob job, final RejectedExecutionException cause) {
    LOG.warn(ERROR_MSG, job.getKey(), cause);
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
   * Leaves a refused streamed job to the broker, which holds on to it until its timeout expires.
   *
   * <p>The broker takes a streamed job back by itself when the push to the worker fails, which is
   * how the streaming path recovers without the worker having to say anything. That does not cover
   * this case: the push had already succeeded and the job was only refused afterwards, so nothing
   * tells the broker that this worker will never run it.
   */
  private void leaveStreamedJobToBroker(
      final ActivatedJob job, final RejectedExecutionException cause) {
    LOG.warn(STREAMED_ERROR_MSG, job.getKey(), cause);
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
   *
   * @return true if this call was the one that gave the slot back
   */
  private boolean releaseCapacity(final AtomicBoolean capacityHeld) {
    if (!capacityHeld.compareAndSet(true, false)) {
      return false;
    }
    final int actualRemainingJobs = remainingJobs.decrementAndGet();
    if (!isPollScheduled.get() && shouldPoll(actualRemainingJobs)) {
      tryPoll();
    }
    return true;
  }

  private void handleStreamJobFinished() {
    metrics.jobHandled(1);
  }
}
