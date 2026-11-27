/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import static io.camunda.zeebe.engine.metrics.JobBacklogMetricsDoc.ACTIVATABLE_JOBS;
import static io.camunda.zeebe.engine.metrics.JobBacklogMetricsDoc.ACTIVATION_LAG;

import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.EngineKeyNames;
import io.camunda.zeebe.util.micrometer.BoundedMeterCache;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter.MeterProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Tracks, per job type, how many jobs are activatable and how long the type has waited for an
 * activation.
 *
 * <p>Only types with a non-zero activatable count have an entry in the map: an entry is removed
 * when its count drops to zero, so the map is bounded by the number of types with a backlog, not by
 * every job type ever seen. Job types can be computed at runtime, so this matters.
 *
 * <p>The mutating methods are called from the stream processor actor of the partition only. The
 * gauges are read on scrape from other threads: each gauge looks its type up by name at scrape
 * time, so a pruned entry reads as zero and is never orphaned behind a registered gauge.
 *
 * <p>The gauges are registered lazily and are dropped and registered again by {@link
 * #resetActivatable(Map)}. The engine re-creates its state on the same registry, and a gauge
 * already registered there keeps reading the values of the state that created it.
 *
 * <p>Assumption: {@link #resetActivatable(Map)} removes every meter with the two names from the
 * registry it is given, so it is correct only while exactly one live instance owns a per-partition
 * registry. The engine re-creates its state on the same registry and drops the old instance, which
 * is why this removal is safe.
 *
 * <p>Known limit: gauges are cached per type in a {@link BoundedMeterCache} of 500 entries per
 * partition. When a type is evicted, its gauges are removed from the registry and are only
 * registered again when that type's count next changes or on the next recovery. A type that stays
 * stuck without further changes can therefore be missing from scrapes, and the alert cannot see
 * types beyond this bound.
 */
@NullMarked
public final class JobBacklogMetrics {

  private final MeterRegistry registry;
  private final Map<String, TypeBacklog> backlogByType = new ConcurrentHashMap<>();
  private BoundedMeterCache<Gauge> activatableGauges;
  private BoundedMeterCache<Gauge> lagGauges;

  public JobBacklogMetrics(final MeterRegistry registry) {
    this.registry = registry;
    activatableGauges = newActivatableGauges();
    lagGauges = newLagGauges();
  }

  /** Counts a job of the given type that became activatable. */
  public void incrementActivatable(final String type) {
    final TypeBacklog backlog = backlogByType.computeIfAbsent(type, t -> new TypeBacklog());
    if (backlog.activatable.get() == 0) {
      // set before the count leaves zero, so a scrape never sees a count without its reference
      backlog.referenceNanos.set(now());
    }
    backlog.activatable.incrementAndGet();
    registerGauges(type);
  }

  /**
   * Counts a job of the given type that is no longer activatable. Never goes below zero: a type
   * that reaches zero loses its entry.
   */
  public void decrementActivatable(final String type) {
    final @Nullable TypeBacklog backlog = backlogByType.get(type);
    if (backlog == null) {
      return;
    }
    if (backlog.activatable.decrementAndGet() <= 0) {
      backlogByType.remove(type);
    }
  }

  /**
   * Replaces the activatable counts with the given ones, e.g. the counts read from state on
   * recovery. Every type not in the map, or with a count of zero, is pruned.
   */
  public void resetActivatable(final Map<String, Long> countsByType) {
    removeGauges();
    activatableGauges = newActivatableGauges();
    lagGauges = newLagGauges();

    backlogByType.clear();
    final long recoveredAt = now();
    countsByType.forEach(
        (type, count) -> {
          if (count > 0) {
            final TypeBacklog backlog = new TypeBacklog();
            backlog.activatable.set(count);
            // the last activation is not part of the state, so the lag restarts at recovery
            backlog.referenceNanos.set(recoveredAt);
            backlogByType.put(type, backlog);
            registerGauges(type);
          }
        });
  }

  /**
   * Records an activation or push of a job of the given type; resets its activation lag. Does not
   * create an entry for a type without a backlog, because the lag reference is set when the backlog
   * starts.
   */
  public void recordActivation(final String type) {
    final @Nullable TypeBacklog backlog = backlogByType.get(type);
    if (backlog != null) {
      backlog.referenceNanos.set(now());
    }
  }

  private void registerGauges(final String type) {
    activatableGauges.get(type);
    lagGauges.get(type);
  }

  private BoundedMeterCache<Gauge> newActivatableGauges() {
    final MeterProvider<Gauge> provider =
        tags -> {
          final String type = typeOf(tags);
          return Gauge.builder(ACTIVATABLE_JOBS.getName(), this, m -> m.activatableCount(type))
              .description(ACTIVATABLE_JOBS.getDescription())
              .tags(Tags.of(tags))
              .strongReference(true)
              .register(registry);
        };
    return BoundedMeterCache.of(registry, provider, EngineKeyNames.JOB_TYPE);
  }

  private BoundedMeterCache<Gauge> newLagGauges() {
    final MeterProvider<Gauge> provider =
        tags -> {
          final String type = typeOf(tags);
          return Gauge.builder(ACTIVATION_LAG.getName(), this, m -> m.lagSeconds(type))
              .description(ACTIVATION_LAG.getDescription())
              .baseUnit(ACTIVATION_LAG.getBaseUnit())
              .tags(Tags.of(tags))
              .strongReference(true)
              .register(registry);
        };
    return BoundedMeterCache.of(registry, provider, EngineKeyNames.JOB_TYPE);
  }

  private static String typeOf(final Iterable<? extends Tag> tags) {
    final String typeKey = EngineKeyNames.JOB_TYPE.asString();
    for (final Tag tag : tags) {
      if (tag.getKey().equals(typeKey)) {
        return tag.getValue();
      }
    }
    throw new IllegalStateException("Expected a '" + typeKey + "' tag on the job backlog meter");
  }

  private long activatableCount(final String type) {
    final @Nullable TypeBacklog backlog = backlogByType.get(type);
    return backlog == null ? 0 : backlog.activatable.get();
  }

  private double lagSeconds(final String type) {
    final @Nullable TypeBacklog backlog = backlogByType.get(type);
    if (backlog == null || backlog.activatable.get() <= 0) {
      return 0;
    }
    final long elapsedNanos = Math.max(0, now() - backlog.referenceNanos.get());
    return elapsedNanos / (double) TimeUnit.SECONDS.toNanos(1);
  }

  private void removeGauges() {
    for (final String name : List.of(ACTIVATABLE_JOBS.getName(), ACTIVATION_LAG.getName())) {
      List.copyOf(registry.find(name).meters()).forEach(registry::remove);
    }
  }

  private long now() {
    return registry.config().clock().monotonicTime();
  }

  private static final class TypeBacklog {
    private final AtomicLong activatable = new AtomicLong();
    private final AtomicLong referenceNanos = new AtomicLong();
  }
}
