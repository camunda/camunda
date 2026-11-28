/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring.detector;

import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Reads the current values of the meters a detector watches, keyed by meter id. */
final class MeterReader {

  private static final String NO_PARTITION = "none";
  private static final String PARTITION_PREFIX = "Partition-";

  private final MeterRegistry registry;

  MeterReader(final MeterRegistry registry) {
    this.registry = registry;
  }

  Collection<Timer> timers(final String name) {
    return registry.find(name).timers();
  }

  /** The counts of every counter of that name, including function counters. */
  Map<Meter.Id, Double> counts(final String name) {
    final var counts = new HashMap<Meter.Id, Double>();
    registry.find(name).counters().forEach(c -> counts.put(c.getId(), c.count()));
    registry.find(name).functionCounters().forEach(c -> counts.put(c.getId(), c.count()));
    return counts;
  }

  Map<Meter.Id, Double> gauges(final String name) {
    final var values = new HashMap<Meter.Id, Double>();
    for (final Gauge gauge : registry.find(name).gauges()) {
      final var value = gauge.value();
      if (!Double.isNaN(value)) {
        values.put(gauge.getId(), value);
      }
    }
    return values;
  }

  /**
   * The health tree child a meter belongs to: the partition's component name, as {@code
   * ZeebePartition#componentName} builds it, or {@code fallback} for meters of the whole node.
   */
  /**
   * The partition tags of a child that {@link #child} named, also with a {@code :suffix} such as an
   * exporter id; {@code none} for children that are not a partition.
   */
  static Tags partitionTags(final String child) {
    if (!child.startsWith(PARTITION_PREFIX)) {
      return PartitionKeyNames.noPartition();
    }
    final var suffix = child.indexOf(':');
    final var name =
        child.substring(PARTITION_PREFIX.length(), suffix < 0 ? child.length() : suffix);
    final var lastDash = name.lastIndexOf('-');
    return lastDash < 0
        ? Tags.of(
            PartitionKeyNames.PHYSICAL_TENANT.asString(),
            "none",
            PartitionKeyNames.PARTITION.asString(),
            name)
        : Tags.of(
            PartitionKeyNames.PHYSICAL_TENANT.asString(),
            name.substring(0, lastDash),
            PartitionKeyNames.PARTITION.asString(),
            name.substring(lastDash + 1));
  }

  static String child(final Meter.Id id, final String fallback) {
    final var partition = id.getTag(PartitionKeyNames.PARTITION.asString());
    if (partition == null || NO_PARTITION.equals(partition)) {
      return fallback;
    }
    final var tenant = id.getTag(PartitionKeyNames.PHYSICAL_TENANT.asString());
    return tenant == null
        ? PARTITION_PREFIX + partition
        : PARTITION_PREFIX + tenant + "-" + partition;
  }
}
