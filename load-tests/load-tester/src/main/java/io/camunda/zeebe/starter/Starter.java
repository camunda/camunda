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
package io.camunda.zeebe.starter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.DeployResourceCommandStep1.DeployResourceCommandStep2;
import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.api.search.response.ProcessInstance;
import io.camunda.zeebe.client.api.search.response.SearchResponsePage;
import io.camunda.zeebe.client.api.search.sort.ProcessInstanceSort;
import io.camunda.zeebe.config.LoadTesterProperties;
import io.camunda.zeebe.config.StarterProperties;
import io.camunda.zeebe.metrics.ConnectionMonitor;
import io.camunda.zeebe.metrics.ProcessInstanceStartMeter;
import io.camunda.zeebe.metrics.StarterLatencyMetricsDoc;
import io.camunda.zeebe.util.PayloadReader;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("starter")
public class Starter implements CommandLineRunner {

  private static final Logger THROTTLED_LOGGER =
      new ThrottledLogger(LoggerFactory.getLogger(Starter.class), Duration.ofSeconds(5));
  private static final Logger LOG = LoggerFactory.getLogger(Starter.class);
  private static final long NANOS_PER_SECOND = Duration.ofSeconds(1).toNanos();
  private static final TypeReference<HashMap<String, Object>> VARIABLES_TYPE_REF =
      new TypeReference<>() {};
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final ZeebeClient client;
  private final LoadTesterProperties properties;
  private final StarterProperties starterCfg;
  private final MeterRegistry registry;
  private final PayloadReader payloadReader;
  private final ConnectionMonitor connectionMonitor;
  private final AtomicLong businessKey = new AtomicLong(0);

  private Timer responseLatencyTimer;
  private ScheduledExecutorService executorService;
  private ProcessInstanceStartMeter processInstanceStartMeter;
  private SearchResponsePage responsePage = null;

  public Starter(
      final ZeebeClient client,
      final LoadTesterProperties properties,
      final MeterRegistry registry,
      final PayloadReader payloadReader,
      final ConnectionMonitor connectionMonitor) {
    this.client = client;
    this.properties = properties;
    starterCfg = properties.getStarter();
    this.registry = registry;
    this.payloadReader = payloadReader;
    this.connectionMonitor = connectionMonitor;
  }

  @Override
  public void run(final String... args) {
    connectionMonitor.awaitAndPrintTopology();

    responseLatencyTimer =
        MicrometerUtil.buildTimer(StarterLatencyMetricsDoc.RESPONSE_LATENCY).register(registry);

    if (properties.isMonitorDataAvailability()) {
      setupDataAvailabilityMeter();
    }

    deployProcess();

    final CountDownLatch countDownLatch = new CountDownLatch(1);
    executorService = Executors.newScheduledThreadPool(starterCfg.getThreads());
    final ScheduledFuture<?> scheduledTask =
        scheduleProcessInstanceCreation(executorService, countDownLatch);

    try {
      countDownLatch.await();
    } catch (final InterruptedException e) {
      LOG.error("Awaiting of count down latch was interrupted.", e);
    }

    LOG.info("Starter finished");
    scheduledTask.cancel(true);
    shutdown();
  }

  @PreDestroy
  public void shutdown() {
    if (executorService != null && !executorService.isShutdown()) {
      executorService.shutdown();
      try {
        executorService.awaitTermination(60, TimeUnit.SECONDS);
      } catch (final InterruptedException e) {
        LOG.error("Shutdown executor service was interrupted", e);
      }
    }
    if (processInstanceStartMeter != null) {
      processInstanceStartMeter.close();
    }
  }

  private void setupDataAvailabilityMeter() {
    LOG.info("Monitor data availability of started process instances");
    processInstanceStartMeter =
        new ProcessInstanceStartMeter(
            System::nanoTime,
            registry,
            Executors.newScheduledThreadPool(1),
            properties.getMonitorDataAvailabilityInterval(),
            (listOfStartedInstances) -> {
              final var sendFuture =
                  client
                      .newProcessInstanceQuery()
                      .filter(f -> f.active(true).running(true))
                      .sort(ProcessInstanceSort::startDate)
                      .page(
                          p ->
                              p.limit(1000)
                                  .searchAfter(
                                      responsePage == null
                                          ? List.of()
                                          : responsePage.lastSortValues()))
                      .send();

              return sendFuture.thenApply(
                  processInstanceSearchResponse -> {
                    responsePage = processInstanceSearchResponse.page();
                    return processInstanceSearchResponse.items().stream()
                        .map(ProcessInstance::getKey)
                        .toList();
                  });
            });
    processInstanceStartMeter.start();
  }

