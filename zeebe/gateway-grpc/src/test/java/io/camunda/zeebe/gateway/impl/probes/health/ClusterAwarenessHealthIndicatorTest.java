/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.probes.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.BrokerMemberId;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

public class ClusterAwarenessHealthIndicatorTest {

  @Test
  public void shouldRejectNullSupplierInConstructor() {
    // when + then
    assertThatThrownBy(() -> new ClusterAwarenessHealthIndicator(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldReportDownIfSupplierReturnsEmptyMap() {
    // given
    final Supplier<Map<String, BrokerClusterState>> statesSupplier = Map::of;
    final var sutHealthIndicator = new ClusterAwarenessHealthIndicator(statesSupplier);

    // when
    final var actualHealth = sutHealthIndicator.health();

    // then
    assertThat(actualHealth).isNotNull();
    assertThat(actualHealth.getStatus()).isEqualTo(Status.DOWN);
  }

  @Test
  public void shouldReportDownIfNoGroupHasBrokers() {
    // given
    final BrokerClusterState defaultState = mock(BrokerClusterState.class);
    when(defaultState.getBrokers()).thenReturn(List.of());
    final BrokerClusterState secondState = mock(BrokerClusterState.class);
    when(secondState.getBrokers()).thenReturn(List.of());

    final Supplier<Map<String, BrokerClusterState>> statesSupplier =
        () -> Map.of("default", defaultState, "tenant-b", secondState);
    final var sutHealthIndicator = new ClusterAwarenessHealthIndicator(statesSupplier);

    // when
    final var actualHealth = sutHealthIndicator.health();

    // then
    assertThat(actualHealth).isNotNull();
    assertThat(actualHealth.getStatus()).isEqualTo(Status.DOWN);
  }

  @Test
  public void shouldReportUpIfAnyGroupHasBrokers() {
    // given
    final BrokerClusterState defaultState = mock(BrokerClusterState.class);
    when(defaultState.getBrokers()).thenReturn(List.of());
    final BrokerClusterState secondState = mock(BrokerClusterState.class);
    when(secondState.getBrokers()).thenReturn(List.of(BrokerMemberId.from(1)));

    final Supplier<Map<String, BrokerClusterState>> statesSupplier =
        () -> Map.of("default", defaultState, "tenant-b", secondState);
    final var sutHealthIndicator = new ClusterAwarenessHealthIndicator(statesSupplier);

    // when
    final var actualHealth = sutHealthIndicator.health();

    // then
    assertThat(actualHealth).isNotNull();
    assertThat(actualHealth.getStatus()).isEqualTo(Status.UP);
    assertThat(actualHealth.getDetails())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("default", Map.of("brokers", 0), "tenant-b", Map.of("brokers", 1)));
  }

  @Test
  public void shouldReportUpIfSingleGroupHasBrokers() {
    // given
    final BrokerClusterState defaultState = mock(BrokerClusterState.class);
    when(defaultState.getBrokers()).thenReturn(List.of(BrokerMemberId.from(1)));

    final Supplier<Map<String, BrokerClusterState>> statesSupplier =
        () -> Map.of("default", defaultState);
    final var sutHealthIndicator = new ClusterAwarenessHealthIndicator(statesSupplier);

    // when
    final var actualHealth = sutHealthIndicator.health();

    // then
    assertThat(actualHealth).isNotNull();
    assertThat(actualHealth.getStatus()).isEqualTo(Status.UP);
  }
}
