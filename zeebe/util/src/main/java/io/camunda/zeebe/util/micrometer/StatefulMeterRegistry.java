/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.micrometer;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link CompositeMeterRegistry} extension which tracks stateful gauges, returning the same
 * instance when requested.
 *
 * <p>The reason for this is that Micrometer does not allow custom meter types, so we have to wrap
 * an existing, underlying gauge. At the same time, we still want to return the same object which
 * tracks the state. For example, we re-create the processing DB state which has some gauges in
 * different parts of the code base, where it would be difficult to share the instance across all
 * these boundaries. Instead, we rely on this stateful registry to do this.
 */
final class StatefulMeterRegistry extends CompositeMeterRegistry {
  private static final Logger LOGGER = LoggerFactory.getLogger(StatefulMeterRegistry.class);

  private final ConcurrentMap<Meter.Id, StatefulGauge> gauges = new ConcurrentHashMap<>();

  StatefulMeterRegistry(final MeterRegistry parent) {
    super(parent.config().clock(), Collections.singleton(parent));
    config().onMeterAdded(this::onMeterAdded).onMeterRemoved(this::onMeterRemoved);
  }

  StatefulGauge registerIfNecessary(final Meter.Id id) {
    return gauges.computeIfAbsent(id, this::registerStatefulGauge);
  }

  StatefulGauge registerStatefulGauge(final Meter.Id id) {
    return StatefulGauge.registerAsGauge(id, this);
  }

  private void onMeterAdded(final Meter meter) {
    final var existing = gauges.get(meter.getId());

    // we explicitly compare identity, because the meters should be the same
    if (existing != null && existing.delegate() != meter) {
      LOGGER.warn(
          """
          A new meter {} was added a the stateful meter registry, but there was already an \
          existing stateful gauge; this is probably a mistake""",
          meter.getId());
    }
  }

  private void onMeterRemoved(final Meter meter) {
    gauges.remove(meter.getId());
  }
}
