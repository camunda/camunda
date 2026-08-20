/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.BrokerMemberId;
import io.camunda.service.ClusterTopologyServices.ClusterBroker;
import io.camunda.service.ClusterTopologyServices.ClusterTopology;
import io.camunda.service.ClusterTopologyServices.PhysicalTenantBroker;
import io.camunda.service.TopologyServices.Broker;
import io.camunda.service.TopologyServices.Health;
import io.camunda.service.TopologyServices.Partition;
import io.camunda.service.TopologyServices.Role;
import io.camunda.service.TopologyServices.State;
import io.camunda.service.TopologyServices.Topology;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class ClusterTopologyServicesTest {

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";
  private static final String DEFAULT_TENANT = "default";

  @Test
  void shouldReportOnePhysicalTenantBlockForASingleInitializedTenant() {
    // given
    final var services =
        servicesOf(Map.of(DEFAULT_TENANT, topology("cluster-1", 3, 1, 3, 10L, brokers(0, 1))));

    // when
    final var topology = getTopology(services);

    // then
    assertThat(topology.physicalTenants()).hasSize(1);
    final var pt = topology.physicalTenants().get(0);
    assertThat(pt.physicalTenantId()).isEqualTo(DEFAULT_TENANT);
    assertThat(pt.partitionsCount()).isEqualTo(3);
    assertThat(pt.replicationFactor()).isEqualTo(1);
    assertThat(pt.lastCompletedChangeId()).isEqualTo(10L);
    assertThat(topology.clusterId()).isEqualTo("cluster-1");
    assertThat(topology.clusterSize()).isEqualTo(3);
  }

  @Test
  void shouldSortPhysicalTenantsByIdRegardlessOfMapInsertionOrder() {
    // given — insert tenant b before tenant a, so the test fails without an explicit sort
    final Map<String, TopologyServices> byTenant = new LinkedHashMap<>();
    byTenant.put(TENANT_B, mockOf(topology("cluster-1", 2, 1, 1, 1L, brokers(0))));
    byTenant.put(TENANT_A, mockOf(topology("cluster-1", 2, 1, 1, 1L, brokers(1))));
    final var services = new ClusterTopologyServices(byTenant);

    // when
    final var topology = getTopology(services);

    // then
    assertThat(topology.physicalTenants())
        .extracting(pt -> pt.physicalTenantId())
        .containsExactly(TENANT_A, TENANT_B);
  }

  @Test
  void shouldReportClusterLevelFieldsOnceEvenWhenEveryTenantReportsTheSameValue() {
    // given
    final var services =
        servicesOf(
            Map.of(
                TENANT_A, topology("cluster-1", 3, 1, 3, 5L, brokers(0)),
                TENANT_B, topology("cluster-1", 3, 1, 3, 7L, brokers(1))));

    // when
    final var topology = getTopology(services);

    // then
    assertThat(topology.clusterId()).isEqualTo("cluster-1");
    assertThat(topology.clusterSize()).isEqualTo(3);
  }

  @Test
  void shouldUnionClusterLevelBrokersAcrossPhysicalTenants() {
    // given — tenant a hosts brokers {0,1}, tenant b hosts brokers {1,2}: broker 1 must not be
    // duplicated, and broker 2 (only in tenant b) must not be omitted
    final var services =
        servicesOf(
            Map.of(
                TENANT_A, topology("cluster-1", 3, 1, 1, 1L, brokers(0, 1)),
                TENANT_B, topology("cluster-1", 3, 1, 1, 1L, brokers(1, 2))));

    // when
    final var topology = getTopology(services);

    // then
    assertThat(topology.brokers())
        .extracting(ClusterBroker::brokerId)
        .extracting(BrokerMemberId::id)
        .containsExactly("0", "1", "2");
  }

  @Test
  void shouldSkipAPhysicalTenantWhoseFutureFailedButStillCompleteTheOverallFuture() {
    // given
    final Map<String, TopologyServices> byTenant = new LinkedHashMap<>();
    byTenant.put(TENANT_A, mockOf(topology("cluster-1", 2, 1, 1, 1L, brokers(0))));
    final var failing = mock(TopologyServices.class);
    when(failing.getTopology())
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("topology unavailable")));
    byTenant.put(TENANT_B, failing);
    final var services = new ClusterTopologyServices(byTenant);

    // when
    final var topology = getTopology(services);

    // then — the failed tenant contributed nothing, but the request still succeeds
    assertThat(topology.physicalTenants())
        .extracting(pt -> pt.physicalTenantId())
        .containsExactly(TENANT_A);
  }

  @Test
  void shouldIncludeAnUninitializedPhysicalTenantButNotSourceClusterLevelFieldsFromIt() {
    // given — tenant a is uninitialized (sentinel values), tenant b is initialized
    final var uninitialized = topology("", -1, 0, 0, -1L, List.of());
    final var initialized = topology("cluster-1", 3, 1, 3, 5L, brokers(0));
    final var services = servicesOf(Map.of(TENANT_A, uninitialized, TENANT_B, initialized));

    // when
    final var topology = getTopology(services);

    // then — both blocks are present ...
    assertThat(topology.physicalTenants())
        .extracting(pt -> pt.physicalTenantId())
        .containsExactly(TENANT_A, TENANT_B);
    // ... but the cluster-level fields come from the initialized tenant, not the sentinel
    assertThat(topology.clusterId()).isEqualTo("cluster-1");
    assertThat(topology.clusterSize()).isEqualTo(3);
  }

  @Test
  void shouldNormalizeClusterLevelFieldsWhenEveryPhysicalTenantIsUninitialized() {
    // given
    final var services = servicesOf(Map.of(DEFAULT_TENANT, topology("", -1, 0, 0, -1L, List.of())));

    // when
    final var topology = getTopology(services);

    // then
    assertThat(topology.physicalTenants()).hasSize(1);
    assertThat(topology.clusterId()).isNull();
    assertThat(topology.clusterSize()).isZero();
  }

  @Test
  void shouldCompleteWithEmptyTopologyWhenNoPhysicalTenantIsKnown() {
    // given
    final var services = new ClusterTopologyServices(Map.of());

    // when
    final var topology = getTopology(services);

    // then
    assertThat(topology.brokers()).isEmpty();
    assertThat(topology.physicalTenants()).isEmpty();
    assertThat(topology.clusterId()).isNull();
    assertThat(topology.clusterSize()).isZero();
  }

  @Test
  void shouldCarryPerBrokerPartitionDetailThroughUnchanged() {
    // given
    final var partition = new Partition(1, Role.LEADER, Health.HEALTHY, State.ACTIVE);
    final var broker = new Broker(null, 0, "host-0", 26501, List.of(partition), "8.10.0");
    final var services =
        servicesOf(Map.of(DEFAULT_TENANT, topology("cluster-1", 1, 1, 1, 1L, List.of(broker))));

    // when
    final var topology = getTopology(services);

    // then
    final PhysicalTenantBroker resultBroker = topology.physicalTenants().get(0).brokers().get(0);
    assertThat(resultBroker.partitions()).containsExactly(partition);
  }

  private static List<Broker> brokers(final int... nodeIds) {
    return java.util.Arrays.stream(nodeIds)
        .mapToObj(nodeId -> new Broker(null, nodeId, "host-" + nodeId, 26501, List.of(), "8.10.0"))
        .toList();
  }

  private static Topology topology(
      final String clusterId,
      final int clusterSize,
      final int replicationFactor,
      final int partitionsCount,
      final long lastCompletedChangeId,
      final List<Broker> brokers) {
    final var builder = TopologyServices.Topology.Builder.create();
    builder
        .clusterId(clusterId)
        .clusterSize(clusterSize)
        .partitionsCount(partitionsCount)
        .replicationFactor(replicationFactor)
        .lastCompletedChangeId(lastCompletedChangeId)
        .gatewayVersion("8.10.0");
    brokers.forEach(builder::addBroker);
    return builder.build();
  }

  private static TopologyServices mockOf(final Topology topology) {
    final var topologyServices = mock(TopologyServices.class);
    when(topologyServices.getTopology()).thenReturn(CompletableFuture.completedFuture(topology));
    return topologyServices;
  }

  private static ClusterTopologyServices servicesOf(final Map<String, Topology> topologyByTenant) {
    final Map<String, TopologyServices> byTenant = new LinkedHashMap<>();
    topologyByTenant.forEach((tenantId, topology) -> byTenant.put(tenantId, mockOf(topology)));
    return new ClusterTopologyServices(byTenant);
  }

  private static ClusterTopology getTopology(final ClusterTopologyServices services) {
    return services.getTopology().join();
  }
}
