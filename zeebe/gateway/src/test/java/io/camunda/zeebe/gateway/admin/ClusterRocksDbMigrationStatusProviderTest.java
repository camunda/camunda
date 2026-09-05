/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.BrokerMemberId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.cluster.migration.MigrationState;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.dynamic.config.state.PartitionState.State;
import io.camunda.zeebe.protocol.impl.encoding.AdminResponse;
import io.camunda.zeebe.protocol.impl.encoding.MigrationStatusCode;
import io.camunda.zeebe.protocol.impl.encoding.MigrationStatusPayload;
import io.camunda.zeebe.protocol.impl.encoding.PartitionMigrationStatus;
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

final class ClusterRocksDbMigrationStatusProviderTest {

  private static final String DEFAULT = PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

  private static final BrokerClusterState TOPOLOGY =
      ofTopology(
          Map.of(
              1,
              List.of(BrokerMemberId.from(1), BrokerMemberId.from(2), BrokerMemberId.from(3)),
              2,
              List.of(BrokerMemberId.from(2), BrokerMemberId.from(1), BrokerMemberId.from(3))));

  @Test
  void shouldReportConditionName() {
    final var provider =
        new ClusterRocksDbMigrationStatusProvider(mock(BrokerClient.class), singleTenant(DEFAULT));
    assertThat(provider.conditionName())
        .isEqualTo(ClusterRocksDbMigrationStatusProvider.CONDITION_NAME);
  }

  @Test
  void shouldQueryEveryReplicaOfEveryPartitionForTheKnownTenant() {
    // given
    final var client = setupBrokerClient(Map.of(DEFAULT, TOPOLOGY));
    when(client.sendRequest(any()))
        .thenAnswer(invocation -> respondWith(MigrationStatusCode.MIGRATED, "ok"));
    final var provider = new ClusterRocksDbMigrationStatusProvider(client, singleTenant(DEFAULT));

    // when
    provider.getMigrationStatus();

    // then - leader and every follower of every partition, not just leaders
    for (final var partition : TOPOLOGY.getPartitions()) {
      verify(client)
          .sendRequest(
              argThatTargets(DEFAULT, partition, TOPOLOGY.getLeaderForPartition(partition)));
      for (final var follower : TOPOLOGY.getFollowersForPartition(partition)) {
        verify(client).sendRequest(argThatTargets(DEFAULT, partition, follower));
      }
    }
  }

  @Test
  void shouldQueryEveryKnownPhysicalTenantIndependently() {
    // given - two tenants, each with their own topology and their own leader
    final var topologyB =
        ofTopology(Map.of(1, List.of(BrokerMemberId.from(4), BrokerMemberId.from(5))));
    final var client = setupBrokerClient(Map.of(DEFAULT, TOPOLOGY, "tenantB", topologyB));
    when(client.sendRequest(any()))
        .thenAnswer(invocation -> respondWith(MigrationStatusCode.MIGRATED, "ok"));
    final var provider =
        new ClusterRocksDbMigrationStatusProvider(client, multiTenant(DEFAULT, "tenantB"));

    // when
    final var statuses = provider.getMigrationStatus();

    // then
    assertThat(statuses).containsOnlyKeys(DEFAULT, "tenantB");
    assertThat(statuses.get(DEFAULT).state()).isEqualTo(MigrationState.MIGRATED);
    assertThat(statuses.get("tenantB").state()).isEqualTo(MigrationState.MIGRATED);
    verify(client).sendRequest(argThatTargets("tenantB", 1, BrokerMemberId.from(4)));
    verify(client).sendRequest(argThatTargets("tenantB", 1, BrokerMemberId.from(5)));
  }

  @Test
  void shouldReportMigratedWhenEveryReplicaIsMigrated() {
    // given
    final var client = setupBrokerClient(Map.of(DEFAULT, TOPOLOGY));
    when(client.sendRequest(any()))
        .thenAnswer(invocation -> respondWith(MigrationStatusCode.MIGRATED, "migrated"));
    final var provider = new ClusterRocksDbMigrationStatusProvider(client, singleTenant(DEFAULT));

    // when
    final var statuses = provider.getMigrationStatus();

    // then
    assertThat(statuses.get(DEFAULT).state()).isEqualTo(MigrationState.MIGRATED);
  }

