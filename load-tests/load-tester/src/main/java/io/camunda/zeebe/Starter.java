/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe;

import static io.camunda.zeebe.util.ProcessInstanceUtil.deserializeVariables;
import static io.camunda.zeebe.util.ProcessInstanceUtil.startInstanceByMessagePublishing;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.ProcessInstanceSort;
import io.camunda.zeebe.config.AppCfg;
import io.camunda.zeebe.config.StarterCfg;
import io.camunda.zeebe.util.ProcessInstanceUtil;
import io.camunda.zeebe.util.ProcessUtil;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.Timer;
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

  private static final Logger THROTTLED_LOGGER =
      new ThrottledLogger(LoggerFactory.getLogger(Starter.class), Duration.ofSeconds(5));
  private static final Logger LOG = LoggerFactory.getLogger(Starter.class);
  private static final long NANOS_PER_SECOND = Duration.ofSeconds(1).toNanos();
  private final StarterCfg starterCfg;
  private Timer responseLatencyTimer;
  private ScheduledExecutorService executorService;
  private ProcessInstanceStartMeter processInstanceStartMeter;

  Starter(final AppCfg config) {
    super(config);
    starterCfg = config.getStarter();
  }

  @Override
  public void run() {
    responseLatencyTimer =
        MicrometerUtil.buildTimer(StarterLatencyMetricsDoc.RESPONSE_LATENCY).register(registry);

    final CamundaClient client = createCamundaClient();
    if (config.isMonitorDataAvailability()) {
      setupDataAvailabilityMeter(client);
    }

    // init - check for topology and deploy process
    printTopology(client);
    deployProcess(client, starterCfg);

    // setup to start instances on given rate
    final CountDownLatch countDownLatch = new CountDownLatch(1);
    final ScheduledExecutorService executorService =
        Executors.newScheduledThreadPool(starterCfg.getThreads());
    final ScheduledFuture<?> scheduledTask;

    scheduledTask = scheduleProcessInstanceCreation(executorService, countDownLatch, client);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  if (!executorService.isShutdown()) {
                    executorService.shutdown();
                    if (config.isMonitorDataAvailability()) {
                      processInstanceStartMeter.close();
                    }
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

    if (config.isMonitorDataAvailability()) {
      processInstanceStartMeter.close();
    }
  }

  private void setupDataAvailabilityMeter(final CamundaClient client) {
    LOG.info("Monitor data availability of started process instances");
    processInstanceStartMeter =
        new ProcessInstanceStartMeter(
            registry,
            Executors.newScheduledThreadPool(1),
            config.getMonitorDataAvailabilityInterval(),
            (listOfStartedInstances) -> {
              final CamundaFuture<SearchResponse<ProcessInstance>> send =
                  client
                      .newProcessInstanceSearchRequest()
                      .filter((f) -> f.processInstanceKey(key -> key.in(listOfStartedInstances)))
                      .sort(ProcessInstanceSort::startDate)
                      .send();

              return send.thenApply(
                  processInstanceSearchResponse ->
                      processInstanceSearchResponse.items().stream()
                          .map(ProcessInstance::getProcessInstanceKey)
                          .toList());
            });
    processInstanceStartMeter.start();
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
        requestFuture =
            ProcessInstanceUtil.startInstance(
                client,
                starterCfg.getProcessId(),
                vars,
                config.isMonitorDataAvailability() ? processInstanceStartMeter : null);
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
