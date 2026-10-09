/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering.zoneaware;

import static io.camunda.zeebe.qa.util.cluster.util.ZoneFixtures.ZONE_A;
import static io.camunda.zeebe.qa.util.cluster.util.ZoneFixtures.ZONE_B;

import io.camunda.configuration.Zone;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Verifies that a zone-aware cluster honours its zone layout for <em>every</em> physical tenant,
 * not only the default one.
 *
 * <p>A zone layout is cluster-wide: {@code cluster.partitioning} and {@code cluster.zone} are both
 * on the physical-tenant override deny-list ({@code PhysicalTenantOverridePolicyValidation}), and
 * {@code StaticConfigurationGenerator} builds a single {@code PartitionDistributor} from the root
 * broker configuration. What <em>is</em> per-tenant is the partition group that distributor is
 * applied to, so the property under test is that each tenant's own partition group is laid out
 * across the zones the cluster declares.
 *
 * <p>The tenants deliberately have different partition counts. {@code
 * StaticConfigurationGenerator#getSortedPartitionIds} flattens every tenant's partitions into one
 * list ordered by {@code (physicalTenantId, partitionNumber)}, and {@code
 * ZoneAwarePartitionDistributor} offsets each partition's placement by its index in that flat list,
 * so each tenant is placed at a different offset within it. The assertions below are layout
 * invariants rather than exact member sets, so they hold at any offset; the differing counts
 * exercise them at several offsets instead of at the single symmetric one equal counts would
 * produce. The offsets themselves are deliberately not pinned — they are the distributor's internal
 * round-robin, not the zone layout under test.
 *
 * <p>Cluster shape: {@value #BROKERS_COUNT} brokers over two zones, RF {@value
 * #REPLICATION_FACTOR}. Zone A has 2 brokers and takes 1 replica at priority 1000; zone B has 1
 * broker and takes 1 replica at priority 500. Since a zone holds fewer replicas than it has
 * brokers, which broker inside zone A holds a given partition is a real choice rather than a
 * consequence of RF covering the whole cluster.
 */
@ZeebeIntegration
final class MultiPhysicalTenantZoneAwarePartitionDistributionIT {

  private static final int BROKERS_COUNT = 3;
  private static final int REPLICATION_FACTOR = 2;

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";
  private static final int DEFAULT_TENANT_PARTITIONS = 2;
  private static final int TENANT_A_PARTITIONS = 3;
  private static final int TENANT_B_PARTITIONS = 1;

  private static final List<Zone> ZONE_CONFIGS =
      List.of(new Zone(ZONE_A, 2, 1, 1000), new Zone(ZONE_B, 1, 1, 500));

  // the default tenant's partition count stays the cluster's own, so the extension's readiness
  // wait - which only sees the gateway's unscoped topology - still matches
  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none(), TENANT_A_PARTITIONS)
          .withTenant(TENANT_B, Storage.none(), TENANT_B_PARTITIONS)
          .build();

  @TestZeebe(purgeAfterEach = false)
  private static final TestCluster CLUSTER =
      TestCluster.builder()
          .withName("multi-pt-zone-aware-distribution")
          .withBrokersCount(BROKERS_COUNT)
          .withEmbeddedGateway(true)
          .withPartitionsCount(DEFAULT_TENANT_PARTITIONS)
          .withReplicationFactor(REPLICATION_FACTOR)
          .multiZone(ZONE_CONFIGS)
          // replaces the builder's default broker config, which is where unauthenticated access
          // would otherwise be enabled
          .withBrokerConfig(broker -> TENANTS.configure(broker.withUnauthenticatedAccess()))
          .build();

  @ParameterizedTest(name = "physical tenant ''{0}''")
  @MethodSource("physicalTenants")
  void shouldAssignPartitionsPerZoneLayout(
      final String physicalTenantId, final int partitionCount) {
    ZoneHelpers.assertPartitionsAssignedPerZoneLayout(
        ClusterActuator.of(CLUSTER.availableGateway()),
        physicalTenantId,
        ZONE_CONFIGS,
        partitionCount,
        REPLICATION_FACTOR);
  }

  @ParameterizedTest(name = "physical tenant ''{0}''")
  @MethodSource("physicalTenants")
  void shouldElectLeadersInHighestPriorityZone(
      final String physicalTenantId, final int partitionCount) {
    // zoneA(priority 1000) > zoneB(priority 500)
    ZoneHelpers.assertLeadersInZone(CLUSTER, physicalTenantId, ZONE_A, partitionCount);
  }

  static Stream<Arguments> physicalTenants() {
    return Stream.of(
        Arguments.of(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, DEFAULT_TENANT_PARTITIONS),
        Arguments.of(TENANT_A, TENANT_A_PARTITIONS),
        Arguments.of(TENANT_B, TENANT_B_PARTITIONS));
  }
}