  @Test
  void shouldReportMigrationInProgressWhenOneReplicaIsBehind() {
    // given
    final var client = setupBrokerClient(Map.of(DEFAULT, TOPOLOGY));
    when(client.sendRequest(any()))
        .thenAnswer(invocation -> respondWith(MigrationStatusCode.MIGRATED, "migrated"));
    when(client.sendRequest(argThatTargets(DEFAULT, 1, TOPOLOGY.getLeaderForPartition(1))))
        .thenAnswer(invocation -> respondWith(MigrationStatusCode.MIGRATION_IN_PROGRESS, "behind"));
    final var provider = new ClusterRocksDbMigrationStatusProvider(client, singleTenant(DEFAULT));

    // when
    final var statuses = provider.getMigrationStatus();

    // then
    assertThat(statuses.get(DEFAULT).state()).isEqualTo(MigrationState.MIGRATION_IN_PROGRESS);
  }

  @Test
  void shouldReportUnknownWhenAnyReplicaIsUnknownEvenIfOthersAreMigrated() {
    // given - UNKNOWN takes precedence: we must not claim a confident answer when part of the
    // picture is missing.
    final var client = setupBrokerClient(Map.of(DEFAULT, TOPOLOGY));
    when(client.sendRequest(any()))
        .thenAnswer(invocation -> respondWith(MigrationStatusCode.MIGRATED, "migrated"));
    when(client.sendRequest(argThatTargets(DEFAULT, 1, TOPOLOGY.getLeaderForPartition(1))))
        .thenAnswer(invocation -> respondWith(MigrationStatusCode.UNKNOWN, "unreachable"));
    final var provider = new ClusterRocksDbMigrationStatusProvider(client, singleTenant(DEFAULT));

    // when
    final var statuses = provider.getMigrationStatus();

    // then
    assertThat(statuses.get(DEFAULT).state()).isEqualTo(MigrationState.UNKNOWN);
  }

