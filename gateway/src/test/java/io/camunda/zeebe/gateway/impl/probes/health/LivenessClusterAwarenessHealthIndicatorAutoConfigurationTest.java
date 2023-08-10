/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.probes.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.gateway.impl.MicronautGatewayBridge;
import io.camunda.zeebe.gateway.impl.broker.cluster.BrokerClusterState;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthResult;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

public class LivenessClusterAwarenessHealthIndicatorAutoConfigurationTest {

  private MicronautGatewayBridge helperGatewayBridge;

  private ClusterAwarenessHealthIndicatorAutoConfiguration sutAutoConfig;

  @Before
  public void setUp() {
    helperGatewayBridge = new MicronautGatewayBridge();
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
    final Publisher<HealthResult> actualHealth = healthIndicator.getResult();
    final var healthResult = Mono.from(actualHealth).block(Duration.ofMillis(5000));

    // then
    assertThat(healthResult).isNotNull();
    assertThat(healthResult.getStatus()).isSameAs(HealthStatus.UP);
  }
}
