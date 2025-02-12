/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.micrometer;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Meter.Id;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import net.jcip.annotations.ThreadSafe;

/**
 * A {@link io.micrometer.core.instrument.composite.CompositeMeterRegistry} extension which keeps
 * track of gauge states. This allows you to share the same gauge across boundaries without worrying
 * about updating the wrong state.
 *
 * <p>NOTE: when doing this, you still need to carefully consider that updating a gauge from
 * different contexts may lead to the wrong value being shown, so avoid using set operations unless
 * you're really, really sure.
 */
@ThreadSafe
public final class StatefulMeterRegistry extends CompositeMeterRegistry {
  private final ConcurrentMap<Meter.Id, StatefulGauge> gauges = new ConcurrentHashMap<>();

  public StatefulMeterRegistry(final MeterRegistry wrapped) {
    super();
    add(wrapped);
  }

  /**
   * Registers a gauge whose will report the value of the returned {@link StatefulGauge#state()}. If
   * a gauge with the same documentation already exists <strong>in this registry</strong>, it will
   * return the same {@link StatefulGauge} again and will not register anything.
   *
   * @param documentation the documentation for the gauge
   * @return an {@link StatefulGauge} which represents the gauge value and its state
   */
  public StatefulGauge newLongGauge(final ExtendedMeterDocumentation documentation) {
    return newLongGauge(documentation, Tags.empty());
  }

  /**
   * Registers a gauge whose will report the value of the returned {@link StatefulGauge#state()}. If
   * a gauge with the same documentation <strong>and</strong> tags already exists <strong>in this
   * registry</strong>, it will return the same {@link StatefulGauge} again and will not register
   * anything.
   *
   * @param documentation the documentation for the gauge
   * @param tags a set of tags to apply to the gauge
   * @return an {@link StatefulGauge} which represents the gauge value and its state
   */
  public StatefulGauge newLongGauge(
      final ExtendedMeterDocumentation documentation, final Tag... tags) {
    return newLongGauge(documentation, Tags.of(tags));
  }

  /**
   * Registers a gauge whose will report the value of the returned {@link AtomicLong} object. If a
   * gauge with the same documentation <strong>and</strong> tags already exists <strong>in this
   * registry</strong>, it will return the same {@link AtomicLong} again and will not register
   * anything.
   *
   * <p>The tags are expected to be key-value pairs. So if you wanted to add {@code foo=bar} and
   * {@code baz=buzz}, then you would pass {@code newLongGauge(doc, "foo", "bar", "baz", "buz")}.
   *
   * @param documentation the documentation for the gauge
   * @param tags a set of tags as ordered key-value pairs to apply to the gauge
   * @return an {@link AtomicLong} which represents the gauge value
   */
  public StatefulGauge newLongGauge(
      final ExtendedMeterDocumentation documentation, final String... tags) {
    return newLongGauge(documentation, Tags.of(tags));
  }

  /**
   * Registers a gauge whose will report the value of the returned {@link StatefulGauge#state()}. If
   * a gauge with the same documentation <strong>and</strong> tags already exists <strong>in this
   * registry</strong>, it will return the same {@link StatefulGauge} again and will not register
   * anything.
   *
   * @param documentation the documentation for the gauge
   * @param tags a set of tags to apply to the gauge
   * @return an {@link StatefulGauge} which represents the gauge value and its state
   */
  public StatefulGauge newLongGauge(
      final ExtendedMeterDocumentation documentation, final Tags tags) {
    final var type = documentation.getType();
    if (type != Type.GAUGE) {
      throw new IllegalArgumentException(
          "Expected to register a new stateful long gauge, but it's documented as a meter of type %s"
              .formatted(type));
    }

    final var id =
        new Meter.Id(
            documentation.getName(),
            tags,
            documentation.getBaseUnit(),
            documentation.getDescription(),
            type);

    final var existing = gauges.get(id);
    if (existing != null) {
      return existing;
    }

    // NOTE: one big downside is we can't easily know if the gauge previously existed or not, so we
    // could be return a new state that will simply do nothing. This is the same behavior as
    // registering a gauge twice in Micrometer with a different state object anyway, but I want to
    // highlight it here because the class may give the impression this is not possible, while it
    // definitely is.
    final var state = new AtomicLong();
    final var gauge =
        Gauge.builder(id.getName(), state, AtomicLong::get)
            .description(id.getDescription())
            .tags(id.getTags())
            .strongReference(true)
            .register(this);
    final var statefulGauge = new StatefulGauge(gauge, state);
    gauges.put(id, statefulGauge);

    return statefulGauge;
  }

  @SuppressWarnings("NullableProblems")
  @Override
  public Meter remove(final Id mappedId) {
    final var removed = super.remove(mappedId);
    if (mappedId.getType() == Meter.Type.GAUGE) {
      gauges.remove(mappedId);
    }

    return removed;
  }

  public record StatefulGauge(Gauge gauge, AtomicLong state) {}
}
