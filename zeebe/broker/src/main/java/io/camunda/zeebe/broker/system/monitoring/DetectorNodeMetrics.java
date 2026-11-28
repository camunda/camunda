/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring;

import io.camunda.zeebe.broker.system.monitoring.HealthMetricsDoc.NodesKeyNames;
import io.camunda.zeebe.util.health.HealthStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Reports the children of health detectors, for example one per partition, as nodes of the {@link
 * HealthMetricsDoc#NODES} metric. A detector's children are not registered components, so {@link
 * HealthTreeMetrics} only reports the detector itself; with this, a state timeline shows one row
 * per child, and filtering by partition works because each child carries its partition's tags.
 */
@NullMarked
public final class DetectorNodeMetrics implements AutoCloseable {

  private final MeterRegistry registry;
  private final String root;
  private final Map<String, Meter.Id> gauges = new ConcurrentHashMap<>();

  /**
   * @param root the component name of the broker's health tree root, for example {@code Broker-0}
   */
  public DetectorNodeMetrics(final MeterRegistry registry, final String root) {
    this.registry = registry;
    this.root = root;
  }

  /**
   * @param partitionTags the {@code physicalTenant} and {@code partition} tags of the child
   * @param status reads the child's current status; null once the child is gone
   */
  public void register(
      final String detector,
      final String child,
      final Tags partitionTags,
      final Supplier<@Nullable HealthStatus> status) {
    final var path = root + "/" + detector + "/" + child;
    final var meterDoc = HealthMetricsDoc.NODES;
    final var gauge =
        Gauge.builder(meterDoc.getName(), status, s -> HealthTreeMetrics.statusValue(s.get()))
            .description(meterDoc.getDescription())
            .tag(NodesKeyNames.ID.asString(), child)
            .tag(NodesKeyNames.PATH.asString(), path)
            .tags(partitionTags)
            // the supplier is a lambda nothing else references
            .strongReference(true)
            .register(registry);
    final var previous = gauges.put(path, gauge.getId());
    if (previous != null && !previous.equals(gauge.getId())) {
      registry.remove(previous);
    }
  }

  public void remove(final String detector, final String child) {
    final var id = gauges.remove(root + "/" + detector + "/" + child);
    if (id != null) {
      registry.remove(id);
    }
  }

  @Override
  public void close() {
    gauges.values().forEach(registry::remove);
    gauges.clear();
  }
}
