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
import io.camunda.zeebe.config.SuspenderProperties;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stresses the process-instance suspend/resume feature by periodically suspending running
 * instances, holding them for a configurable duration while the starter (and workers) keep sending
 * traffic so buffered commands accumulate, then resuming them.
 *
 * <p>Runs as a scheduled meter inside the starter (mirroring {@link
 * io.camunda.zeebe.read.DataReadMeter}), so no separate deployment or image is needed: the whole
 * load can be enabled and configured through {@code load-tester.suspender.*} on the existing
 * starter pod. Two drivers are supported (see {@link SuspenderProperties.Mode}): {@code SINGLE}
 * issues one suspend/resume command per instance; {@code BATCH} issues process-instance batch
 * operations over a filter. It is only constructed when {@link SuspenderProperties#isEnabled()} is
 * {@code true}; leaving it disabled is the A/B baseline arm.
 *
 * <p>When {@link SuspenderProperties#isTargetEnabled()} is set, it instead runs the blast-radius /
 * interference test: it deploys a dedicated heavy process definition (large fan-out of jobs and
 * message subscriptions), creates a few instances of it, and suspends/resumes only those on a
 * cadence while the starter's normal workload runs untouched — to measure how much suspend/resume
 * of one heavy definition impacts unrelated running processes.
 */
public class SuspensionMeter implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(SuspensionMeter.class);
  private static final Logger THROTTLED_LOGGER =
      new ThrottledLogger(LoggerFactory.getLogger(SuspensionMeter.class), Duration.ofSeconds(5));
  private static final long NANOS_PER_SECOND = Duration.ofSeconds(1).toNanos();

  private final MeterRegistry registry;
  private final ScheduledExecutorService executor;
  private final CamundaClient client;
  private final SuspenderProperties cfg;

  // Candidate keys pending suspension, and suspended keys awaiting their resume deadline. Touched
  // only from the single-threaded suspend/resume executor tasks below, but kept concurrent so the
  // in-flight gauge can read the size safely.
  private final Deque<Long> candidates = new ArrayDeque<>();
  private final Map<Long, Instant> suspendedUntil = new ConcurrentHashMap<>();

  private Counter suspendRequests;
  private Counter resumeRequests;
  private Counter suspendErrors;
  private Counter resumeErrors;

  public SuspensionMeter(
      final MeterRegistry registry,
      final ScheduledExecutorService executor,
      final CamundaClient client,
      final SuspenderProperties cfg) {
    this.registry = registry;
    this.executor = executor;
    this.client = client;
    this.cfg = cfg;
  }

  /** Registers metrics and schedules the suspend/resume tasks for the configured mode. */
  public void start() {
    registerMetrics();
    if (cfg.isTargetEnabled()) {
      startTargetMode();
      return;
    }
    LOG.info(
        "Starting suspension meter: mode={}, processId={}, holdDuration={}",
        cfg.getMode(),
        cfg.getProcessId(),
        cfg.getHoldDuration());
    switch (cfg.getMode()) {
      case SINGLE -> startSingleMode();
      case BATCH -> startBatchMode();
    }
  }

  private void registerMetrics() {
    suspendRequests =
        Counter.builder("suspender_suspend_requests_total")
            .description("Number of suspend requests issued by the suspension meter")
            .register(registry);
    resumeRequests =
        Counter.builder("suspender_resume_requests_total")
            .description("Number of resume requests issued by the suspension meter")
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
        .description("Instances currently suspended by the suspension meter, awaiting resume")
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

  // ---- TARGET mode (blast-radius / interference test) ------------------------------------------

  private void startTargetMode() {
    LOG.info(
        "Starting suspension meter in target mode: processId={}, instances={}, jobs={}, "
            + "subscriptions={}, holdDuration={}, cycleGap={}",
        cfg.getTargetProcessId(),
        cfg.getTargetInstances(),
        cfg.getJobCount(),
        cfg.getSubscriptionCount(),
        cfg.getHoldDuration(),
        cfg.getBatchInterval());

    deployTarget();
    createTargetInstances();
    if (suspendedUntil.isEmpty() && candidates.isEmpty()) {
      LOG.warn("No target instances were created; suspension meter has nothing to do");
      return;
    }

    // One repeating cycle: suspend all target instances, hold, resume all. The first cycle starts
    // after batchInterval, giving the instances time to reach full fan-out; batchInterval is also
    // the idle gap between cycles.
    executor.scheduleWithFixedDelay(
        this::targetCycle,
        cfg.getBatchInterval().toMillis(),
        cfg.getBatchInterval().toMillis(),
        TimeUnit.MILLISECONDS);
  }

  private void deployTarget() {
    client
        .newDeployResourceCommand()
        .addResourceFromClasspath(cfg.getTargetBpmnPath())
        .send()
        .join();
    LOG.info("Deployed target process from {}", cfg.getTargetBpmnPath());
  }

  private void createTargetInstances() {
    for (int i = 0; i < cfg.getTargetInstances(); i++) {
      final var variables =
          buildTargetVariables(cfg.getJobCount(), cfg.getSubscriptionCount(), "i" + i);
      try {
        final var event =
            client
                .newCreateInstanceCommand()
                .bpmnProcessId(cfg.getTargetProcessId())
                .latestVersion()
                .variables(variables)
                .send()
                .join();
        // Reused across cycles; not yet suspended, so it starts as a candidate.
        candidates.add(event.getProcessInstanceKey());
        LOG.info("Created target instance {}", event.getProcessInstanceKey());
      } catch (final Exception e) {
        THROTTLED_LOGGER.warn("Failed to create target instance", e);
      }
    }
  }

  /**
   * Builds the fan-out variables for one target instance: a {@code jobs} list driving the
   * multi-instance service task and a {@code subs} list of distinct correlation keys driving the
   * multi-instance message catch. The {@code keyPrefix} keeps subscription keys unique across
   * instances so their subscriptions never collide.
   */
  static Map<String, Object> buildTargetVariables(
      final int jobCount, final int subscriptionCount, final String keyPrefix) {
    final List<Integer> jobs = new ArrayList<>(jobCount);
    for (int i = 0; i < jobCount; i++) {
      jobs.add(i);
    }
    final List<String> subs = new ArrayList<>(subscriptionCount);
    for (int i = 0; i < subscriptionCount; i++) {
      subs.add(keyPrefix + "-" + i);
    }
    return Map.of("jobs", jobs, "subs", subs);
  }

  private void targetCycle() {
    final Instant deadline = Instant.now().plus(cfg.getHoldDuration());
    for (final long key : List.copyOf(candidates)) {
      try {
        client.newSuspendProcessInstanceCommand(key).send().join();
        suspendRequests.increment();
        candidates.remove(key);
        suspendedUntil.put(key, deadline);
      } catch (final Exception e) {
        suspendErrors.increment();
        THROTTLED_LOGGER.warn("Failed to suspend target instance {}", key, e);
      }
    }

    try {
      Thread.sleep(cfg.getHoldDuration().toMillis());
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      return;
    }

    for (final long key : List.copyOf(suspendedUntil.keySet())) {
      try {
        client.newResumeProcessInstanceCommand(key).send().join();
        resumeRequests.increment();
        suspendedUntil.remove(key);
        candidates.add(key);
      } catch (final Exception e) {
        resumeErrors.increment();
        THROTTLED_LOGGER.warn("Failed to resume target instance {}", key, e);
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

  @Override
  public void close() {
    executor.shutdownNow();
  }
}
