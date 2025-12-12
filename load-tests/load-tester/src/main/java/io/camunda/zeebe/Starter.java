/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe;

import static io.camunda.zeebe.util.ProcessInstanceUtil.deserializeVariables;
import static io.camunda.zeebe.util.ProcessInstanceUtil.startInstance;
import static io.camunda.zeebe.util.ProcessInstanceUtil.startInstanceByMessagePublishing;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.benchmark.MetricsReader;
import io.camunda.zeebe.config.AppCfg;
import io.camunda.zeebe.config.StarterCfg;
import io.camunda.zeebe.util.ProcessInstanceUtil;
import io.camunda.zeebe.util.ProcessUtil;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

public class Starter extends App {

  public static final int DYNAMIC_RATE_MAX = 300;
  private static final Logger THROTTLED_LOGGER =
      new ThrottledLogger(LoggerFactory.getLogger(Starter.class), Duration.ofSeconds(5));
  private static final Logger LOG = LoggerFactory.getLogger(Starter.class);
  private static final long NANOS_PER_SECOND = Duration.ofSeconds(1).toNanos();
  private static final int DYNAMIC_RATE_MIN = 1;
  private static final int DYNAMIC_RATE_INITIAL = 50;
  private final StarterCfg starterCfg;

  Starter(final AppCfg config) {
    super(config);
    starterCfg = config.getStarter();
  }

  @Override
  public void run() {
    final CamundaClient client = createCamundaClient();

    // init - check for topology and deploy process
    printTopology(client);
    deployProcess(client, starterCfg);

    // setup to start instances on given rate
    final CountDownLatch countDownLatch = new CountDownLatch(1);
    final ScheduledExecutorService executorService =
        Executors.newScheduledThreadPool(starterCfg.getThreads());
    final ScheduledFuture<?> scheduledTask;

    if (starterCfg.getRate() > 0) {
      // Fixed rate mode
      scheduledTask = scheduleProcessInstanceCreation(executorService, countDownLatch, client);
    } else {
      // Dynamic rate mode
      final MetricsReader metricsReader = createMetricsReader();
      scheduledTask =
          scheduleProcessInstanceCreationWithDynamicRate(
              executorService, countDownLatch, client, metricsReader);
    }

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
  }

