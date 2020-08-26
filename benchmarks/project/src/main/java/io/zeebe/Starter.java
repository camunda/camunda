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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Starter extends App {

  private static final Logger LOG = LoggerFactory.getLogger(Starter.class);

  private final AppCfg appCfg;

  Starter(AppCfg appCfg) {
    this.appCfg = appCfg;
  }

  @Override
  public void run() {
    final StarterCfg starterCfg = appCfg.getStarter();
    final int rate = starterCfg.getRate();
    final String processId = starterCfg.getProcessId();
    final BlockingQueue<Future<?>> requestFutures = new ArrayBlockingQueue<>(5_000);
    final int durationLimit = starterCfg.getDurationLimit();

    final ZeebeClient client = createZeebeClient();

    printTopology(client);

    final ScheduledExecutorService executorService =
        Executors.newScheduledThreadPool(starterCfg.getThreads());

    deployWorkflow(client, starterCfg.getBpmnXmlPath());

    // start instances
    final int intervalMs = Math.floorDiv(1000, rate);
    LOG.info("Creating an instance every {}ms", intervalMs);

    final String variables = readVariables(starterCfg.getPayloadPath());
    final LocalDateTime startTime = LocalDateTime.now();
    executorService.scheduleAtFixedRate(
        () -> {
          final long duration = ChronoUnit.SECONDS.between(startTime, LocalDateTime.now());
          if (durationLimit <= 0 || duration < durationLimit) {
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
            } catch (Exception e) {
              LOG.error("Error on creating new workflow instance", e);
            }
          } else {
            // TODO can one use scheduledFuture.cancel(false) to gracefully
            // stop the task from being scheduled again and allow the response
            // checker to receive the responses for everything that was started?
            System.exit(0);
          }
        },
        0,
        intervalMs,
        TimeUnit.MILLISECONDS);

    final ResponseChecker responseChecker = new ResponseChecker(requestFutures);
    responseChecker.start();

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  executorService.shutdown();
                  try {
                    executorService.awaitTermination(60, TimeUnit.SECONDS);
                  } catch (InterruptedException e) {
                    e.printStackTrace();
                  }
                  client.close();
                  responseChecker.close();
                }));
  }

  private ZeebeClient createZeebeClient() {
    final ZeebeClientBuilder builder =
        ZeebeClient.newClientBuilder()
            .brokerContactPoint(appCfg.getBrokerUrl())
            .numJobWorkerExecutionThreads(0)
            .withProperties(System.getProperties())
            .withInterceptors(monitoringInterceptor);

    if (!appCfg.isTls()) {
      builder.usePlaintext();
    }

    return builder.build();
  }

  private void deployWorkflow(ZeebeClient client, String bpmnXmlPath) {
    while (true) {
      try {
        client.newDeployCommand().addResourceFromClasspath(bpmnXmlPath).send().join();
        break;
      } catch (Exception e) {
        LOG.warn("Failed to deploy workflow, retrying", e);
        try {
          Thread.sleep(200);
        } catch (InterruptedException ex) {
          // ignore
        }
      }
    }
  }

  public static void main(String[] args) {
    createApp(Starter::new);
  }
}
