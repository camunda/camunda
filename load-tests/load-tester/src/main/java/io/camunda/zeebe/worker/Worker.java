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
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.PublishMessageResponse;
import io.camunda.client.api.worker.JobClient;
import io.camunda.zeebe.config.LoadTesterProperties;
import io.camunda.zeebe.config.WorkerProperties;
import io.camunda.zeebe.metrics.ConnectionMonitor;
import io.camunda.zeebe.util.PayloadReader;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
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
  private final BlockingQueue<Future<?>> requestFutures =
      new ArrayBlockingQueue<>(REQUEST_FUTURES_CAPACITY);
  private final ResponseChecker responseChecker;
  private final ConnectionMonitor connectionMonitor;

  public Worker(
      final CamundaClient client,
      final LoadTesterProperties properties,
      final PayloadReader payloadReader,
      final ConnectionMonitor connectionMonitor) {
    this.client = client;
    workerCfg = properties.getWorker();
    variables = payloadReader.readPayload(workerCfg.getPayloadPath());
    responseChecker = new ResponseChecker(requestFutures);
    this.connectionMonitor = connectionMonitor;
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

    if (workerCfg.isSendMessage()) {
      final var correlationKey =
          job.getVariable(workerCfg.getCorrelationKeyVariableName()).toString();

      final var messagePublishedFuture = publishMessage(correlationKey);
      // We do not want to block the worker here - as this would throttle the general throuhgput
      // due to individual response latencies
      messagePublishedFuture.whenComplete(
          (value, error) -> {
            if (error == null) {
              final var command = jobClient.newCompleteCommand(job.getKey()).variables(variables);
              tryToOffer(command.send());
            } else {
              // Instead of failing the job, we simply let the job time out, so someone else has to
              // pick up the job later. This might delay the individual process instance, but
              // overall it
              // has a lesser impact, as we can work on a different job in the meantime, keeping up
              // the
              // throughput.
              //
              // It might be that one partition has currently some struggle due to restarts or role
              // changes, chances are low that this affects all partitions.
              //
              // This might cause issues for the current job to publish a message, but we are
              // sending
              // messages via correlation key,   based on the process instance payload.
              //
              // On the next job/message published the chances are (partition count - 1 / partition
              // count) that we hit another partition where it works without issues.
              return;
            }
          });

      // job will eventually completed by the worker - later after the response of the publish
      // received or on
      // retry
      return;
    }

    final var command = jobClient.newCompleteCommand(job.getKey()).variables(variables);
    addDelayToCompletion(workerCfg.getCompletionDelay().toMillis(), startHandlingTime);
    tryToOffer(command.send());
  }

  private void tryToOffer(final CamundaFuture<?> commandFuture) {
    if (!requestFutures.offer(commandFuture)) {
      // Non-blocking: if the response-check queue is saturated, drop tracking for this
      // completion rather than stalling the job handler thread (which would cascade into
      // broker timeouts). We lose visibility into its eventual result — log throttled so
      // the operator can notice sustained backpressure without flooding the log.
      THROTTLED_LOGGER.warn(
          "Completion-response queue full (capacity: {}); dropping future tracking",
          REQUEST_FUTURES_CAPACITY);
    }
  }

  private CamundaFuture<PublishMessageResponse> publishMessage(final String correlationKey) {
    final var messageName = workerCfg.getMessageName();

    LOGGER.debug("Publish message '{}' with correlation key '{}'", messageName, correlationKey);
    return client
        .newPublishMessageCommand()
        .messageName(messageName)
        .correlationKey(correlationKey)
        .send();
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
}
