/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleCounterBuilder;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.metrics.ObservableLongCounter;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;
import io.opentelemetry.context.Context;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.jspecify.annotations.NullMarked;

/**
 * A {@link MeterProvider} backed by Micrometer. OTel SDK components that accept a {@link
 * MeterProvider} for internal telemetry (e.g. {@code BatchLogRecordProcessor}, {@code
 * OtlpHttpLogRecordExporter}) can be given this bridge so their self-metrics appear at the broker's
 * Prometheus endpoint without any extra collection cycle.
 *
 * <p>Supported instrument types:
 *
 * <ul>
 *   <li>{@code counterBuilder().build()} — maps to Micrometer {@link Counter}; {@code add()}
 *       increments.
 *   <li>{@code upDownCounterBuilder().build()} — maps to Micrometer {@link Gauge} backed by {@link
 *       AtomicLong}.
 *   <li>{@code upDownCounterBuilder().buildWithCallback()} — registers a Micrometer {@link Gauge}
 *       whose supplier invokes the OTel callback synchronously at scrape time.
 *   <li>Histogram and double gauge builders — delegated to noop.
 * </ul>
 *
 * <p>All meters receive the fixed tag {@value COMPONENT_TAG_KEY}={@value COMPONENT_TAG_VALUE}.
 */
@NullMarked
final class MicrometerMeterProvider implements MeterProvider {

  static final String COMPONENT_TAG_KEY = "otel.component.origin";
  static final String COMPONENT_TAG_VALUE = "analytics_exporter";

  /** Pre-computed tags for the common empty-attributes case — avoids per-call allocation. */
  static final Tags ORIGIN_ONLY_TAGS = Tags.of(COMPONENT_TAG_KEY, COMPONENT_TAG_VALUE);

  /** Noop meter used for unimplemented instrument types (histogram, double gauge, etc.). */
  private static final Meter NOOP_METER = OpenTelemetry.noop().getMeter("noop");

  private final MeterRegistry registry;

  MicrometerMeterProvider(final MeterRegistry registry) {
    this.registry = registry;
  }

  @Override
  public MeterBuilder meterBuilder(final String instrumentationScopeName) {
    return new BridgeMeterBuilder(instrumentationScopeName);
  }

  /**
   * Converts OTel {@link Attributes} to Micrometer {@link Tags}, appending the fixed component
   * origin tag. Returns the pre-computed {@link #ORIGIN_ONLY_TAGS} singleton when attributes are
   * empty to avoid per-call allocation on the hot path.
   */
  static Tags toMicrometerTags(final Attributes attributes) {
    if (attributes.isEmpty()) {
      return ORIGIN_ONLY_TAGS;
    }
    final var pairs = new ArrayList<String>(attributes.size() * 2 + 2);
    attributes.forEach(
        (key, value) -> {
          pairs.add(key.getKey());
          pairs.add(String.valueOf(value));
        });
    pairs.add(COMPONENT_TAG_KEY);
    pairs.add(COMPONENT_TAG_VALUE);
    return Tags.of(pairs.toArray(new String[0]));
  }

  // -------------------------------------------------------------------------
  // MeterBuilder
  // -------------------------------------------------------------------------

  private final class BridgeMeterBuilder implements MeterBuilder {
    @SuppressWarnings("unused")
    private final String scopeName;

    BridgeMeterBuilder(final String scopeName) {
      this.scopeName = scopeName;
    }

    @Override
    public MeterBuilder setSchemaUrl(final String schemaUrl) {
      return this;
    }

    @Override
    public MeterBuilder setInstrumentationVersion(final String instrumentationScopeVersion) {
      return this;
    }

    @Override
    public Meter build() {
      return new BridgeMeter(registry);
    }
  }

  // -------------------------------------------------------------------------
  // Meter
  // -------------------------------------------------------------------------

  private static final class BridgeMeter implements Meter {
    private final MeterRegistry registry;

    BridgeMeter(final MeterRegistry registry) {
      this.registry = registry;
    }

    @Override
    public LongCounterBuilder counterBuilder(final String name) {
      return new BridgeLongCounterBuilder(registry, name);
    }

    @Override
    public LongUpDownCounterBuilder upDownCounterBuilder(final String name) {
      return new BridgeLongUpDownCounterBuilder(registry, name);
    }

    @Override
    public DoubleHistogramBuilder histogramBuilder(final String name) {
      return NOOP_METER.histogramBuilder(name);
    }

    @Override
    public DoubleGaugeBuilder gaugeBuilder(final String name) {
      return NOOP_METER.gaugeBuilder(name);
    }
  }

  // -------------------------------------------------------------------------
  // LongCounter builder + instrument
  // -------------------------------------------------------------------------

  private static final class BridgeLongCounterBuilder implements LongCounterBuilder {
    private final MeterRegistry registry;
    private final String name;
    private String description = "";

