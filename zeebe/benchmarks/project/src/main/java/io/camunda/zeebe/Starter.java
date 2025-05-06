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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import io.camunda.zeebe.client.api.command.DeployResourceCommandStep1.DeployResourceCommandStep2;
import io.camunda.zeebe.config.AppCfg;
import io.camunda.zeebe.config.StarterCfg;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import org.agrona.CloseHelper;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Starter extends App {

  private static final Logger THROTTLED_LOGGER =
      new ThrottledLogger(LoggerFactory.getLogger(Starter.class), Duration.ofSeconds(5));
  private static final Logger LOG = LoggerFactory.getLogger(Starter.class);
  private static final long NANOS_PER_SECOND = Duration.ofSeconds(1).toNanos();
  private static final TypeReference<HashMap<String, Object>> VARIABLES_TYPE_REF =
      new TypeReference<>() {};
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private final AppCfg appCfg;

  // assume a no-op visibility monitor initially
  private ApiVisibilityMonitor apiVisibilityMonitor = ignored -> {};

  private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

  Starter(final AppCfg appCfg) {
    this.appCfg = appCfg;
  }

  @Override
  public void run() {
    final StarterCfg starterCfg = appCfg.getStarter();
    final int rate = starterCfg.getRate();
    final String processId = starterCfg.getProcessId();
    final BlockingQueue<Future<?>> requestFutures = new ArrayBlockingQueue<>(5_000);

    final ZeebeClient client = createZeebeClient();

    printTopology(client);

    final ScheduledExecutorService executorService =
        Executors.newScheduledThreadPool(starterCfg.getThreads());

    deployProcess(client, starterCfg);

    // start instances
    final long intervalNanos = Math.floorDiv(NANOS_PER_SECOND, rate);
    LOG.info("Creating an instance every {}ns", intervalNanos);

    final String variablesString = readVariables(starterCfg.getPayloadPath());
    final HashMap<String, Object> variables = deserializeVariables(variablesString);

    final BooleanSupplier shouldContinue = createContinuationCondition(starterCfg);

    final CountDownLatch countDownLatch = new CountDownLatch(1);
    final AtomicLong businessKey = new AtomicLong(0);

    final var scheduledTask =
        executorService.scheduleAtFixedRate(
            () -> {
              variables.put(starterCfg.getBusinessKey(), businessKey.incrementAndGet());
              runStarter(
                  starterCfg,
                  processId,
                  requestFutures,
                  client,
                  serializeVariables(variables),
                  shouldContinue,
                  countDownLatch);
              return;
            },
            0,
            intervalNanos,
            TimeUnit.NANOSECONDS);

    if (starterCfg.isMonitorApiVisibility()) {
      final var operateClient =
          new OperateClient(
              URI.create((appCfg.isTls() ? "https://" : "http://") + appCfg.getOperateUrl()),
              virtualExecutor);
      apiVisibilityMonitor = new ProcessInstanceMonitor(operateClient, virtualExecutor, registry);
    }

    final ResponseChecker responseChecker = new ResponseChecker(requestFutures);
    responseChecker.start();

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  awaitExecutorShutdown(executorService);

                  if (responseChecker.isAlive()) {
                    responseChecker.close();
                  }

                  CloseHelper.quietCloseAll(apiVisibilityMonitor);
                  awaitExecutorShutdown(virtualExecutor);
                }));

    // wait for starter to finish
    try {
      countDownLatch.await();
    } catch (final InterruptedException e) {
      LOG.error("Awaiting of count down latch was interrupted.", e);
    }

    LOG.info("Starter finished");

    scheduledTask.cancel(true);
    executorService.shutdown();
    responseChecker.close();
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private static void awaitExecutorShutdown(final ExecutorService executorService) {
    if (executorService.isShutdown()) {
      return;
    }

    try {
      executorService.shutdown();
      executorService.awaitTermination(60, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      LOG.warn("Shutdown executor service was interrupted", e);
    }
  }

  private static HashMap<String, Object> deserializeVariables(final String variablesString) {
    final HashMap<String, Object> variables;
    try {
      variables = OBJECT_MAPPER.readValue(variablesString, VARIABLES_TYPE_REF);
    } catch (final JsonProcessingException e) {
      LOG.error("Failed to parse variables '{}'", variablesString, e);
      throw new RuntimeException(e);
    }
    return variables;
  }

  private static String serializeVariables(final HashMap<String, Object> variables) {
    try {
      return OBJECT_MAPPER.writeValueAsString(variables);
    } catch (final JsonProcessingException e) {
      LOG.error("Failed to convert variables to string: '{}' ", variables, e);
      throw new RuntimeException(e);
    }
  }

  private void runStarter(
      final StarterCfg starterCfg,
      final String processId,
      final BlockingQueue<Future<?>> requestFutures,
      final ZeebeClient client,
      final String variables,
      final BooleanSupplier shouldContinue,
      final CountDownLatch countDownLatch) {
    if (shouldContinue.getAsBoolean()) {
      try {
        if (starterCfg.isStartViaMessage()) {
          requestFutures.put(
              client
                  .newPublishMessageCommand()
                  .messageName(starterCfg.getMsgName())
                  .correlationKey(UUID.randomUUID().toString())
                  .variables(variables)
                  .timeToLive(Duration.ZERO)
                  .send());
        } else {
          startViaCommand(starterCfg, processId, requestFutures, client, variables);
        }

      } catch (final Exception e) {
        THROTTLED_LOGGER.error("Error on creating new process instance", e);
      }
    } else {
      countDownLatch.countDown();
    }
  }

  private void startViaCommand(
      final StarterCfg starterCfg,
      final String processId,
      final BlockingQueue<Future<?>> requestFutures,
      final ZeebeClient client,
      final String variables)
      throws InterruptedException {
    if (starterCfg.isWithResults()) {
      requestFutures.put(
          client
              .newCreateInstanceCommand()
              .bpmnProcessId(processId)
              .latestVersion()
              .variables(variables)
              .withResult()
              .requestTimeout(starterCfg.getWithResultsTimeout())
              .send());
    } else {
      final var request =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId(processId)
              .latestVersion()
              .variables(variables)
              .send();

      request.thenAcceptAsync(
          r -> apiVisibilityMonitor.monitor(r.getProcessInstanceKey()), virtualExecutor);
      requestFutures.put(request);
    }
  }

  private ZeebeClient createZeebeClient() {
    final ZeebeClientBuilder builder =
        ZeebeClient.newClientBuilder()
            .gatewayAddress(appCfg.getBrokerUrl())
            .numJobWorkerExecutionThreads(0)
            .withProperties(System.getProperties())
            .withInterceptors(monitoringInterceptor);

    if (!appCfg.isTls()) {
      builder.usePlaintext();
    }

    return builder.build();
  }

  private void deployProcess(final ZeebeClient client, final StarterCfg starterCfg) {
    final var deployCmd = constructDeploymentCommand(client, starterCfg);
    final var idleStrategy =
        new BackoffIdleStrategy(
            0, 0, Duration.ofMillis(200).toNanos(), Duration.ofSeconds(1).toNanos());

    // allow exiting on shutdown signal and so on if the thread is interrupted
    while (!Thread.currentThread().isInterrupted()) {
      try {
        deployCmd.send().join();
        break;
      } catch (final Exception e) {
        THROTTLED_LOGGER.warn("Failed to deploy process, backing off and retrying...", e);
        idleStrategy.idle();
      }
    }
  }

  private static DeployResourceCommandStep2 constructDeploymentCommand(
      final ZeebeClient client, final StarterCfg starterCfg) {
    final var deployCmd =
        client.newDeployResourceCommand().addResourceFromClasspath(starterCfg.getBpmnXmlPath());

    final var extraBpmnModels = starterCfg.getExtraBpmnModels();
    if (extraBpmnModels != null) {
      for (final var model : extraBpmnModels) {
        deployCmd.addResourceFromClasspath(model);
      }
    }
    return deployCmd;
  }

  private BooleanSupplier createContinuationCondition(final StarterCfg starterCfg) {
    final int durationLimit = starterCfg.getDurationLimit();

    if (durationLimit > 0) {
      // if there is a duration limit
      final LocalDateTime endTime = LocalDateTime.now().plusSeconds(durationLimit);
      // continue until time is up
      return () -> LocalDateTime.now().isBefore(endTime);
    } else {
      // otherwise continue forever
      return () -> true;
    }
  }

  public static void main(final String[] args) {
    createApp(Starter::new);
  }
}
