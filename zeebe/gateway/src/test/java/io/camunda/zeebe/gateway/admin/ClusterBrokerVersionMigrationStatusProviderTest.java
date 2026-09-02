/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.admin;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.BrokerMemberId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.cluster.migration.MigrationState;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.util.SemanticVersion;
import io.camunda.zeebe.util.VersionUtil;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ClusterBrokerVersionMigrationStatusProviderTest {

  private static final BrokerMemberId BROKER_1 = BrokerMemberId.from(1);
  private static final BrokerMemberId BROKER_2 = BrokerMemberId.from(2);

  @Test
  void shouldReportConditionName() {
    final var provider =
        new ClusterBrokerVersionMigrationStatusProvider(
            mock(BrokerClient.class), singleTenant(DEFAULT_PHYSICAL_TENANT_ID));
    assertThat(provider.conditionName())
        .isEqualTo(ClusterBrokerVersionMigrationStatusProvider.CONDITION_NAME);
  }

  @Test
  void shouldReportMigratedWhenEveryBrokerIsOnTheCurrentVersion() {
    // given
    final var currentVersion = VersionUtil.getVersion();
    final var client =
        setupBrokerClient(
            List.of(BROKER_1, BROKER_2),
            Map.of(BROKER_1, currentVersion, BROKER_2, currentVersion));
    final var provider =
        new ClusterBrokerVersionMigrationStatusProvider(
            client, singleTenant(DEFAULT_PHYSICAL_TENANT_ID));

    // when
    final var statuses = provider.getMigrationStatus();

    // then
    assertThat(statuses.get(DEFAULT_PHYSICAL_TENANT_ID).state()).isEqualTo(MigrationState.MIGRATED);
  }

  @Test
  void shouldReportMigrationInProgressWhenOneBrokerIsOnAnOlderMinorVersion() {
    // given - one broker hasn't restarted on the target version yet
    final var currentVersion = VersionUtil.getVersion();
    final var previousVersion = oneMinorBehind(currentVersion);
    final var client =
        setupBrokerClient(
            List.of(BROKER_1, BROKER_2),
            Map.of(BROKER_1, currentVersion, BROKER_2, previousVersion));
    final var provider =
        new ClusterBrokerVersionMigrationStatusProvider(
            client, singleTenant(DEFAULT_PHYSICAL_TENANT_ID));

    // when
    final var status = provider.getMigrationStatus().get(DEFAULT_PHYSICAL_TENANT_ID);

    // then
    assertThat(status.state()).isEqualTo(MigrationState.MIGRATION_IN_PROGRESS);
    assertThat(status.detail()).contains(BROKER_2.toString());
  }

  @Test
  void shouldReportMigratedWhenTheOnlyDifferenceIsAPatchVersion() {
    // given - a patch-only difference never changes the wire/record format, so it doesn't count
    final var currentVersion = VersionUtil.getVersion();
    final var patchAhead = withPatchOffset(currentVersion, 1);
    final var client = setupBrokerClient(List.of(BROKER_1), Map.of(BROKER_1, patchAhead));
    final var provider =
        new ClusterBrokerVersionMigrationStatusProvider(
            client, singleTenant(DEFAULT_PHYSICAL_TENANT_ID));

    // when
    final var status = provider.getMigrationStatus().get(DEFAULT_PHYSICAL_TENANT_ID);

    // then
    assertThat(status.state()).isEqualTo(MigrationState.MIGRATED);
  }

  @Test
  void shouldReportUnknownWhenABrokerVersionIsNotYetKnown() {
    // given - the topology has gossiped this broker's membership but not its version yet
    final var client = setupBrokerClient(List.of(BROKER_1), Map.of());
    final var provider =
        new ClusterBrokerVersionMigrationStatusProvider(
            client, singleTenant(DEFAULT_PHYSICAL_TENANT_ID));

    // when
    final var status = provider.getMigrationStatus().get(DEFAULT_PHYSICAL_TENANT_ID);

    // then
    assertThat(status.state()).isEqualTo(MigrationState.UNKNOWN);
    assertThat(status.detail()).contains("version not yet known");
  }

  @Test
  void shouldReportUnknownWhenTopologyIsNotYetKnown() {
    // given - an unstubbed mock's getTopology() already returns null, no explicit stub needed
    final var client = mock(BrokerClient.class);
    final var topologyManager = mock(BrokerTopologyManager.class);
    when(client.getTopologyManager()).thenReturn(topologyManager);
    final var provider =
        new ClusterBrokerVersionMigrationStatusProvider(
            client, singleTenant(DEFAULT_PHYSICAL_TENANT_ID));

    // when
    final var status = provider.getMigrationStatus().get(DEFAULT_PHYSICAL_TENANT_ID);

    // then
    assertThat(status.state()).isEqualTo(MigrationState.UNKNOWN);
    assertThat(status.detail()).contains("topology not yet known");
  }

  @Test
  void shouldReportUnknownWhenNoBrokersAreKnownYet() {
    // given
    final var client = setupBrokerClient(List.of(), Map.of());
    final var provider =
        new ClusterBrokerVersionMigrationStatusProvider(
            client, singleTenant(DEFAULT_PHYSICAL_TENANT_ID));

    // when
    final var status = provider.getMigrationStatus().get(DEFAULT_PHYSICAL_TENANT_ID);

    // then
    assertThat(status.state()).isEqualTo(MigrationState.UNKNOWN);
    assertThat(status.detail()).contains("no brokers known");
  }

  @Test
  void shouldReportTheSameStatusUnderEveryKnownPhysicalTenant() {
    // given - broker membership/version is cluster-wide, not scoped to any one physical tenant
    final var previousVersion = oneMinorBehind(VersionUtil.getVersion());
    final var client = setupBrokerClient(List.of(BROKER_1), Map.of(BROKER_1, previousVersion));
    final var provider =
        new ClusterBrokerVersionMigrationStatusProvider(
            client, multiTenant("tenant-a", "tenant-b"));

    // when
    final var statuses = provider.getMigrationStatus();

    // then
    assertThat(statuses).containsOnlyKeys("tenant-a", "tenant-b");
    assertThat(statuses.get("tenant-a")).isEqualTo(statuses.get("tenant-b"));
    assertThat(statuses.get("tenant-a").state()).isEqualTo(MigrationState.MIGRATION_IN_PROGRESS);
  }

  /**
   * A version exactly one minor behind {@code version} -- {@code VersionUtil
   * .getPreviousSemanticVersion()} is a separately-configured backwards-compatibility baseline that
   * only coincidentally stays one minor behind {@code VersionUtil.getVersion()}, so relying on it
   * here would make this test depend on the two staying in lockstep.
   */
  private static String oneMinorBehind(final String version) {
    final var semanticVersion = SemanticVersion.parse(version).orElseThrow();
    return semanticVersion.major() + "." + (semanticVersion.minor() - 1) + ".0";
  }

  private static String withPatchOffset(final String version, final int offset) {
    final var semanticVersion = SemanticVersion.parse(version).orElseThrow();
    return semanticVersion.major()
        + "."
        + semanticVersion.minor()
        + "."
        + (semanticVersion.patch() + offset);
  }

  private static PhysicalTenantIds singleTenant(final String physicalTenantId) {
    return () -> Set.of(physicalTenantId);
  }

  private static PhysicalTenantIds multiTenant(final String... physicalTenantIds) {
    return () -> Set.of(physicalTenantIds);
  }

  private static BrokerClient setupBrokerClient(
      final List<BrokerMemberId> brokers, final Map<BrokerMemberId, String> versionsByBroker) {
    final var topology = mock(BrokerClusterState.class);
    when(topology.getBrokers()).thenReturn(brokers);
    brokers.forEach(
        brokerId ->
            when(topology.getBrokerVersion(brokerId)).thenReturn(versionsByBroker.get(brokerId)));

    final var client = mock(BrokerClient.class);
    final var topologyManager = mock(BrokerTopologyManager.class);
    when(topologyManager.getTopology()).thenReturn(topology);
    when(client.getTopologyManager()).thenReturn(topologyManager);
    return client;
  }
}