    BridgeLongCounterBuilder(final MeterRegistry registry, final String name) {
      this.registry = registry;
      this.name = name;
    }

    @Override
    public LongCounterBuilder setDescription(final String description) {
      this.description = description;
      return this;
    }

    @Override
    public LongCounterBuilder setUnit(final String unit) {
      return this;
    }

    @Override
    public DoubleCounterBuilder ofDoubles() {
      return NOOP_METER.counterBuilder(name).ofDoubles();
    }

    @Override
    public LongCounter build() {
      return new BridgeLongCounter(registry, name, description);
    }

    @Override
    public ObservableLongCounter buildWithCallback(
        final Consumer<ObservableLongMeasurement> callback) {
      // Not used by any OTel SDK self-metrics we care about — delegate to noop.
      return NOOP_METER.counterBuilder(name).buildWithCallback(callback);
    }
  }

  private static final class BridgeLongCounter implements LongCounter {
    private final MeterRegistry registry;
    private final String name;
    private final String description;
    private final ConcurrentHashMap<Tags, Counter> counters = new ConcurrentHashMap<>();

    BridgeLongCounter(final MeterRegistry registry, final String name, final String description) {
      this.registry = registry;
      this.name = name;
      this.description = description;
    }

    @Override
    public void add(final long value) {
      add(value, Attributes.empty());
    }

    @Override
    public void add(final long value, final Attributes attributes) {
      final Tags tags = toMicrometerTags(attributes);
      counters
          .computeIfAbsent(
              tags, t -> Counter.builder(name).description(description).tags(t).register(registry))
          .increment(value);
    }

    @Override
    public void add(final long value, final Attributes attributes, final Context context) {
      add(value, attributes);
    }
  }

  // -------------------------------------------------------------------------
  // LongUpDownCounter builder + instrument
  // -------------------------------------------------------------------------

  private static final class BridgeLongUpDownCounterBuilder implements LongUpDownCounterBuilder {
    private final MeterRegistry registry;
    private final String name;
    private String description = "";

    BridgeLongUpDownCounterBuilder(final MeterRegistry registry, final String name) {
      this.registry = registry;
      this.name = name;
    }

    @Override
    public LongUpDownCounterBuilder setDescription(final String description) {
      this.description = description;
      return this;
    }

    @Override
    public LongUpDownCounterBuilder setUnit(final String unit) {
      return this;
    }

    @Override
    public DoubleUpDownCounterBuilder ofDoubles() {
      return NOOP_METER.upDownCounterBuilder(name).ofDoubles();
    }

    @Override
    public LongUpDownCounter build() {
      return new BridgeLongUpDownCounter(registry, name, description);
    }

    @Override
    public ObservableLongUpDownCounter buildWithCallback(
        final Consumer<ObservableLongMeasurement> callback) {
      // Register a Micrometer Gauge that invokes the OTel callback synchronously at scrape time.
      final Tags commonTags = ORIGIN_ONLY_TAGS;
      final AtomicLong captured = new AtomicLong();
      Gauge.builder(
              name,
              () -> {
                callback.accept(
                    new ObservableLongMeasurement() {
                      @Override
                      public void record(final long value) {
                        captured.set(value);
                      }

                      @Override
                      public void record(final long value, final Attributes attributes) {
                        captured.set(value);
                      }
                    });
                return (double) captured.get();
              })
          .description(description)
          .tags(commonTags)
          .register(registry);
      // Return a noop — the OTel caller only uses this for unregistration which we don't need.
      return NOOP_METER.upDownCounterBuilder(name).buildWithCallback(callback);
    }
  }

  private static final class BridgeLongUpDownCounter implements LongUpDownCounter {
    private final MeterRegistry registry;
    private final String name;
    private final String description;
    // Key: name + "|" + tags string. Value: AtomicLong backing the gauge.
    private final ConcurrentHashMap<String, AtomicLong> gauges = new ConcurrentHashMap<>();

    BridgeLongUpDownCounter(
        final MeterRegistry registry, final String name, final String description) {
      this.registry = registry;
      this.name = name;
      this.description = description;
    }

    @Override
    public void add(final long value) {
      add(value, Attributes.empty());
    }

    @Override
    public void add(final long value, final Attributes attributes) {
      final Tags tags = toMicrometerTags(attributes);
      final var key = name + "|" + tags;
      final var backing =
          gauges.computeIfAbsent(
              key,
              k -> {
                final var atomicLong = new AtomicLong();
                Gauge.builder(name, atomicLong, a -> (double) a.get())
                    .description(description)
                    .tags(tags)
                    .register(registry);
                return atomicLong;
              });
      backing.addAndGet(value);
    }

    @Override
    public void add(final long value, final Attributes attributes, final Context context) {
      add(value, attributes);
    }
  }
}
