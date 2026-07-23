/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.metrics.LongPollingMetricsDoc.GatewayProtocol;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

final class LongPollingMetricsTest {

  @Test
  void shouldTagAndValueGaugesPerPhysicalTenantIndependently() {
    // given
    final var registry = new SimpleMeterRegistry();
    final var factory = new LongPollingMetricsFactory(registry, GatewayProtocol.GRPC);
    final var tenantAMetrics = factory.forPhysicalTenant("tenantA");
    final var tenantBMetrics = factory.forPhysicalTenant("tenantB");

    // when
    tenantAMetrics.setBlockedRequestsCount("jobTypeX", 3);
    tenantBMetrics.setBlockedRequestsCount("jobTypeX", 7);

    // then
    final var tenantAGauge =
        registry
            .find("zeebe.long.polling.queued.current")
            .tag("physicalTenantId", "tenantA")
            .tag("type", "jobTypeX")
            .gauge();
    final var tenantBGauge =
        registry
            .find("zeebe.long.polling.queued.current")
            .tag("physicalTenantId", "tenantB")
            .tag("type", "jobTypeX")
            .gauge();

    assertThat(tenantAGauge).isNotNull();
    assertThat(tenantBGauge).isNotNull();
    assertThat(tenantAGauge.value()).isEqualTo(3);
    assertThat(tenantBGauge.value()).isEqualTo(7);
  }
}