  private ScheduledFuture<?> scheduleProcessInstanceCreation(
      final ScheduledExecutorService executorService,
      final CountDownLatch countDownLatch,
      final CamundaClient client) {

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

          createProcessInstance(client, baseVariables, businessKey);
        },
        0,
        intervalNanos,
        TimeUnit.NANOSECONDS);
  }

  private ScheduledFuture<?> scheduleProcessInstanceCreationWithDynamicRate(
      final ScheduledExecutorService executorService,
      final CountDownLatch countDownLatch,
      final CamundaClient client,
      final MetricsReader metricsReader) {

    final AtomicLong currentRate = new AtomicLong(DYNAMIC_RATE_INITIAL);
    LOG.info("Starting with dynamic rate of {} instances/second", currentRate.get());

    final String variablesString = readVariables(starterCfg.getPayloadPath());
    final Map<String, Object> baseVariables =
        Collections.unmodifiableMap(deserializeVariables(variablesString));

    final BooleanSupplier shouldContinue = createContinuationCondition(starterCfg);
    final AtomicLong businessKey = new AtomicLong(0);
    final AtomicLong instancesCreatedInCurrentSecond = new AtomicLong(0);
    final AtomicLong lastAdjustmentTimeMillis = new AtomicLong(System.currentTimeMillis());
    final AtomicLong ticksInCurrentSecond = new AtomicLong(0);

    return executorService.scheduleAtFixedRate(
        () -> {
          if (!shouldContinue.getAsBoolean()) {
            // signal completion of starter
            countDownLatch.countDown();
            return;
          }

          // Adjust rate based on exporter load at configured interval
          final long currentTimeMillis = System.currentTimeMillis();
          if (currentTimeMillis - lastAdjustmentTimeMillis.get()
              >= starterCfg.getRateAdjustmentIntervalMs()) {
            final long newRate = adjustRateBasedOnLoad(metricsReader, currentRate.get());
            currentRate.set(newRate);
            instancesCreatedInCurrentSecond.set(0);
            ticksInCurrentSecond.set(0);
            lastAdjustmentTimeMillis.set(currentTimeMillis);
          }

          // Calculate how many instances should have been created by now in this second
          // based on the current tick number (each tick is 10ms, 100 ticks per second)
          final long currentTick = ticksInCurrentSecond.incrementAndGet();
          final long instancesToCreateNow =
              calculateInstancesToCreate(
                  currentRate.get(), currentTick, instancesCreatedInCurrentSecond.get());

          // Create the calculated number of instances
          for (long i = 0; i < instancesToCreateNow; i++) {
            instancesCreatedInCurrentSecond.incrementAndGet();
            createProcessInstance(client, baseVariables, businessKey);
          }
        },
        0,
        10,
        TimeUnit.MILLISECONDS);
  }

  private void createProcessInstance(
      final CamundaClient client,
      final Map<String, Object> baseVariables,
      final AtomicLong businessKey) {
    try {
      final var vars = new HashMap<>(baseVariables);
      vars.put(starterCfg.getBusinessKey(), businessKey.incrementAndGet());

      final CompletionStage<?> requestFuture;
      if (starterCfg.isStartViaMessage()) {
        requestFuture = startInstanceByMessagePublishing(client, vars, starterCfg.getMsgName());
      } else if (starterCfg.isWithResults()) {
        requestFuture =
            ProcessInstanceUtil.startInstanceWithAwaitingResult(
                client, starterCfg.getProcessId(), vars, starterCfg.getWithResultsTimeout());
      } else {
        requestFuture = startInstance(client, starterCfg.getProcessId(), vars);
      }
      requestFuture.exceptionally(
          (error) -> {
            if (error instanceof final StatusRuntimeException statusRuntimeException) {
              if (statusRuntimeException.getStatus().getCode() != Code.RESOURCE_EXHAUSTED) {
                // we don't want to flood the log
                THROTTLED_LOGGER.warn(
                    "Error on creating new process instance with business key {}",
                    businessKey.get(),
                    error);
              }
            }
            return null;
          });
    } catch (final Exception e) {
      THROTTLED_LOGGER.error("Error on creating new process instance", e);
    }
  }

  private CamundaClient createCamundaClient() {
    return newClientBuilder().numJobWorkerExecutionThreads(0).build();
  }

  private boolean isClusterOverloaded(final MetricsReader metricsReader) {
    final var recordsNotExported = metricsReader.getRecordsNotExported();
    final var clusterLoad = metricsReader.getClusterLoad();
    final var backpressureRate = metricsReader.getBackpressureRate();
    THROTTLED_LOGGER.info("Current number of records not exported: {}", recordsNotExported);
    THROTTLED_LOGGER.info("Current cluster load: {}", clusterLoad);
    THROTTLED_LOGGER.info("Current backpressure rate: {}", backpressureRate);
    return recordsNotExported > 10000 || clusterLoad > 0.8 || backpressureRate > 0.1;
  }

  private long adjustRateBasedOnLoad(final MetricsReader metricsReader, final long oldRate) {
    final boolean overloaded = isClusterOverloaded(metricsReader);
    final double adjustmentFactor = starterCfg.getRateAdjustmentFactor();

    final long newRate;
    if (overloaded) {
      // Decrease rate by configured factor
      newRate = Math.max(DYNAMIC_RATE_MIN, (long) (oldRate * (1 - adjustmentFactor)));
      THROTTLED_LOGGER.info(
          "Exporter overloaded, decreasing rate from {} to {} instances/second (factor: {})",
          oldRate,
          newRate,
          adjustmentFactor);
    } else {
      // Increase rate by configured factor
      newRate = Math.min(DYNAMIC_RATE_MAX, (long) (oldRate * (1 + adjustmentFactor)));
      THROTTLED_LOGGER.info(
          "Exporter not overloaded, increasing rate from {} to {} instances/second (factor: {})",
          oldRate,
          newRate,
          adjustmentFactor);
    }
    return newRate;
  }

  private long calculateInstancesToCreate(
      final long currentRate, final long currentTick, final long instancesCreatedSoFar) {
    final long instancesShouldHaveCreated = (currentRate * currentTick) / 100;
    return instancesShouldHaveCreated - instancesCreatedSoFar;
  }

  private void deployProcess(final CamundaClient client, final StarterCfg starterCfg) {
    final List<String> bpmnXmlPaths = new ArrayList<>();

    bpmnXmlPaths.add(starterCfg.getBpmnXmlPath());
    if (starterCfg.getExtraBpmnModels() != null) {
      bpmnXmlPaths.addAll(starterCfg.getExtraBpmnModels());
    }

    ProcessUtil.deployProcess(client, bpmnXmlPaths);
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
