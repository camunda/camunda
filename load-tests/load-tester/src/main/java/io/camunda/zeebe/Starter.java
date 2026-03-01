/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.DeployResourceCommandStep1.DeployResourceCommandStep2;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.ProcessInstanceSort;
import io.camunda.zeebe.config.AppCfg;
import io.camunda.zeebe.config.OptimizeCfg;
import io.camunda.zeebe.config.StarterCfg;
import io.camunda.zeebe.read.DataReadMeter;
import io.camunda.zeebe.read.DataReadMeterQueryProvider;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.apache.commons.lang3.tuple.Pair;
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
  private final StarterCfg starterCfg;
  private Timer responseLatencyTimer;
  private ScheduledExecutorService executorService;
  private ProcessInstanceStartMeter processInstanceStartMeter;
  private DataReadMeter dataReadMeter;
  private final AtomicLong businessKey = new AtomicLong(0);
  private final AtomicLong lastProcessInstanceKey = new AtomicLong(0);
  private final AtomicReference<Instant> lastProcessInstanceKeyTimestamp =
      new AtomicReference<>(Instant.now());

  // Optimize components (optional)
  private OptimizeReportLoadTester optimizeLoadTester;
  private ScheduledExecutorService optimizeExecutorService;
  private ScheduledFuture<?> optimizeScheduledTask;

  // Optimize Metrics
  private Timer dashboardResponseTimer;
  private Timer maxReportResponseTimer;
  private Timer homepageLoadTimer;
  private Counter dashboardSuccessCounter;
  private Counter dashboardErrorCounter;
  private Timer benchmarkDashboardResponseTimer;
  private Timer benchmarkMaxReportEvaluationTimer;
  private Timer benchmarkMaxDetailedEvaluationTimer;
  private Timer benchmarkTotalLoadTimer;
  private Counter benchmarkDashboardSuccessCounter;
  private Counter benchmarkDashboardErrorCounter;

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

    // init - check for topology and deploy process
    printTopology(client);
    if (config.isMonitorDataAvailability()) {
      setupDataAvailabilityMeter(client);
    }

    if (config.isPerformReadBenchmarks()) {
      setupDataReadMeter(client);
    }
    deployProcess(client, starterCfg);

    if (config.isPerformReadBenchmarks()) {
      dataReadMeter.start();
    }

    // Initialize Optimize if configured
    if (config.getOptimize().isEnableOptimize()) {
      initializeOptimize();
    }

    // setup to start instances on given rate
    final CountDownLatch countDownLatch = new CountDownLatch(1);
    executorService = Executors.newScheduledThreadPool(starterCfg.getThreads());
    final ScheduledFuture<?> scheduledTask =
        scheduleProcessInstanceCreation(executorService, countDownLatch, client);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  if (!executorService.isShutdown()) {
                    executorService.shutdown();
                    if (config.isMonitorDataAvailability()) {
                      processInstanceStartMeter.close();
                    }
                    if (config.isPerformReadBenchmarks()) {
                      dataReadMeter.close();
                    }
                    try {
                      executorService.awaitTermination(60, TimeUnit.SECONDS);
                    } catch (final InterruptedException e) {
                      LOG.error("Shutdown executor service was interrupted", e);
                    }
                  }
                  // Shutdown Optimize if running
                  if (optimizeExecutorService != null && !optimizeExecutorService.isShutdown()) {
                    optimizeExecutorService.shutdown();
                    try {
                      optimizeExecutorService.awaitTermination(60, TimeUnit.SECONDS);
                    } catch (final InterruptedException e) {
                      LOG.error("Shutdown Optimize executor service was interrupted", e);
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

    if (config.isPerformReadBenchmarks()) {
      dataReadMeter.close();
    }

    // Cleanup Optimize if running
    if (optimizeScheduledTask != null) {
      optimizeScheduledTask.cancel(true);
      optimizeExecutorService.shutdown();
      LOG.info("Optimize load tester finished");
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

  private void setupDataReadMeter(final CamundaClient client) {
    LOG.info("Starting read benchmark queries");
    dataReadMeter =
        new DataReadMeter(
            registry,
            Executors.newScheduledThreadPool(2),
            client,
            DataReadMeterQueryProvider.getDefaultQueries());
    dataReadMeter.setContextProcessDefinitionId(starterCfg.getProcessId());
    dataReadMeter.setContextBusinessKeySupplier(
        () -> Pair.of(starterCfg.getBusinessKey(), businessKey.get() - starterCfg.getRate() * 60L));
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

  private CompletionStage<ProcessInstanceEvent> startInstance(
      final CamundaClient client,
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
              if (config.isMonitorDataAvailability()) {
                final long processInstanceKey = response.getProcessInstanceKey();
                processInstanceStartMeter.recordProcessInstanceStart(processInstanceKey, startTime);
              }
              return response;
            })
        .thenApply(
            (response) -> {
              // this is not totally threadsafe but good enough for our purpose
              if (config.isPerformReadBenchmarks()
                  && lastProcessInstanceKeyTimestamp
                      .get()
                      .plus(1, ChronoUnit.MINUTES)
                      .isBefore(Instant.now())) {
                lastProcessInstanceKeyTimestamp.set(Instant.now());
                final var oldValue =
                    lastProcessInstanceKey.getAndSet(response.getProcessInstanceKey());
                dataReadMeter.setContextProcessInstanceKey(
                    oldValue == 0 ? response.getProcessInstanceKey() : oldValue);
              }
              return response;
            });
  }

  private CompletionStage<?> startInstanceWithAwaitingResult(
      final CamundaClient client, final String processId, final HashMap<String, Object> variables) {
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
      final CamundaClient client, final Map<String, Object> variables) {
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

  private CamundaClient createCamundaClient() {
    return newClientBuilder().numJobWorkerExecutionThreads(0).build();
  }

  private void deployProcess(final CamundaClient client, final StarterCfg starterCfg) {
    final var deployCmd = constructDeploymentCommand(client, starterCfg);

    while (true) {
      try {
        final var result = deployCmd.send().join();
        final var benchmarkProcessDefinitionKey =
            result.getProcesses().stream()
                .filter(p -> p.getBpmnProcessId().equals(starterCfg.getProcessId()))
                .findFirst()
                .map(Process::getProcessDefinitionKey)
                .orElse(0L);
        if (config.isPerformReadBenchmarks()) {
          dataReadMeter.setContextProcessDefinitionKey(benchmarkProcessDefinitionKey);
        }
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
      final CamundaClient client, final StarterCfg starterCfg) {
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

  // ==================== Optimize Integration Methods ====================

  private void initializeOptimize() {
    final OptimizeCfg optimizeCfg = config.getOptimize();
    LOG.info("Initializing Optimize load tester");

    // Initialize metrics
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

    // Create load tester instance
    optimizeLoadTester =
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
      LOG.info("Authenticating Optimize with Keycloak...");
      optimizeLoadTester.authenticateWithAuthorizationCodeFlow();
      LOG.info("Optimize successfully authenticated");
    } catch (final Exception e) {
      LOG.error("Failed to authenticate Optimize with Keycloak", e);
      throw new RuntimeException("Optimize authentication failed", e);
    }

    // Setup scheduled execution
    optimizeExecutorService = Executors.newScheduledThreadPool(1);
    optimizeScheduledTask = scheduleOptimizeEvaluations(optimizeExecutorService, optimizeCfg);
  }

  private ScheduledFuture<?> scheduleOptimizeEvaluations(
      final ScheduledExecutorService executorService, final OptimizeCfg optimizeCfg) {

    final int intervalSeconds = optimizeCfg.getEvaluationIntervalSeconds();
    LOG.info(
        "Scheduling Optimize dashboard and report evaluations every {} seconds", intervalSeconds);

    final BooleanSupplier shouldContinue = createOptimizeContinuationCondition(optimizeCfg);

    return executorService.scheduleAtFixedRate(
        () -> {
          if (!shouldContinue.getAsBoolean()) {
            return;
          }

          try {
            evaluateDashboardAndReports();
            evaluateInstantBenchmark();
          } catch (final Exception e) {
            THROTTLED_LOGGER.error("Error during Optimize evaluation cycle", e);
          }
        },
        10,
        intervalSeconds,
        TimeUnit.SECONDS);
  }

  private BooleanSupplier createOptimizeContinuationCondition(final OptimizeCfg optimizeCfg) {
    final int durationLimit = optimizeCfg.getDurationLimit();

    if (durationLimit > 0) {
      final LocalDateTime endTime = LocalDateTime.now().plus(durationLimit, ChronoUnit.SECONDS);
      return () -> LocalDateTime.now().isBefore(endTime);
    } else {
      return () -> true;
    }
  }

  private void evaluateDashboardAndReports() {
    LOG.info("Starting Optimize evaluation cycle");

    try {
      optimizeLoadTester.ensureValidToken();

      final OptimizeReportLoadTester.DashboardWithReportsResult result =
          optimizeLoadTester.evaluateDashboardWithReports();

      final OptimizeReportLoadTester.DashboardEvaluationResult dashboardResult =
          result.getDashboardResult();
      recordDashboardMetrics(
          dashboardResult,
          dashboardResponseTimer,
          dashboardSuccessCounter,
          dashboardErrorCounter,
          "Dashboard");

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

      maxReportResponseTimer.record(result.getMaxReportTimeMs(), TimeUnit.MILLISECONDS);
      homepageLoadTimer.record(result.getHomepageLoadTimeMs(), TimeUnit.MILLISECONDS);

      LOG.info(
          "Optimize evaluation cycle completed - Dashboard: {}ms, Reports: {}, Max report: {}ms, Homepage load: {}ms, Total: {}ms",
          dashboardResult.getResponseTimeMs(),
          reportResults.size(),
          result.getMaxReportTimeMs(),
          result.getHomepageLoadTimeMs(),
          result.getTotalResponseTimeMs());

    } catch (final Exception e) {
      dashboardErrorCounter.increment();
      THROTTLED_LOGGER.error("Failed to evaluate Optimize dashboard and reports", e);
    }
  }

  private void evaluateInstantBenchmark() {
    LOG.info("Starting Optimize instant benchmark evaluation cycle");

    try {
      optimizeLoadTester.ensureValidToken();

      final OptimizeReportLoadTester.InstantBenchmarkResult result =
          optimizeLoadTester.evaluateInstantBenchmark(
              config.getOptimize().getProcessDefinitionKey());

      final OptimizeReportLoadTester.DashboardEvaluationResult dashboardResult =
          result.getDashboardResult();
      recordDashboardMetrics(
          dashboardResult,
          benchmarkDashboardResponseTimer,
          benchmarkDashboardSuccessCounter,
          benchmarkDashboardErrorCounter,
          "Benchmark dashboard");

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

      benchmarkMaxReportEvaluationTimer.record(
          result.getMaxReportEvaluationTimeMs(), TimeUnit.MILLISECONDS);
      benchmarkMaxDetailedEvaluationTimer.record(
          result.getMaxDetailedEvaluationTimeMs(), TimeUnit.MILLISECONDS);
      benchmarkTotalLoadTimer.record(result.getTotalResponseTimeMs(), TimeUnit.MILLISECONDS);

      LOG.info(
          "Optimize instant benchmark cycle completed - Dashboard: {}ms, Report evaluations: {}, Detailed evaluations: {}, Max report eval: {}ms, Max detailed eval: {}ms, Total: {}ms",
          dashboardResult.getResponseTimeMs(),
          reportEvalResults.size(),
          detailedResults.size(),
          result.getMaxReportEvaluationTimeMs(),
          result.getMaxDetailedEvaluationTimeMs(),
          result.getTotalResponseTimeMs());

    } catch (final Exception e) {
      benchmarkDashboardErrorCounter.increment();
      THROTTLED_LOGGER.error("Failed to evaluate Optimize instant benchmark", e);
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
    createApp(Starter::new);
  }
}
