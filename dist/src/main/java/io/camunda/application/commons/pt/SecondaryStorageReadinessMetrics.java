/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.cluster.SecondaryStorageReadiness;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.jspecify.annotations.NullMarked;

/**
 * Registers a per-physical-tenant secondary-storage-readiness gauge, pulled from {@link
 * SecondaryStorageReadiness} on every scrape.
 *
 * <p>This is a pull-based gauge, not a transition log: it only reports the current state, it does
 * not log or alert when a tenant flips between ready and degraded. Degraded-transition logging is a
 * deferred follow-up (https://github.com/camunda/camunda/issues/57025,
 * https://github.com/camunda/camunda/issues/54299).
 */
@NullMarked
public class SecondaryStorageReadinessMetrics implements MeterBinder {

  private static final String METRIC_NAME = "camunda.physical.tenant.secondary.storage.ready";

  private final PhysicalTenantIds physicalTenantIds;
  private final SecondaryStorageReadiness readiness;

  public SecondaryStorageReadinessMetrics(
      final PhysicalTenantIds physicalTenantIds, final SecondaryStorageReadiness readiness) {
    this.physicalTenantIds = physicalTenantIds;
    this.readiness = readiness;
  }

  @Override
  public void bindTo(final MeterRegistry registry) {
    physicalTenantIds
        .known()
        .forEach(
            physicalTenantId ->
                Gauge.builder(METRIC_NAME, () -> readiness.isReady(physicalTenantId) ? 1 : 0)
                    .description(
                        "Whether the physical tenant's secondary storage is ready (1) or degraded"
                            + " (0)")
                    .tag(PartitionKeyNames.PHYSICAL_TENANT.asString(), physicalTenantId)
                    .register(registry));
  }
}
