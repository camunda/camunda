/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.suspender;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.zeebe.config.LoadTesterProperties;
import io.camunda.zeebe.config.SuspenderProperties;
import io.camunda.zeebe.metrics.ConnectionMonitor;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Load-test role that stresses the process-instance suspend/resume feature by periodically
 * suspending running instances, holding them for a configurable duration while other roles keep
 * sending traffic (so buffered commands accumulate), then resuming them.
 *
 * <p>Two drivers are supported (see {@link SuspenderProperties.Mode}): {@code SINGLE} issues one
 * suspend/resume command per instance; {@code BATCH} issues process-instance batch operations over
 * a filter. Leaving {@link SuspenderProperties#isEnabled()} {@code false} is the A/B baseline arm.
 */
@Component
@Profile("suspender")
public class Suspender implements CommandLineRunner {

  private static final Logger LOG = LoggerFactory.getLogger(Suspender.class);
  private static final Logger THROTTLED_LOGGER =
      new ThrottledLogger(LoggerFactory.getLogger(Suspender.class), Duration.ofSeconds(5));
  private static final long NANOS_PER_SECOND = Duration.ofSeconds(1).toNanos();

  private final CamundaClient client;
  private final SuspenderProperties cfg;
  private final MeterRegistry registry;
  private final ConnectionMonitor connectionMonitor;

  // single mode: candidate keys pending suspension, and suspended keys awaiting their resume
  // deadline. Both touched only from the single-threaded suspend/resume executor tasks below, but
  // kept concurrent so the resume gauge can read the size safely.
  private final Deque<Long> candidates = new ArrayDeque<>();
  private final Map<Long, Instant> suspendedUntil = new ConcurrentHashMap<>();

  private Counter suspendRequests;
  private Counter resumeRequests;
  private Counter suspendErrors;
  private Counter resumeErrors;
  private ScheduledExecutorService executor;

  public Suspender(
      final CamundaClient client,
      final LoadTesterProperties properties,
      final MeterRegistry registry,
      final ConnectionMonitor connectionMonitor) {
    this.client = client;
    cfg = properties.getSuspender();
    this.registry = registry;
    this.connectionMonitor = connectionMonitor;
  }

  @Override
  public void run(final String... args) {
    if (!cfg.isEnabled()) {
      LOG.info("Suspender disabled (load-tester.suspender.enabled=false); idling as baseline arm");
      return;
    }

    connectionMonitor.awaitAndPrintTopology();
    registerMetrics();

    LOG.info(
        "Starting suspender: mode={}, processId={}, holdDuration={}",
        cfg.getMode(),
        cfg.getProcessId(),
        cfg.getHoldDuration());

    executor = Executors.newScheduledThreadPool(2);
    switch (cfg.getMode()) {
      case SINGLE -> startSingleMode();
      case BATCH -> startBatchMode();
    }
  }

  private void registerMetrics() {
    suspendRequests =
        Counter.builder("suspender_suspend_requests_total")
            .description("Number of suspend requests issued by the suspender")
            .register(registry);
    resumeRequests =
        Counter.builder("suspender_resume_requests_total")
            .description("Number of resume requests issued by the suspender")
            .register(registry);
    suspendErrors =
        Counter.builder("suspender_errors_total")
            .description("Number of failed suspend/resume requests")
            .tag("op", "suspend")
            .register(registry);
    resumeErrors =
        Counter.builder("suspender_errors_total")
            .description("Number of failed suspend/resume requests")
            .tag("op", "resume")
            .register(registry);
    Gauge.builder("suspender_in_flight_suspended", suspendedUntil, Map::size)
        .description("Instances currently suspended by the suspender, awaiting resume")
        .register(registry);
  }

  // ---- SINGLE mode -----------------------------------------------------------------------------

  private void startSingleMode() {
    final long intervalNanos = (long) (NANOS_PER_SECOND / cfg.getRatePerSecond());
    LOG.info(
        "Single mode: suspending one instance every {}ns (rate {}/{})",
        intervalNanos,
        cfg.getRate(),
        cfg.getRateDuration());

    executor.scheduleAtFixedRate(
        this::suspendOne, intervalNanos, intervalNanos, TimeUnit.NANOSECONDS);
    // Resume sweep runs frequently enough to keep hold times close to the configured value.
    executor.scheduleWithFixedDelay(this::resumeDue, 1, 1, TimeUnit.SECONDS);
  }

  private void suspendOne() {
    try {
      if (candidates.isEmpty()) {
        refillCandidates();
      }
      final Long key = candidates.poll();
      if (key == null) {
        return;
      }
      client.newSuspendProcessInstanceCommand(key).send().join();
      suspendRequests.increment();
      suspendedUntil.put(key, Instant.now().plus(cfg.getHoldDuration()));
    } catch (final Exception e) {
      suspendErrors.increment();
      THROTTLED_LOGGER.warn("Failed to suspend instance", e);
    }
  }

  private void refillCandidates() {
    final var response =
        client
            .newProcessInstanceSearchRequest()
            .filter(
                f -> f.processDefinitionId(cfg.getProcessId()).state(ProcessInstanceState.ACTIVE))
            .sort(s -> s.startDate().asc())
            .page(p -> p.limit(cfg.getSampleSize()))
            .send()
            .join();
    for (final ProcessInstance pi : response.items()) {
      final long key = pi.getProcessInstanceKey();
      if (!suspendedUntil.containsKey(key)) {
        candidates.add(key);
      }
    }
  }

  private void resumeDue() {
    final Instant now = Instant.now();
    for (final Map.Entry<Long, Instant> entry : Set.copyOf(suspendedUntil.entrySet())) {
      if (entry.getValue().isAfter(now)) {
        continue;
      }
      final long key = entry.getKey();
      try {
        client.newResumeProcessInstanceCommand(key).send().join();
        resumeRequests.increment();
        suspendedUntil.remove(key);
      } catch (final Exception e) {
        resumeErrors.increment();
        THROTTLED_LOGGER.warn("Failed to resume instance {}", key, e);
        // Keep the key so a later sweep retries it rather than leaking a suspended instance.
      }
    }
  }

  // ---- BATCH mode ------------------------------------------------------------------------------

  private void startBatchMode() {
    final long intervalMs = cfg.getBatchInterval().toMillis();
    LOG.info(
        "Batch mode: suspend batch every {}ms, resume batch offset by holdDuration {}",
        intervalMs,
        cfg.getHoldDuration());

    executor.scheduleWithFixedDelay(
        this::suspendBatch, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    // Offset the resume cycle by the hold duration so suspended instances are held before resume.
    executor.scheduleWithFixedDelay(
        this::resumeBatch,
        cfg.getHoldDuration().toMillis() + intervalMs,
        intervalMs,
        TimeUnit.MILLISECONDS);
  }

  private void suspendBatch() {
    try {
      client
          .newCreateBatchOperationCommand()
          .processInstanceSuspend()
          .filter(f -> f.processDefinitionId(cfg.getProcessId()).state(ProcessInstanceState.ACTIVE))
          .send()
          .join();
      suspendRequests.increment();
    } catch (final Exception e) {
      suspendErrors.increment();
      THROTTLED_LOGGER.warn("Failed to create suspend batch operation", e);
    }
  }

  private void resumeBatch() {
    try {
      client
          .newCreateBatchOperationCommand()
          .processInstanceResume()
          .filter(
              f -> f.processDefinitionId(cfg.getProcessId()).state(ProcessInstanceState.SUSPENDED))
          .send()
          .join();
      resumeRequests.increment();
    } catch (final Exception e) {
      resumeErrors.increment();
      THROTTLED_LOGGER.warn("Failed to create resume batch operation", e);
    }
  }

  @PreDestroy
  public void shutdown() {
    if (executor != null && !executor.isShutdown()) {
      executor.shutdownNow();
    }
  }
}
