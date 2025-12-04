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
import io.camunda.zeebe.ProcessInstanceStartMeter.PiCreationResult;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import io.camunda.zeebe.client.api.command.DeployResourceCommandStep1.DeployResourceCommandStep2;
import io.camunda.zeebe.config.AppCfg;
import io.camunda.zeebe.config.StarterCfg;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.Timer;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
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
  private final StarterCfg starterCfg;
  private Timer responseLatencyTimer;
  private ScheduledExecutorService executorService;
  private ScheduledExecutorService piCheckExecutorService;
  private ConcurrentHashMap<Integer, Timer> partitionToTimerMap;
  private CopyOnWriteArrayList<PiCreationResult> results;

  Starter(final AppCfg appCfg) {
    this.appCfg = appCfg;
    starterCfg = appCfg.getStarter();
  }

  @Override
  public void run() {
    partitionToTimerMap = new ConcurrentHashMap<>();
    responseLatencyTimer =
        MicrometerUtil.buildTimer(StarterLatencyMetricsDoc.RESPONSE_LATENCY).register(registry);
    results = new CopyOnWriteArrayList<>();
    partitionToTimerMap.put(
        1,
        MicrometerUtil.buildTimer(StarterLatencyMetricsDoc.DATA_AVAILABILITY_LATENCY)
            .tag("partition", "1")
            .register(registry));
    partitionToTimerMap.put(
        2,
        MicrometerUtil.buildTimer(StarterLatencyMetricsDoc.DATA_AVAILABILITY_LATENCY)
            .tag("partition", "2")
            .register(registry));
    partitionToTimerMap.put(
        3,
        MicrometerUtil.buildTimer(StarterLatencyMetricsDoc.DATA_AVAILABILITY_LATENCY)
            .tag("partition", "3")
            .register(registry));
    final ZeebeClient client = createZeebeClient();

    // init - check for topology and deploy process
    printTopology(client);
    deployProcess(client, starterCfg);

    // setup to start instances on given rate
    piCheckExecutorService = Executors.newScheduledThreadPool(20);
    final CountDownLatch countDownLatch = new CountDownLatch(1);

    piCheckExecutorService.scheduleAtFixedRate(
        () -> {
          try {
            queryProcessInstances(client);
          } catch (final Exception e) {
            LOG.error("Failed to query process instances. Will retry...", e);
          }
        },
        1000,
        250,
        TimeUnit.MILLISECONDS);

    executorService = Executors.newScheduledThreadPool(starterCfg.getThreads());
    final ScheduledFuture<?> scheduledTask =
        scheduleProcessInstanceCreation(executorService, countDownLatch, client);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  if (!executorService.isShutdown()) {
                    executorService.shutdown();
                    piCheckExecutorService.shutdown();
                    try {
                      executorService.awaitTermination(60, TimeUnit.SECONDS);
                    } catch (final InterruptedException e) {
                      LOG.error("Shutdown executor service was interrupted", e);
                    }
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
    piCheckExecutorService.shutdown();
  }

  private void queryProcessInstances(final ZeebeClient client) {
    LOG.warn("Started {} process instances so far", results.size());
    // request results for started instances

    final PiCreationResult first = results.getFirst();
    LOG.info("First result {} ", results.getFirst());
    final long startTimeNanos = first.startTimeNanos();
    LOG.info("StartTimeNanos {} ", startTimeNanos);
    final long millis = TimeUnit.NANOSECONDS.toMillis(startTimeNanos);
    LOG.info("millis {} ", millis);
    final long startTimeEpochMillis = first.startTimeEpochMillis;
    LOG.info("EpochMillis {} ", startTimeEpochMillis);
    final Instant temporal = Instant.ofEpochMilli(startTimeEpochMillis);
    LOG.info("Instant {} ", temporal);
    final OffsetDateTime from = OffsetDateTime.from(temporal.atZone(ZoneId.of("UTC")));
    LOG.info("OffsetDateTime {} ", from);
    final List<Long> list = results.stream().map(k -> k.processInstanceKey).toList();
    //
    //    client
    //        .newProcessInstanceSearchRequest()
    //        .filter((f) -> f.processInstanceKey(key -> key.in(list)))
    //        .sort(ProcessInstanceSort::startDate)
    //        .send()
    //        .whenCompleteAsync(
    //            (resp, error) -> {
    //              if (error != null) {
    //                LOG.error("Error while requesting process instances", error);
    //                return;
    //              }
    //
    //              LOG.warn("Response items: {} ", resp.items().size());
    //              resp.items()
    //                  .forEach(
    //                      pi -> {
    //                        LOG.info(
    //                            "PI {} - {} start {}",
    //                            pi,
    //                            pi.getProcessInstanceKey(),
    //                            pi.getStartDate());
    //                        for (final PiCreationResult waitingPI :
    //                            Collections.unmodifiableList(results)) {
    //
    //                          // TODO do more efficient search
    //                          final long processInstanceKey = waitingPI.processInstanceKey;
    //                          if (processInstanceKey == pi.getProcessInstanceKey()) {
    //                            final long durationNanos = System.nanoTime() -
    // waitingPI.startTimeNanos;
    //                            final long durationMillisNanos =
    //                                System.currentTimeMillis() - waitingPI.startTimeNanos;
    //                            LOG.warn(
    //                                "Process instance {} retrieved in {} ms; {} ms (from epoch)",
    //                                processInstanceKey,
    //                                TimeUnit.NANOSECONDS.toMillis(durationNanos),
    //                                durationMillisNanos);
    //
    //                            final int partitionId =
    // Protocol.decodePartitionId(processInstanceKey);
    //                            partitionToTimerMap
    //                                .get(partitionId)
    //                                .record(durationNanos, TimeUnit.NANOSECONDS);
    //                            results.remove(waitingPI);
    //                            break;
    //                          }
    //                          //                          else {
    //                          //                            LOG.info(
    //                          //                                "PI {} not match {}",
    //                          //                                pi.getProcessInstanceKey(),
    //                          //                                waitingPI.processInstanceKey);
    //                          //                          }
    //                        }
    //                      });
    //            },
    //            piCheckExecutorService);
  }

  private ScheduledFuture<?> scheduleProcessInstanceCreation(
      final ScheduledExecutorService executorService,
      final CountDownLatch countDownLatch,
      final ZeebeClient client) {

    final long intervalNanos = Math.floorDiv(NANOS_PER_SECOND, starterCfg.getRate());
    LOG.info("Creating an instance every {}ns", intervalNanos);

    final String variablesString = readVariables(starterCfg.getPayloadPath());
    final Map<String, Object> baseVariables =
        Collections.unmodifiableMap(deserializeVariables(variablesString));

    final BooleanSupplier shouldContinue = createContinuationCondition(starterCfg);
    final AtomicLong businessKey = new AtomicLong(0);

    return executorService.scheduleAtFixedRate(
        () -> {
          if (!shouldContinue.getAsBoolean()) {
            // signal completion of starter
            countDownLatch.countDown();
            return;
          }

          try {
            final var vars = new HashMap<>(baseVariables);
            vars.put(starterCfg.getBusinessKey(), businessKey.incrementAndGet());

            final var startTime = System.nanoTime();
            final CompletionStage<?> requestFuture;
            if (starterCfg.isStartViaMessage()) {
              requestFuture = startInstanceByMessagePublishing(client, vars);
            } else if (starterCfg.isWithResults()) {
              requestFuture =
                  startInstanceWithAwaitingResult(client, starterCfg.getProcessId(), vars);
            } else {
              requestFuture = startInstance(client, startTime, starterCfg.getProcessId(), vars);
            }
            requestFuture.whenComplete(
                (noop, error) -> {
                  final long durationNanos = System.nanoTime() - startTime;
                  responseLatencyTimer.record(durationNanos, TimeUnit.NANOSECONDS);
                  if (error instanceof final StatusRuntimeException statusRuntimeException) {
                    if (statusRuntimeException.getStatus().getCode() != Code.RESOURCE_EXHAUSTED) {
                      // we don't want to flood the log
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

  private CompletionStage<?> startInstance(
      final ZeebeClient client,
      final long startTime,
      final String processId,
      final HashMap<String, Object> variables) {
    return client
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .variables(variables)
        .send()
        .thenApply(
            (response) -> {
              final long processInstanceKey = response.getProcessInstanceKey();
              final long duration = Duration.ofNanos(System.nanoTime() - startTime).toMillis();
              final long epochMilli = System.currentTimeMillis() - duration;
              results.add(new PiCreationResult(processInstanceKey, startTime, epochMilli));
              //              LOG.warn(
              //              LOG.warn(
              //                  "Process instance {} started at around {} (delay {} ms) try to get
              // from API",
              //                  processInstanceKey,
              //                  Instant.ofEpochMilli(epochMilli),
              //                  duration);
              //              checkForProcessInstanceExistence(client, startTime,
              // processInstanceKey);
              return response;
            });
  }

  private void checkForProcessInstanceExistence(
      final ZeebeClient client, final long startTime, final long processInstanceKey) {

    final long duration = Duration.ofNanos(System.nanoTime() - startTime).toMillis();
    LOG.warn(
        "Process instance {} started at around {} (delay {} ms) try to get from API",
        processInstanceKey,
        Instant.ofEpochMilli(System.currentTimeMillis() - duration),
        duration);
    client
        .newProcessInstanceGetRequest(processInstanceKey)
        .send()
        .whenCompleteAsync(
            (resp, err) -> {
              if (err != null) {
                // on error, we need to retry
                piCheckExecutorService.schedule(
                    () -> checkForProcessInstanceExistence(client, startTime, processInstanceKey),
                    100,
                    TimeUnit.MILLISECONDS);
                LOG.trace("Failed to get process instance {}", processInstanceKey, err);
              } else {
                final long durationNanos = System.nanoTime() - startTime;
                LOG.warn(
                    "Process instance {} retrieved in {} ms",
                    processInstanceKey,
                    durationNanos / 1_000_000);

                final int partitionId = Protocol.decodePartitionId(processInstanceKey);
                partitionToTimerMap.get(partitionId).record(durationNanos, TimeUnit.NANOSECONDS);
              }
            },
            piCheckExecutorService);
  }

  private CompletionStage<?> startInstanceWithAwaitingResult(
      final ZeebeClient client, final String processId, final HashMap<String, Object> variables) {
    return client
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .variables(variables)
        .withResult()
        .requestTimeout(starterCfg.getWithResultsTimeout())
        .send();
  }

  private CompletionStage<?> startInstanceByMessagePublishing(
      final ZeebeClient client, final Map<String, Object> variables) {
    return client
        .newPublishMessageCommand()
        .messageName(starterCfg.getMsgName())
        .correlationKey(UUID.randomUUID().toString())
        .variables(variables)
        .timeToLive(Duration.ZERO)
        .send();
  }

  private static HashMap<String, Object> deserializeVariables(final String variablesString) {
    final HashMap<String, Object> variables;
    try {
      variables = OBJECT_MAPPER.readValue(variablesString, VARIABLES_TYPE_REF);
    } catch (final JsonProcessingException e) {
      LOG.error("Failed to parse variables '{}'.", variablesString, e);
      throw new RuntimeException(e);
    }
    return variables;
  }

  private ZeebeClient createZeebeClient() {
    final ZeebeClientBuilder builder =
        ZeebeClient.newClientBuilder()
            .grpcAddress(URI.create(appCfg.getBrokerUrl()))
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
