/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.micrometer;

import io.camunda.zeebe.util.VisibleForTesting;
import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.noop.NoopGauge;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * A stateful gauge is a custom meter type that wraps a {@link Gauge}, while keeping track of an
 * intermediate state value (the {@link #state}).
 *
 * <p>Using a stateful gauge solves the issue of re-registering the same {@link Gauge} that
 * Micrometer currently has: if you register one gauge with a specific object, and then try again
 * with a different one, it will simply silently ignore the second one. Their reasoning is
 * documented <a
 * href="https://docs.micrometer.io/micrometer/reference/concepts/gauges.html">here</a>
 *
 * <p>In a long living, multithreaded application, the only way to ensure this is to tie the
 * lifetime of a gauge to the lifetime of whatever resource it is tracking, and remove the gauge
 * once that object is closed/released. While not wrong per se if you want accurate metrics, it
 * leads to a brittle system.
 *
 * <p>NOTE: If the underlying registry is closed, like with other metrics, you will get a no-op
 * gauge, and you will not always get the same one. But it's acceptable since the underlying
 * registry passed to the builder is closed.
 *
 * <p>For example, say you have a client component. Initially, you use it only once.
 */
@SuppressWarnings("NullableProblems")
public sealed class StatefulGauge extends AbstractMeter {
  private final Gauge delegate;
  private final AtomicLong state;

  public StatefulGauge(final Gauge gauge, final AtomicLong state) {
    super(gauge.getId());
    delegate = gauge;
    this.state = state;
  }

  @Override
  public Iterable<Measurement> measure() {
    return delegate.measure();
  }

  /** Sets the value of the underlying gauge. */
  public void set(final long value) {
    state.set(value);
  }

  /**
   * Convenience method when dealing with gauges representing switches (i.e. 1 or 0).
   *
   * <p>Passing {@code false} will set the gauge to 0, and passing {@code true} will set it to 1.
   */
  public void set(final boolean value) {
    state.set(value ? 1 : 0);
  }

  /** Atomically increments and returns the new value. */
  public long increment() {
    return state.incrementAndGet();
  }

  /** Atomically decrements and returns the new value. */
  public long decrement() {
    return state.decrementAndGet();
  }

  @VisibleForTesting("convenience method to assert value in tests")
  public double value() {
    return delegate.value();
  }

  @VisibleForTesting("convenience accessor to test state identity")
  AtomicLong state() {
    return state;
  }

  /** Returns a builder for a meter with the given name. */
  public static StatefulGauge.Builder builder(final String name) {
    return new Builder(name);
  }

  /**
   * Builds a {@link StatefulGauge}.
   *
   * <p>NOTE: unfortunately there is no common builder class or interface in Micrometer, requiring
   * us to pretty much copy the interface by hand.
   */
  public static final class Builder {

    private final String name;
    private Tags tags = Tags.empty();
    private String description;
    private String baseUnit;

    private Builder(final String name) {
      this.name = name;
    }

    /**
     * @param tags Must be an even number of arguments representing key/value pairs of tags.
     * @return The gauge builder with added tags.
     */
    public Builder tags(final String... tags) {
      return tags(Tags.of(tags));
    }

    /**
     * @param tags Tags to add to the eventual gauge.
     * @return The gauge builder with added tags.
     */
    public Builder tags(final Iterable<Tag> tags) {
      this.tags = this.tags.and(tags);
      return this;
    }

    /**
     * @param key The tag key.
     * @param value The tag value.
     * @return The gauge builder with a single added tag.
     */
    public Builder tag(final String key, final String value) {
      tags = tags.and(key, value);
      return this;
    }

    /**
     * @param description Description text of the eventual gauge.
     * @return The gauge builder with added description.
     */
    public Builder description(final String description) {
      this.description = description;
      return this;
    }

    /**
     * NOTE: this can change the name of the meter depending on the target backend. For example, on
     * Prometheus, this adds a suffix to the metric name. Use with caution when working with
     * pre-existing metrics to avoid backwards compatibility breaks.
     *
     * @param unit Base unit of the eventual gauge.
     * @return The gauge builder with added base unit.
     */
    public Builder baseUnit(final String unit) {
      baseUnit = unit;
      return this;
    }

    /**
     * Convenience method to create meters from the builder that only differ in tags. This method
     * can be used for dynamic tagging by creating the builder once and applying the dynamically
     * changing tags using the returned {@link MeterProvider}.
     *
     * @param registry A registry to add the meter to, if it doesn't already exist.
     * @return A {@link MeterProvider} that returns a meter based on the provided tags.
     */
    public MeterProvider<StatefulGauge> withRegistry(final MeterRegistry registry) {
      return extraTags -> register(registry, tags.and(extraTags));
    }

    /**
     * Registers the stateful gauge to the given registry.
     *
     * @param registry the registry on which to get or create the gauge
     * @return the stateful gauge registered as identified by the name and tags
     */
    public StatefulGauge register(final MeterRegistry registry) {
      return register(registry, tags);
    }

    @SuppressWarnings("DataFlowIssue")
    private StatefulGauge register(final MeterRegistry registry, final Tags tags) {
      final var id = new Id(name, tags, baseUnit, description, Type.GAUGE);
      final var state = new AtomicLong();
      final Function<Meter.Id, StatefulGauge> builder =
          id2 ->
              new StatefulGauge(
                  MicrometerInternal.newGauge(registry, id2, state, AtomicLong::get), state);
      final Function<Meter.Id, StatefulGauge> noopBuilder = NoopStatefulGauge::new;

      return MicrometerInternal.registerMeterIfNecessary(
          registry, StatefulGauge.class, id, builder, noopBuilder);
    }
  }

  private static final class NoopStatefulGauge extends StatefulGauge {

    public NoopStatefulGauge(final Id id) {
      super(new NoopGauge(id), null); // let the state be garbage collected
    }

    @Override
    public void set(final long value) {}
  }
}
