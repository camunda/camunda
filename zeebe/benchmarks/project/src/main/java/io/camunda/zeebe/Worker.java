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
package io.camunda.zeebe;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.camunda.zeebe.client.api.worker.JobWorkerMetrics;
import io.camunda.zeebe.config.AppCfg;
import io.camunda.zeebe.config.WorkerCfg;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Worker extends App {

  public static final Logger LOGGER = LoggerFactory.getLogger(Worker.class);
  private static final Logger THROTTLED_LOGGER = new ThrottledLogger(LOGGER, Duration.ofSeconds(5));
  private final AppCfg appCfg;
  private final WorkerCfg workerCfg;

  Worker(final AppCfg appCfg) {
    this.appCfg = appCfg;
    workerCfg = appCfg.getWorker();
  }

  @Override
  public void run() {
    final String jobType = workerCfg.getJobType();
    final long completionDelay = workerCfg.getCompletionDelay().toMillis();
    final boolean isStreamEnabled = workerCfg.isStreamEnabled();
    final var variables = readVariables(workerCfg.getPayloadPath());
    final BlockingQueue<Future<?>> requestFutures = new ArrayBlockingQueue<>(10_000);
    final ZeebeClient client = createZeebeClient();
    final JobWorkerMetrics metrics =
        JobWorkerMetrics.micrometer()
            .withMeterRegistry(prometheusRegistry)
            .withTags(Tags.of("workerName", workerCfg.getWorkerName(), "jobType", jobType))
            .build();
    printTopology(client);

    final JobWorker worker =
        client
            .newWorker()
            .jobType(jobType)
            .handler(handleJob(client, variables, completionDelay, requestFutures))
            .streamEnabled(isStreamEnabled)
            .metrics(metrics)
            .open();

    final ResponseChecker responseChecker = new ResponseChecker(requestFutures);
    responseChecker.start();

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  worker.close();
                  client.close();
                  responseChecker.close();
                }));
  }

  private JobHandler handleJob(
      final ZeebeClient client,
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

        final boolean messagePublishedSuccessfully = publishMessage(client, correlationKey);
        if (!messagePublishedSuccessfully) {
          // - On issues with publishing a message we need to retry the job
          // - thus is to make sure our message gets published
          // - otherwise our process might get stuck
          // - failing the job makes it immediately available again
          jobClient
              .newFailCommand(job)
              .retries(job.getRetries())
              .errorMessage("Message publish failed.")
              .send();
          return;
        }
      }

      final var command = jobClient.newCompleteCommand(job.getKey()).variables(variables);
      addDelayToCompletion(completionDelay, startHandlingTime);
      requestFutures.add(command.send());
    };
  }

  private boolean publishMessage(final ZeebeClient client, final String correlationKey) {
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

  private ZeebeClient createZeebeClient() {
    final WorkerCfg workerCfg = appCfg.getWorker();
    final var timeout =
        appCfg.getWorker().getTimeout() != Duration.ZERO
            ? appCfg.getWorker().getTimeout()
            : workerCfg.getCompletionDelay().multipliedBy(6);
    final ZeebeClientBuilder builder =
        ZeebeClient.newClientBuilder()
            .gatewayAddress(appCfg.getBrokerUrl())
            .numJobWorkerExecutionThreads(workerCfg.getThreads())
            .defaultJobWorkerName(workerCfg.getWorkerName())
            .defaultJobTimeout(timeout)
            .defaultJobWorkerMaxJobsActive(workerCfg.getCapacity())
            .defaultJobPollInterval(workerCfg.getPollingDelay())
            .withProperties(System.getProperties())
            .withInterceptors(monitoringInterceptor);

    if (!appCfg.isTls()) {
      builder.usePlaintext();
    }

    return builder.build();
  }

  public static void main(final String[] args) {
    createApp(Worker::new);
  }
}
