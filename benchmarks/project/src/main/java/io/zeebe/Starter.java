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
package io.zeebe;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.ZeebeClientBuilder;
import io.zeebe.config.AppCfg;
import io.zeebe.config.StarterCfg;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Starter extends App {

  private static final Logger LOG = LoggerFactory.getLogger(Starter.class);

  private final AppCfg appCfg;

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

    deployProcess(client, starterCfg.getBpmnXmlPath());

    // start instances
    final int intervalMs = Math.floorDiv(1000, rate);
    LOG.info("Creating an instance every {}ms", intervalMs);

    final String variables = readVariables(starterCfg.getPayloadPath());
    final BooleanSupplier shouldContinue = createContinuationCondition(starterCfg);

    final CountDownLatch countDownLatch = new CountDownLatch(1);

    final ScheduledFuture scheduledTask =
        executorService.scheduleAtFixedRate(
            () ->
                runStarter(
                    starterCfg,
                    processId,
                    requestFutures,
                    client,
                    variables,
                    shouldContinue,
                    countDownLatch),
            0,
            intervalMs,
            TimeUnit.MILLISECONDS);

    final ResponseChecker responseChecker = new ResponseChecker(requestFutures);
    responseChecker.start();

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  if (!executorService.isShutdown()) {
                    executorService.shutdown();
                    try {
                      executorService.awaitTermination(60, TimeUnit.SECONDS);
                    } catch (final InterruptedException e) {
                      LOG.error("Shutdown executor service was interrupted", e);
                    }
                  }
                  if (responseChecker.isAlive()) {
                    responseChecker.close();
                  }
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
          requestFutures.put(
              client
                  .newCreateInstanceCommand()
                  .bpmnProcessId(processId)
                  .latestVersion()
                  .variables(variables)
                  .send());
        }
      } catch (final Exception e) {
        LOG.error("Error on creating new process instance", e);
      }
    } else {
      countDownLatch.countDown();
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

  private void deployProcess(final ZeebeClient client, final String bpmnXmlPath) {
    while (true) {
      try {
        client.newDeployCommand().addResourceFromClasspath(bpmnXmlPath).send().join();
        break;
      } catch (final Exception e) {
        LOG.warn("Failed to deploy process, retrying", e);
        try {
          Thread.sleep(200);
        } catch (final InterruptedException ex) {
          // ignore
        }
      }
    }
  }

  private BooleanSupplier createContinuationCondition(final StarterCfg starterCfg) {
    final int durationLimit = starterCfg.getDurationLimit();

    if (durationLimit > 0) {
      // if there is a duration limit
      final LocalDateTime endTime = LocalDateTime.now().plus(durationLimit, ChronoUnit.SECONDS);
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
