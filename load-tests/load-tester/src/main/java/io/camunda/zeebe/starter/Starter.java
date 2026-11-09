/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.starter;

import io.camunda.zeebe.config.LoadTesterProperties;
import io.camunda.zeebe.config.StarterProperties;
import io.camunda.zeebe.metrics.ProcessInstanceStartMeter;
import io.camunda.zeebe.metrics.StarterMetricsDoc;
import io.camunda.zeebe.metrics.StarterMetricsDoc.StarterMetricKeyNames;
import io.camunda.zeebe.optimize.OptimizeReportEvaluator;
import io.camunda.zeebe.read.DataReadMeter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("starter")
public class Starter implements CommandLineRunner {

  private static final Logger LOG = LoggerFactory.getLogger(Starter.class);

  private final ApplicationContext applicationContext;
  private final StarterProperties starterCfg;
  private final MeterRegistry registry;
  private final StarterLivenessIndicator livenessIndicator;
  private final ProcessDeployer processDeployer;
  private final ProcessInstanceCreator processInstanceCreator;
  private final AtomicInteger runFinished = new AtomicInteger(0);

  // Temporary: Starter starts and stops these meters until they react to application events.
  private final Optional<ProcessInstanceStartMeter> processInstanceStartMeter;
  private final Optional<DataReadMeter> dataReadMeter;
  private final Optional<OptimizeReportEvaluator> optimizeReportEvaluator;

  private ScheduledExecutorService executorService;

  public Starter(
      final LoadTesterProperties properties,
      final MeterRegistry registry,
      final StarterLivenessIndicator livenessIndicator,
      final ProcessDeployer processDeployer,
      final ProcessInstanceCreator processInstanceCreator,
      final Optional<ProcessInstanceStartMeter> processInstanceStartMeter,
      final Optional<DataReadMeter> dataReadMeter,
      final Optional<OptimizeReportEvaluator> optimizeReportEvaluator,
      final ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
    starterCfg = properties.getStarter();
    this.registry = registry;
    this.livenessIndicator = livenessIndicator;
    this.processDeployer = processDeployer;
    this.processInstanceCreator = processInstanceCreator;
    this.processInstanceStartMeter = processInstanceStartMeter;
    this.dataReadMeter = dataReadMeter;
    this.optimizeReportEvaluator = optimizeReportEvaluator;

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

    processInstanceStartMeter.ifPresent(ProcessInstanceStartMeter::start);

    final long processDefinitionKey = processDeployer.deploy();

    dataReadMeter.ifPresent(
        meter -> {
          // Temporary: the meter receives the key from Starter until the deployer blocks startup.
          meter.setContextProcessDefinitionKey(processDefinitionKey);
          meter.start();
        });
    optimizeReportEvaluator.ifPresent(OptimizeReportEvaluator::start);

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
    processInstanceStartMeter.ifPresent(ProcessInstanceStartMeter::close);
    dataReadMeter.ifPresent(DataReadMeter::close);
    optimizeReportEvaluator.ifPresent(OptimizeReportEvaluator::close);
  }
}
