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
import io.camunda.zeebe.dynamic.config.changes.PhysicalTenantConfigChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.PhysicalTenantConfigReference;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

/**
 * Runs the configuration changes that dynamic-config hands to this member through {@link
 * PhysicalTenantLifecycleService}: a tenant that does not run here is added as the incarnation the
 * cluster agreed on, a running one is replaced, and a removed one is stopped. A change whose
 * configuration this member already runs, by hash, restarts nothing.
 *
 * <p>Changes run one at a time on a thread of their own, since a tenant restart blocks until the
 * module is up again. A change this member cannot apply fails, and dynamic-config retries it; the
 * tenant reports {@link State#DEGRADED} until it applies.
 *
 * <p>The version each tenant runs with on this member is exposed as the gauge {@value
 * #APPLIED_VERSION_METRIC}.
 */
@NullMarked
public final class PhysicalTenantConfigExecutor
    implements PhysicalTenantConfigChangeExecutor, AutoCloseable {

  static final String APPLIED_VERSION_METRIC = "zeebe.physical.tenant.config.applied.version";
  private static final Logger LOG = Loggers.SYSTEM_LOGGER;
  private static final Duration READY_TIMEOUT = Duration.ofMinutes(2);

  private final PhysicalTenantLifecycleService lifecycle;
  private final DefaultPhysicalTenantRegistry registry;
  private final MeterRegistry meterRegistry;
  private final ExecutorService changes =
      Executors.newSingleThreadExecutor(
          Thread.ofPlatform().name("physical-tenant-config").daemon().factory());
  private final Map<String, Applied> applied = new ConcurrentHashMap<>();
  private final Map<String, Gauge> gauges = new ConcurrentHashMap<>();

  public PhysicalTenantConfigExecutor(
      final PhysicalTenantLifecycleService lifecycle,
      final DefaultPhysicalTenantRegistry registry,
      final MeterRegistry meterRegistry) {
    this.lifecycle = lifecycle;
    this.registry = registry;
    this.meterRegistry = meterRegistry;
  }

  /** Records the subtree a tenant runs with since this node started, before any change. */
  void runsFromStaticConfiguration(
      final String physicalTenantId, final Map<String, String> subtree) {
    applied.put(
        physicalTenantId,
        new Applied(
            0,
            PhysicalTenantConfigCodec.sha256(PhysicalTenantConfigCodec.encode(subtree)),
            subtree));
    registerGauge(physicalTenantId);
  }

  /** Records that a tenant runs the given version, if it runs the configuration it names. */
  void runsVersion(final String physicalTenantId, final PhysicalTenantConfigReference reference) {
    applied.computeIfPresent(
        physicalTenantId,
        (id, running) ->
            running.sha256().equals(reference.sha256())
                ? new Applied(reference.version(), running.sha256(), running.subtree())
                : running);
  }

  /** What a tenant runs with on this member. */
  Optional<Applied> applied(final String physicalTenantId) {
    return Optional.ofNullable(applied.get(physicalTenantId));
  }

  @Override
  public CompletableFuture<Void> apply(
      final String physicalTenantId,
      final PhysicalTenantConfigReference reference,
      final byte[] configuration) {
    return CompletableFuture.runAsync(
        () -> applyNow(physicalTenantId, reference, configuration), changes);
  }

  @Override
  public CompletableFuture<Void> stop(final String physicalTenantId) {
    return CompletableFuture.runAsync(
        () -> {
          if (registry.find(physicalTenantId).isPresent()) {
            lifecycle.remove(physicalTenantId);
          }
          applied.remove(physicalTenantId);
          final var gauge = gauges.remove(physicalTenantId);
          if (gauge != null) {
            meterRegistry.remove(gauge);
          }
        },
        changes);
  }

  private void applyNow(
      final String physicalTenantId,
      final PhysicalTenantConfigReference reference,
      final byte[] configuration) {
    final var subtree = PhysicalTenantConfigCodec.decode(configuration);
    final var running = applied.get(physicalTenantId);
    final var runsHere = registry.find(physicalTenantId).isPresent();
    if (runsHere && running != null && running.sha256().equals(reference.sha256())) {
      record(physicalTenantId, reference, subtree);
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
        lifecycle.replace(physicalTenantId, PhysicalTenantConfigCodec.asProperties(subtree));
        awaitReady(physicalTenantId);
      } else {
        lifecycle.add(
            physicalTenantId,
            PhysicalTenantConfigCodec.asProperties(subtree),
            UUID.fromString(reference.incarnationId()));
      }
    } catch (final PhysicalTenantChangeException e) {
      if (e.reason() == Reason.INVALID && registry.find(physicalTenantId).isPresent()) {
        registry.reportHealth(
            physicalTenantId,
            new PhysicalTenantHealth(
                State.DEGRADED,
                "cannot apply version %d of its configuration: %s"
                    .formatted(reference.version(), e.getMessage())));
      }
      throw e;
    }
    record(physicalTenantId, reference, subtree);
  }

  /**
   * Holds the rollout on this member until the restarted tenant installed its partitions again, so
   * that the next member restarts only once this one serves; otherwise a partition could lose its
   * quorum while every replica restarts at once. A tenant that is being added has no partitions
   * yet: they are bootstrapped once every member runs it, so an add is not held.
   */
  private void awaitReady(final String physicalTenantId) {
    try {
      lifecycle.awaitReady(physicalTenantId).get(READY_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    } catch (final ExecutionException | TimeoutException e) {
      throw new IllegalStateException(
          "Physical tenant '%s' did not become ready after its restart".formatted(physicalTenantId),
          e);
    }
  }

  private void record(
      final String physicalTenantId,
      final PhysicalTenantConfigReference reference,
      final Map<String, String> subtree) {
    applied.put(physicalTenantId, new Applied(reference.version(), reference.sha256(), subtree));
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

  /** The configuration a tenant runs with on this member, and its version (0 if from the file). */
  record Applied(long version, String sha256, Map<String, String> subtree) {}
}
