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

import com.google.common.util.concurrent.UncheckedExecutionException;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.ZeebeClientBuilder;
import io.zeebe.config.AppCfg;
import io.zeebe.config.StarterCfg;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
    final BlockingQueue<Future> requestFutures = new ArrayBlockingQueue<>(5_000);

    final ZeebeClient client = createZeebeClient();

    printTopology(client);

    final ScheduledExecutorService executorService =
        Executors.newScheduledThreadPool(starterCfg.getThreads());

    deployWorkflow(client, starterCfg.getBpmnXmlPath());

    // start instances
    final ClassLoader classLoader = getClass().getClassLoader();
    final int intervalMs = Math.floorDiv(1000, rate);
    LOG.info("Creating an instance every {}ms", intervalMs);

    final String variables = readVariables(starterCfg, classLoader);
    executorService.scheduleAtFixedRate(
        () -> {
          try {
            requestFutures.put(
                client
                    .newCreateInstanceCommand()
                    .bpmnProcessId(processId)
                    .latestVersion()
                    .variables(variables)
                    .send());
          } catch (Exception e) {
            LOG.error("Error on creating new workflow instance", e);
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

  private String readVariables(final StarterCfg starterCfg, final ClassLoader classLoader) {
    try {
      final StringBuilder stringBuilder = new StringBuilder();
      try (final InputStream variablesStream =
          classLoader.getResourceAsStream(starterCfg.getPayloadPath())) {
        if (variablesStream == null) {
          throw new IllegalStateException(
              "Expected to access "
                  + starterCfg.getPayloadPath()
                  + ", but failed to open an input stream.");
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(variablesStream))) {
          String line;
          while ((line = br.readLine()) != null) {
            stringBuilder.append(line).append("\n");
          }
        }
      }

      return stringBuilder.toString();
    } catch (IOException e) {
      throw new UncheckedExecutionException(e);
    }
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
