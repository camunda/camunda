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
import io.micrometer.core.instrument.Timer;
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
 * interference test: each cycle it creates a dedicated heavy instance (large fan-out of jobs and
 * message subscriptions, plus many short timers), suspends it, holds while the timers come due and
 * their triggers buffer, resumes to drain that buffered backlog, then cancels it — all while the
 * starter's normal workload runs untouched, to measure how much suspend/resume of one heavy
 * definition impacts unrelated running processes.
 */
public class SuspensionMeter implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(SuspensionMeter.class);
  private static final Logger THROTTLED_LOGGER =
      new ThrottledLogger(LoggerFactory.getLogger(SuspensionMeter.class), Duration.ofSeconds(5));
  private static final long NANOS_PER_SECOND = Duration.ofSeconds(1).toNanos();
  // Must match the message name in bpmn/suspend_target.bpmn.
  private static final String TARGET_MESSAGE_NAME = "suspend-target-msg";

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
  private Counter resumeCorrelationMessages;
  // Client-observed latency of the suspend/resume commands. The suspend timer captures the heavy
  // suspend cost directly (there is no broker suspend-duration metric); the resume timer captures
  // the resume command round-trip (the full resume incl. drain is the broker's
  // zeebe_process_instance_resume_duration).
  private Timer suspendLatency;
  private Timer resumeLatency;

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
    resumeCorrelationMessages =
        Counter.builder("suspender_resume_correlation_messages_total")
            .description(
                "Messages published to suspended target instances that correlate on resume")
            .register(registry);
    suspendLatency =
        Timer.builder("suspender_suspend_duration")
            .description("Client-observed latency of the suspend command")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
    resumeLatency =
        Timer.builder("suspender_resume_duration")
            .description("Client-observed latency of the resume command")
            .publishPercentiles(0.5, 0.95, 0.99)
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
      suspendLatency.record(() -> client.newSuspendProcessInstanceCommand(key).send().join());
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
        resumeLatency.record(() -> client.newResumeProcessInstanceCommand(key).send().join());
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
            + "subscriptions={}, timers={} (buffered backlog per instance), timerDuration={}, "
            + "warmup={}, holdDuration={}, settle={}, cycleGap={}",
        cfg.getTargetProcessId(),
        cfg.getTargetInstances(),
        cfg.getJobCount(),
        cfg.getSubscriptionCount(),
        cfg.getTimerCount(),
        cfg.getTimerDuration(),
        cfg.getWarmup(),
        cfg.getHoldDuration(),
        cfg.getSettle(),
        cfg.getBatchInterval());

    deployTarget();

    // The target is recreated every cycle so its timers are fresh (each timer fires exactly once
    // while suspended, then is spent). batchInterval is the idle gap between cycles, during which
    // the target is gone and the cluster is quiet — keeping the A/B interference signal clean.
    executor.scheduleWithFixedDelay(
        this::targetCycle, 0, cfg.getBatchInterval().toMillis(), TimeUnit.MILLISECONDS);
  }

  private void deployTarget() {
    client
        .newDeployResourceCommand()
        .addResourceFromClasspath(cfg.getTargetBpmnPath())
        .send()
        .join();
    LOG.info("Deployed target process from {}", cfg.getTargetBpmnPath());
  }

  /**
   * Builds the fan-out variables for one target instance: a {@code jobs} list driving the
   * multi-instance service task, a {@code subs} list of distinct correlation keys driving the
   * multi-instance receive task, a {@code timers} list driving the multi-instance timer
   * sub-process, and the {@code timerDuration} each timer waits. The {@code keyPrefix} keeps
   * subscription keys unique across instances so their subscriptions never collide.
   */
  static Map<String, Object> buildTargetVariables(
      final int jobCount,
      final int subscriptionCount,
      final int timerCount,
      final String timerDuration,
      final String keyPrefix) {
    final List<Integer> jobs = new ArrayList<>(jobCount);
    for (int i = 0; i < jobCount; i++) {
      jobs.add(i);
    }
    final List<String> subs = new ArrayList<>(subscriptionCount);
    for (int i = 0; i < subscriptionCount; i++) {
      subs.add(keyPrefix + "-" + i);
    }
    final List<Integer> timers = new ArrayList<>(timerCount);
    for (int i = 0; i < timerCount; i++) {
      timers.add(i);
    }
    return Map.of("jobs", jobs, "subs", subs, "timers", timers, "timerDuration", timerDuration);
  }

  /**
   * One full cycle: create fresh target instances, warm up so their fan-out (and timers) are armed,
   * suspend them, hold while each timer comes due and buffers its trigger, resume to drain the
   * buffered backlog, let the drain settle, then cancel the instances so the next cycle starts from
   * a clean slate. Runs on the executor thread, so blocking sleeps are fine.
   */
  /** A created target instance: its key and the correlation-key prefix of its subscriptions. */
  private record TargetInstance(long key, String prefix) {}

  private void targetCycle() {
    final List<TargetInstance> instances = createTargetInstances();
    if (instances.isEmpty()) {
      return;
    }
    final List<Long> keys = instances.stream().map(TargetInstance::key).toList();
    try {
      sleep(cfg.getWarmup());

      for (final long key : keys) {
        try {
          suspendLatency.record(() -> client.newSuspendProcessInstanceCommand(key).send().join());
          suspendRequests.increment();
          suspendedUntil.put(key, Instant.now().plus(cfg.getHoldDuration()));
        } catch (final Exception e) {
          suspendErrors.increment();
          THROTTLED_LOGGER.warn("Failed to suspend target instance {}", key, e);
        }
      }

      // Now that the instances are suspended (subscriptions closed), publish messages that will sit
      // in the message buffer until resume reopens the subscriptions and correlates them — a
      // resume-time correlation burst, distinct from the timer buffered-command drain.
      if (cfg.isGenerateResumeCorrelations()) {
        publishResumeCorrelations(instances);
      }

      sleep(cfg.getHoldDuration());

      for (final long key : keys) {
        try {
          resumeLatency.record(() -> client.newResumeProcessInstanceCommand(key).send().join());
          resumeRequests.increment();
        } catch (final Exception e) {
          resumeErrors.increment();
          THROTTLED_LOGGER.warn("Failed to resume target instance {}", key, e);
        } finally {
          suspendedUntil.remove(key);
        }
      }

      sleep(cfg.getSettle());
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      // Always clean up, even if interrupted, so instances do not accumulate across cycles.
      cancelInstances(keys);
      keys.forEach(suspendedUntil::remove);
    }
  }

  private List<TargetInstance> createTargetInstances() {
    final List<TargetInstance> instances = new ArrayList<>();
    final String timerDuration = isoDuration(cfg.getTimerDuration());
    for (int i = 0; i < cfg.getTargetInstances(); i++) {
      final String prefix = "i" + i;
      final var variables =
          buildTargetVariables(
              cfg.getJobCount(),
              cfg.getSubscriptionCount(),
              cfg.getTimerCount(),
              timerDuration,
              prefix);
      try {
        final var event =
            client
                .newCreateInstanceCommand()
                .bpmnProcessId(cfg.getTargetProcessId())
                .latestVersion()
                .variables(variables)
                .send()
                .join();
        instances.add(new TargetInstance(event.getProcessInstanceKey(), prefix));
        LOG.info("Created target instance {}", event.getProcessInstanceKey());
      } catch (final Exception e) {
        THROTTLED_LOGGER.warn("Failed to create target instance", e);
      }
    }
    return instances;
  }

  /**
   * Publishes one message per subscription of each suspended instance. The instance's subscriptions
   * are closed while it is suspended, so each message is stored in the message buffer with a TTL
   * long enough to survive the hold; when resume reopens the subscriptions they correlate,
   * producing a burst of correlation work at resume time.
   */
  private void publishResumeCorrelations(final List<TargetInstance> instances) {
    // TTL must outlast the remaining hold plus the resume/settle window, with margin to spare.
    final Duration ttl = cfg.getHoldDuration().plus(cfg.getSettle()).plus(Duration.ofSeconds(90));
    for (final TargetInstance instance : instances) {
      for (int j = 0; j < cfg.getSubscriptionCount(); j++) {
        try {
          client
              .newPublishMessageCommand()
              .messageName(TARGET_MESSAGE_NAME)
              .correlationKey(instance.prefix() + "-" + j)
              .timeToLive(ttl)
              .send()
              .join();
          resumeCorrelationMessages.increment();
        } catch (final Exception e) {
          THROTTLED_LOGGER.warn(
              "Failed to publish resume-correlation message for {}", instance.prefix(), e);
        }
      }
    }
  }

  private void cancelInstances(final List<Long> keys) {
    for (final long key : keys) {
      try {
        client.newCancelInstanceCommand(key).send().join();
      } catch (final Exception e) {
        THROTTLED_LOGGER.warn("Failed to cancel target instance {}", key, e);
      }
    }
  }

  private static String isoDuration(final Duration duration) {
    // BPMN timeDuration expects an ISO-8601 duration string (e.g. PT30S); Duration#toString emits
    // exactly that.
    return duration.toString();
  }

  private static void sleep(final Duration duration) throws InterruptedException {
    Thread.sleep(duration.toMillis());
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
