/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.health.Status;
import io.camunda.zeebe.gateway.impl.broker.cluster.BrokerClusterState;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class SpringGatewayBridgeTest {

  private SpringGatewayBridge sutBrigde;

  @Before
  public void setUp() {
    sutBrigde = new SpringGatewayBridge();
  }

  @Test
  public void shouldReturnNoGatewayStatusByDefault() {
    // when
    final Optional<Status> actual = sutBrigde.getGatewayStatus();

    // then
    assertThat(actual).describedAs("Gateway status when no supplier is set").isEmpty();
  }

  @Test
  public void shouldUseGatewayStatusSupplierWhenSet() {
    // given
    final Supplier<Status> testSupplier = () -> Status.RUNNING;
    sutBrigde.registerGatewayStatusSupplier(testSupplier);

    // when
    final var actual = sutBrigde.getGatewayStatus();

    // then
    assertThat(actual).contains(Status.RUNNING);
  }

  @Test
  public void shouldReturnNoClusterStateByDefault() {
    // when
    final var actual = sutBrigde.getClusterState();

    // then
    assertThat(actual).describedAs("Cluster status when no supplier is set").isEmpty();
  }

  @Test
  public void shouldUseClusterStateSupplierWhenSet() {
    // given
    final BrokerClusterState mockClusterState = Mockito.mock(BrokerClusterState.class);

    final Supplier<Optional<BrokerClusterState>> testSupplier = () -> Optional.of(mockClusterState);
    sutBrigde.registerClusterStateSupplier(testSupplier);

    // when
    final var actual = sutBrigde.getClusterState();

    // then
    assertThat(actual).contains(mockClusterState);
  }
}
