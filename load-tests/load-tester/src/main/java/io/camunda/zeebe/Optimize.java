/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe;

import io.camunda.zeebe.config.AppCfg;
import io.camunda.zeebe.config.OptimizeCfg;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Optimize extends App {

  private static final Logger LOG = LoggerFactory.getLogger(Optimize.class);
  private static final Logger THROTTLED_LOGGER =
      new ThrottledLogger(LoggerFactory.getLogger(Optimize.class), Duration.ofSeconds(5));

  private final OptimizeCfg optimizeCfg;
  private OptimizeReportLoadTester loadTester;
  private ScheduledExecutorService executorService;

  // Metrics
  private Timer dashboardResponseTimer;
  private Timer reportResponseTimer;
  private Timer maxReportResponseTimer;
  private Timer homepageLoadTimer;
  private Counter dashboardSuccessCounter;
  private Counter dashboardErrorCounter;
  private Counter reportSuccessCounter;
  private Counter reportErrorCounter;

  Optimize(final AppCfg config) {
    super(config);
    optimizeCfg = config.getOptimize();
  }

  @Override
  public void run() {
    LOG.info("Starting Optimize load tester");

    // Initialize metrics
    initializeMetrics();

    // Create load tester instance
    loadTester =
        new OptimizeReportLoadTester(
            optimizeCfg.getBaseUrl(),
            optimizeCfg.getKeycloakUrl(),
            optimizeCfg.getRealm(),
            optimizeCfg.getClientId(),
            optimizeCfg.getUsername(),
            optimizeCfg.getPassword(),
            optimizeCfg.getClientSecret());

    // Authenticate once at startup
    try {
      LOG.info("Authenticating with Keycloak...");
      loadTester.authenticateWithAuthorizationCodeFlow();
      LOG.info("Successfully authenticated");
    } catch (final Exception e) {
      LOG.error("Failed to authenticate with Keycloak", e);
      throw new RuntimeException("Authentication failed", e);
    }

    // Setup scheduled execution
    final CountDownLatch countDownLatch = new CountDownLatch(1);
    executorService = Executors.newScheduledThreadPool(1);
    final ScheduledFuture<?> scheduledTask = scheduleEvaluations(executorService, countDownLatch);

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

    // Wait for completion
    try {
      countDownLatch.await();
    } catch (final InterruptedException e) {
      LOG.error("Awaiting of count down latch was interrupted.", e);
    }

    LOG.info("Optimize load tester finished");

    scheduledTask.cancel(true);
    executorService.shutdown();
  }

  private void initializeMetrics() {
    dashboardResponseTimer =
        Timer.builder("optimize.dashboard.response.time")
            .description("Response time for dashboard evaluation")
            .register(registry);

    reportResponseTimer =
        Timer.builder("optimize.report.response.time")
            .description("Response time for report evaluation")
            .register(registry);

    maxReportResponseTimer =
        Timer.builder("optimize.report.max.response.time")
            .description("Maximum (slowest) report response time per evaluation cycle")
            .register(registry);

    homepageLoadTimer =
        Timer.builder("optimize.homepage.load.time")
            .description("Total homepage load time (dashboard + max report time)")
            .register(registry);

    dashboardSuccessCounter =
        Counter.builder("optimize.dashboard.success")
            .description("Successful dashboard evaluations")
            .register(registry);

    dashboardErrorCounter =
        Counter.builder("optimize.dashboard.error")
            .description("Failed dashboard evaluations")
            .register(registry);

    reportSuccessCounter =
        Counter.builder("optimize.report.success")
            .description("Successful report evaluations")
            .register(registry);

    reportErrorCounter =
        Counter.builder("optimize.report.error")
            .description("Failed report evaluations")
            .register(registry);
  }

  private ScheduledFuture<?> scheduleEvaluations(
      final ScheduledExecutorService executorService, final CountDownLatch countDownLatch) {

    final int intervalSeconds = optimizeCfg.getEvaluationIntervalSeconds();
    LOG.info("Scheduling dashboard and report evaluations every {} seconds", intervalSeconds);

    final BooleanSupplier shouldContinue = createContinuationCondition(optimizeCfg);

    return executorService.scheduleAtFixedRate(
        () -> {
          if (!shouldContinue.getAsBoolean()) {
            // signal completion
            countDownLatch.countDown();
            return;
          }

          try {
            evaluateDashboardAndReports();
          } catch (final Exception e) {
            THROTTLED_LOGGER.error("Error during evaluation cycle", e);
          }
        },
        60,
        intervalSeconds,
        TimeUnit.SECONDS);
  }

  private void evaluateDashboardAndReports() {
    LOG.info("Starting evaluation cycle");

    try {
      // Ensure token is valid before making requests
      loadTester.ensureValidToken();

      // Evaluate dashboard and all its reports
      final OptimizeReportLoadTester.DashboardWithReportsResult result =
          loadTester.evaluateDashboardWithReports();

      // Record dashboard metrics
      final OptimizeReportLoadTester.DashboardEvaluationResult dashboardResult =
          result.getDashboardResult();
      dashboardResponseTimer.record(dashboardResult.getResponseTimeMs(), TimeUnit.MILLISECONDS);

      if (dashboardResult.isSuccess()) {
        dashboardSuccessCounter.increment();
        LOG.info("Dashboard evaluated successfully in {}ms", dashboardResult.getResponseTimeMs());
      } else {
        dashboardErrorCounter.increment();
        LOG.error("Dashboard evaluation failed with status {}", dashboardResult.getStatusCode());
      }

      // Record report metrics
      final List<OptimizeReportLoadTester.ReportEvaluationResult> reportResults =
          result.getReportResults();
      for (final OptimizeReportLoadTester.ReportEvaluationResult reportResult : reportResults) {
        reportResponseTimer.record(reportResult.getResponseTimeMs(), TimeUnit.MILLISECONDS);

        if (reportResult.isSuccess()) {
          reportSuccessCounter.increment();
          LOG.info(
              "Report {} evaluated successfully in {}ms",
              reportResult.getReportId(),
              reportResult.getResponseTimeMs());
        } else {
          reportErrorCounter.increment();
          LOG.error(
              "Report {} evaluation failed with status {}",
              reportResult.getReportId(),
              reportResult.getStatusCode());
        }
      }

      // Record max report time and homepage load time metrics
      maxReportResponseTimer.record(result.getMaxReportTimeMs(), TimeUnit.MILLISECONDS);
      homepageLoadTimer.record(result.getHomepageLoadTimeMs(), TimeUnit.MILLISECONDS);

      LOG.info(
          "Evaluation cycle completed - Dashboard: {}ms, Reports: {}, Max report: {}ms, Homepage load: {}ms, Total: {}ms",
          dashboardResult.getResponseTimeMs(),
          reportResults.size(),
          result.getMaxReportTimeMs(),
          result.getHomepageLoadTimeMs(),
          result.getTotalResponseTimeMs());

    } catch (final Exception e) {
      dashboardErrorCounter.increment();
      THROTTLED_LOGGER.error("Failed to evaluate dashboard and reports", e);
    }
  }

  private BooleanSupplier createContinuationCondition(final OptimizeCfg optimizeCfg) {
    final int durationLimit = optimizeCfg.getDurationLimit();

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
    createApp(Optimize::new);
  }
}
