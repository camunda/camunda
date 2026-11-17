/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.probes.health;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.BrokerMemberId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

public class ClusterHealthIndicatorTest {

  @Test
  public void shouldRejectNullInConstructor() {
    // when + then
    assertThatThrownBy(() -> new ClusterHealthIndicator(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldReportDownIfSupplierReturnsEmptyMap() {
    // given
    final Supplier<Map<String, BrokerClusterState>> stateSupplier = Map::of;
    final var sutHealthIndicator = new ClusterHealthIndicator(stateSupplier);

    // when
    final var actualHealth = sutHealthIndicator.health();

    // then
    assertThat(actualHealth).isNotNull();
    assertThat(actualHealth.getStatus()).isEqualTo(Status.DOWN);
  }

  @Test
  public void shouldReportUpIfOnlyNonDefaultTenantIsKnownAndHealthy() {
    // given
    final BrokerClusterState mockClusterState = mock(BrokerClusterState.class);
    when(mockClusterState.getBrokers()).thenReturn(List.of(BrokerMemberId.from(1)));
    when(mockClusterState.getPartitions()).thenReturn(List.of(1));
    when(mockClusterState.getLeaderForPartition(1)).thenReturn(BrokerMemberId.from(1));
    when(mockClusterState.getPartitionHealth(BrokerMemberId.from(1), 1))
        .thenReturn(PartitionHealthStatus.HEALTHY);

    final Supplier<Map<String, BrokerClusterState>> stateSupplier =
        () -> Map.of("other-tenant", mockClusterState);
    final var sutHealthIndicator = new ClusterHealthIndicator(stateSupplier);

    // when
    final var actualHealth = sutHealthIndicator.health();

    // then
    assertThat(actualHealth).isNotNull();
    assertThat(actualHealth.getStatus()).isEqualTo(Status.UP);
  }

  @Test
  public void shouldReportDegradedIfOneTenantIsDownAndAnotherIsHealthy() {
    // given
    final BrokerClusterState downState = mock(BrokerClusterState.class);
    when(downState.getBrokers()).thenReturn(List.of());
    final BrokerClusterState healthyState = mock(BrokerClusterState.class);
    when(healthyState.getBrokers()).thenReturn(List.of(BrokerMemberId.from(1)));
    when(healthyState.getPartitions()).thenReturn(List.of(1));
    when(healthyState.getLeaderForPartition(1)).thenReturn(BrokerMemberId.from(1));
    when(healthyState.getPartitionHealth(BrokerMemberId.from(1), 1))
        .thenReturn(PartitionHealthStatus.HEALTHY);

    final Supplier<Map<String, BrokerClusterState>> stateSupplier =
        () ->
            Map.of(
                PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID,
                downState,
                "other-tenant",
                healthyState);
    final var sutHealthIndicator = new ClusterHealthIndicator(stateSupplier);

    // when
    final var actualHealth = sutHealthIndicator.health();

    // then
    assertThat(actualHealth).isNotNull();
    assertThat(actualHealth.getStatus()).isEqualTo(new Status("DEGRADED"));
    assertThat(actualHealth.getDetails())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID,
                Map.of("status", "DOWN", "partitions", Map.of()),
                "other-tenant",
                Map.of(
                    "status",
                    "UP",
                    "partitions",
                    Map.of("Partition 1", PartitionHealthStatus.HEALTHY))));
  }

  @Test
  public void shouldReportDownIfAllTenantsAreDown() {
    // given
    final BrokerClusterState downState = mock(BrokerClusterState.class);
    when(downState.getBrokers()).thenReturn(List.of());
    final BrokerClusterState otherDownState = mock(BrokerClusterState.class);
    when(otherDownState.getBrokers()).thenReturn(List.of());

    final Supplier<Map<String, BrokerClusterState>> stateSupplier =
        () ->
            Map.of(
                PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID,
                downState,
                "other-tenant",
                otherDownState);
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
    when(mockClusterState.getBrokers()).thenReturn(List.of(BrokerMemberId.from(1)));
    when(mockClusterState.getPartitions()).thenReturn(List.of());

    final Supplier<Map<String, BrokerClusterState>> stateSupplier =
        () -> Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, mockClusterState);
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
    when(mockClusterState.getBrokers()).thenReturn(List.of(BrokerMemberId.from(1)));
    when(mockClusterState.getPartitions()).thenReturn(List.of(1));
    when(mockClusterState.getLeaderForPartition(1)).thenReturn(BrokerMemberId.from(1));
    when(mockClusterState.getPartitionHealth(BrokerMemberId.from(1), 1))
        .thenReturn(PartitionHealthStatus.HEALTHY);

    final Supplier<Map<String, BrokerClusterState>> stateSupplier =
        () -> Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, mockClusterState);
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
    when(mockClusterState.getBrokers()).thenReturn(List.of(BrokerMemberId.from(1)));
    when(mockClusterState.getPartitions()).thenReturn(List.of(1, 2, 3));
    when(mockClusterState.getLeaderForPartition(1)).thenReturn(BrokerMemberId.from(1));
    when(mockClusterState.getLeaderForPartition(2)).thenReturn(BrokerMemberId.from(2));
    when(mockClusterState.getLeaderForPartition(3)).thenReturn(BrokerMemberId.from(3));
    when(mockClusterState.getPartitionHealth(BrokerMemberId.from(1), 1))
        .thenReturn(PartitionHealthStatus.HEALTHY);
    when(mockClusterState.getPartitionHealth(BrokerMemberId.from(2), 2))
        .thenReturn(PartitionHealthStatus.HEALTHY);
    when(mockClusterState.getPartitionHealth(BrokerMemberId.from(3), 3))
        .thenReturn(PartitionHealthStatus.DEAD);

