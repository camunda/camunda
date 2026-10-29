/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import io.camunda.cluster.DefaultPhysicalTenantRegistry;
import io.camunda.cluster.PhysicalTenantHealth;
import io.camunda.cluster.PhysicalTenantHealth.State;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.broker.PhysicalTenantChangeException.Reason;
import io.camunda.zeebe.broker.PhysicalTenantRolloutReadiness.NotReadyException;
import io.camunda.zeebe.dynamic.config.changes.PhysicalTenantConfigChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.PhysicalTenantConfigReference;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

/**
 * Runs the configuration changes that dynamic-config hands to this member through {@link
 * PhysicalTenantLifecycleService}: a tenant that does not run here is added as the incarnation the
 * cluster agreed on, a running one is replaced, and a removed one is stopped. A change whose
 * configuration this member already runs, by hash, restarts nothing.
 *
 * <p>The changes of one tenant run one after the other; changes of different tenants run at the
 * same time, except that only one tenant's module starts or stops at a time on this member. A
 * replace waits, before the restart, until the tenant's partitions keep a quorum on the other
 * members, and after it, until the tenant serves here again (see {@link
 * PhysicalTenantRolloutReadiness}); only then does the rollout go on to the next member. A tenant
 * that does not become ready in time reports {@link State#DEGRADED} and fails the step, which
 * dynamic-config retries; the retry waits again without restarting the tenant again.
 *
 * <p>The version each tenant runs with on this member is exposed as the gauge {@value
 * #APPLIED_VERSION_METRIC}.
 */
