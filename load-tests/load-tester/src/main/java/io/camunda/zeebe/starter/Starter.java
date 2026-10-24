/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.starter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.zeebe.config.LoadTesterProperties;
import io.camunda.zeebe.config.StarterProperties;
import io.camunda.zeebe.metrics.ProcessInstanceStartMeter;
import io.camunda.zeebe.metrics.StarterMetricsDoc;
import io.camunda.zeebe.metrics.StarterMetricsDoc.StarterMetricKeyNames;
import io.camunda.zeebe.optimize.OptimizeReportEvaluator;
import io.camunda.zeebe.optimize.OptimizeReportEvaluatorFactory;
import io.camunda.zeebe.read.DataReadMeter;
import io.camunda.zeebe.read.DataReadMeterQueryProvider;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@Profile("starter")
public class Starter implements CommandLineRunner {

  private static final Logger LOG = LoggerFactory.getLogger(Starter.class);

  private final CamundaClient queryClient;
  private final ApplicationContext applicationContext;
  private final LoadTesterProperties properties;
  private final StarterProperties starterCfg;
  private final MeterRegistry registry;
  private final StarterLivenessIndicator livenessIndicator;
  private final WebClient.Builder webClientBuilder;
  private final ObjectMapper objectMapper;
  private final ProcessDeployer processDeployer;
  private final ProcessInstanceCreator processInstanceCreator;
  private final AtomicLong lastProcessInstanceKey = new AtomicLong(0);
  private final AtomicInteger runFinished = new AtomicInteger(0);
  private final AtomicReference<Instant> lastProcessInstanceKeyTimestamp =
      new AtomicReference<>(Instant.now());

  private ScheduledExecutorService executorService;
  private ProcessInstanceStartMeter processInstanceStartMeter;
  private DataReadMeter dataReadMeter;
  private OptimizeReportEvaluator optimizeReportEvaluator;

  public Starter(
      @Qualifier("queryCamundaClient") final CamundaClient queryClient,
      final LoadTesterProperties properties,
      final MeterRegistry registry,
      final StarterLivenessIndicator livenessIndicator,
      final WebClient.Builder webClientBuilder,
      final ObjectMapper objectMapper,
      final ProcessDeployer processDeployer,
      final ProcessInstanceCreator processInstanceCreator,
      final ApplicationContext applicationContext) {
    this.queryClient = queryClient;
    this.applicationContext = applicationContext;
    this.properties = properties;
    starterCfg = properties.getStarter();
    this.registry = registry;
    this.livenessIndicator = livenessIndicator;
    this.webClientBuilder = webClientBuilder;
    this.objectMapper = objectMapper;
    this.processDeployer = processDeployer;
    this.processInstanceCreator = processInstanceCreator;

    // Expose the client information early: these are only static values which are not supposed to
    // be affected by the current state of the client (whether it successfully connects to the
    // brokers, etc.)
    // Having these infos early could help to investigate the client faster.
    Gauge.builder(StarterMetricsDoc.CLIENT_INFO.getName(), () -> 1)
        .description(StarterMetricsDoc.CLIENT_INFO.getDescription())
        .tag(StarterMetricKeyNames.NAME.asString(), "starter")
        .tag(StarterMetricKeyNames.PROCESS_ID.asString(), starterCfg.getProcessId())
        .tag(StarterMetricKeyNames.NB_THREADS.asString(), String.valueOf(starterCfg.getThreads()))
        .register(registry);
  }

  @Override
  public void run(final String... args) {
    Gauge.builder(StarterMetricsDoc.RUN_FINISHED.getName(), runFinished, AtomicInteger::doubleValue)
        .description(StarterMetricsDoc.RUN_FINISHED.getDescription())
        .register(registry);

    if (properties.isMonitorDataAvailability()) {
      setupDataAvailabilityMeter();
    }

    if (properties.isPerformReadBenchmarks()) {
      setupDataReadMeter();
    }

    if (properties.getOptimize().isReportEvaluationEnabled()) {
      setupOptimizeReportEvaluator();
    }

    final long processDefinitionKey = processDeployer.deploy();

    if (properties.isPerformReadBenchmarks()) {
      dataReadMeter.setContextProcessDefinitionKey(processDefinitionKey);
      dataReadMeter.start();
    }

    if (optimizeReportEvaluator != null) {
      optimizeReportEvaluator.start();
    }

    final CountDownLatch countDownLatch = new CountDownLatch(1);
    executorService = Executors.newScheduledThreadPool(starterCfg.getThreads());
    final ScheduledFuture<?> scheduledTask =
        processInstanceCreator.schedule(executorService, countDownLatch);

    try {
      countDownLatch.await();
    } catch (final InterruptedException e) {
      LOG.error("Awaiting of count down latch was interrupted.", e);
    }

    if (processInstanceCreator.fatalError() != null) {
      scheduledTask.cancel(true);
      System.exit(SpringApplication.exit(applicationContext, () -> 1));
    }

    runFinished.set(1);
    livenessIndicator.recordFinished();
    LOG.info(
        "Starter finished. Total process instance start requests submitted: {}",
        processInstanceCreator.submittedCount());
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
    if (dataReadMeter != null) {
      dataReadMeter.close();
    }
    if (optimizeReportEvaluator != null) {
      optimizeReportEvaluator.close();
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
              final CamundaFuture<SearchResponse<ProcessInstance>> send =
                  queryClient
                      .newProcessInstanceSearchRequest()
                      .filter((f) -> f.processInstanceKey(key -> key.in(listOfStartedInstances)))
                      .sort(s -> s.startDate().asc())
                      .page(p -> p.limit(2500))
                      .send();

              return send.thenApply(
                  processInstanceSearchResponse ->
                      processInstanceSearchResponse.items().stream()
                          .map(ProcessInstance::getProcessInstanceKey)
                          .toList());
            });
    processInstanceCreator.addStartListener(
        (processInstanceKey, startTime) ->
            processInstanceStartMeter.recordProcessInstanceStart(processInstanceKey, startTime));
    processInstanceStartMeter.start();
  }

  private void setupOptimizeReportEvaluator() {
    LOG.info("Starting Optimize report evaluation meter");
    optimizeReportEvaluator =
        OptimizeReportEvaluatorFactory.create(
            properties.getOptimize(), webClientBuilder, objectMapper, registry);
  }

  private void setupDataReadMeter() {
    LOG.info("Starting read benchmark queries");
    dataReadMeter =
        new DataReadMeter(
            registry,
            Executors.newScheduledThreadPool(2),
            queryClient,
            DataReadMeterQueryProvider.getDefaultQueries(properties.getDisabledQueriesList()));
    dataReadMeter.setContextProcessDefinitionId(starterCfg.getProcessId());
    dataReadMeter.setContextBusinessKeySupplier(
        () ->
            Pair.of(
                starterCfg.getBusinessKey(),
                processInstanceCreator.currentBusinessKey()
                    - (long) (starterCfg.getRatePerSecond() * 60.0)));
    processInstanceCreator.addStartListener(
        (processInstanceKey, startTime) -> recordReadContextProcessInstance(processInstanceKey));
  }

  private void recordReadContextProcessInstance(final long processInstanceKey) {
    if (lastProcessInstanceKeyTimestamp.get().plus(1, ChronoUnit.MINUTES).isBefore(Instant.now())) {
      lastProcessInstanceKeyTimestamp.set(Instant.now());
      final var oldValue = lastProcessInstanceKey.getAndSet(processInstanceKey);
      dataReadMeter.setContextProcessInstanceKey(oldValue == 0 ? processInstanceKey : oldValue);
    }
  }
}
