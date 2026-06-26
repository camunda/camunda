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
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleCounterBuilder;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.LongHistogramBuilder;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *   <li>{@code histogramBuilder().build()} — maps to Micrometer {@link Timer}; {@code record()}
 *       converts seconds (unit {@code "s"}) to nanoseconds before recording.
 *   <li>Double gauge builders — delegated to noop.
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

  private static final Logger LOG = LoggerFactory.getLogger(MicrometerMeterProvider.class);

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
   *
   * <p>Hot path for {@code otel.sdk.log.created} (empty attrs): returns {@link #ORIGIN_ONLY_TAGS}
   * singleton — zero allocation. Non-empty attrs (e.g. {@code error.type=queue_full}): rebuilds
   * {@link Tags} on every call for the map key. The {@link
   * io.micrometer.core.instrument.Counter}/{@link io.micrometer.core.instrument.Timer} itself is
   * cached in the instrument's {@link ConcurrentHashMap} after first registration. Further caching
   * of {@link Tags} objects by {@link io.opentelemetry.api.common.Attributes} would require a
   * second CHM lookup and is not worth the added complexity given the low OTel SDK self-metric call
   * rate.
   */
  static Tags toMicrometerTags(final Attributes attributes) {
    if (attributes.isEmpty()) {
      return ORIGIN_ONLY_TAGS;
    }
    final var pairs = new ArrayList<String>(attributes.size() * 2 + 2);
    attributes.forEach(
        (key, value) -> {
          pairs.add(sanitizeKey(key.getKey()));
          pairs.add(sanitizeValue(String.valueOf(value)));
        });
    pairs.add(COMPONENT_TAG_KEY);
    pairs.add(COMPONENT_TAG_VALUE);
    return Tags.of(pairs.toArray(new String[0]));
  }

  /** Sanitizes an OTel attribute key to a valid Prometheus label name. */
  private static String sanitizeKey(final String key) {
    // Replace any character that is not [a-zA-Z0-9_] with '_'
    return key.replaceAll("[^a-zA-Z0-9_]", "_");
  }

  /** Strips control characters from a Prometheus label value. */
  private static String sanitizeValue(final String value) {
    return value.replaceAll("[\\r\\n]", "");
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
      return new BridgeDoubleHistogramBuilder(registry, name);
    }

    @Override
    public DoubleGaugeBuilder gaugeBuilder(final String name) {
      // Noop: OTel SDK self-metrics don't emit double gauge instruments; all observable gauges use
      // upDownCounterBuilder().buildWithCallback() instead.
      LOG.warn(
          "MicrometerMeterProvider: gauge '{}' is not bridged to Micrometer; measurements will be lost",
          name);
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
      // Noop: OTel SDK self-metrics use only the long (integer) counter variant.
      LOG.warn(
          "MicrometerMeterProvider: counter '{}' requested as double; not bridged to Micrometer",
          name);
      return NOOP_METER.counterBuilder(name).ofDoubles();
    }

    @Override
    public LongCounter build() {
      return new BridgeLongCounter(registry, name, description);
    }

    @Override
    public ObservableLongCounter buildWithCallback(
        final Consumer<ObservableLongMeasurement> callback) {
      // Noop: no OTel SDK self-metric uses an observable (pull-model) long counter.
      LOG.warn(
          "MicrometerMeterProvider: observable counter '{}' is not bridged to Micrometer; measurements will be lost",
          name);
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
      try {
        final Tags tags = toMicrometerTags(attributes);
        counters
            .computeIfAbsent(
                tags,
                t -> Counter.builder(name).description(description).tags(t).register(registry))
            .increment(value);
      } catch (final Exception e) {
        LOG.warn("Failed to record metric {}: {}", name, e.getMessage());
      }
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
      // Noop: OTel SDK self-metrics use only the long (integer) up-down counter variant.
      LOG.warn(
          "MicrometerMeterProvider: up-down counter '{}' requested as double; not bridged to Micrometer",
          name);
      return NOOP_METER.upDownCounterBuilder(name).ofDoubles();
    }

    @Override
    public LongUpDownCounter build() {
      return new BridgeLongUpDownCounter(registry, name, description);
    }

    @Override
    public ObservableLongUpDownCounter buildWithCallback(
        final Consumer<ObservableLongMeasurement> callback) {
      // On each Prometheus scrape, the Gauge supplier fires the OTel callback to refresh all
      // values. One Gauge is registered per distinct attribute set seen; new ones are added lazily.
      final ConcurrentHashMap<Tags, AtomicLong> backings = new ConcurrentHashMap<>();
      final AtomicLong defaultBacking = new AtomicLong();
      backings.put(ORIGIN_ONLY_TAGS, defaultBacking);
      Gauge.builder(
              name,
              () -> {
                callback.accept(
                    new ObservableLongMeasurement() {
                      @Override
                      public void record(final long value) {
                        defaultBacking.set(value);
                      }

                      @Override
                      public void record(final long value, final Attributes attributes) {
                        final Tags tags = toMicrometerTags(attributes);
                        if (tags.equals(ORIGIN_ONLY_TAGS)) {
                          defaultBacking.set(value);
                        } else {
                          backings
                              .computeIfAbsent(
                                  tags,
                                  t -> {
                                    final AtomicLong b = new AtomicLong();
                                    Gauge.builder(name, b, a -> (double) a.get())
                                        .description(description)
                                        .tags(t)
                                        .register(registry);
                                    return b;
                                  })
                              .set(value);
                        }
                      }
                    });
                return (double) defaultBacking.get();
              })
          .description(description)
          .tags(ORIGIN_ONLY_TAGS)
          .register(registry);
      // Return a noop — the OTel caller only uses this for unregistration which we don't need.
      return NOOP_METER.upDownCounterBuilder(name).buildWithCallback(callback);
    }
  }

  private static final class BridgeLongUpDownCounter implements LongUpDownCounter {
    private final MeterRegistry registry;
    private final String name;
    private final String description;
    private final ConcurrentHashMap<Tags, AtomicLong> gauges = new ConcurrentHashMap<>();

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
      try {
        final Tags tags = toMicrometerTags(attributes);
        final var backing =
            gauges.computeIfAbsent(
                tags,
                t -> {
                  final var atomicLong = new AtomicLong();
                  Gauge.builder(name, atomicLong, a -> (double) a.get())
                      .description(description)
                      .tags(t)
                      .register(registry);
                  return atomicLong;
                });
        backing.addAndGet(value);
      } catch (final Exception e) {
        LOG.warn("Failed to record metric {}: {}", name, e.getMessage());
      }
    }

    @Override
    public void add(final long value, final Attributes attributes, final Context context) {
      add(value, attributes);
    }
  }

  // -------------------------------------------------------------------------
  // DoubleHistogram builder + instrument
  // -------------------------------------------------------------------------

  // Note: DoubleHistogramBuilder has no buildWithCallback — observable histograms are not part of
  // the OTel SDK LATEST internal-telemetry schema, so no noop override is needed here.
  private static final class BridgeDoubleHistogramBuilder implements DoubleHistogramBuilder {
    private final MeterRegistry registry;
    private final String name;
    private String description = "";

    BridgeDoubleHistogramBuilder(final MeterRegistry registry, final String name) {
      this.registry = registry;
      this.name = name;
    }

    @Override
    public DoubleHistogramBuilder setDescription(final String description) {
      this.description = description;
      return this;
    }

    @Override
    public DoubleHistogramBuilder setUnit(final String unit) {
      return this;
    }

    @Override
    public DoubleHistogramBuilder setExplicitBucketBoundariesAdvice(
        final java.util.List<Double> bucketBoundaries) {
      return this;
    }

    @Override
    public LongHistogramBuilder ofLongs() {
      // Noop: OTel SDK self-metrics use only the double histogram variant (export duration in "s").
      LOG.warn(
          "MicrometerMeterProvider: histogram '{}' requested as long; not bridged to Micrometer",
          name);
      return NOOP_METER.histogramBuilder(name).ofLongs();
    }

    @Override
    public DoubleHistogram build() {
      return new BridgeDoubleHistogram(registry, name, description);
    }
  }

  private static final class BridgeDoubleHistogram implements DoubleHistogram {
    private final MeterRegistry registry;
    private final String name;
    private final String description;
    private final ConcurrentHashMap<Tags, Timer> timers = new ConcurrentHashMap<>();

    BridgeDoubleHistogram(
        final MeterRegistry registry, final String name, final String description) {
      this.registry = registry;
      this.name = name;
      this.description = description;
    }

    @Override
    public void record(final double amount) {
      record(amount, Attributes.empty());
    }

    @Override
    public void record(final double amount, final Attributes attributes) {
      try {
        final Tags tags = toMicrometerTags(attributes);
        timers
            .computeIfAbsent(
                tags, t -> Timer.builder(name).description(description).tags(t).register(registry))
            .record((long) (amount * 1_000_000_000L), TimeUnit.NANOSECONDS);
      } catch (final Exception e) {
        LOG.warn("Failed to record metric {}: {}", name, e.getMessage());
      }
    }

    @Override
    public void record(final double amount, final Attributes attributes, final Context context) {
      record(amount, attributes);
    }
  }
}
