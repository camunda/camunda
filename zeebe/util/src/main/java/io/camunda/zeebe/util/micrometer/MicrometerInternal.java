/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.micrometer;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import org.agrona.LangUtil;

/**
 * A utility class to access Micrometer internals via reflection. This currently unfortunately
 * required, as Micrometer is poorly extensible, and does not allow for things like custom meters,
 * or registering directly with an existing {@link io.micrometer.core.instrument.Meter.Id} without
 * having to re-create (and thus, re-allocate) it.
 *
 * <p>This is not our preferred solution, so if there is any way in the future to avoid using
 * reflection to access the internals, we should do so.
 */
public final class MicrometerInternal {
  private static final MethodHandle REGISTER_IF_NECESSARY_METHOD;
  private static final MethodHandle NEW_GAUGE_METHOD;

  static {
    final var lookup = MethodHandles.lookup();

    try {
      REGISTER_IF_NECESSARY_METHOD = resolveRegisterIfNecessaryMethod(lookup);
      NEW_GAUGE_METHOD = resolveNewGaugeMethod(lookup);
    } catch (final IllegalAccessException | NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }

  private MicrometerInternal() {}

  public static <T> Gauge newGauge(
      final MeterRegistry registry,
      final Meter.Id id,
      final T obj,
      final ToDoubleFunction<T> valueFunction) {
    final var newGauge = NEW_GAUGE_METHOD.bindTo(registry);

    // based on the signature of the original newGauge method, we know this should never return null
    try {
      return (Gauge) newGauge.invoke(id, obj, valueFunction);
    } catch (final Throwable e) {
      LangUtil.rethrowUnchecked(e);
      return null; // unreachable
    }
  }

  public static <M extends Meter> M registerMeterIfNecessary(
      final MeterRegistry registry,
      final Class<M> meterClass,
      final Meter.Id id,
      final Function<Meter.Id, M> builder,
      final Function<Meter.Id, M> noopBuilder) {
    final var registerIfNecessary = REGISTER_IF_NECESSARY_METHOD.bindTo(registry);
    try {
      //noinspection unchecked
      return (M) registerIfNecessary.invoke(meterClass, id, builder, noopBuilder);
    } catch (final Throwable e) {
      LangUtil.rethrowUnchecked(e);
      return null; // unreachable
    }
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

  private static MethodHandle resolveRegisterIfNecessaryMethod(final MethodHandles.Lookup lookup)
      throws IllegalAccessException, NoSuchMethodException {
    return MethodHandles.privateLookupIn(MeterRegistry.class, lookup)
        .findVirtual(
            MeterRegistry.class,
            "registerMeterIfNecessary",
            MethodType.methodType(
                Meter.class, Class.class, Meter.Id.class, Function.class, Function.class));
  }
}
