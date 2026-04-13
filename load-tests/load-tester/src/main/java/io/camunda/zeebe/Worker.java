/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.client.api.worker.JobWorkerMetrics;
import io.camunda.zeebe.config.LoadTesterProperties;
import io.camunda.zeebe.config.WorkerProperties;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Worker {

  public static final Logger LOGGER = LoggerFactory.getLogger(Worker.class);
  private static final Logger THROTTLED_LOGGER = new ThrottledLogger(LOGGER, Duration.ofSeconds(5));

  private final CamundaClient client;
  private final LoadTesterProperties config;
  private final WorkerProperties workerCfg;
  private final MeterRegistry registry;

  private JobWorker worker;
  private ResponseChecker responseChecker;

  Worker(
      final CamundaClient client, final LoadTesterProperties config, final MeterRegistry registry) {
    this.client = client;
    this.config = config;
    this.workerCfg = config.getWorker();
    this.registry = registry;
  }

  public void run() {
    final String jobType = workerCfg.getJobType();
    final long completionDelay = workerCfg.getCompletionDelay().toMillis();
    final boolean isStreamEnabled = workerCfg.isStreamEnabled();
    final var variables = PayloadReader.readVariables(workerCfg.getPayloadPath());
    final BlockingQueue<Future<?>> requestFutures = new ArrayBlockingQueue<>(10_000);
    final JobWorkerMetrics metrics =
        JobWorkerMetrics.micrometer()
            .withMeterRegistry(registry)
            .withTags(Tags.of("workerName", workerCfg.getWorkerName(), "jobType", jobType))
            .build();
    printTopology(client);

    final var timeout =
        workerCfg.getTimeout() != Duration.ZERO
            ? workerCfg.getTimeout()
            : workerCfg.getCompletionDelay().multipliedBy(6);

    worker =
        client
            .newWorker()
            .jobType(jobType)
            .handler(handleJob(variables, completionDelay, requestFutures))
            .name(workerCfg.getWorkerName())
            .timeout(timeout)
            .maxJobsActive(workerCfg.getCapacity())
            .pollInterval(workerCfg.getPollingDelay())
            .streamEnabled(isStreamEnabled)
            .metrics(metrics)
            .open();

    responseChecker = new ResponseChecker(requestFutures);
    responseChecker.start();
  }

  void close() {
    if (worker != null) {
      worker.close();
    }
    if (responseChecker != null) {
      responseChecker.close();
    }
  }

  private JobHandler handleJob(
      final String variables,
      final long completionDelay,
      final BlockingQueue<Future<?>> requestFutures) {
    return (jobClient, job) -> {
      // we record the start handling time to better calculate the completion delay
      // as when we send a message we already have a delay due to waiting on the response
      final long startHandlingTime = System.currentTimeMillis();

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

          return;
        }
      }

      final var command = jobClient.newCompleteCommand(job.getKey()).variables(variables);
      addDelayToCompletion(completionDelay, startHandlingTime);
      requestFutures.add(command.send());
    };
  }

  private boolean publishMessage(final String correlationKey) {
    final var messageName = workerCfg.getMessageName();

    LOGGER.debug("Publish message '{}' with correlation key '{}'", messageName, correlationKey);
    final var messageSendFuture =
        client
            .newPublishMessageCommand()
            .messageName(messageName)
            .correlationKey(correlationKey)
            .send();

    try {
      messageSendFuture.get(10, TimeUnit.SECONDS);
      return true;
    } catch (final Exception ex) {
      THROTTLED_LOGGER.error(
          "Exception on publishing a message with name {} and correlationKey {}",
          messageName,
          correlationKey,
          ex);
      return false;
    }
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
            "Skip sleep. Elapsed time {} is larger then {} completion delay.",
            elapsedTime,
            completionDelay);
      }
    } catch (final Exception e) {
      THROTTLED_LOGGER.error("Exception on sleep with completion delay {}", completionDelay, e);
    }
  }

  private void printTopology(final CamundaClient client) {
    while (true) {
      try {
        final var topology = client.newTopologyRequest().send().join();
        topology
            .getBrokers()
            .forEach(
                b -> {
                  LOGGER.info("Broker {} - {} ({})", b.getNodeId(), b.getAddress(), b.getVersion());
                  b.getPartitions()
                      .forEach(p -> LOGGER.info("{} - {}", p.getPartitionId(), p.getRole()));
                });
        break;
      } catch (final Exception e) {
        THROTTLED_LOGGER.warn("Topology request failed: ", e);
        try {
          Thread.sleep(1000);
        } catch (final InterruptedException ex) {
          throw new RuntimeException(ex);
        }
      }
    }
  }
}
