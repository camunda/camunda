/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import io.camunda.cluster.PhysicalTenantAvailability;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.jspecify.annotations.NullMarked;

/**
 * Registers a per-physical-tenant serviceability gauge, pulled from {@link
 * PhysicalTenantAvailability} on every scrape.
 *
 * <p>This is a pull-based gauge, not a transition log: it only reports the current state, it does
 * not log or alert when a tenant flips between serviceable and degraded. Degraded-transition
 * logging is a deferred follow-up (https://github.com/camunda/camunda/issues/57025,
 * https://github.com/camunda/camunda/issues/54299).
 */
@NullMarked
public class PhysicalTenantAvailabilityMetrics implements MeterBinder {

  private static final String METRIC_NAME = "camunda.physical.tenant.serviceable";

  private final PhysicalTenantIds physicalTenantIds;
  private final PhysicalTenantAvailability availability;

  public PhysicalTenantAvailabilityMetrics(
      final PhysicalTenantIds physicalTenantIds, final PhysicalTenantAvailability availability) {
    this.physicalTenantIds = physicalTenantIds;
    this.availability = availability;
  }

  @Override
  public void bindTo(final MeterRegistry registry) {
    physicalTenantIds
        .known()
        .forEach(
            physicalTenantId ->
                Gauge.builder(
                        METRIC_NAME, () -> availability.isServiceable(physicalTenantId) ? 1 : 0)
                    .description("Whether the physical tenant is serviceable (1) or degraded (0)")
                    .tag(PartitionKeyNames.PHYSICAL_TENANT.asString(), physicalTenantId)
                    .register(registry));
  }
}
