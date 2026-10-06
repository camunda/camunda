/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static io.camunda.zeebe.it.physicaltenant.PhysicalTenantProvisioningSupport.assertProcessCanBeDeployedAndStarted;
import static io.camunda.zeebe.it.physicaltenant.PhysicalTenantProvisioningSupport.restartCluster;
import static io.camunda.zeebe.qa.util.cluster.util.ZoneFixtures.ZONE_A;
import static io.camunda.zeebe.qa.util.cluster.util.ZoneFixtures.ZONE_B;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.configuration.Zone;
import io.camunda.zeebe.it.cluster.clustering.zoneaware.ZoneHelpers;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestClusterBuilder;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Verifies that a new physical tenant added to the static configuration after a zone-aware cluster
 * has already bootstrapped is provisioned additively: its partitions are placed per the cluster's
 * zone layout ({@link io.camunda.zeebe.dynamic.config.util.ZoneAwareAdditivePartitionReassigner}),
 * without moving any replica of a physical tenant that already existed.
 *
 * <p>See {@code PhysicalTenantProvisioningIT} for the equivalent single-zone scenario, including
 * coverage of the restart strategy matrix (full/rolling) - this test focuses on the zone-layout
 * dimension alone.
 */
@ZeebeIntegration
final class PhysicalTenantZoneAwareProvisioningIT {

  // zone-a: higher priority, 2 replicas per partition. zone-b: lower priority, 1 replica.
  private static final List<Zone> ZONE_CONFIGS =
      List.of(new Zone(ZONE_A, 2, 2, 100), new Zone(ZONE_B, 2, 1, 50));
  private static final int BROKERS_COUNT = 4;
  private static final int REPLICATION_FACTOR = 3;

  // the existing tenant sorts *after* the newly-provisioned one (see NEW_TENANT below): partition
  // ids are grouped and sorted by tenant id (PartitionId.compareTo), so if provisioning ever
  // regressed from true additive placement to a from-scratch recomputation over all groups sorted
  // by id, processing the new tenant first would disturb the existing tenant's placement and be
  // caught below - the opposite tenant ordering could coincidentally leave it unchanged.
  private static final String EXISTING_TENANT = "tenantz";
  private static final String NEW_TENANT = "tenanta";
  // different partition counts so a request misrouted to the wrong tenant's identically-shaped
  // partitions would still be caught by the completeness assertions below
  private static final int EXISTING_TENANT_PARTITIONS_COUNT = 2;
  private static final int NEW_TENANT_PARTITIONS_COUNT = 3;

  // declared at cluster bootstrap time: only the default tenant and the existing tenant
  private static final PhysicalTenantsITHelper TENANTS_BEFORE =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(EXISTING_TENANT, Storage.none(), EXISTING_TENANT_PARTITIONS_COUNT)
          .build();

  // the same tenants, plus the new tenant - added to the static configuration only after the
  // zone-aware cluster has already bootstrapped once, then applied on restart
  private static final PhysicalTenantsITHelper TENANTS_AFTER =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(EXISTING_TENANT, Storage.none(), EXISTING_TENANT_PARTITIONS_COUNT)
          .withTenant(NEW_TENANT, Storage.none(), NEW_TENANT_PARTITIONS_COUNT)
          .build();

  @TestZeebe
  private final TestCluster cluster =
      new TestClusterBuilder()
          .withBrokersCount(BROKERS_COUNT)
          .withReplicationFactor(REPLICATION_FACTOR)
          .withPartitionsCount(2)
          .multiZone(ZONE_CONFIGS)
          .withBrokerConfig(broker -> TENANTS_BEFORE.configure(broker.withUnauthenticatedAccess()))
          .build();

  @Test
  @Timeout(2 * 60)
  void shouldProvisionNewPhysicalTenantOnZoneAwareClusterAdditively() {
    // given - the zone-aware cluster is up and running with only the default tenant and the
    // existing tenant, whose partitions already respect the configured zone layout
    cluster.awaitCompleteTopology();
    final var actuator = ClusterActuator.of(cluster.availableGateway());

    TENANTS_BEFORE.awaitTopologyComplete(
        cluster.availableGateway(),
        EXISTING_TENANT,
        BROKERS_COUNT,
        EXISTING_TENANT_PARTITIONS_COUNT,
        REPLICATION_FACTOR);
    ZoneHelpers.assertPartitionsAssignedPerZoneLayout(
        actuator,
        EXISTING_TENANT,
        ZONE_CONFIGS,
        EXISTING_TENANT_PARTITIONS_COUNT,
        REPLICATION_FACTOR);
    final Map<Integer, Set<MemberId>> existingTenantReplicasBefore =
        ZoneHelpers.assignedMembersByPartition(actuator, EXISTING_TENANT);

    // when - the new tenant is added to every broker's static configuration and the cluster is
    // restarted
    restartCluster(cluster, TENANTS_AFTER::configure);

    // then - the new tenant is provisioned with its partitions placed per the cluster's zone
    // layout - including each partition's primary landing in the highest-priority zone - and
    // becomes usable end-to-end
    TENANTS_AFTER.awaitTopologyComplete(
        cluster.availableGateway(),
        NEW_TENANT,
        BROKERS_COUNT,
        NEW_TENANT_PARTITIONS_COUNT,
        REPLICATION_FACTOR);
    ZoneHelpers.assertPartitionsAssignedPerZoneLayout(
        actuator, NEW_TENANT, ZONE_CONFIGS, NEW_TENANT_PARTITIONS_COUNT, REPLICATION_FACTOR);

    try (final var newTenantClient =
        TENANTS_AFTER.newClientBuilder(cluster.availableGateway(), NEW_TENANT).build()) {
      assertProcessCanBeDeployedAndStarted(newTenantClient, "zone-aware-provisioning-process");
    }

    // and - the existing tenant's replicas were never touched by the new tenant's provisioning:
    // same partitions, same brokers, per partition
    assertThat(ZoneHelpers.assignedMembersByPartition(actuator, EXISTING_TENANT))
        .as("the existing tenant's replicas are unaffected by the new tenant's provisioning")
        .isEqualTo(existingTenantReplicasBefore);
  }
}
