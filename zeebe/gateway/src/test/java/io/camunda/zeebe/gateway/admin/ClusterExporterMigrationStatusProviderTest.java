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
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
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

final class ClusterExporterMigrationStatusProviderTest {

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
        new ClusterExporterMigrationStatusProvider(mock(BrokerClient.class), singleTenant(DEFAULT));
    assertThat(provider.conditionName())
        .isEqualTo(ClusterExporterMigrationStatusProvider.CONDITION_NAME);
  }

  @Test
  void shouldQueryOnlyTheLeaderOfEveryPartitionWhenItAnswers() {
    // given
    final var client = setupBrokerClient(Map.of(DEFAULT, TOPOLOGY));
    when(client.sendRequest(any()))
        .thenAnswer(invocation -> respondWith(MigrationStatusCode.MIGRATED, "ok"));
    final var provider = new ClusterExporterMigrationStatusProvider(client, singleTenant(DEFAULT));

    // when
    provider.getMigrationStatus();

    // then - the leader answers, so no follower is ever queried
    for (final var partition : TOPOLOGY.getPartitions()) {
      verify(client)
          .sendRequest(
              argThatTargets(DEFAULT, partition, TOPOLOGY.getLeaderForPartition(partition)));
      for (final var follower : TOPOLOGY.getFollowersForPartition(partition)) {
        verify(client, never()).sendRequest(argThatTargets(DEFAULT, partition, follower));
      }
    }
  }

  @Test
  void shouldFallBackToAFollowerWhenTheLeaderRequestFails() {
    // given - register the catch-all first, then override for the leader specifically; Mockito's
    // last-registered-matching-stub wins, so registering the specific override second is required
    final var client = setupBrokerClient(Map.of(DEFAULT, TOPOLOGY));
    when(client.sendRequest(any()))
        .thenAnswer(invocation -> respondWith(MigrationStatusCode.MIGRATED, "ok"));
    when(client.sendRequest(argThatTargets(DEFAULT, 1, TOPOLOGY.getLeaderForPartition(1))))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("leader unreachable")));
    final var provider = new ClusterExporterMigrationStatusProvider(client, singleTenant(DEFAULT));

    // when
    final var statuses = provider.getMigrationStatus();

    // then - a follower answered instead, so the condition is still confidently MIGRATED
    assertThat(statuses.get(DEFAULT).state()).isEqualTo(MigrationState.MIGRATED);
    verify(client).sendRequest(argThatTargets(DEFAULT, 1, TOPOLOGY.getLeaderForPartition(1)));
    verify(client)
        .sendRequest(
            argThatTargets(DEFAULT, 1, TOPOLOGY.getFollowersForPartition(1).iterator().next()));
  }

  @Test
  void shouldFallBackToAFollowerWhenTheLeaderStallsRatherThanFails() {
    // given - the leader never responds at all (as opposed to failing outright)
    final var client = setupBrokerClient(Map.of(DEFAULT, TOPOLOGY));
    when(client.sendRequest(any()))
        .thenAnswer(invocation -> respondWith(MigrationStatusCode.MIGRATED, "ok"));
    when(client.sendRequest(argThatTargets(DEFAULT, 1, TOPOLOGY.getLeaderForPartition(1))))
        .thenReturn(new CompletableFuture<>());
    final var provider =
        new ClusterExporterMigrationStatusProvider(
            client, singleTenant(DEFAULT), Duration.ofMillis(300));

    // when
    final var statuses = provider.getMigrationStatus();

    // then - the follower's prompt answer is used well before the shared budget expires
    assertThat(statuses.get(DEFAULT).state()).isEqualTo(MigrationState.MIGRATED);
    verify(client)
        .sendRequest(
            argThatTargets(DEFAULT, 1, TOPOLOGY.getFollowersForPartition(1).iterator().next()));
  }

  @Test
  void shouldReportMigratedWhenEveryPartitionIsMigrated() {
    // given
    final var client = setupBrokerClient(Map.of(DEFAULT, TOPOLOGY));
    when(client.sendRequest(any()))
        .thenAnswer(invocation -> respondWith(MigrationStatusCode.MIGRATED, "migrated"));
    final var provider = new ClusterExporterMigrationStatusProvider(client, singleTenant(DEFAULT));

    // when
    final var statuses = provider.getMigrationStatus();

    // then
    assertThat(statuses.get(DEFAULT).state()).isEqualTo(MigrationState.MIGRATED);
  }

  @Test
  void shouldReportMigrationInProgressWhenOnePartitionIsBehind() {
    // given
    final var client = setupBrokerClient(Map.of(DEFAULT, TOPOLOGY));
    when(client.sendRequest(any()))
        .thenAnswer(invocation -> respondWith(MigrationStatusCode.MIGRATED, "migrated"));
    when(client.sendRequest(argThatTargets(DEFAULT, 1, TOPOLOGY.getLeaderForPartition(1))))
        .thenAnswer(invocation -> respondWith(MigrationStatusCode.MIGRATION_IN_PROGRESS, "behind"));
    final var provider = new ClusterExporterMigrationStatusProvider(client, singleTenant(DEFAULT));

    // when
    final var statuses = provider.getMigrationStatus();

    // then
    assertThat(statuses.get(DEFAULT).state()).isEqualTo(MigrationState.MIGRATION_IN_PROGRESS);
  }

  @Test
  void shouldReportUnknownWhenAnyPartitionIsUnknownEvenIfOthersAreMigrated() {
    // given - UNKNOWN takes precedence: we must not claim a confident answer when part of the
    // picture is missing.
    final var client = setupBrokerClient(Map.of(DEFAULT, TOPOLOGY));
    when(client.sendRequest(any()))
        .thenAnswer(invocation -> respondWith(MigrationStatusCode.MIGRATED, "migrated"));
    when(client.sendRequest(argThatTargets(DEFAULT, 1, TOPOLOGY.getLeaderForPartition(1))))
        .thenAnswer(invocation -> respondWith(MigrationStatusCode.UNKNOWN, "unreachable"));
    final var provider = new ClusterExporterMigrationStatusProvider(client, singleTenant(DEFAULT));

    // when
    final var statuses = provider.getMigrationStatus();

    // then
    assertThat(statuses.get(DEFAULT).state()).isEqualTo(MigrationState.UNKNOWN);
  }

  @Test
  void shouldReportUnknownWhenEveryReplicaOfAPartitionFails() {
    // given - leader and every follower fail; there is no replica left to fall back to
    final var client = setupBrokerClient(Map.of(DEFAULT, TOPOLOGY));
    when(client.sendRequest(any()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unreachable")));
    final var provider = new ClusterExporterMigrationStatusProvider(client, singleTenant(DEFAULT));

    // when
    final var statuses = provider.getMigrationStatus();

    // then - a failed fan-out must never throw out of getMigrationStatus()
    assertThat(statuses.get(DEFAULT).state()).isEqualTo(MigrationState.UNKNOWN);
  }

  @Test
  void shouldReportUnknownWhenTheFanOutTimesOut() {
    // given - every replica of every partition never completes, and a short shared timeout so
    // the test itself stays fast
    final var client = setupBrokerClient(Map.of(DEFAULT, TOPOLOGY));
    when(client.sendRequest(any())).thenReturn(new CompletableFuture<>());
    final var provider =
        new ClusterExporterMigrationStatusProvider(
            client, singleTenant(DEFAULT), Duration.ofMillis(200));

    // when
    final var statuses = provider.getMigrationStatus();

    // then
    assertThat(statuses.get(DEFAULT).state()).isEqualTo(MigrationState.UNKNOWN);
    assertThat(statuses.get(DEFAULT).detail()).contains("no known replica to query");
  }

  @Test
  void shouldReportUnknownForATenantWithoutFailingOthersWhenTopologyIsNull() {
    // given - the topology manager returns null for "tenantB" (not yet known)
    final var client = setupBrokerClient(Map.of(DEFAULT, TOPOLOGY));
    when(client.sendRequest(any()))
        .thenAnswer(invocation -> respondWith(MigrationStatusCode.MIGRATED, "ok"));
    final var provider =
        new ClusterExporterMigrationStatusProvider(client, multiTenant(DEFAULT, "tenantB"));

    // when
    final var statuses = provider.getMigrationStatus();

    // then - "tenantB" reports UNKNOWN on its own, without failing "default"'s fan-out too
    assertThat(statuses.get(DEFAULT).state()).isEqualTo(MigrationState.MIGRATED);
    assertThat(statuses.get("tenantB").state()).isEqualTo(MigrationState.UNKNOWN);
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
        new ClusterExporterMigrationStatusProvider(client, multiTenant(DEFAULT, "tenantB"));

    // when
    final var statuses = provider.getMigrationStatus();

    // then
    assertThat(statuses).containsOnlyKeys(DEFAULT, "tenantB");
    assertThat(statuses.get(DEFAULT).state()).isEqualTo(MigrationState.MIGRATED);
    assertThat(statuses.get("tenantB").state()).isEqualTo(MigrationState.MIGRATED);
    verify(client).sendRequest(argThatTargets("tenantB", 1, BrokerMemberId.from(4)));
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
        new ClusterExporterMigrationStatusProvider(
            client, multiTenant(DEFAULT, "tenantB"), Duration.ofMillis(200));

    // when
    final var statuses = provider.getMigrationStatus();

    // then - the slow tenant does not hold back the fast one
    assertThat(statuses.get(DEFAULT).state()).isEqualTo(MigrationState.MIGRATED);
    assertThat(statuses.get("tenantB").state()).isEqualTo(MigrationState.UNKNOWN);
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

  private static BrokerRequest<AdminResponse> argThatTargets(
      final String physicalTenantId, final int partitionId, final BrokerMemberId brokerId) {
    return argThat(
        request ->
            request != null
                && request.getPartitionGroup().equals(physicalTenantId)
                && request.getPartitionId() == partitionId
                && request.getBrokerId().orElseThrow().equals(brokerId));
  }

  private static BrokerRequest<AdminResponse> argThatTargetsTenant(final String physicalTenantId) {
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
