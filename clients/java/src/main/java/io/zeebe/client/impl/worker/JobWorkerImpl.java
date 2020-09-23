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
package io.zeebe.client.impl.worker;

import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.client.api.worker.BackoffSupplier;
import io.zeebe.client.api.worker.JobWorker;
import io.zeebe.client.impl.Loggers;
import java.io.Closeable;
import java.time.Duration;
import java.util.Optional;
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
 * retryDelaySupplier} to ask for a new {@code pollInterval}. By default this retry delay supplier
 * is the {@link ExponentialBackoff}.
 */
public final class JobWorkerImpl implements JobWorker, Closeable {

  private static final Logger LOG = Loggers.JOB_WORKER_LOGGER;

  // job queue state
  private final int maxJobsActive;
  private final int activationThreshold;
  private final AtomicInteger remainingJobs;

  // job execution facilities
  private final ScheduledExecutorService executor;
  private final JobRunnableFactory jobHandlerFactory;
  private final long initialPollInterval;
  private final BackoffSupplier backoffSupplier;

  // state synchronization
  private final AtomicBoolean acquiringJobs = new AtomicBoolean(true);
  private final AtomicReference<JobPoller> claimableJobPoller;
  private final AtomicBoolean isPollScheduled = new AtomicBoolean(false);

  private long pollInterval;

  public JobWorkerImpl(
      final int maxJobsActive,
      final ScheduledExecutorService executor,
      final Duration pollInterval,
      final JobRunnableFactory jobHandlerFactory,
      final JobPoller jobPoller,
      final BackoffSupplier backoffSupplier) {
    this.maxJobsActive = maxJobsActive;
    activationThreshold = Math.round(maxJobsActive * 0.3f);
    remainingJobs = new AtomicInteger(0);

    this.executor = executor;
    this.jobHandlerFactory = jobHandlerFactory;
    initialPollInterval = pollInterval.toMillis();
    this.backoffSupplier = backoffSupplier;

    claimableJobPoller = new AtomicReference<>(jobPoller);
    this.pollInterval = initialPollInterval;

    schedulePoll();
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
  }

  /**
   * Schedules a poll for jobs with a delay of {@code pollInterval}. Does not schedule twice if a
   * poll is already scheduled.
   */
  private void schedulePoll() {
    if (isPollScheduled.compareAndSet(false, true)) {
      executor.schedule(this::onScheduledPoll, pollInterval, TimeUnit.MILLISECONDS);
    }
  }

  /** Frees up the scheduler and polls for new jobs. */
  private void onScheduledPoll() {
    isPollScheduled.set(false);
    final int actualRemainingJobs = remainingJobs.get();
    if (shouldPoll(actualRemainingJobs)) {
      poll();
    }
  }

  private boolean shouldPoll(final int remainingJobs) {
    return acquiringJobs.get() && remainingJobs <= activationThreshold;
  }

  private void poll() {
    tryClaimJobPoller()
        .ifPresent(
            jobPoller -> {
              // check the condition again within the critical section
              // to avoid race conditions that would let us exceed the buffer size
              final int actualRemainingJobs = remainingJobs.get();
              if (shouldPoll(actualRemainingJobs)) {
                final int maxJobsToActivate = maxJobsActive - actualRemainingJobs;
                try {
                  jobPoller.poll(
                      maxJobsToActivate,
                      this::handleJob,
                      activatedJobs -> onPollSuccess(jobPoller, activatedJobs),
                      error -> onPollError(jobPoller, error),
                      this::isOpen);
                } catch (final Exception e) {
                  LOG.warn("Failed to activate jobs", e);
                  releaseJobPoller(jobPoller);
                }
              } else {
                releaseJobPoller(jobPoller);
              }
            });
  }

  private Optional<JobPoller> tryClaimJobPoller() {
    return Optional.ofNullable(claimableJobPoller.getAndSet(null));
  }

  private void releaseJobPoller(final JobPoller jobPoller) {
    claimableJobPoller.set(jobPoller);
  }

  private void onPollSuccess(final JobPoller jobPoller, final int activatedJobs) {
    remainingJobs.addAndGet(activatedJobs);
    pollInterval = initialPollInterval;
    releaseJobPoller(jobPoller);
    if (activatedJobs == 0) {
      schedulePoll();
    }
    // if jobs were activated, then successive polling happens due to handleJobFinished
  }

  private void onPollError(final JobPoller jobPoller, final Throwable error) {
    pollInterval = backoffSupplier.supplyRetryDelay(pollInterval);
    LOG.debug(
        "Failed to activate jobs due to {}, delay retry for {} ms",
        error.getMessage(),
        pollInterval);
    releaseJobPoller(jobPoller);
    schedulePoll();
  }

  private void handleJob(final ActivatedJob job) {
    executor.execute(jobHandlerFactory.create(job, this::handleJobFinished));
  }

  private void handleJobFinished() {
    final int actualRemainingJobs = remainingJobs.decrementAndGet();
    if (!isPollScheduled.get() && shouldPoll(actualRemainingJobs)) {
      poll();
    }
  }
}
