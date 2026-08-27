/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.worker;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.spring.properties.CamundaClientProperties;
import io.camunda.zeebe.config.LoadTesterProperties;
import io.camunda.zeebe.config.WorkerProperties;
import io.camunda.zeebe.metrics.ConnectionMonitor;
import io.camunda.zeebe.metrics.WorkerMetricsDoc;
import io.camunda.zeebe.metrics.WorkerMetricsDoc.WorkerMetricKeyNames;
import io.camunda.zeebe.util.PayloadReader;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.camunda.zeebe.worker.ResponseChecker.PendingRequest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("worker")
public class Worker {

  private static final Logger LOGGER = LoggerFactory.getLogger(Worker.class);
  private static final Logger THROTTLED_LOGGER = new ThrottledLogger(LOGGER, Duration.ofSeconds(5));
  private static final int REQUEST_FUTURES_CAPACITY = 10_000;

  private final CamundaClient client;
  private final WorkerProperties workerCfg;
  private final String variables;
  private final BlockingQueue<PendingRequest> requestFutures =
      new ArrayBlockingQueue<>(REQUEST_FUTURES_CAPACITY);
  private final ResponseChecker responseChecker;
  private final ConnectionMonitor connectionMonitor;
  private final Timer handleDurationTimer;
  private final Timer intakeDelayTimer;
  // Null when no job timeout is configured, in which case the job deadline cannot be resolved back
  // to an activation instant and the intake delay is not recorded at all.
  private final Duration jobTimeout;
  private final Map<PublishOutcome, Timer> publishDurationTimers =
      new EnumMap<>(PublishOutcome.class);

  public Worker(
      final CamundaClient client,
      final LoadTesterProperties properties,
      final CamundaClientProperties clientProperties,
      final PayloadReader payloadReader,
      final ConnectionMonitor connectionMonitor,
      final MeterRegistry registry) {
    this.client = client;
    workerCfg = properties.getWorker();
    variables = payloadReader.readPayload(workerCfg.getPayloadPath());
    responseChecker = new ResponseChecker(requestFutures, registry);
    this.connectionMonitor = connectionMonitor;
    jobTimeout = clientProperties.getWorker().getDefaults().getTimeout();

    handleDurationTimer =
        MicrometerUtil.buildTimer(WorkerMetricsDoc.HANDLE_DURATION).register(registry);
    intakeDelayTimer = MicrometerUtil.buildTimer(WorkerMetricsDoc.INTAKE_DELAY).register(registry);
    for (final var outcome : PublishOutcome.values()) {
      publishDurationTimers.put(
          outcome,
          MicrometerUtil.buildTimer(WorkerMetricsDoc.PUBLISH_DURATION)
              .tag(WorkerMetricKeyNames.OUTCOME.asString(), outcome.tagValue)
              .register(registry));
    }
    MicrometerUtil.buildGauge(WorkerMetricsDoc.COMPLETION_QUEUE_DEPTH, requestFutures::size)
        .register(registry);
  }

  @PostConstruct
  void awaitTopologyAndLogConfig() {
    responseChecker.start();
    connectionMonitor.awaitAndPrintTopology();
    LOGGER.info(
        "Worker config: completionDelay={}, sendMessage={}, messageName={}, "
            + "correlationKeyVariable={}, payloadPath={}",
        workerCfg.getCompletionDelay(),
        workerCfg.isSendMessage(),
        workerCfg.getMessageName(),
        workerCfg.getCorrelationKeyVariableName(),
        workerCfg.getPayloadPath());
  }

