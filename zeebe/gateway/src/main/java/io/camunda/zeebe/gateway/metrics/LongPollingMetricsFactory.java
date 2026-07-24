/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.metrics;

import io.camunda.zeebe.gateway.metrics.LongPollingMetricsDoc.GatewayProtocol;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Builds {@link LongPollingMetrics} instances for a given gateway protocol, one per physical
 * tenant. Created once at gateway startup, before any physical tenant is known; records no metrics
 * itself.
 */
public sealed class LongPollingMetricsFactory {

  private final MeterRegistry registry;
  private final GatewayProtocol gatewayProtocol;

  public LongPollingMetricsFactory(
      final MeterRegistry registry, final GatewayProtocol gatewayProtocol) {
    this.registry = registry;
    this.gatewayProtocol = gatewayProtocol;
  }

  /** Returns a factory whose instances do nothing. Mostly useful for testing. */
  public static LongPollingMetricsFactory noop() {
    return new Noop();
  }

  /** Returns a {@link LongPollingMetrics} instance tagged for the given physical tenant. */
  public LongPollingMetrics forPhysicalTenant(final String physicalTenantId) {
    return new LongPollingMetrics(registry, gatewayProtocol, physicalTenantId);
  }

  private static final class Noop extends LongPollingMetricsFactory {

    private Noop() {
      super(null, null);
    }

    @Override
    public LongPollingMetrics forPhysicalTenant(final String physicalTenantId) {
      return LongPollingMetrics.noop();
    }
  }
}
