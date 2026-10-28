/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring;

import static io.camunda.zeebe.broker.system.monitoring.PhysicalTenantDiskUsageMetricsDoc.LIMIT_BYTES;
import static io.camunda.zeebe.broker.system.monitoring.PhysicalTenantDiskUsageMetricsDoc.SCAN_DURATION;
import static io.camunda.zeebe.broker.system.monitoring.PhysicalTenantDiskUsageMetricsDoc.USED_BYTES;

import io.camunda.zeebe.broker.system.monitoring.PhysicalTenantDiskUsageMetricsDoc.UsedSpaceKeyNames;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import org.jspecify.annotations.NullMarked;

@NullMarked
final class PhysicalTenantDiskUsageMetrics implements AutoCloseable {

  private static final String PHYSICAL_TENANT_TAG = PartitionKeyNames.PHYSICAL_TENANT.asString();

  private final MeterRegistry registry;
  private final String physicalTenantId;
  private final List<Meter> meters = new ArrayList<>();
  private final Timer scanDuration;

  PhysicalTenantDiskUsageMetrics(
      final MeterRegistry registry, final String physicalTenantId, final LongSupplier usedBytes) {
    this.registry = registry;
    this.physicalTenantId = physicalTenantId;

    meters.add(
        Gauge.builder(USED_BYTES.getName(), usedBytes::getAsLong)
            .description(USED_BYTES.getDescription())
            .tag(PHYSICAL_TENANT_TAG, physicalTenantId)
            .register(registry));
    scanDuration =
        MicrometerUtil.buildTimer(SCAN_DURATION)
            .tag(PHYSICAL_TENANT_TAG, physicalTenantId)
            .register(registry);
    meters.add(scanDuration);
  }

  void registerLimit(final String threshold, final long limitBytes) {
    meters.add(
        Gauge.builder(LIMIT_BYTES.getName(), () -> limitBytes)
            .description(LIMIT_BYTES.getDescription())
            .tag(PHYSICAL_TENANT_TAG, physicalTenantId)
            .tag(UsedSpaceKeyNames.THRESHOLD.asString(), threshold)
            .register(registry));
  }

  Timer scanDuration() {
    return scanDuration;
  }

  @Override
  public void close() {
    meters.forEach(registry::remove);
    meters.clear();
  }
}