  @PreDestroy
  void shutdown() {
    // ResponseChecker extends Thread with a default (non-daemon) factory, so without
    // an explicit close() it keeps the JVM alive after the Spring context stops —
    // tests appear to pass but the forked process never exits on IDE runners.
    responseChecker.close();
    try {
      responseChecker.join(Duration.ofSeconds(5).toMillis());
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @JobWorker(autoComplete = false)
  public void handleJob(final JobClient jobClient, final ActivatedJob job) {
    final long startHandlingTime = System.currentTimeMillis();
    final long startHandlingNanos = System.nanoTime();
    recordIntakeDelay(job, startHandlingTime);
    try {
      handleJobInternal(jobClient, job, startHandlingTime);
    } finally {
      handleDurationTimer.record(System.nanoTime() - startHandlingNanos, TimeUnit.NANOSECONDS);
    }
  }

  /**
   * The broker sets the job deadline to the instant it activated the job plus the configured job
   * timeout, so the time already spent on delivery is what the timeout minus the remaining lease
   * leaves.
   */
  private void recordIntakeDelay(final ActivatedJob job, final long startHandlingTime) {
    if (jobTimeout == null) {
      return;
    }
    final long remainingLease = job.getDeadline() - startHandlingTime;
    intakeDelayTimer.record(jobTimeout.toMillis() - remainingLease, TimeUnit.MILLISECONDS);
  }

  private void handleJobInternal(
      final JobClient jobClient, final ActivatedJob job, final long startHandlingTime) {
    if (workerCfg.isSendMessage()) {
      final var correlationKey =
          job.getVariable(workerCfg.getCorrelationKeyVariableName()).toString();

      final boolean messagePublishedSuccessfully = publishMessage(correlationKey);
      if (!messagePublishedSuccessfully) {
        // Instead of failing the job, we simply let the job time out, so someone else has to
        // pick up the job later. This might delay the individual process instance, but overall it
        // has a lesser impact, as we can work on a different job in the meantime, keeping up the
        // throughput.
        //
        // It might be that one partition has currently some struggle due to restarts or role
        // changes, chances are low that this affects all partitions.
        //
        // This might cause issues for the current job to publish a message, but we are sending
        // messages via correlation key,   based on the process instance payload.
        //
        // On the next job/message published the chances are (partition count - 1 / partition
        // count) that we hit another partition where it works without issues.
        //
        // Apply the same completion delay as the success path before returning. Without it the
        // handler returns immediately, the client re-polls at full speed, and we keep hammering
        // the struggling partition, defeating the time-out-and-retry strategy above.
        addDelayToCompletion(workerCfg.getCompletionDelay().toMillis(), startHandlingTime);
        return;
      }
    }

    final var command = jobClient.newCompleteCommand(job.getKey()).variables(variables);
    addDelayToCompletion(workerCfg.getCompletionDelay().toMillis(), startHandlingTime);
    final long completeStartNanos = System.nanoTime();
    if (!requestFutures.offer(new PendingRequest(command.send(), completeStartNanos))) {
      // Non-blocking: if the response-check queue is saturated, drop tracking for this
      // completion rather than stalling the job handler thread (which would cascade into
      // broker timeouts). We lose visibility into its eventual result — log throttled so
      // the operator can notice sustained backpressure without flooding the log.
      THROTTLED_LOGGER.warn(
          "Completion-response queue full (capacity: {}); dropping future tracking",
          REQUEST_FUTURES_CAPACITY);
    }
  }

  private boolean publishMessage(final String correlationKey) {
    final var messageName = workerCfg.getMessageName();

    LOGGER.debug("Publish message '{}' with correlation key '{}'", messageName, correlationKey);
    final long publishStartNanos = System.nanoTime();
    final var messageSendFuture =
        client
            .newPublishMessageCommand()
            .messageName(messageName)
            .correlationKey(correlationKey)
            .send();

    try {
      messageSendFuture.get(10, TimeUnit.SECONDS);
      recordPublishDuration(PublishOutcome.SUCCESS, publishStartNanos);
      return true;
    } catch (final Exception ex) {
      recordPublishDuration(
          ex instanceof TimeoutException ? PublishOutcome.TIMEOUT : PublishOutcome.ERROR,
          publishStartNanos);
      THROTTLED_LOGGER.error(
          "Exception on publishing a message with name {} and correlationKey {}",
          messageName,
          correlationKey,
          ex);
      return false;
    }
  }

  private void recordPublishDuration(final PublishOutcome outcome, final long startNanos) {
    publishDurationTimers.get(outcome).record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
  }

  private static void addDelayToCompletion(
      final long completionDelay, final long startHandlingTime) {
    try {
      final var elapsedTime = System.currentTimeMillis() - startHandlingTime;
      if (elapsedTime < completionDelay) {
        final long sleepTime = completionDelay - elapsedTime;
        LOGGER.debug("Sleep for {} ms", sleepTime);
        Thread.sleep(sleepTime);
      } else {
        LOGGER.debug(
            "Skip sleep. Elapsed time {} is larger than {} completion delay.",
            elapsedTime,
            completionDelay);
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      THROTTLED_LOGGER.error(
          "Interrupted during completion delay sleep of {} ms", completionDelay, e);
    } catch (final Exception e) {
      THROTTLED_LOGGER.error("Exception on sleep with completion delay {}", completionDelay, e);
    }
  }

  private enum PublishOutcome {
    SUCCESS("success"),
    TIMEOUT("timeout"),
    ERROR("error");

    private final String tagValue;

    PublishOutcome(final String tagValue) {
      this.tagValue = tagValue;
    }
  }
}