    final Supplier<Map<String, BrokerClusterState>> stateSupplier =
        () -> Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, mockClusterState);
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
    when(mockClusterState.getBrokers()).thenReturn(List.of(BrokerMemberId.from(1)));
    when(mockClusterState.getPartitions()).thenReturn(List.of(1, 2, 3));
    when(mockClusterState.getLeaderForPartition(1)).thenReturn(BrokerMemberId.from(1));
    when(mockClusterState.getLeaderForPartition(2)).thenReturn(BrokerMemberId.from(2));
    when(mockClusterState.getLeaderForPartition(3)).thenReturn(BrokerMemberId.from(3));
    when(mockClusterState.getPartitionHealth(BrokerMemberId.from(1), 1))
        .thenReturn(PartitionHealthStatus.UNHEALTHY);
    when(mockClusterState.getPartitionHealth(BrokerMemberId.from(2), 2))
        .thenReturn(PartitionHealthStatus.UNHEALTHY);
    when(mockClusterState.getPartitionHealth(BrokerMemberId.from(3), 3))
        .thenReturn(PartitionHealthStatus.DEAD);

    final Supplier<Map<String, BrokerClusterState>> stateSupplier =
        () -> Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, mockClusterState);
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
    when(mockClusterState.getBrokers()).thenReturn(List.of(BrokerMemberId.from(1)));
    when(mockClusterState.getPartitions()).thenReturn(List.of(1, 2, 3));
    when(mockClusterState.getLeaderForPartition(1)).thenReturn(BrokerMemberId.from(1));
    when(mockClusterState.getLeaderForPartition(2)).thenReturn(BrokerMemberId.from(2));
    when(mockClusterState.getLeaderForPartition(3)).thenReturn(null);
    when(mockClusterState.getPartitionHealth(BrokerMemberId.from(1), 1))
        .thenReturn(PartitionHealthStatus.HEALTHY);
    when(mockClusterState.getPartitionHealth(BrokerMemberId.from(2), 2))
        .thenReturn(PartitionHealthStatus.HEALTHY);
    when(mockClusterState.getPartitionHealth(BrokerMemberId.from(3), 3))
        .thenReturn(PartitionHealthStatus.UNHEALTHY);

    final Supplier<Map<String, BrokerClusterState>> stateSupplier =
        () -> Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, mockClusterState);
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

    final Supplier<Map<String, BrokerClusterState>> stateSupplier =
        () -> Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, mockClusterState);
    final var sutHealthIndicator = new ClusterHealthIndicator(stateSupplier);

    // when
    final var actualHealth = sutHealthIndicator.health();

    // then
    assertThat(actualHealth).isNotNull();
    assertThat(actualHealth.getStatus()).isEqualTo(Status.DOWN);
  }
}
