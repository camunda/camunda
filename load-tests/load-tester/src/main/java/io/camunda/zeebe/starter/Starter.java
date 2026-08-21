/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.starter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.zeebe.config.LoadTesterProperties;
import io.camunda.zeebe.config.StarterProperties;
import io.camunda.zeebe.metrics.ConnectionMonitor;
import io.camunda.zeebe.metrics.ProcessInstanceStartMeter;
import io.camunda.zeebe.metrics.StarterLatencyMetricsDoc;
import io.camunda.zeebe.metrics.StarterMetricsDoc;
import io.camunda.zeebe.metrics.StarterMetricsDoc.StarterMetricKeyNames;
import io.camunda.zeebe.optimize.OptimizeReportEvaluator;
import io.camunda.zeebe.optimize.OptimizeReportEvaluatorFactory;
import io.camunda.zeebe.read.DataReadMeter;
import io.camunda.zeebe.read.DataReadMeterQueryProvider;
import io.camunda.zeebe.util.PayloadReader;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

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

  private final CamundaClient client;
  private final LoadTesterProperties properties;
  private final StarterProperties starterCfg;
  private final MeterRegistry registry;
  private final PayloadReader payloadReader;
  private final ConnectionMonitor connectionMonitor;
  private final WebClient.Builder webClientBuilder;
  private final ObjectMapper objectMapper;
  private final AtomicLong businessKey = new AtomicLong(0);
  private final AtomicLong lastProcessInstanceKey = new AtomicLong(0);
  private final AtomicLong currentProcessDefinitionKey = new AtomicLong(0);
  private final AtomicInteger runFinished = new AtomicInteger(0);
  private final AtomicInteger deployCounter = new AtomicInteger(0);
  private final AtomicReference<Instant> lastProcessInstanceKeyTimestamp =
      new AtomicReference<>(Instant.now());

  private Timer responseLatencyTimer;
  private Counter processInstancesStartedCounter;
  private ScheduledExecutorService executorService;
  private ScheduledExecutorService redeployExecutorService;
  private ProcessInstanceStartMeter processInstanceStartMeter;
  private DataReadMeter dataReadMeter;
  private OptimizeReportEvaluator optimizeReportEvaluator;
  // Base BPMN XML of the main process, read once from the classpath at startup and reused for
  // every (re)deploy; each deploy injects a unique marker so Zeebe creates a fresh version.
  private String baseBpmnXml;

  public Starter(
      final CamundaClient client,
      final LoadTesterProperties properties,
      final MeterRegistry registry,
      final PayloadReader payloadReader,
      final ConnectionMonitor connectionMonitor,
      final WebClient.Builder webClientBuilder,
      final ObjectMapper objectMapper) {
    this.client = client;
    this.properties = properties;
    starterCfg = properties.getStarter();
    this.registry = registry;
    this.payloadReader = payloadReader;
    this.connectionMonitor = connectionMonitor;
    this.webClientBuilder = webClientBuilder;
    this.objectMapper = objectMapper;

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
    connectionMonitor.awaitAndPrintTopology();

    responseLatencyTimer =
        MicrometerUtil.buildTimer(StarterLatencyMetricsDoc.RESPONSE_LATENCY).register(registry);

    processInstancesStartedCounter =
        Counter.builder(StarterMetricsDoc.PROCESS_INSTANCES_STARTED.getName())
            .description(StarterMetricsDoc.PROCESS_INSTANCES_STARTED.getDescription())
            .register(registry);

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

    deployProcess();

    if (properties.isPerformReadBenchmarks()) {
      dataReadMeter.start();
    }

    if (optimizeReportEvaluator != null) {
      optimizeReportEvaluator.start();
    }

    final CountDownLatch countDownLatch = new CountDownLatch(1);
    executorService = Executors.newScheduledThreadPool(starterCfg.getThreads());
    final ScheduledFuture<?> scheduledTask =
        scheduleProcessInstanceCreation(executorService, countDownLatch);

    final ScheduledFuture<?> redeployTask =
        starterCfg.isRedeployEnabled() ? scheduleRedeploy() : null;

    try {
      countDownLatch.await();
    } catch (final InterruptedException e) {
      LOG.error("Awaiting of count down latch was interrupted.", e);
    }

    runFinished.set(1);
    LOG.info(
        "Starter finished. Total process instance start requests submitted: {}",
        processInstancesStartedCounter == null ? 0 : (long) processInstancesStartedCounter.count());
    scheduledTask.cancel(true);
    if (redeployTask != null) {
      redeployTask.cancel(true);
    }
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
    if (redeployExecutorService != null && !redeployExecutorService.isShutdown()) {
      redeployExecutorService.shutdown();
      try {
        redeployExecutorService.awaitTermination(60, TimeUnit.SECONDS);
      } catch (final InterruptedException e) {
        LOG.error("Shutdown redeploy executor service was interrupted", e);
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
                  client
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
            client,
            DataReadMeterQueryProvider.getDefaultQueries(properties.getDisabledQueriesList()));
    dataReadMeter.setContextProcessDefinitionId(starterCfg.getProcessId());
    dataReadMeter.setContextBusinessKeySupplier(
        () ->
            Pair.of(
                starterCfg.getBusinessKey(),
                businessKey.get() - (long) (starterCfg.getRatePerSecond() * 60.0)));
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
            processInstancesStartedCounter.increment();

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
            })
        .thenApply(
            (response) -> {
              if (properties.isPerformReadBenchmarks()
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
    baseBpmnXml = readClasspathResource(starterCfg.getBpmnXmlPath());
    LOG.info(
        "Deploying main resource: {}, extra resources: {}",
        starterCfg.getBpmnXmlPath(),
        starterCfg.getExtraBpmnModels());
    while (true) {
      try {
        currentProcessDefinitionKey.set(deployVersion());
        if (properties.isPerformReadBenchmarks()) {
          dataReadMeter.setContextProcessDefinitionKey(currentProcessDefinitionKey.get());
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

  /**
   * Continuously deploys a new version of the benchmark process definition and deletes the previous
   * one, so the previous version enters the DRAINING state while its instances finish. This
   * exercises the draining-deletion path under load: the delete distribution and the per-instance
   * drain-finalize hook, without ever blocking new-instance creation.
   */
  private ScheduledFuture<?> scheduleRedeploy() {
    final long intervalMillis = starterCfg.getRedeployInterval().toMillis();
    final boolean deleteHistory = starterCfg.isRedeployDeleteHistory();
    LOG.info(
        "Redeploy loop enabled: deploying a new version and deleting the previous one every {}ms (deleteHistory={})",
        intervalMillis,
        deleteHistory);

    // Only ever touched by the single-thread redeploy executor, so no synchronization needed.
    final Deque<Long> keysPendingDeletion = new ArrayDeque<>();

    redeployExecutorService = Executors.newScheduledThreadPool(1);
    return redeployExecutorService.scheduleAtFixedRate(
        () -> {
          try {
            // Deploy the new version before deleting the old one so that `latestVersion` always
            // resolves to an ACTIVE definition; the create loop never observes a DRAINING latest.
            // Advance the current key only after a successful deploy, then enqueue the superseded
            // key for deletion. Deletes are retried on the next tick so a transient failure never
            // orphans a version.
            final long newKey = deployVersion();
            final long superseded = currentProcessDefinitionKey.getAndSet(newKey);
            if (superseded != 0) {
              keysPendingDeletion.add(superseded);
            }
            for (int pending = keysPendingDeletion.size(); pending > 0; pending--) {
              final long key = keysPendingDeletion.poll();
              try {
                client.newDeleteResourceCommand(key).deleteHistory(deleteHistory).send().join();
              } catch (final Exception e) {
                keysPendingDeletion.add(key);
                THROTTLED_LOGGER.warn(
                    "Failed to delete process definition {}, will retry next tick", key, e);
                break;
              }
            }
          } catch (final Exception e) {
            THROTTLED_LOGGER.warn("Failed to redeploy the process definition", e);
          }
        },
        intervalMillis,
        intervalMillis,
        TimeUnit.MILLISECONDS);
  }

  /**
   * Deploys the benchmark process with a byte-unique marker injected into each version, so Zeebe's
   * checksum-based deployment deduplication always creates a fresh version instead of returning the
   * previous definition key. Without this the redeploy loop would re-resolve the same definition and
   * delete the live version, never building up a draining backlog.
   */
  private long deployVersion() {
    final String uniqueMain = withUniqueMarker(baseBpmnXml, deployCounter.incrementAndGet());
    var deployCmd =
        client
            .newDeployResourceCommand()
            .addResourceStringUtf8(uniqueMain, starterCfg.getBpmnXmlPath());
    for (final var model : starterCfg.getExtraBpmnModels()) {
      deployCmd = deployCmd.addResourceFromClasspath(model);
    }
    return deployCmd.send().join().getProcesses().stream()
        .filter(p -> p.getBpmnProcessId().equals(starterCfg.getProcessId()))
        .findFirst()
        .map(Process::getProcessDefinitionKey)
        .orElse(0L);
  }

  private static String withUniqueMarker(final String xml, final int seq) {
    // A comment in the XML prolog is valid and ignored by the BPMN parser, so the model semantics
    // and bpmnProcessId are unchanged while the resource checksum differs on every deploy.
    final String marker = "<!-- churn-version " + seq + " -->";
    final int declEnd = xml.indexOf("?>");
    return declEnd >= 0
        ? xml.substring(0, declEnd + 2) + "\n" + marker + xml.substring(declEnd + 2)
        : marker + "\n" + xml;
  }

  private static String readClasspathResource(final String path) {
    try (final var in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("Resource not found on classpath: " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to read classpath resource: " + path, e);
    }
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
