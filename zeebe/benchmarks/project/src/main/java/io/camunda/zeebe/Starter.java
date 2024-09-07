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
import io.micrometer.core.instrument.Timer;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
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
  private HttpClient httpClient;
  private String operateUrl;
  private Timer dataAvailabilityDelay;

  Starter(final AppCfg appCfg) {
    this.appCfg = appCfg;
  }

  @Override
  public void run() {
    final StarterCfg starterCfg = appCfg.getStarter();
    final int rate = starterCfg.getRate();
    final String processId = starterCfg.getProcessId();
    final BlockingQueue<Future<?>> requestFutures = new ArrayBlockingQueue<>(5_000);

    dataAvailabilityDelay =
        Timer.builder("zeebe.clients.observed.camunda.data.availability.delay")
            .description(
                "Duration Camunda needs to show data in Operate. Measured via creating an process instance and querying Operate API. Measured in seconds.")
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofMillis(10))
            .register(prometheusRegistry);

    httpClient =
        HttpClient.newBuilder().cookieHandler(new CookieManager()).version(Version.HTTP_2).build();
    operateUrl = starterCfg.getOperateUrl();
    loginToOperate(operateUrl);

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

    final ScheduledFuture scheduledTask =
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

  private void loginToOperate(final String operateUrl) {
    // login to Operate
    final String endpoint = operateUrl + "/api/login?username=demo&password=demo";
    final var loginRequest =
        HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();

    var login = false;
    do {
      LOG.debug("Sent log in request to {}", endpoint);
      try {
        final HttpResponse<String> response =
            httpClient.send(loginRequest, BodyHandlers.ofString());
        LOG.debug(
            "Retrieved response with status {} and body {}",
            response.statusCode(),
            response.body());
        login = response.statusCode() >= 200 && response.statusCode() < 300;
      } catch (final Exception ex) {
        THROTTLED_LOGGER.warn("Sending login request to {}", endpoint, ex);
      }
    } while (!login);

    LOG.info("Successfully logged into Operate");
  }

  private static HashMap<String, Object> deserializeVariables(final String variablesString) {
    final HashMap<String, Object> variables;
    try {
      variables = OBJECT_MAPPER.readValue(variablesString, VARIABLES_TYPE_REF);
    } catch (final JsonProcessingException e) {
      LOG.error(String.format("Failed to parse variables '%s'.", variablesString), e);
      throw new RuntimeException(e);
    }
    return variables;
  }

  private static String serializeVariables(final HashMap<String, Object> variables) {
    try {
      return OBJECT_MAPPER.writeValueAsString(variables);
    } catch (final JsonProcessingException e) {
      LOG.error(String.format("Failed to convert variables to string: '%s' ", variables), e);
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
      final long createPITime = System.currentTimeMillis();
      final var createPIFuture =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId(processId)
              .latestVersion()
              .variables(variables)
              .send();

      createPIFuture.whenComplete(
          (processInstanceEvent, throwable) -> {
            final long processInstanceKey = processInstanceEvent.getProcessInstanceKey();

            final String endpoint =
                starterCfg.getOperateUrl() + "v1/process-instances/" + processInstanceKey;
            final var retrievePIRequest =
                HttpRequest.newBuilder().uri(URI.create(endpoint)).GET().build();

            var retrievedPI = false;
            var retry = 0;
            do {
              if (retry++ > 0) {
                try {
                  Thread.sleep(10);
                } catch (final InterruptedException e) {
                  THROTTLED_LOGGER.warn("Interrupted on sleep");
                }
              }
              LOG.debug(
                  "Request PI with key {} from {} [retry: {}]",
                  processInstanceKey,
                  endpoint,
                  retry);
              try {
                final HttpResponse<String> response =
                    httpClient.send(retrievePIRequest, BodyHandlers.ofString());
                LOG.debug(
                    "Retrieved process instance with status {} and body {}",
                    response.statusCode(),
                    response.body());
                retrievedPI = response.statusCode() >= 200 && response.statusCode() < 300;
              } catch (final Exception ex) {
                THROTTLED_LOGGER.warn("Error on retrieving process instance {}", endpoint, ex);
              }
            } while (!retrievedPI);
            // TODO metrics update
            final long pIAvailableToUser = System.currentTimeMillis();
            final var delay = pIAvailableToUser - createPITime;
            LOG.debug("Process instance was available in Operate after {} ms ", delay);
            dataAvailabilityDelay.record(delay, TimeUnit.MILLISECONDS);
          });
      requestFutures.put(createPIFuture);
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

    while (true) {
      try {
        deployCmd.send().join();
        break;
      } catch (final Exception e) {
        THROTTLED_LOGGER.warn("Failed to deploy process, retrying", e);
        try {
          Thread.sleep(200);
        } catch (final InterruptedException ex) {
          // ignore
        }
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