  @Test
  void shouldReportUnknownWhenARequestFails() {
    // given
    final var client = setupBrokerClient(Map.of(DEFAULT, TOPOLOGY));
    when(client.sendRequest(any()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unreachable")));
    final var provider = new ClusterRocksDbMigrationStatusProvider(client, singleTenant(DEFAULT));

    // when
    final var statuses = provider.getMigrationStatus();

    // then - a failed fan-out must never throw out of getMigrationStatus()
    assertThat(statuses.get(DEFAULT).state()).isEqualTo(MigrationState.UNKNOWN);
  }

  @Test
  void shouldReportUnknownWhenTheFanOutTimesOut() {
    // given - a request that never completes, and a short timeout so the test itself stays fast
    final var client = setupBrokerClient(Map.of(DEFAULT, TOPOLOGY));
    when(client.sendRequest(any())).thenReturn(new CompletableFuture<>());
    final var provider =
        new ClusterRocksDbMigrationStatusProvider(
            client, singleTenant(DEFAULT), Duration.ofMillis(200));

    // when
    final var statuses = provider.getMigrationStatus();

    // then
    assertThat(statuses.get(DEFAULT).state()).isEqualTo(MigrationState.UNKNOWN);
    assertThat(statuses.get(DEFAULT).detail()).contains("timed out");
  }

  @Test
  void shouldReportEachTenantIndependentlyWhenOnlyOneTimesOut() {
    // given - "default" resolves quickly, "tenantB" never responds; both share one timeout budget
    final var topologyB =
        ofTopology(Map.of(1, List.of(BrokerMemberId.from(4), BrokerMemberId.from(5))));
    final var client = setupBrokerClient(Map.of(DEFAULT, TOPOLOGY, "tenantB", topologyB));
    when(client.sendRequest(any()))
        .thenAnswer(invocation -> respondWith(MigrationStatusCode.MIGRATED, "migrated"));
    when(client.sendRequest(argThatTargetsTenant("tenantB"))).thenReturn(new CompletableFuture<>());
    final var provider =
        new ClusterRocksDbMigrationStatusProvider(
            client, multiTenant(DEFAULT, "tenantB"), Duration.ofMillis(200));

    // when
    final var statuses = provider.getMigrationStatus();

    // then - the slow tenant does not hold back the fast one
    assertThat(statuses.get(DEFAULT).state()).isEqualTo(MigrationState.MIGRATED);
    assertThat(statuses.get("tenantB").state()).isEqualTo(MigrationState.UNKNOWN);
  }

  @Test
  void shouldReportUnknownWithoutQueryingAnyReplicaWhenTopologyIsIncomplete() {
    // given - partition 2's leader is not known yet (e.g. gossip hasn't caught up), so the
    // topology doesn't yet reflect every partition's full replica set
    final var incompleteTopology =
        ofTopology(
            Map.of(
                1, List.of(BrokerMemberId.from(1), BrokerMemberId.from(2)),
                2, List.of()));
    final var client = setupBrokerClient(Map.of(DEFAULT, incompleteTopology));
    final var provider = new ClusterRocksDbMigrationStatusProvider(client, singleTenant(DEFAULT));

    // when
    final var statuses = provider.getMigrationStatus();

    // then - reported unknown rather than silently aggregating only the partitions that are known
    assertThat(statuses.get(DEFAULT).state()).isEqualTo(MigrationState.UNKNOWN);
    assertThat(statuses.get(DEFAULT).detail()).contains("incomplete topology");
    verify(client, never()).sendRequest(any());
  }

  private static CompletableFuture<BrokerResponse<AdminResponse>> respondWith(
      final MigrationStatusCode code, final String detail) {
    final var response = mock(AdminResponse.class);
    when(response.getPayload())
        .thenReturn(MigrationStatusPayload.encode(new PartitionMigrationStatus(code, detail)));
    return CompletableFuture.completedFuture(new BrokerResponse<>(response));
  }

  private static PhysicalTenantIds singleTenant(final String physicalTenantId) {
    return () -> Set.of(physicalTenantId);
  }

  private static PhysicalTenantIds multiTenant(final String... physicalTenantIds) {
    return () -> Set.of(physicalTenantIds);
  }

  private static BrokerClient setupBrokerClient(
      final Map<String, BrokerClusterState> topologyByTenant) {
    final var client = mock(BrokerClient.class);
    final var topologyManager = mock(BrokerTopologyManager.class);
    topologyByTenant.forEach(
        (tenantId, topology) -> when(topologyManager.getTopology(tenantId)).thenReturn(topology));
    when(client.getTopologyManager()).thenReturn(topologyManager);
    return client;
  }

  private static io.camunda.zeebe.broker.client.api.dto.BrokerRequest<AdminResponse> argThatTargets(
      final String physicalTenantId, final int partitionId, final BrokerMemberId brokerId) {
    return argThat(
        request ->
            request != null
                && request.getPartitionGroup().equals(physicalTenantId)
                && request.getPartitionId() == partitionId
                && request.getBrokerId().orElseThrow().equals(brokerId));
  }

  private static io.camunda.zeebe.broker.client.api.dto.BrokerRequest<AdminResponse>
      argThatTargetsTenant(final String physicalTenantId) {
    return argThat(
        request -> request != null && request.getPartitionGroup().equals(physicalTenantId));
  }

  private static BrokerClusterState ofTopology(final Map<Integer, List<BrokerMemberId>> topology) {
    return new BrokerClusterState() {
      @Override
      public boolean isInitialized() {
        return true;
      }

      @Override
      public int getClusterSize() {
        return (int) topology.values().stream().flatMap(Collection::stream).distinct().count();
      }

      @Override
      public int getPartitionsCount() {
        return topology.size();
      }

      @Override
      public int getReplicationFactor() {
        return topology.values().stream().map(List::size).max(Integer::compareTo).orElse(1);
      }

      @Override
      public @Nullable BrokerMemberId getLeaderForPartition(final int partition) {
        return Optional.ofNullable(topology.get(partition))
            .filter(brokers -> !brokers.isEmpty())
            .map(List::getFirst)
            .orElse(null);
      }

      @Override
      public Set<BrokerMemberId> getFollowersForPartition(final int partition) {
        return topology.getOrDefault(partition, List.of()).stream()
            .skip(1)
            .collect(Collectors.toSet());
      }

      @Override
      public Set<BrokerMemberId> getInactiveNodesForPartition(final int partition) {
        return Set.of();
      }

      @Override
      public @Nullable BrokerMemberId getRandomBroker() {
        throw new UnsupportedOperationException();
      }

      @Override
      public List<Integer> getPartitions() {
        return new ArrayList<>(topology.keySet());
      }

      @Override
      public List<BrokerMemberId> getBrokers() {
        return topology.values().stream().flatMap(Collection::stream).distinct().toList();
      }

      @Override
      public @Nullable String getBrokerAddress(final @NonNull BrokerMemberId brokerId) {
        throw new UnsupportedOperationException();
      }

      @Override
      public @Nullable String getBrokerVersion(final @NonNull BrokerMemberId brokerId) {
        throw new UnsupportedOperationException();
      }

      @Override
      public PartitionHealthStatus getPartitionHealth(
          final @NonNull BrokerMemberId brokerId, final int partition) {
        throw new UnsupportedOperationException();
      }

      @Override
      public State getPartitionState(final BrokerMemberId brokerId, final int partition) {
        throw new UnsupportedOperationException();
      }

      @Override
      public long getLastCompletedChangeId() {
        return 0;
      }

      @Override
      public String getClusterId() {
        throw new UnsupportedOperationException();
      }
    };
  }
}
