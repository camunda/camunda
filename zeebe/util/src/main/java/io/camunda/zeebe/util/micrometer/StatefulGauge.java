/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.micrometer;

import io.micrometer.common.lang.NonNull;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.noop.NoopGauge;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import org.agrona.LangUtil;

@SuppressWarnings("NullableProblems")
public sealed class StatefulGauge extends AbstractMeter {
  private static final MethodHandle REGISTER_METHOD;
  private static final MethodHandle NEW_GAUGE_METHOD;

  static {
    final var lookup = MethodHandles.lookup();

    try {
      REGISTER_METHOD = resolveRegisterMethod(lookup);
      NEW_GAUGE_METHOD = resolveNewGaugeMethod(lookup);
    } catch (final IllegalAccessException | NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }

  private final Gauge delegate;

  private final AtomicLong state;

  public StatefulGauge(final Gauge gauge, final AtomicLong state) {
    super(gauge.getId());
    delegate = gauge;
    this.state = state;
  }

  private static MethodHandle resolveNewGaugeMethod(final MethodHandles.Lookup lookup)
      throws IllegalAccessException, NoSuchMethodException {
    return MethodHandles.privateLookupIn(MeterRegistry.class, lookup)
        .findVirtual(
            MeterRegistry.class,
            "newGauge",
            MethodType.methodType(
                Gauge.class, Meter.Id.class, Object.class, ToDoubleFunction.class));
  }

  private static MethodHandle resolveRegisterMethod(final MethodHandles.Lookup lookup)
      throws IllegalAccessException, NoSuchMethodException {
    return MethodHandles.privateLookupIn(MeterRegistry.class, lookup)
        .findVirtual(
            MeterRegistry.class,
            "registerMeterIfNecessary",
            MethodType.methodType(
                Meter.class, Class.class, Meter.Id.class, Function.class, Function.class));
  }

  public double value() {
    return delegate.value();
  }

  @Override
  public Iterable<Measurement> measure() {
    return delegate.measure();
  }

  public void set(final long value) {
    state.set(value);
  }

  public static StatefulGauge.Builder builder(final String name) {
    return new Builder(name);
  }

  private static @NonNull Gauge newGauge(
      final MethodHandle newGauge, final Meter.Id id, final AtomicLong state) {
    final var stateFn = (ToDoubleFunction<Object>) obj -> ((Number) obj).doubleValue();
    try {
      return (Gauge) newGauge.invoke(id, state, stateFn);
    } catch (final Throwable e) {
      LangUtil.rethrowUnchecked(e);
      return null; // unreachable
    }
  }

  public static final class Builder {

    private final String name;
    private Tags tags = Tags.empty();
    @Nullable private String description;
    @Nullable private String baseUnit;

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
    public Builder description(@Nullable final String description) {
      this.description = description;
      return this;
    }

    /**
     * @param unit Base unit of the eventual gauge.
     * @return The gauge builder with added base unit.
     */
    public Builder baseUnit(@Nullable final String unit) {
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

    public @NonNull StatefulGauge register(final MeterRegistry registry) {
      return register(registry, tags);
    }

    private @NonNull StatefulGauge register(final MeterRegistry registry, final Tags tags) {
      final var id = new Id(name, tags, baseUnit, description, Type.GAUGE);
      final var state = new AtomicLong();
      final var newGauge = NEW_GAUGE_METHOD.bindTo(registry);
      final Function<Meter.Id, StatefulGauge> builder =
          id2 -> new StatefulGauge(newGauge(newGauge, id2, state), state);
      final Function<Meter.Id, StatefulGauge> noopBuilder = NoopStatefulGauge::new;

      try {
        return (StatefulGauge)
            REGISTER_METHOD.bindTo(registry).invoke(StatefulGauge.class, id, builder, noopBuilder);
      } catch (final Throwable e) {
        LangUtil.rethrowUnchecked(e);
        return null; // unreachable
      }
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
