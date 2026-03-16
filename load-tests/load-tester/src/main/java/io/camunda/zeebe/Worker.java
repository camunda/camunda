/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.BatchOperationState;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.client.api.worker.JobWorkerMetrics;
import io.camunda.zeebe.config.AppCfg;
import io.camunda.zeebe.config.StarterCfg;
import io.camunda.zeebe.config.WorkerCfg;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Worker extends App {

  private static final Logger LOG = LoggerFactory.getLogger(Worker.class);
  private static final Logger THROTTLED_LOGGER = new ThrottledLogger(LOG, Duration.ofSeconds(5));

  /** Element ID of the source task in the BPMN. */
  private static final String SOURCE_ELEMENT_ID = "taskA";

  /** Element ID of the target task in the BPMN. */
  private static final String TARGET_ELEMENT_ID = "taskB";

  /** How long to wait between batch-operation status polls. */
  private static final Duration POLL_INTERVAL = Duration.ofSeconds(5);

  /** Upper bound for waiting on the batch operation to finish. */
  private static final Duration BATCH_OPERATION_TIMEOUT = Duration.ofMinutes(30);

  private final WorkerCfg workerCfg;
  private final int totalInstances;
  private final String processId;

  Worker(final AppCfg config) {
    super(config);
    workerCfg = config.getWorker();

    final StarterCfg starterCfg = config.getStarter();
    // Derive the expected number of instances from the starter's rate and duration limit.
    // The Starter runs at `rate` instances/s for `durationLimit` seconds.
    totalInstances = starterCfg.getRate() * starterCfg.getDurationLimit();
    processId = starterCfg.getProcessId();
  }

  @Override
  public void run() {
    final CamundaClient client = createCamundaClient();
    printTopology(client);

    LOG.info(
        "BatchModifyWorker started. Waiting for {} job activations on job type '{}' before"
            + " triggering batch modification ({} → {}).",
        totalInstances,
        workerCfg.getJobType(),
        SOURCE_ELEMENT_ID,
        TARGET_ELEMENT_ID);

    // Latch that is released once we have seen `totalInstances` distinct job activations.
    final CountDownLatch readyLatch = new CountDownLatch(1);
    final AtomicLong activatedJobs = new AtomicLong(0);
    final ConcurrentHashMap<Long, Long> seenJobs =
        new ConcurrentHashMap<>(50000); // Pre-size to avoid resizing overhead.

    final JobWorkerMetrics metrics =
        JobWorkerMetrics.micrometer()
            .withMeterRegistry(registry)
            .withTags(
                Tags.of("workerName", workerCfg.getWorkerName(), "jobType", workerCfg.getJobType()))
            .build();

    // Open the worker. The handler counts activations but never completes jobs, so every
    // instance stays parked at taskA until the batch modification moves it.
    final JobWorker worker =
        client
            .newWorker()
            .jobType(workerCfg.getJobType())
            .handler(
                (jobClient, job) -> {
                  seenJobs.put(job.getKey(), 1L);
                  final long count = seenJobs.size();
                  THROTTLED_LOGGER.info(
                      "Activated job {}/{} (process instance key: {})",
                      count,
                      totalInstances,
                      job.getProcessInstanceKey());

                  if (count >= totalInstances) {
                    // Signal the main thread that enough instances have been seen.
                    readyLatch.countDown();
                  }
                  // Intentionally do NOT complete the job. The job will time out and be
                  // retried by the engine, keeping the instance at taskA.
                })
            .streamEnabled(workerCfg.isStreamEnabled())
            .metrics(metrics)
            .open();

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  worker.close();
                  client.close();
                }));

    // ── Wait until all instances are parked at taskA ───────────────────────────
    try {
      readyLatch.await();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.error("Interrupted while waiting for job activations.", e);
      return;
    }

    LOG.info(
        "All {} process instances are active at '{}'. Triggering batch modification.",
        totalInstances,
        SOURCE_ELEMENT_ID);

    // ── Trigger batch modifyProcessInstance ───────────────────────────────────
    final String batchOperationKey;
    try {
      batchOperationKey =
          client
              .newCreateBatchOperationCommand()
              .modifyProcessInstance()
              .addMoveInstruction(SOURCE_ELEMENT_ID, TARGET_ELEMENT_ID)
              .filter(f -> f.processDefinitionId(processId).tags(Starter.BATCH_MODIFY_TAG))
              .send()
              .join()
              .getBatchOperationKey();
    } catch (final Exception e) {
      LOG.error("Failed to create batch operation.", e);
      return;
    }

    LOG.info("Batch operation created (key={}). Polling for completion…", batchOperationKey);

    // ── Poll until COMPLETED ───────────────────────────────────────────────────
    final Instant batchStart = Instant.now();
    final Instant deadline = batchStart.plus(BATCH_OPERATION_TIMEOUT);

    while (Instant.now().isBefore(deadline)) {
      try {
        final var batchOp = client.newBatchOperationGetRequest(batchOperationKey).send().join();
        final BatchOperationState state = batchOp.getStatus();

        THROTTLED_LOGGER.info(
            "Batch operation {}: state={}, completed={}, failed={}",
            batchOperationKey,
            state,
            batchOp.getOperationsCompletedCount(),
            batchOp.getOperationsFailedCount());

        if (BatchOperationState.COMPLETED.equals(state)) {
          final Duration elapsed = Duration.between(batchStart, Instant.now());
          LOG.info(
              "Batch operation {} COMPLETED in {} ms ({} s) for {} instances.",
              batchOperationKey,
              elapsed.toMillis(),
              elapsed.toSeconds(),
              totalInstances);
          return;
        }

        if (BatchOperationState.FAILED.equals(state)) {
          LOG.error("Batch operation {} entered FAILED state.", batchOperationKey);
          return;
        }
      } catch (final Exception e) {
        THROTTLED_LOGGER.warn("Error polling batch operation {}, retrying…", batchOperationKey, e);
      }

      try {
        Thread.sleep(POLL_INTERVAL.toMillis());
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }

    LOG.error(
        "Timed out waiting for batch operation {} to complete after {}.",
        batchOperationKey,
        BATCH_OPERATION_TIMEOUT);
  }

  private CamundaClient createCamundaClient() {
    final var timeout =
        config.getWorker().getTimeout() != Duration.ZERO
            ? config.getWorker().getTimeout()
            : workerCfg.getCompletionDelay().multipliedBy(6);
    return newClientBuilder()
        .numJobWorkerExecutionThreads(workerCfg.getThreads())
        .defaultJobWorkerName(workerCfg.getWorkerName())
        .defaultJobTimeout(timeout)
        .defaultJobWorkerMaxJobsActive(workerCfg.getCapacity())
        .defaultJobPollInterval(workerCfg.getPollingDelay())
        .build();
  }

  public static void main(final String[] args) {
    createApp(Worker::new);
  }
}
