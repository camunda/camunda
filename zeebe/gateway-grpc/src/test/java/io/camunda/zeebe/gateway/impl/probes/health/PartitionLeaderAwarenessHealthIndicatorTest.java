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

public class PartitionLeaderAwarenessHealthIndicatorTest {

  @Test
  public void shouldRejectNullSupplierInConstructor() {
    // when + then
    assertThatThrownBy(() -> new PartitionLeaderAwarenessHealthIndicator(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldReportDownIfSupplierReturnsEmptyMap() {
    // given
    final Supplier<Map<String, BrokerClusterState>> statesSupplier = Map::of;
    final var sutHealthIndicator = new PartitionLeaderAwarenessHealthIndicator(statesSupplier);

    // when
    final var actualHealth = sutHealthIndicator.health();

    // then
    assertThat(actualHealth).isNotNull();
    assertThat(actualHealth.getStatus()).isEqualTo(Status.DOWN);
  }

  @Test
  public void shouldReportDownIfNoGroupHasPartitions() {
    // given
    final BrokerClusterState defaultState = mock(BrokerClusterState.class);
    when(defaultState.getPartitions()).thenReturn(List.of());
    final BrokerClusterState secondState = mock(BrokerClusterState.class);
    when(secondState.getPartitions()).thenReturn(List.of());

    final Supplier<Map<String, BrokerClusterState>> statesSupplier =
        () -> Map.of("default", defaultState, "tenant-b", secondState);
    final var sutHealthIndicator = new PartitionLeaderAwarenessHealthIndicator(statesSupplier);

    // when
    final var actualHealth = sutHealthIndicator.health();

    // then
    assertThat(actualHealth).isNotNull();
    assertThat(actualHealth.getStatus()).isEqualTo(Status.DOWN);
  }

  @Test
  public void shouldReportDownIfNoGroupHasPartitionLeader() {
    // given
    final BrokerClusterState defaultState = mock(BrokerClusterState.class);
    when(defaultState.getPartitions()).thenReturn(List.of(1));
    when(defaultState.getLeaderForPartition(1)).thenReturn(null);
    final BrokerClusterState secondState = mock(BrokerClusterState.class);
    when(secondState.getPartitions()).thenReturn(List.of(2));
    when(secondState.getLeaderForPartition(2)).thenReturn(null);

    final Supplier<Map<String, BrokerClusterState>> statesSupplier =
        () -> Map.of("default", defaultState, "tenant-b", secondState);
    final var sutHealthIndicator = new PartitionLeaderAwarenessHealthIndicator(statesSupplier);

    // when
    final var actualHealth = sutHealthIndicator.health();

    // then
    assertThat(actualHealth).isNotNull();
    assertThat(actualHealth.getStatus()).isEqualTo(Status.DOWN);
  }

  @Test
  public void shouldReportUpIfAnyGroupHasPartitionLeader() {
    // given
    final BrokerClusterState defaultState = mock(BrokerClusterState.class);
    when(defaultState.getPartitions()).thenReturn(List.of(1));
    when(defaultState.getLeaderForPartition(1)).thenReturn(null);
    final BrokerClusterState secondState = mock(BrokerClusterState.class);
    when(secondState.getPartitions()).thenReturn(List.of(2));
    when(secondState.getLeaderForPartition(2)).thenReturn(BrokerMemberId.from(42));

    final Supplier<Map<String, BrokerClusterState>> statesSupplier =
        () -> Map.of("default", defaultState, "tenant-b", secondState);
    final var sutHealthIndicator = new PartitionLeaderAwarenessHealthIndicator(statesSupplier);

    // when
    final var actualHealth = sutHealthIndicator.health();

    // then
    assertThat(actualHealth).isNotNull();
    assertThat(actualHealth.getStatus()).isEqualTo(Status.UP);
    assertThat(actualHealth.getDetails())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "default", Map.of("partitions", 1, "partitionsWithLeader", 0L),
                "tenant-b", Map.of("partitions", 1, "partitionsWithLeader", 1L)));
  }

  @Test
  public void shouldReportUpIfSingleGroupHasPartitionLeader() {
    // given
    final BrokerClusterState defaultState = mock(BrokerClusterState.class);
    when(defaultState.getPartitions()).thenReturn(List.of(1));
    when(defaultState.getLeaderForPartition(1)).thenReturn(BrokerMemberId.from(42));

    final Supplier<Map<String, BrokerClusterState>> statesSupplier =
        () -> Map.of("default", defaultState);
    final var sutHealthIndicator = new PartitionLeaderAwarenessHealthIndicator(statesSupplier);

    // when
    final var actualHealth = sutHealthIndicator.health();

    // then
    assertThat(actualHealth).isNotNull();
    assertThat(actualHealth.getStatus()).isEqualTo(Status.UP);
  }
}
