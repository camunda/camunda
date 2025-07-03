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

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.client.api.worker.JobWorkerMetrics;
import io.camunda.zeebe.config.AppCfg;
import io.camunda.zeebe.config.WorkerCfg;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.Tags;
import java.net.URI;
import java.time.Duration;
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
    final CamundaClient client = createCamundaClient();
    final JobWorkerMetrics metrics =
        JobWorkerMetrics.micrometer()
            .withMeterRegistry(registry)
            .withTags(Tags.of("workerName", workerCfg.getWorkerName(), "jobType", jobType))
            .build();
    printTopology(client);

    final JobWorker worker =
        client
            .newWorker()
            .jobType(jobType)
            .handler(handleJob(client, variables, completionDelay))
            .streamEnabled(isStreamEnabled)
            .metrics(metrics)
            .open();

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  worker.close();
                  client.close();
                }));
  }

  private JobHandler handleJob(
      final CamundaClient client, final String variables, final long completionDelay) {
    return (jobClient, job) -> {
      // we record the start handling time to better calculate the completion delay
      // as when we send a message we already have a delay due to waiting on the response
      final long startHandlingTime = System.currentTimeMillis();

      if (workerCfg.isSendMessage()) {

        final var correlationKey =
            job.getVariable(workerCfg.getCorrelationKeyVariableName()).toString();

        final boolean messagePublishedSuccessfully = publishMessage(client, correlationKey);
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
      command
          .send()
          .whenComplete(
              (v, e) -> {
                if (e == null) {
                  return;
                }

                final Throwable cause = e.getCause();
                if (cause instanceof final StatusRuntimeException statusRuntimeException) {
                  if (statusRuntimeException.getStatus().getCode() != Code.RESOURCE_EXHAUSTED) {
                    // we don't want to flood the log
                    THROTTLED_LOGGER.warn("Request failed", e);
                  }
                }
              });
    };
  }

  private boolean publishMessage(final CamundaClient client, final String correlationKey) {
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

  private CamundaClient createCamundaClient() {
    final WorkerCfg workerCfg = appCfg.getWorker();
    final var timeout =
        appCfg.getWorker().getTimeout() != Duration.ZERO
            ? appCfg.getWorker().getTimeout()
            : workerCfg.getCompletionDelay().multipliedBy(6);
    final CamundaClientBuilder builder =
        CamundaClient.newClientBuilder()
            .grpcAddress(URI.create(appCfg.getBrokerUrl()))
            .restAddress(URI.create(appCfg.getBrokerRestUrl()))
            .preferRestOverGrpc(appCfg.isPreferRest())
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
