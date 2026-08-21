/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.secrets;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ResolveSecretsResponse;
import io.camunda.zeebe.config.LoadTesterProperties;
import io.camunda.zeebe.config.ZeebeSecretsDriverProperties;
import io.camunda.zeebe.metrics.ConnectionMonitor;
import io.camunda.zeebe.metrics.ZeebeSecretsDriverMetricsDoc;
import io.camunda.zeebe.metrics.ZeebeSecretsDriverMetricsDoc.ZeebeSecretsDriverMetricKeyNames;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Benchmark driver for the secret-resolution API. Issues {@code POST /v2/secrets/resolve} and
 * {@code POST /v2/secrets/list} against the gateway at a configured rate and concurrency, recording
 * latency, throughput and outcome as Micrometer metrics so the existing Prometheus/Grafana pipeline
 * captures p50/p95/p99, RPS and error rate without additional wiring.
 *
 * <p>Active only under the {@code zeebe-secrets} Spring profile and only when {@link
 * ZeebeSecretsDriverProperties#isEnabled()} is set, so the same load-tester image runs as a
 * starter, worker or zeebe-secrets driver depending on how it is deployed.
 *
 * <p>Requests go through the injected {@link CamundaClient}, which already carries the gateway
 * address and OAuth configuration the other load-tester components use, so the driver adds no
 * transport or authentication of its own.
 */
@Component
@Profile("zeebe-secrets")
public class SecretsDriver implements CommandLineRunner {

  private static final Logger LOG = LoggerFactory.getLogger(SecretsDriver.class);
  private static final Logger THROTTLED_LOGGER =
      new ThrottledLogger(LoggerFactory.getLogger(SecretsDriver.class), Duration.ofSeconds(5));
  private static final long NANOS_PER_SECOND = Duration.ofSeconds(1).toNanos();
  private static final String ENDPOINT_RESOLVE = "resolve";
  private static final String ENDPOINT_LIST = "list";
  private static final String OUTCOME_SUCCESS = "success";
  private static final String OUTCOME_ERROR = "error";

  private final CamundaClient client;
  private final ZeebeSecretsDriverProperties config;
  private final MeterRegistry registry;
  private final ConnectionMonitor connectionMonitor;

  private final AtomicInteger runFinished = new AtomicInteger(0);
  private final List<String> referencePool;

  private ScheduledExecutorService executorService;
  private Counter resolveSubmitted;
  private Counter listSubmitted;
  private final Map<String, Timer> latencyTimers = new HashMap<>();

  public SecretsDriver(
      final CamundaClient client,
      final LoadTesterProperties properties,
      final MeterRegistry registry,
      final ConnectionMonitor connectionMonitor) {
    this.client = client;
    config = properties.getZeebeSecrets();
    this.registry = registry;
    this.connectionMonitor = connectionMonitor;
    referencePool = config.buildReferencePool();

    Gauge.builder(ZeebeSecretsDriverMetricsDoc.DRIVER_INFO.getName(), () -> 1)
        .description(ZeebeSecretsDriverMetricsDoc.DRIVER_INFO.getDescription())
        .tag(
            ZeebeSecretsDriverMetricKeyNames.RESOLVE_RATIO.asString(),
            String.valueOf(config.getResolveRatio()))
        .tag(
            ZeebeSecretsDriverMetricKeyNames.BATCH_SIZE.asString(),
            String.valueOf(config.getEffectiveBatchSize()))
        .tag(
            ZeebeSecretsDriverMetricKeyNames.DUPLICATE_RATIO.asString(),
            String.valueOf(config.getDuplicateRatio()))
        .tag(
            ZeebeSecretsDriverMetricKeyNames.NB_THREADS.asString(),
            String.valueOf(config.getThreads()))
        .register(registry);
  }

  @Override
  public void run(final String... args) {
    if (!config.isEnabled()) {
      LOG.info(
          "Zeebe zeebe-secrets driver is disabled (zeebeSecretsBenchmark.driver enabled=false); not issuing any requests.");
      return;
    }

    connectionMonitor.awaitAndPrintTopology();

    resolveSubmitted =
        Counter.builder(ZeebeSecretsDriverMetricsDoc.REQUESTS_SUBMITTED.getName())
            .description(ZeebeSecretsDriverMetricsDoc.REQUESTS_SUBMITTED.getDescription())
            .tag(ZeebeSecretsDriverMetricKeyNames.ENDPOINT.asString(), ENDPOINT_RESOLVE)
            .register(registry);
    listSubmitted =
        Counter.builder(ZeebeSecretsDriverMetricsDoc.REQUESTS_SUBMITTED.getName())
            .description(ZeebeSecretsDriverMetricsDoc.REQUESTS_SUBMITTED.getDescription())
            .tag(ZeebeSecretsDriverMetricKeyNames.ENDPOINT.asString(), ENDPOINT_LIST)
            .register(registry);

    registerLatencyTimers();

    Gauge.builder(
            ZeebeSecretsDriverMetricsDoc.RUN_FINISHED.getName(),
            runFinished,
            AtomicInteger::doubleValue)
        .description(ZeebeSecretsDriverMetricsDoc.RUN_FINISHED.getDescription())
        .register(registry);

    if (config.isWarmup()) {
      warmUpCache();
    }

    final CountDownLatch countDownLatch = new CountDownLatch(1);
    executorService = Executors.newScheduledThreadPool(config.getThreads());
    final List<ScheduledFuture<?>> scheduledTasks =
        scheduleRequests(executorService, countDownLatch);

    try {
      countDownLatch.await();
    } catch (final InterruptedException e) {
      LOG.error("Awaiting of count down latch was interrupted.", e);
      Thread.currentThread().interrupt();
    }

    runFinished.set(1);
    LOG.info(
        "Zeebe zeebe-secrets driver finished. Total requests submitted: resolve={}, list={}",
        (long) resolveSubmitted.count(),
        (long) listSubmitted.count());
    scheduledTasks.forEach(task -> task.cancel(true));
    shutdown();
  }

  /**
   * Pre-registers the four request-latency timers (endpoint × outcome) once, so the per-request
   * completion path only records into an already-resolved timer instead of rebuilding and
   * re-registering a meter on every response — avoidable overhead that would otherwise skew the
   * latency the driver is meant to measure.
   */
  void registerLatencyTimers() {
    for (final String endpoint : new String[] {ENDPOINT_RESOLVE, ENDPOINT_LIST}) {
      for (final String outcome : new String[] {OUTCOME_SUCCESS, OUTCOME_ERROR}) {
        latencyTimers.put(
            latencyTimerKey(endpoint, outcome),
            MicrometerUtil.buildTimer(ZeebeSecretsDriverMetricsDoc.REQUEST_LATENCY)
                .tag(ZeebeSecretsDriverMetricKeyNames.ENDPOINT.asString(), endpoint)
                .tag(ZeebeSecretsDriverMetricKeyNames.OUTCOME.asString(), outcome)
                .register(registry));
      }
    }
  }

  private static String latencyTimerKey(final String endpoint, final String outcome) {
    return endpoint + '|' + outcome;
  }

  @PreDestroy
  public void shutdown() {
    if (executorService != null && !executorService.isShutdown()) {
      executorService.shutdown();
      try {
        if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
          executorService.shutdownNow();
        }
      } catch (final InterruptedException e) {
        LOG.error("Shutdown executor service was interrupted", e);
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * Resolves the whole reference pool once, in gateway-sized batches, before the measured load
   * begins. A cache-first run must not pay the one-off store-read cost of first-touching each
   * reference in its recorded latencies; this pays it up front instead. Failures are logged and
   * ignored, since a missing reference should surface in the measured run, not abort warm-up.
   */
  private void warmUpCache() {
    LOG.info("Warming up secret cache for {} references", referencePool.size());
    final int batchSize = config.getEffectiveBatchSize();
    for (int i = 0; i < referencePool.size(); i += batchSize) {
      final List<String> batch =
          referencePool.subList(i, Math.min(i + batchSize, referencePool.size()));
      try {
        client
            .newResolveSecretsCommand()
            .references(batch)
            .requestTimeout(config.getRequestTimeout())
            .send()
            .join();
      } catch (final Exception e) {
        THROTTLED_LOGGER.warn("Error warming up secret references {}", batch, e);
      }
    }
    LOG.info("Secret cache warm-up complete");
  }

  private List<ScheduledFuture<?>> scheduleRequests(
      final ScheduledExecutorService executor, final CountDownLatch countDownLatch) {
    final int threads = config.getThreads();
    final long intervalNanos = (long) (NANOS_PER_SECOND / config.getRatePerSecond());
    // Each of the `threads` request-issuing loops fires at 1/threads of the aggregate rate, so the
    // combined submission rate matches config.getRatePerSecond() while genuinely spreading request
    // submission across the pool's threads.
    final long perThreadIntervalNanos = intervalNanos * threads;
    LOG.info(
        "Issuing secrets requests across {} thread(s), one every {}ns aggregate (rate: {} per {}, resolveRatio: {})",
        threads,
        intervalNanos,
        config.getRate(),
        config.getRateDuration(),
        config.getResolveRatio());

    final BooleanSupplier shouldContinue = createContinuationCondition();

    final List<ScheduledFuture<?>> tasks = new ArrayList<>(threads);
    for (int t = 0; t < threads; t++) {
      final long initialDelayNanos = intervalNanos * t;
      tasks.add(
          executor.scheduleAtFixedRate(
              () -> {
                if (!shouldContinue.getAsBoolean()) {
                  countDownLatch.countDown();
                  return;
                }
                try {
                  if (ThreadLocalRandom.current().nextDouble() < config.getResolveRatio()) {
                    issueResolve();
                  } else {
                    issueList();
                  }
                } catch (final Exception e) {
                  THROTTLED_LOGGER.error("Error on issuing secrets request", e);
                }
              },
              initialDelayNanos,
              perThreadIntervalNanos,
              TimeUnit.NANOSECONDS));
    }
    return tasks;
  }

  private void issueResolve() {
    final List<String> references = buildResolveBatch();
    resolveSubmitted.increment();
    final long startTime = System.nanoTime();
    final CompletionStage<ResolveSecretsResponse> future =
        client
            .newResolveSecretsCommand()
            .references(references)
            .requestTimeout(config.getRequestTimeout())
            .send();
    recordOnComplete(future, ENDPOINT_RESOLVE, startTime, SecretsDriver::isResolveSuccess);
  }

  private void issueList() {
    listSubmitted.increment();
    final long startTime = System.nanoTime();
    final CompletionStage<?> future =
        client.newListSecretsCommand().requestTimeout(config.getRequestTimeout()).send();
    recordOnComplete(future, ENDPOINT_LIST, startTime, result -> result != null);
  }

  /**
   * A resolve request only counts as a success when every requested reference resolved.
   * Per-reference failures (missing or denied secrets) are returned as {@link
   * ResolveSecretsResponse#getErrors()} response data rather than raised as exceptions, so a
   * request that resolved nothing would otherwise be recorded as a success and keep the reported
   * error ratio at 0% even while the benchmark is not actually reading secrets.
   */
  // Package-private for SecretsDriverTest.
  static boolean isResolveSuccess(final ResolveSecretsResponse response) {
    return response != null && response.isFullyResolved();
  }

  <T> void recordOnComplete(
      final CompletionStage<T> future,
      final String endpoint,
      final long startTime,
      final Predicate<T> resultIsSuccess) {
    future.whenComplete(
        (result, error) -> {
          final long durationNanos = System.nanoTime() - startTime;
          final boolean success = error == null && resultIsSuccess.test(result);
          final String outcome = success ? OUTCOME_SUCCESS : OUTCOME_ERROR;
          latencyTimers
              .get(latencyTimerKey(endpoint, outcome))
              .record(durationNanos, TimeUnit.NANOSECONDS);
          if (error != null) {
            THROTTLED_LOGGER.warn("Error on secrets {} request", endpoint, error);
          } else if (!success) {
            THROTTLED_LOGGER.warn("Secrets {} request returned unresolved references", endpoint);
          }
        });
  }

  /**
   * Builds one resolve batch of {@link ZeebeSecretsDriverProperties#getEffectiveBatchSize()}
   * references: a {@code duplicateRatio} fraction of the batch repeats the first picked reference
   * so the gateway's server-side deduplication has something to collapse, and the remainder are
   * distinct references sampled <em>without replacement</em> from the pool. Sampling the distinct
   * portion without replacement keeps the intended duplicate count exact — sampling with
   * replacement could add unintended repeats (likely for small pools) and make {@code
   * duplicateRatio} and the dedup measurement inaccurate. Duplicates are deliberately literal
   * repeats of one reference so the effect is unambiguous in the measured backend read count.
   */
  List<String> buildResolveBatch() {
    final int batchSize = config.getEffectiveBatchSize();
    final int duplicates =
        Math.min(batchSize - 1, (int) Math.round(batchSize * config.getDuplicateRatio()));
    final int distinct = batchSize - duplicates;

    final List<String> references = new ArrayList<>(batchSize);
    final ThreadLocalRandom random = ThreadLocalRandom.current();
    final int poolSize = referencePool.size();
    final Set<Integer> pickedIndices = new HashSet<>(distinct);
    while (pickedIndices.size() < distinct) {
      final int index = random.nextInt(poolSize);
      if (pickedIndices.add(index)) {
        references.add(referencePool.get(index));
      }
    }
    final String repeated = references.get(0);
    for (int i = 0; i < duplicates; i++) {
      references.add(repeated);
    }
    return references;
  }

  private BooleanSupplier createContinuationCondition() {
    final int durationLimit = config.getDurationLimit();
    if (durationLimit > 0) {
      final LocalDateTime endTime = LocalDateTime.now().plus(durationLimit, ChronoUnit.SECONDS);
      return () -> LocalDateTime.now().isBefore(endTime);
    }
    return () -> true;
  }
}
