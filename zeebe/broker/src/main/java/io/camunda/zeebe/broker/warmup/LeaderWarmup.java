/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.warmup;

import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.broker.PartitionRaftListener;
import io.camunda.zeebe.broker.system.PhysicalTenantContext;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.LeaderWarmupCfg;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.broker.warmup.LeaderWarmupMetrics.State;
import io.camunda.zeebe.broker.warmup.WarmupWorkload.Outcome;
import io.camunda.zeebe.util.FileUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.lang.management.CompilationMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Warms up leader-only code on a broker that has not led a partition since it started. Once all of
 * the broker's partitions are installed and healthy and a delay has passed, and only if it has
 * never become leader, a one-shot synthetic workload runs through an isolated {@link ScratchEngine}
 * on a dedicated thread, so that the JIT has compiled command processing before the broker has to
 * do it for real.
 *
 * <p>Becoming leader of any partition stops the warm-up at the next event it handles. The listener
 * only raises a flag, so it never delays the role transition. Failures inside the warm-up are
 * logged and end it; they never affect the broker.
 */
@NullMarked
public final class LeaderWarmup implements PartitionRaftListener {

  static final Logger LOG = LoggerFactory.getLogger(LeaderWarmup.class);
  private static final Duration HEALTH_CHECK_INTERVAL = Duration.ofSeconds(1);

  private final LeaderWarmupCfg cfg;
  private final BrokerCfg brokerCfg;
  private final PhysicalTenantContext tenantContext;
  private final BrokerHealthCheckService healthCheckService;
  private final LeaderWarmupMetrics metrics;
  private final CompletableFuture<Void> stopRequested = new CompletableFuture<>();
  private final CompletableFuture<Void> terminated = new CompletableFuture<>();
  private final Thread thread;
  private volatile boolean hasBeenLeader;
  private volatile String stopReason = "";

  public LeaderWarmup(
      final LeaderWarmupCfg cfg,
      final BrokerCfg brokerCfg,
      final PhysicalTenantContext tenantContext,
      final BrokerHealthCheckService healthCheckService,
      final MeterRegistry meterRegistry) {
    this.cfg = cfg;
    this.brokerCfg = brokerCfg;
    this.tenantContext = tenantContext;
    this.healthCheckService = healthCheckService;
    metrics = new LeaderWarmupMetrics(meterRegistry);
    thread = new Thread(this::run, "zeebe-leader-warmup");
    thread.setDaemon(true);
  }

  public void start() {
    thread.start();
  }

  /** Stops the warm-up; the returned future completes once its resources have been released. */
  public CompletableFuture<Void> closeAsync() {
    requestStop("the broker is shutting down");
    return terminated;
  }

  @Override
  public void onBecameRaftFollower(final PartitionId partitionId, final long term) {}

  @Override
  public void onBecameRaftLeader(final PartitionId partitionId, final long term) {
    hasBeenLeader = true;
    requestStop("this broker became leader for partition " + partitionId);
  }

  private void requestStop(final String reason) {
    if (!stopRequested.isDone()) {
      stopReason = reason;
      stopRequested.complete(null);
    }
  }

  private void run() {
    try {
      runWarmup();
    } catch (final Throwable e) {
      metrics.setState(State.FAILED);
      LOG.warn("Leader warm-up failed and was abandoned; this does not affect the broker", e);
    } finally {
      terminated.complete(null);
    }
  }

  private void runWarmup() throws Exception {
    while (!healthCheckService.isBrokerHealthy()) {
      if (waitForStop(HEALTH_CHECK_INTERVAL)) {
        skip(stopReason);
        return;
      }
    }
    if (waitForStop(cfg.getStartDelay())) {
      skip(stopReason);
      return;
    }
    if (hasBeenLeader) {
      skip("this broker has already been leader");
      return;
    }

    final var directory = Path.of(brokerCfg.getData().getDirectory()).resolve("leader-warmup");
    FileUtil.deleteFolderIfExists(directory);
    Files.createDirectories(directory);

    LOG.info(
        "Starting leader warm-up: {} process instances, at most {} in flight, for at most {}",
        cfg.getProcessInstances(),
        cfg.getMaxInFlightInstances(),
        cfg.getMaxDuration());
    metrics.setState(State.RUNNING);
    final var startNanos = System.nanoTime();
    final var compilationMillisBefore = compilationMillis();

    final var workload =
        new WarmupWorkload(cfg.getProcessInstances(), cfg.getMaxInFlightInstances());
    final Outcome outcome;
    try (final var ignored =
        new ScratchEngine(
            directory,
            brokerCfg,
            tenantContext,
            workload.transport(),
            new ScratchJobStreamer(
                WarmupWorkload.STREAMED_JOB_TYPE, WarmupWorkload.CLAIMS, workload::onJobPushed))) {
      outcome =
          workload.run(
              stopRequested::isDone,
              () -> !healthCheckService.isBrokerHealthy(),
              startNanos + cfg.getMaxDuration().toNanos());
    } finally {
      FileUtil.deleteFolderIfExists(directory);
    }

    final var elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
    metrics.setDuration(elapsed);
    final var compilation = Duration.ofMillis(compilationMillis() - compilationMillisBefore);
    switch (outcome) {
      case COMPLETED -> {
        metrics.setState(State.COMPLETED);
        LOG.info(
            "Completed leader warm-up in {}: {} process instances, {} commands sent, {} JIT compilation time",
            elapsed,
            workload.completedInstances(),
            workload.commandsSent(),
            compilation);
      }
      case TIMED_OUT -> {
        metrics.setState(State.TIMED_OUT);
        LOG.info(
            "Stopped leader warm-up after reaching its maximum duration {}: {} process instances, {} commands sent, {} JIT compilation time",
            elapsed,
            workload.completedInstances(),
            workload.commandsSent(),
            compilation);
      }
      case CANCELLED -> {
        metrics.setState(State.CANCELLED);
        LOG.info(
            "Cancelled leader warm-up after {} because {}: {} process instances, {} commands sent",
            elapsed,
            stopReason,
            workload.completedInstances(),
            workload.commandsSent());
      }
      default -> throw new IllegalStateException("Unexpected warm-up outcome " + outcome);
    }
  }

  private void skip(final String reason) {
    metrics.setState(State.SKIPPED);
    LOG.info("Skipping leader warm-up because {}", reason);
  }

  /** Returns true if a stop was requested before the timeout elapsed. */
  private boolean waitForStop(final Duration timeout) throws InterruptedException {
    try {
      stopRequested.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      return true;
    } catch (final TimeoutException e) {
      return false;
    } catch (final ExecutionException e) {
      return true;
    }
  }

  private static long compilationMillis() {
    final @Nullable CompilationMXBean compilation = ManagementFactory.getCompilationMXBean();
    return compilation != null && compilation.isCompilationTimeMonitoringSupported()
        ? compilation.getTotalCompilationTime()
        : 0;
  }
}
