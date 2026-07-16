/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.internals;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * The "is state healthy" store-overlay pack: a bounded cache's active-overlay size (entries and an
 * approximate byte footprint) — the pinned-until-checkpoint records whose growth forces early cuts
 * (see {@link CutMetrics#countEarlyCut()}). One instance is constructed per owning task and reused
 * for every store it opens ({@link #bindOverlay} is called once per store name, tagged by it);
 * gauges are bound over caller-supplied suppliers, so they never allocate or serialize — they
 * simply read a racy-safe field at scrape time.
 *
 * <p><b>Lifecycle:</b> the suppliers must dereference the owner's <em>live</em> state (e.g. read a
 * field the owner re-points on a live reload), never capture one state generation — Micrometer
 * keeps the first gauge registered under an id, so a re-bind after a reload would be silently
 * ignored while the old gauge pins the abandoned generation. And the owner must {@link #close()}
 * this instance when it closes: a re-opened task (same stage/partition, same registry) re-binds its
 * own gauges, which Micrometer would otherwise ignore in favor of the dead task's.
 *
 * <p>Optional by design: without a meter registry, {@link #NOOP} makes {@link #bindOverlay} a
 * harmless no-op, mirroring {@link CutMetrics} and {@link FlowMetrics}.
 */
public interface StoreMetrics {

  /** The zero-allocation no-op used when no meter registry is configured. */
  StoreMetrics NOOP = new StoreMetrics() {};

  /**
   * Store instrumentation for {@code stage} ({@code "projection"} or {@code "aggregation"}) on
   * {@code registry}, or {@link #NOOP} when {@code registry} is {@code null}. Tagged by partition
   * too (in addition to stage): every partition task in a JVM shares one {@link MeterRegistry}, and
   * an untagged-by-partition gauge would silently only ever expose the first-registered partition's
   * reading (Micrometer keeps the first Gauge registered under a given id/tags).
   */
  static StoreMetrics of(final MeterRegistry registry, final String stage, final int partition) {
    return registry == null ? NOOP : new MicrometerStoreMetrics(registry, stage, partition);
  }

  /**
   * Binds {@code store}'s active-overlay entry count and approximate byte footprint as gauges,
   * tagged by the store's name. Call once per store name this task serves; the suppliers must read
   * the owner's live state (see the class javadoc).
   */
  default void bindOverlay(
      final String store, final LongSupplier entries, final LongSupplier approxBytes) {}

  /**
   * Deregisters every gauge this instance bound. The owning task calls this when it closes, so a
   * re-opened task's fresh registration is not ignored and the closed task becomes collectable.
   */
  default void close() {}

  /** The Micrometer-backed implementation; gauges hold only a reference, never allocate to read. */
  final class MicrometerStoreMetrics implements StoreMetrics {

    private final MeterRegistry registry;
    private final String stage;
    private final String partition;
    private final List<Meter> bound = new ArrayList<>();

    private MicrometerStoreMetrics(
        final MeterRegistry registry, final String stage, final int partition) {
      this.registry = registry;
      this.stage = stage;
      this.partition = Integer.toString(partition);
    }

    @Override
    public void bindOverlay(
        final String store, final LongSupplier entries, final LongSupplier approxBytes) {
      // strongReference(true): Gauge holds its state object via a WeakReference by default, and
      // nothing else keeps these caller-supplied suppliers reachable — without it the gauge would
      // silently start reading NaN once the supplier is collected. close() unpins them again.
      bound.add(
          Gauge.builder("eb.streaming.store.overlay.entries", entries, LongSupplier::getAsLong)
              .description("Active overlay entries pinned in the bounded cache until checkpoint")
              .tag("stage", stage)
              .tag("partition", partition)
              .tag("store", store)
              .strongReference(true)
              .register(registry));
      bound.add(
          Gauge.builder("eb.streaming.store.overlay.bytes", approxBytes, LongSupplier::getAsLong)
              .description(
                  "Approximate byte footprint of the cache (active + frozen + clean layers"
                      + " combined)")
              .tag("stage", stage)
              .tag("partition", partition)
              .tag("store", store)
              .strongReference(true)
              .register(registry));
    }

    @Override
    public void close() {
      bound.forEach(registry::remove);
      bound.clear();
    }
  }
}
