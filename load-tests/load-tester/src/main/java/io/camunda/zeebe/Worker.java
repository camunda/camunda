/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.zeebe.config.LoadTesterProperties;
import io.camunda.zeebe.config.WorkerProperties;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("worker")
public class Worker {

  private static final Logger LOGGER = LoggerFactory.getLogger(Worker.class);
  private static final Logger THROTTLED_LOGGER = new ThrottledLogger(LOGGER, Duration.ofSeconds(5));

  private final CamundaClient client;
  private final WorkerProperties workerCfg;
  private final String variables;
  private final BlockingQueue<Future<?>> requestFutures = new ArrayBlockingQueue<>(10_000);
  private final ResponseChecker responseChecker;

  public Worker(
      final CamundaClient client,
      final LoadTesterProperties properties,
      final PayloadReader payloadReader) {
    this.client = client;
    this.workerCfg = properties.getWorker();
    this.variables = payloadReader.readPayload(workerCfg.getPayloadPath());
    this.responseChecker = new ResponseChecker(requestFutures);
    this.responseChecker.start();
  }

  @JobWorker(autoComplete = false)
  public void handleJob(final JobClient jobClient, final ActivatedJob job) {
    final long startHandlingTime = System.currentTimeMillis();

    if (workerCfg.isSendMessage()) {
      final var correlationKey =
          job.getVariable(workerCfg.getCorrelationKeyVariableName()).toString();

      final boolean messagePublishedSuccessfully = publishMessage(correlationKey);
      if (!messagePublishedSuccessfully) {
        return;
      }
    }

    final var command = jobClient.newCompleteCommand(job.getKey()).variables(variables);
    addDelayToCompletion(workerCfg.getCompletionDelay().toMillis(), startHandlingTime);
    requestFutures.add(command.send());
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
}