@NullMarked
public final class PhysicalTenantConfigExecutor
    implements PhysicalTenantConfigChangeExecutor, AutoCloseable {

  static final String APPLIED_VERSION_METRIC = "zeebe.physical.tenant.config.applied.version";
  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final PhysicalTenantLifecycleService lifecycle;
  private final DefaultPhysicalTenantRegistry registry;
  private final MeterRegistry meterRegistry;
  private final PhysicalTenantRolloutReadiness readiness;
  private final ExecutorService changes =
      Executors.newThreadPerTaskExecutor(
          Thread.ofVirtual().name("physical-tenant-config-", 0).factory());
  private final ReentrantLock moduleChanges = new ReentrantLock();
  private final Map<String, CompletableFuture<Void>> lastChange = new ConcurrentHashMap<>();
  private final Map<String, Applied> applied = new ConcurrentHashMap<>();
  private final Map<String, RolloutStep> steps = new ConcurrentHashMap<>();
  private final Set<String> degradedByChange = ConcurrentHashMap.newKeySet();
  private final Map<String, Gauge> gauges = new ConcurrentHashMap<>();

  public PhysicalTenantConfigExecutor(
      final PhysicalTenantLifecycleService lifecycle,
      final DefaultPhysicalTenantRegistry registry,
      final MeterRegistry meterRegistry,
      final PhysicalTenantRolloutReadiness readiness) {
    this.lifecycle = lifecycle;
    this.registry = registry;
    this.meterRegistry = meterRegistry;
    this.readiness = readiness;
  }

  /** Records the subtree a tenant runs with since this node started, before any change. */
  void runsFromStaticConfiguration(
      final String physicalTenantId, final Map<String, String> subtree) {
    applied.put(
        physicalTenantId,
        new Applied(
            0,
            PhysicalTenantConfigCodec.sha256(PhysicalTenantConfigCodec.encode(subtree)),
            subtree,
            true));
    registerGauge(physicalTenantId);
  }

  /** Records that a tenant runs the given version, if it runs the configuration it names. */
  void runsVersion(final String physicalTenantId, final PhysicalTenantConfigReference reference) {
    applied.computeIfPresent(
        physicalTenantId,
        (id, running) ->
            running.sha256().equals(reference.sha256())
                ? new Applied(
                    reference.version(), running.sha256(), running.subtree(), running.ready())
                : running);
  }

  /** What a tenant runs with on this member. */
  Optional<Applied> applied(final String physicalTenantId) {
    return Optional.ofNullable(applied.get(physicalTenantId));
  }

  /** What the rollout of a tenant's configuration waits for on this member, if it waits. */
  Optional<RolloutStep> rolloutStep(final String physicalTenantId) {
    return Optional.ofNullable(steps.get(physicalTenantId));
  }

  @Override
  public CompletableFuture<Void> apply(
      final String physicalTenantId,
      final PhysicalTenantConfigReference reference,
      final byte[] configuration) {
    return afterPreviousChange(
        physicalTenantId, () -> applyNow(physicalTenantId, reference, configuration));
  }

  @Override
  public CompletableFuture<Void> stop(final String physicalTenantId) {
    return afterPreviousChange(
        physicalTenantId,
        () -> {
          if (registry.find(physicalTenantId).isPresent()) {
            changeModule(() -> lifecycle.remove(physicalTenantId));
          }
          applied.remove(physicalTenantId);
          steps.remove(physicalTenantId);
          degradedByChange.remove(physicalTenantId);
          final var gauge = gauges.remove(physicalTenantId);
          if (gauge != null) {
            meterRegistry.remove(gauge);
          }
        });
  }

  private CompletableFuture<Void> afterPreviousChange(
      final String physicalTenantId, final Runnable change) {
    return lastChange.compute(
        physicalTenantId,
        (id, previous) ->
            (previous == null ? CompletableFuture.<Void>completedFuture(null) : previous)
                .handle((ignored, error) -> null)
                .thenRunAsync(change, changes));
  }

  private void applyNow(
      final String physicalTenantId,
      final PhysicalTenantConfigReference reference,
      final byte[] configuration) {
    final var subtree = PhysicalTenantConfigCodec.decode(configuration);
    final var running = applied.get(physicalTenantId);
    final var runsHere = registry.find(physicalTenantId).isPresent();
    if (runsHere && running != null && running.sha256().equals(reference.sha256())) {
      if (!running.ready()) {
        awaitReady(physicalTenantId, reference);
      }
      record(physicalTenantId, reference, subtree);
      clearDegradation(physicalTenantId);
      return;
    }
    if (PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID.equals(physicalTenantId)) {
      LOG.warn(
          "Version {} of the default physical tenant's configuration differs from the static"
              + " configuration of this node; the default tenant takes it only with a node restart",
          reference.version());
      registry.reportHealth(
          physicalTenantId,
          new PhysicalTenantHealth(
              State.DEGRADED,
              "runs a configuration that differs from version %d of the cluster"
                  .formatted(reference.version())));
      return;
    }
    try {
      if (runsHere) {
        readiness.awaitQuorumElsewhere(
            physicalTenantId,
            why -> waits(physicalTenantId, reference, "before the restart: " + why));
        changeModule(
            () ->
                lifecycle.replace(
                    physicalTenantId, PhysicalTenantConfigCodec.asProperties(subtree)));
        applied.put(
            physicalTenantId, new Applied(reference.version(), reference.sha256(), subtree, false));
        awaitReady(physicalTenantId, reference);
      } else {
        changeModule(
            () ->
                lifecycle.add(
                    physicalTenantId,
                    PhysicalTenantConfigCodec.asProperties(subtree),
                    UUID.fromString(reference.incarnationId())));
      }
    } catch (final PhysicalTenantChangeException e) {
      if (e.reason() == Reason.INVALID && registry.find(physicalTenantId).isPresent()) {
        registry.reportHealth(
            physicalTenantId,
            new PhysicalTenantHealth(
                State.DEGRADED,
                "cannot apply version %d of its configuration: %s"
                    .formatted(reference.version(), e.getMessage())));
        degradedByChange.add(physicalTenantId);
      }
      throw e;
    }
    record(physicalTenantId, reference, subtree);
  }

  private void awaitReady(
      final String physicalTenantId, final PhysicalTenantConfigReference reference) {
    try {
      readiness.awaitReady(
          physicalTenantId, why -> waits(physicalTenantId, reference, "after the restart: " + why));
    } catch (final NotReadyException e) {
      final var why =
          "applied version %d of its configuration, but is not ready: %s"
              .formatted(reference.version(), e.getMessage());
      LOG.warn("Physical tenant '{}' {}; the rollout waits", physicalTenantId, why);
      registry.reportHealth(physicalTenantId, new PhysicalTenantHealth(State.DEGRADED, why));
      degradedByChange.add(physicalTenantId);
      throw e;
    }
    clearDegradation(physicalTenantId);
  }

  private void clearDegradation(final String physicalTenantId) {
    if (degradedByChange.remove(physicalTenantId)
        && registry
            .health(physicalTenantId)
            .filter(health -> health.state() == State.DEGRADED)
            .isPresent()) {
      registry.reportHealth(physicalTenantId, PhysicalTenantHealth.of(State.HEALTHY));
    }
  }

  private void waits(
      final String physicalTenantId,
      final PhysicalTenantConfigReference reference,
      final String why) {
    steps.compute(
        physicalTenantId,
        (id, step) ->
            step != null && step.version() == reference.version()
                ? new RolloutStep(reference.version(), why, step.since())
                : new RolloutStep(reference.version(), why, Instant.now()));
  }

  private void changeModule(final Runnable change) {
    moduleChanges.lock();
    try {
      change.run();
    } finally {
      moduleChanges.unlock();
    }
  }

  private void record(
      final String physicalTenantId,
      final PhysicalTenantConfigReference reference,
      final Map<String, String> subtree) {
    applied.put(
        physicalTenantId, new Applied(reference.version(), reference.sha256(), subtree, true));
    steps.remove(physicalTenantId);
    registerGauge(physicalTenantId);
  }

  private void registerGauge(final String physicalTenantId) {
    gauges.computeIfAbsent(
        physicalTenantId,
        id ->
            Gauge.builder(
                    APPLIED_VERSION_METRIC,
                    applied,
                    tenants ->
                        Optional.ofNullable(tenants.get(id)).map(Applied::version).orElse(0L))
                .description(
                    "The version of the physical tenant's configuration this member runs; 0 while"
                        + " it runs the static configuration it started with")
                .tag("physicalTenant", id)
                .register(meterRegistry));
  }

  @Override
  public void close() {
    changes.shutdownNow();
    gauges.values().forEach(meterRegistry::remove);
    gauges.clear();
  }

  /**
   * The configuration a tenant runs with on this member, its version (0 if from the file), and
   * whether it was ready since it last restarted with it.
   */
  record Applied(long version, String sha256, Map<String, String> subtree, boolean ready) {}

  /** The rollout of a version waits on this member, since the given instant, for the reason. */
  public record RolloutStep(long version, String waitingFor, Instant since) {}
}
