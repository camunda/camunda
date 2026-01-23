/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.probes.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.gateway.impl.SpringGatewayBridge;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

public class LivenessClusterAwarenessHealthIndicatorAutoConfigurationTest {

  private SpringGatewayBridge helperGatewayBridge;

  private ClusterAwarenessHealthIndicatorAutoConfiguration sutAutoConfig;

  @Before
  public void setUp() {
    helperGatewayBridge = new SpringGatewayBridge();
    sutAutoConfig = new ClusterAwarenessHealthIndicatorAutoConfiguration();
  }

  @Test
  public void shouldCreateHealthIndicatorEvenBeforeClusterStateSupplierIsRegistered() {
    // when
    final var actual = sutAutoConfig.gatewayClusterAwarenessHealthIndicator(helperGatewayBridge);

    // then
    assertThat(actual).isNotNull();
  }

  @Test
  public void
      shouldCreateHealthIndicatorThatReportsHealthBasedOnResultOfRegisteredClusterStateSupplier() {
    // given
    final BrokerClusterState mockClusterState = mock(BrokerClusterState.class);
    when(mockClusterState.getBrokers()).thenReturn(List.of(1));

    final Supplier<Optional<BrokerClusterState>> stateSupplier =
        () -> Optional.of(mockClusterState);

    final var healthIndicator =
        sutAutoConfig.gatewayClusterAwarenessHealthIndicator(helperGatewayBridge);

    // when
    helperGatewayBridge.registerClusterStateSupplier(stateSupplier);
    final Health actualHealth = healthIndicator.health();

    // then
    assertThat(actualHealth).isNotNull();
    assertThat(actualHealth.getStatus()).isSameAs(Status.UP);
  }
}
