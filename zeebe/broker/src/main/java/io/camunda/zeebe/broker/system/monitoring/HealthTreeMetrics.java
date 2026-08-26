/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring;

import io.camunda.zeebe.broker.system.monitoring.HealthMetricsDoc.NodesKeyNames;
import io.camunda.zeebe.util.health.ComponentTreeListener;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HealthTreeMetrics implements ComponentTreeListener {
  public static final int RECOVERING_VALUE = 2;
  private static final Logger LOG = LoggerFactory.getLogger(HealthTreeMetrics.class);
  private static final int MAX_PATH_ITERATIONS = 8;
  private final MeterRegistry meterRegistry;
  private final Map<String, Meter.Id> meters = new HashMap<>();
  // Map of child -> parent: a child only has 1 parent
  private final Map<String, String> relationships = new HashMap<>();
  // Holds the value suppliers of recovering nodes: Micrometer keeps only a weak reference to the
  // object a gauge reads from, so without this the gauge would start reporting NaN once collected
  private final Map<String, AtomicInteger> recoveringValues = new HashMap<>();
  private final Tags extraTags;

  public HealthTreeMetrics(final MeterRegistry meterRegistry) {
    this(meterRegistry, null);
  }

  public HealthTreeMetrics(final MeterRegistry meterRegistry, final Tags extraTags) {
    this.meterRegistry = meterRegistry;
    this.extraTags = extraTags;
  }

  /**
   * @param status to be mapped
   * @return a numerical value mapped from status that can be reported by the gauge
   */
  public static int statusValue(final HealthStatus status) {
    return switch (status) {
      case HEALTHY -> 1;
      case UNHEALTHY -> -1;
      case DEAD -> -2;
      case null -> 0;
    };
  }

  @Override
  public void registerNode(final HealthMonitorable component) {
    final var gauge = buildNodeGauge(component);
    meters.put(component.componentName(), gauge.getId());
  }

  @Override
  public void unregisterNode(final HealthMonitorable component) {
    removeFromRegistry(component.componentName());
  }

  @Override
  public void registerRelationship(final String child, final String parent) {
    relationships.put(child, parent);
  }

  @Override
  public void unregisterRelationship(final String child, final String to) {
    final var parent = relationships.get(child);
    if (parent == null || !parent.equals(to)) {
      LOG.warn("Tried to remove invalid relationship: child={}, parent={}", child, to);
    } else {
      relationships.remove(child);
    }
  }

  /**
   * Registers a node that constantly reports {@link #RECOVERING_VALUE}, for a component that exists
   * only while its partition is in recovery mode. Such a component has no {@link
   * io.camunda.zeebe.util.health.HealthReport} of its own, so {@link
   * #registerNode(HealthMonitorable)} cannot be used; register any relationship first so the
   * reported path matches the one the normal component uses.
   */
  public void registerRecoveringNode(final String componentName) {
    final var value = new AtomicInteger(RECOVERING_VALUE);
    final var meterDoc = HealthMetricsDoc.NODES;
    final var builder =
        Gauge.builder(meterDoc.getName(), value, AtomicInteger::intValue)
            .description(meterDoc.getDescription())
            .tag(NodesKeyNames.ID.asString(), componentName)
            .tag(NodesKeyNames.PATH.asString(), pathOf(componentName));

    if (extraTags != null) {
      builder.tags(extraTags);
    }

    recoveringValues.put(componentName, value);
    meters.put(componentName, builder.register(meterRegistry).getId());
  }

  @Override
  public void close() {
    for (final var id : meters.values()) {
      meterRegistry.remove(id);
    }
    meters.clear();
    recoveringValues.clear();
  }

  private Gauge buildNodeGauge(final HealthMonitorable component) {
    final var meterDoc = HealthMetricsDoc.NODES;
    final var builder =
        Gauge.builder(
                meterDoc.getName(),
                component,
                // make sure to not close over anything else than `c`
                c -> c != null ? statusValue(c.getHealthReport().getStatus()) : statusValue(null))
            .description(meterDoc.getDescription())
            .tag(NodesKeyNames.ID.asString(), component.componentName())
            .tag(NodesKeyNames.PATH.asString(), pathOf(component.componentName()));

    if (extraTags != null) {
      builder.tags(extraTags);
    }

    return builder.register(meterRegistry);
  }

  public String pathOf(final String from) {
    final var pathElements = new LinkedList<String>();
    pathElements.add(from);
    var parent = relationships.get(from);
    var i = 0;
    while (parent != null && i++ < MAX_PATH_ITERATIONS) {
      pathElements.addFirst(parent);
      parent = relationships.get(parent);
    }
    return String.join("/", pathElements);
  }

  private void removeFromRegistry(final String metricName) {
    final var meterId = meters.remove(metricName);
    recoveringValues.remove(metricName);
    if (meterId != null) {
      meterRegistry.remove(meterId);
    }
  }
}
