/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.probes.health;

import static io.camunda.zeebe.broker.client.api.BrokerClusterState.PARTITION_ID_NULL;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.Test;
import org.springframework.boot.health.contributor.Status;

public class ClusterHealthIndicatorTest {

  @Test
  public void shouldRejectNullInConstructor() {
    // when + then
    assertThatThrownBy(() -> new ClusterHealthIndicator(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldReportDownIfSupplierReturnsEmpty() {
    // given
    final Supplier<Optional<BrokerClusterState>> stateSupplier = () -> Optional.empty();
    final var sutHealthIndicator = new ClusterHealthIndicator(stateSupplier);

    // when
    final var actualHealth = sutHealthIndicator.health();

    // then
    assertThat(actualHealth).isNotNull();
    assertThat(actualHealth.getStatus()).isEqualTo(Status.DOWN);
  }

  @Test
  public void shouldReportDownIfListOfBrokersAnIsNotEmptyAndPartitionsIsEmpty() {
    // given
    final BrokerClusterState mockClusterState = mock(BrokerClusterState.class);
    when(mockClusterState.getBrokers()).thenReturn(List.of(1));
    when(mockClusterState.getPartitions()).thenReturn(List.of());

    final Supplier<Optional<BrokerClusterState>> stateSupplier =
        () -> Optional.of(mockClusterState);
    final var sutHealthIndicator = new ClusterHealthIndicator(stateSupplier);

    // when
    final var actualHealth = sutHealthIndicator.health();

    // then
    assertThat(actualHealth).isNotNull();
    assertThat(actualHealth.getStatus()).isEqualTo(Status.DOWN);
  }

  @Test
  public void shouldReportUpIfHasHealthyPartition() {
    // given
    final BrokerClusterState mockClusterState = mock(BrokerClusterState.class);
    when(mockClusterState.getBrokers()).thenReturn(List.of(1));
    when(mockClusterState.getPartitions()).thenReturn(List.of(1));
    when(mockClusterState.getLeaderForPartition(1)).thenReturn(1);
    when(mockClusterState.getPartitionHealth(1, 1)).thenReturn(PartitionHealthStatus.HEALTHY);

    final Supplier<Optional<BrokerClusterState>> stateSupplier =
        () -> Optional.of(mockClusterState);
    final var sutHealthIndicator = new ClusterHealthIndicator(stateSupplier);

    // when
    final var actualHealth = sutHealthIndicator.health();

    // then
    assertThat(actualHealth).isNotNull();
    assertThat(actualHealth.getStatus()).isEqualTo(Status.UP);
  }

  @Test
  public void shouldReportDegradedIfMissingPartitions() {
    final BrokerClusterState mockClusterState = mock(BrokerClusterState.class);
    when(mockClusterState.getBrokers()).thenReturn(List.of(1));
    when(mockClusterState.getPartitions()).thenReturn(List.of(1, 2, 3));
    when(mockClusterState.getLeaderForPartition(1)).thenReturn(1);
    when(mockClusterState.getLeaderForPartition(2)).thenReturn(2);
    when(mockClusterState.getLeaderForPartition(3)).thenReturn(3);
    when(mockClusterState.getPartitionHealth(1, 1)).thenReturn(PartitionHealthStatus.HEALTHY);
    when(mockClusterState.getPartitionHealth(2, 2)).thenReturn(PartitionHealthStatus.HEALTHY);
    when(mockClusterState.getPartitionHealth(3, 3)).thenReturn(PartitionHealthStatus.DEAD);

    final Supplier<Optional<BrokerClusterState>> stateSupplier =
        () -> Optional.of(mockClusterState);
    final var sutHealthIndicator = new ClusterHealthIndicator(stateSupplier);

    // when
    final var actualHealth = sutHealthIndicator.health();

    // then
    assertThat(actualHealth).isNotNull();
    assertThat(actualHealth.getStatus()).isEqualTo(new Status("DEGRADED"));
  }

  @Test
  public void shouldReportUnhealthyIfMissingPartitionsAndTheOthersDegraded() {
    final BrokerClusterState mockClusterState = mock(BrokerClusterState.class);
    when(mockClusterState.getBrokers()).thenReturn(List.of(1));
    when(mockClusterState.getPartitions()).thenReturn(List.of(1, 2, 3));
    when(mockClusterState.getLeaderForPartition(1)).thenReturn(1);
    when(mockClusterState.getLeaderForPartition(2)).thenReturn(2);
    when(mockClusterState.getLeaderForPartition(3)).thenReturn(3);
    when(mockClusterState.getPartitionHealth(1, 1)).thenReturn(PartitionHealthStatus.UNHEALTHY);
    when(mockClusterState.getPartitionHealth(2, 2)).thenReturn(PartitionHealthStatus.UNHEALTHY);
    when(mockClusterState.getPartitionHealth(3, 3)).thenReturn(PartitionHealthStatus.DEAD);

    final Supplier<Optional<BrokerClusterState>> stateSupplier =
        () -> Optional.of(mockClusterState);
    final var sutHealthIndicator = new ClusterHealthIndicator(stateSupplier);

    // when
    final var actualHealth = sutHealthIndicator.health();

    // then
    assertThat(actualHealth).isNotNull();
    assertThat(actualHealth.getStatus()).isEqualTo(Status.DOWN);
  }

  @Test
  public void shouldReportDegradedIfSomePartitionsDontHaveLeader() {
    final BrokerClusterState mockClusterState = mock(BrokerClusterState.class);
    when(mockClusterState.getBrokers()).thenReturn(List.of(1));
    when(mockClusterState.getPartitions()).thenReturn(List.of(1, 2, 3));
    when(mockClusterState.getLeaderForPartition(1)).thenReturn(1);
    when(mockClusterState.getLeaderForPartition(2)).thenReturn(2);
    when(mockClusterState.getLeaderForPartition(3)).thenReturn(PARTITION_ID_NULL);
    when(mockClusterState.getPartitionHealth(1, 1)).thenReturn(PartitionHealthStatus.HEALTHY);
    when(mockClusterState.getPartitionHealth(2, 2)).thenReturn(PartitionHealthStatus.HEALTHY);
    when(mockClusterState.getPartitionHealth(3, 3)).thenReturn(PartitionHealthStatus.UNHEALTHY);

    final Supplier<Optional<BrokerClusterState>> stateSupplier =
        () -> Optional.of(mockClusterState);
    final var sutHealthIndicator = new ClusterHealthIndicator(stateSupplier);

    // when
    final var actualHealth = sutHealthIndicator.health();

    // then
    assertThat(actualHealth).isNotNull();
    assertThat(actualHealth.getStatus()).isEqualTo(new Status("DEGRADED"));
  }

  @Test
  public void shouldReportDownIfListOfBrokersIsEmpty() {
    // given
    final BrokerClusterState mockClusterState = mock(BrokerClusterState.class);
    when(mockClusterState.getBrokers()).thenReturn(emptyList());

    final Supplier<Optional<BrokerClusterState>> stateSupplier =
        () -> Optional.of(mockClusterState);
    final var sutHealthIndicator = new ClusterHealthIndicator(stateSupplier);

    // when
    final var actualHealth = sutHealthIndicator.health();

    // then
    assertThat(actualHealth).isNotNull();
    assertThat(actualHealth.getStatus()).isEqualTo(Status.DOWN);
  }
}
