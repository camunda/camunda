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
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
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

  private static final Logger THROTTLED_LOGGER =
      new ThrottledLogger(LoggerFactory.getLogger(Optimize.class), Duration.ofSeconds(5));
  private static final Logger LOG = LoggerFactory.getLogger(Optimize.class);

  private final OptimizeCfg optimizeCfg;
  private OptimizeReportLoadTester loadTester;
  private ScheduledExecutorService executorService;

  // Metrics
  private Timer dashboardResponseTimer;
  private Timer maxReportResponseTimer;
  private Timer homepageLoadTimer;
  private Counter dashboardSuccessCounter;
  private Counter dashboardErrorCounter;

  // Instant Benchmark Metrics
  private Timer benchmarkDashboardResponseTimer;
  private Timer benchmarkMaxReportEvaluationTimer;
  private Timer benchmarkMaxDetailedEvaluationTimer;
  private Timer benchmarkTotalLoadTimer;
  private Counter benchmarkDashboardSuccessCounter;
  private Counter benchmarkDashboardErrorCounter;

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
        MicrometerUtil.buildTimer(OptimizeMetricsDoc.DASHBOARD_RESPONSE_TIME).register(registry);

    maxReportResponseTimer =
        MicrometerUtil.buildTimer(OptimizeMetricsDoc.REPORT_MAX_RESPONSE_TIME).register(registry);

    homepageLoadTimer =
        MicrometerUtil.buildTimer(OptimizeMetricsDoc.HOMEPAGE_LOAD_TIME).register(registry);

    dashboardSuccessCounter =
        Counter.builder(OptimizeMetricsDoc.DASHBOARD_SUCCESS.getName())
            .description(OptimizeMetricsDoc.DASHBOARD_SUCCESS.getDescription())
            .register(registry);

    dashboardErrorCounter =
        Counter.builder(OptimizeMetricsDoc.DASHBOARD_ERROR.getName())
            .description(OptimizeMetricsDoc.DASHBOARD_ERROR.getDescription())
            .register(registry);

    // Instant Benchmark Metrics
    benchmarkDashboardResponseTimer =
        MicrometerUtil.buildTimer(OptimizeMetricsDoc.BENCHMARK_DASHBOARD_RESPONSE_TIME)
            .register(registry);

    benchmarkMaxReportEvaluationTimer =
        MicrometerUtil.buildTimer(OptimizeMetricsDoc.BENCHMARK_REPORT_MAX_EVALUATION_TIME)
            .register(registry);

    benchmarkMaxDetailedEvaluationTimer =
        MicrometerUtil.buildTimer(OptimizeMetricsDoc.BENCHMARK_DETAILED_MAX_EVALUATION_TIME)
            .register(registry);

    benchmarkTotalLoadTimer =
        MicrometerUtil.buildTimer(OptimizeMetricsDoc.BENCHMARK_TOTAL_LOAD_TIME).register(registry);

    benchmarkDashboardSuccessCounter =
        Counter.builder(OptimizeMetricsDoc.BENCHMARK_DASHBOARD_SUCCESS.getName())
            .description(OptimizeMetricsDoc.BENCHMARK_DASHBOARD_SUCCESS.getDescription())
            .register(registry);

    benchmarkDashboardErrorCounter =
        Counter.builder(OptimizeMetricsDoc.BENCHMARK_DASHBOARD_ERROR.getName())
            .description(OptimizeMetricsDoc.BENCHMARK_DASHBOARD_ERROR.getDescription())
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
            evaluateInstantBenchmark();
          } catch (final Exception e) {
            THROTTLED_LOGGER.error("Error during evaluation cycle", e);
          }
        },
        0,
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
      recordDashboardMetrics(
          dashboardResult,
          dashboardResponseTimer,
          dashboardSuccessCounter,
          dashboardErrorCounter,
          "Dashboard");

      // Record report metrics
      final List<OptimizeReportLoadTester.ReportEvaluationResult> reportResults =
          result.getReportResults();
      recordReportMetrics(
          reportResults,
          "optimize.report.response.time",
          "Response time for report evaluation",
          "optimize.report.success",
          "Successful report evaluations",
          "optimize.report.error",
          "Failed report evaluations",
          "Report {} [{}] evaluated successfully in {}ms",
          "Report {} [{}] evaluation failed with status {}");

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

  private void evaluateInstantBenchmark() {
    LOG.info("Starting instant benchmark evaluation cycle");

    try {
      // Ensure token is valid before making requests
      loadTester.ensureValidToken();

      // Evaluate instant benchmark flow
      final OptimizeReportLoadTester.InstantBenchmarkResult result =
          loadTester.evaluateInstantBenchmark();

      // Record dashboard metrics
      final OptimizeReportLoadTester.DashboardEvaluationResult dashboardResult =
          result.getDashboardResult();
      recordDashboardMetrics(
          dashboardResult,
          benchmarkDashboardResponseTimer,
          benchmarkDashboardSuccessCounter,
          benchmarkDashboardErrorCounter,
          "Benchmark dashboard");

      // Record report evaluation metrics
      final List<OptimizeReportLoadTester.ReportEvaluationResult> reportEvalResults =
          result.getReportEvaluationResults();
      recordReportMetrics(
          reportEvalResults,
          "optimize.benchmark.report.evaluation.time",
          "Response time for benchmark report evaluation",
          "optimize.benchmark.report.evaluation.success",
          "Successful benchmark report evaluations",
          "optimize.benchmark.report.evaluation.error",
          "Failed benchmark report evaluations",
          "Benchmark report {} [{}] evaluated successfully in {}ms",
          "Benchmark report {} [{}] evaluation failed with status {}");

      // Record detailed evaluation metrics
      final List<OptimizeReportLoadTester.ReportEvaluationResult> detailedResults =
          result.getDetailedEvaluationResults();
      recordReportMetrics(
          detailedResults,
          "optimize.benchmark.detailed.evaluation.time",
          "Response time for benchmark detailed evaluation",
          "optimize.benchmark.detailed.evaluation.success",
          "Successful benchmark detailed evaluations",
          "optimize.benchmark.detailed.evaluation.error",
          "Failed benchmark detailed evaluations",
          "Benchmark detailed evaluation for report {} [{}] completed in {}ms",
          "Benchmark detailed evaluation for report {} [{}] failed with status {}");

      // Record max times and total load time
      benchmarkMaxReportEvaluationTimer.record(
          result.getMaxReportEvaluationTimeMs(), TimeUnit.MILLISECONDS);
      benchmarkMaxDetailedEvaluationTimer.record(
          result.getMaxDetailedEvaluationTimeMs(), TimeUnit.MILLISECONDS);
      benchmarkTotalLoadTimer.record(result.getTotalResponseTimeMs(), TimeUnit.MILLISECONDS);

      LOG.info(
          "Instant benchmark cycle completed - Dashboard: {}ms, Report evaluations: {}, Detailed evaluations: {}, Max report eval: {}ms, Max detailed eval: {}ms, Total: {}ms",
          dashboardResult.getResponseTimeMs(),
          reportEvalResults.size(),
          detailedResults.size(),
          result.getMaxReportEvaluationTimeMs(),
          result.getMaxDetailedEvaluationTimeMs(),
          result.getTotalResponseTimeMs());

    } catch (final Exception e) {
      benchmarkDashboardErrorCounter.increment();
      THROTTLED_LOGGER.error("Failed to evaluate instant benchmark", e);
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

  private String getReportNameOrDefault(
      final OptimizeReportLoadTester.ReportEvaluationResult result) {
    return result.getReportName() != null ? result.getReportName() : "unknown";
  }

  private void recordDashboardMetrics(
      final OptimizeReportLoadTester.DashboardEvaluationResult dashboardResult,
      final Timer timer,
      final Counter successCounter,
      final Counter errorCounter,
      final String logPrefix) {
    timer.record(dashboardResult.getResponseTimeMs(), TimeUnit.MILLISECONDS);

    if (dashboardResult.isSuccess()) {
      successCounter.increment();
      LOG.info("{} evaluated successfully in {}ms", logPrefix, dashboardResult.getResponseTimeMs());
    } else {
      errorCounter.increment();
      LOG.error("{} evaluation failed with status {}", logPrefix, dashboardResult.getStatusCode());
    }
  }

  private void recordReportMetrics(
      final List<OptimizeReportLoadTester.ReportEvaluationResult> reportResults,
      final String timerMetricName,
      final String timerDescription,
      final String successCounterName,
      final String successDescription,
      final String errorCounterName,
      final String errorDescription,
      final String successLogTemplate,
      final String errorLogTemplate) {
    for (final OptimizeReportLoadTester.ReportEvaluationResult reportResult : reportResults) {
      final String reportName = getReportNameOrDefault(reportResult);

      // Record response time with report ID and name as tags
      Timer.builder(timerMetricName)
          .description(timerDescription)
          .tag("reportId", reportResult.getReportId())
          .tag("reportName", reportName)
          .register(registry)
          .record(reportResult.getResponseTimeMs(), TimeUnit.MILLISECONDS);

      if (reportResult.isSuccess()) {
        Counter.builder(successCounterName)
            .description(successDescription)
            .tag("reportId", reportResult.getReportId())
            .tag("reportName", reportName)
            .register(registry)
            .increment();
        LOG.info(
            successLogTemplate,
            reportResult.getReportId(),
            reportName,
            reportResult.getResponseTimeMs());
      } else {
        Counter.builder(errorCounterName)
            .description(errorDescription)
            .tag("reportId", reportResult.getReportId())
            .tag("reportName", reportName)
            .register(registry)
            .increment();
        LOG.error(
            errorLogTemplate, reportResult.getReportId(), reportName, reportResult.getStatusCode());
      }
    }
  }

  public static void main(final String[] args) {
    createApp(Optimize::new);
  }
}