  private ScheduledFuture<?> scheduleProcessInstanceCreation(
      final ScheduledExecutorService executorService, final CountDownLatch countDownLatch) {

    final long intervalNanos = (long) (NANOS_PER_SECOND / starterCfg.getRatePerSecond());
    LOG.info(
        "Creating an instance every {}ns (rate: {} per {})",
        intervalNanos,
        starterCfg.getRate(),
        starterCfg.getRateDuration());

    final String variablesString = payloadReader.readPayload(starterCfg.getPayloadPath());
    final Map<String, Object> baseVariables =
        Collections.unmodifiableMap(deserializeVariables(variablesString));

    final BooleanSupplier shouldContinue = createContinuationCondition();

    return executorService.scheduleAtFixedRate(
        () -> {
          if (!shouldContinue.getAsBoolean()) {
            countDownLatch.countDown();
            return;
          }

          try {
            final var vars = new HashMap<>(baseVariables);
            vars.put(starterCfg.getBusinessKey(), businessKey.incrementAndGet());

            final var startTime = System.nanoTime();
            final CompletionStage<?> requestFuture;
            if (starterCfg.isStartViaMessage()) {
              requestFuture = startInstanceByMessagePublishing(vars);
            } else if (starterCfg.isWithResults()) {
              requestFuture = startInstanceWithAwaitingResult(starterCfg.getProcessId(), vars);
            } else {
              requestFuture = startInstance(startTime, starterCfg.getProcessId(), vars);
            }
            requestFuture.whenComplete(
                (noop, error) -> {
                  final long durationNanos = System.nanoTime() - startTime;
                  responseLatencyTimer.record(durationNanos, TimeUnit.NANOSECONDS);
                  if (error instanceof final StatusRuntimeException statusRuntimeException) {
                    if (statusRuntimeException.getStatus().getCode() != Code.RESOURCE_EXHAUSTED) {
                      THROTTLED_LOGGER.warn(
                          "Error on creating new process instance with business key {}",
                          businessKey.get(),
                          error);
                    }
                  }
                });
          } catch (final Exception e) {
            THROTTLED_LOGGER.error("Error on creating new process instance", e);
          }
        },
        0,
        intervalNanos,
        TimeUnit.NANOSECONDS);
  }

  private CompletionStage<ProcessInstanceEvent> startInstance(
      final long startTime, final String processId, final HashMap<String, Object> variables) {
    return client
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .variables(variables)
        .send()
        .thenApply(
            (response) -> {
              if (properties.isMonitorDataAvailability()) {
                final long processInstanceKey = response.getProcessInstanceKey();
                processInstanceStartMeter.recordProcessInstanceStart(processInstanceKey, startTime);
              }
              return response;
            });
  }

  private CompletionStage<?> startInstanceWithAwaitingResult(
      final String processId, final HashMap<String, Object> variables) {
    return client
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .variables(variables)
        .withResult()
        .requestTimeout(starterCfg.getWithResultsTimeout())
        .send();
  }

  private CompletionStage<?> startInstanceByMessagePublishing(final Map<String, Object> variables) {
    return client
        .newPublishMessageCommand()
        .messageName(starterCfg.getMsgName())
        .correlationKey(UUID.randomUUID().toString())
        .variables(variables)
        .timeToLive(Duration.ZERO)
        .send();
  }

  private static HashMap<String, Object> deserializeVariables(final String variablesString) {
    try {
      return OBJECT_MAPPER.readValue(variablesString, VARIABLES_TYPE_REF);
    } catch (final JsonProcessingException e) {
      LOG.error("Failed to parse variables '{}'.", variablesString, e);
      throw new RuntimeException(e);
    }
  }

  private void deployProcess() {
    final var deployCmd = constructDeploymentCommand();

    while (true) {
      try {
        final var result = deployCmd.send().join();
        result.getProcesses().stream()
            .filter(p -> p.getBpmnProcessId().equals(starterCfg.getProcessId()))
            .findFirst()
            .map(Process::getProcessDefinitionKey)
            .orElse(0L);
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

  private DeployResourceCommandStep2 constructDeploymentCommand() {
    LOG.info(
        "Deploying main resource: {}, extra resources: {}",
        starterCfg.getBpmnXmlPath(),
        starterCfg.getExtraBpmnModels());
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

  private BooleanSupplier createContinuationCondition() {
    final int durationLimit = starterCfg.getDurationLimit();

    if (durationLimit > 0) {
      final LocalDateTime endTime = LocalDateTime.now().plus(durationLimit, ChronoUnit.SECONDS);
      return () -> LocalDateTime.now().isBefore(endTime);
    } else {
      return () -> true;
    }
  }
}
