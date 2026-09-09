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
import static org.assertj.core.api.Assertions.assertThatCode;

import feign.FeignException;
import io.atomix.cluster.MemberId;
import io.camunda.client.CamundaClient;
import io.camunda.configuration.Zone;
import io.camunda.zeebe.management.cluster.AddZoneRequest;
import io.camunda.zeebe.management.cluster.BrokerId;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.actuator.RebalanceActuator;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * Verifies the dual-region failover/failback procedure - force-remove a dead zone, then add it back
 * once replacement capacity exists - for <em>every</em> physical tenant, not only the default one.
 *
 * <p>This is a genuinely different scenario from a single-zone broker loss. With only two zones and
 * replication factor {@value #REPLICATION_FACTOR}, a whole zone going down leaves at most one
 * surviving replica - never a Raft majority of two - so the partition group cannot elect a leader
 * on its own; nothing to re-elect to. Recovering it requires an explicit administrative action that
 * reconfigures the raft group to no longer require the dead zone's vote: {@code
 * ClusterActuator#forceRemoveZone}, mirrored end-to-end here from {@code
 * ZoneAwareClusterEndpointIT#shouldRecoverZoneAwareClusterAfterForceRemoveZone}, which only ever
 * exercises the default tenant.
 *
 * <p>{@code ForceRemoveZoneTransformer}'s own javadoc names the exact gap this test closes:
 * "Evicting [a zone's brokers] from the default group alone left the other tenants' partitions on
 * the failed zone's brokers... so the first half of the zone failover procedure could not run on a
 * cluster with more than one tenant." {@code PhysicalTenantForcedRemovalTest} (in {@code
 * zeebe/dynamic-config}) already proves that fix at the config-transformer level, simulated; this
 * proves it through the real REST actuator, against a running cluster, for every tenant.
 *
 * <p>Cluster shape mirrors the reference test exactly: zone A (priority 100, the initial preferred
 * leader zone) has 2 brokers holding 1 replica between them (round-robining across partitions, so
 * no single broker holds every partition's zone-A replica - both must be stopped to take the zone
 * fully down); zone B (priority 10) has 1 broker holding 1 replica. RF {@value
 * #REPLICATION_FACTOR}.
 *
 * <p>Recovery does not restore the original 2-broker zone A: {@code addZone} only needs enough
 * brokers to satisfy the zone's configured {@code numberOfReplicas} (1), so a single fresh broker
 * is sufficient - matching the reference test and real recovery runbooks, which provision
 * replacement capacity rather than insisting on a 1-for-1 rebuild. The post-recovery zone layout
 * used for assertions below therefore has zone A at 1 broker, not the original 2.
 */
@ZeebeIntegration
final class MultiPhysicalTenantZoneFailureFailbackIT {

  private static final int BROKERS_COUNT = 3;
  private static final int PARTITIONS_COUNT = 3;
  private static final int REPLICATION_FACTOR = 2;

  private static final String TENANT_A = "tenanta";

  private static final List<Zone> INITIAL_ZONE_CONFIGS =
      List.of(new Zone(ZONE_A, 2, 1, 100), new Zone(ZONE_B, 1, 1, 10));

  /**
   * The layout after recovery: zone A comes back with the 1 broker {@code addZone} was actually
   * given, not the 2 it started with (see class javadoc). Priorities and per-zone replica counts
   * are unchanged, since the {@code addZone} request in this test asks for the same ones.
   */
  private static final List<Zone> RECOVERED_ZONE_CONFIGS =
      List.of(new Zone(ZONE_A, 1, 1, 100), new Zone(ZONE_B, 1, 1, 10));

  /**
   * The cluster's own membership once recovered: the zone-B survivor plus the single zone-A
   * replacement. Not {@code cluster.brokers().size()} - that map is this test's own bookkeeping of
   * every {@link io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker} it created, so it still
   * lists the two force-removed zone-A brokers (stopped, never forgotten) and knows nothing of the
   * replacement, which is started outside it via {@link ZoneHelpers#startBrokerInZone}.
   */
  private static final int RECOVERED_BROKERS_COUNT = 2;

  private static final List<String> TENANTS_LIST =
      List.of(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, TENANT_A);

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .build();

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .withName("multi-pt-zone-failure-failback")
          .withBrokersCount(BROKERS_COUNT)
          .withEmbeddedGateway(true)
          .withPartitionsCount(PARTITIONS_COUNT)
          .withReplicationFactor(REPLICATION_FACTOR)
          .multiZone(INITIAL_ZONE_CONFIGS)
          .withBrokerConfig(broker -> TENANTS.configure(broker.withUnauthenticatedAccess()))
          .build();

  @Test
  void shouldRecoverEveryPhysicalTenantAfterForceRemovingAndReAddingAZone() {
    // given - every tenant's partitions are assigned per the zone layout, with a leader in the
    // preferred zone A. The actuator is bound to zone B's one broker, since it is the only one
    // that stays alive for the whole test.
    final var zoneBMember = ZoneHelpers.membersOfZone(cluster, ZONE_B).iterator().next();
    final var actuator = ClusterActuator.of(cluster.brokers().get(zoneBMember));
    TENANTS_LIST.forEach(
        tenant ->
            ZoneHelpers.assertPartitionsAssignedPerZoneLayout(
                actuator, tenant, INITIAL_ZONE_CONFIGS, PARTITIONS_COUNT, REPLICATION_FACTOR));
    TENANTS_LIST.forEach(
        tenant -> ZoneHelpers.assertLeadersInZone(cluster, tenant, ZONE_A, PARTITIONS_COUNT));

    // when - zone A is killed: both of its brokers are stopped. With RF 2 across only 2 zones,
    // this leaves no majority for any tenant's partitions - not a re-electable failure, since
    // there is nothing left to elect among. This is why the procedure below exists.
    final var zoneAMembers = ZoneHelpers.membersOfZone(cluster, ZONE_A);
    zoneAMembers.forEach(member -> cluster.brokers().get(member).stop());

    // when - zone A is force-removed: its brokers are evicted from every physical tenant's
    // partition group, not only the default one - the exact fix ForceRemoveZoneTransformer's
    // javadoc describes and PhysicalTenantForcedRemovalTest proves in isolation - and dropped
    // from the distribution config, restoring a single-member majority (zone B) for each tenant.
    final var forceRemoveResponse = actuator.forceRemoveZone(ZONE_A, false);
    Awaitility.await("zone A force-removal is applied")
        .ignoreException(FeignException.class)
        .untilAsserted(
            () ->
                ClusterActuatorAssert.assertThat(actuator).hasAppliedChanges(forceRemoveResponse));
    zoneAMembers.forEach(
        member ->
            ClusterActuatorAssert.assertThat(actuator)
                .doesNotHaveBroker(new BrokerId.String(member.toString())));

    // then - every tenant re-elects a leader on the surviving zone B, now a majority on its own,
    // and stays available for both reads and writes
    TENANTS_LIST.forEach(
        tenant -> ZoneHelpers.assertLeadersOutsideZone(cluster, tenant, ZONE_A, PARTITIONS_COUNT));
    TENANTS_LIST.forEach(this::assertTenantIsWritable);

    // when - zone A is provisioned again with a fresh broker (its old ones were force-evicted, not
    // merely stopped, so their data is orphaned - a replacement, not a restart) and added back
    try (final var replacementBroker =
        ZoneHelpers.startBrokerInZone(
            cluster,
            ZONE_A,
            0,
            BROKERS_COUNT,
            INITIAL_ZONE_CONFIGS,
            broker -> TENANTS.configure(broker))) {
      final var replacementBrokerId = new BrokerId.String(MemberId.from(ZONE_A, 0).toString());
      final var recoveredZoneA =
          RECOVERED_ZONE_CONFIGS.stream()
              .filter(zone -> ZONE_A.equals(zone.name()))
              .findFirst()
              .orElseThrow();
      final var addZoneRequest =
          new AddZoneRequest()
              .numberOfReplicas(recoveredZoneA.numberOfReplicas())
              .priority(recoveredZoneA.priority())
              .brokers(List.of(replacementBrokerId));
      final var addZoneResponse = actuator.addZone(ZONE_A, addZoneRequest, false);
      Awaitility.await("zone A re-addition is applied")
          .untilAsserted(
              () -> ClusterActuatorAssert.assertThat(actuator).hasAppliedChanges(addZoneResponse));

      // then - every tenant's topology is complete and healthy again, with the replacement zone-A
      // broker hosting a replica
      TENANTS_LIST.forEach(this::awaitTenantTopologyComplete);
      TENANTS_LIST.forEach(
          tenant ->
              ZoneHelpers.assertPartitionsAssignedPerZoneLayout(
                  actuator, tenant, RECOVERED_ZONE_CONFIGS, PARTITIONS_COUNT, REPLICATION_FACTOR));

      // and - re-adding zone A does not by itself move leadership back: addZone only joins the
      // replacement as a replica, it does not step down the zone-B leader that has been serving
      // every tenant's partitions since the force-removal. Moving leadership back to the
      // now-again-preferred zone A requires the same explicit rebalance as
      // ZoneAwareClusterEndpointIT#shouldSwapZonePrioritiesAndMoveLeaders.
      //
      // startBrokerInZone deliberately starts the replacement outside the cluster's own
      // bookkeeping, so the surviving zone-B broker (from cluster.brokers()) and the replacement
      // are combined explicitly here for the leader query to see both.
      final var liveBrokers = new HashMap<>(cluster.brokers());
      liveBrokers.put(MemberId.from(ZONE_A, 0), replacementBroker);
      final var rebalanceActuator = RebalanceActuator.of(cluster.availableGateway());
      TENANTS_LIST.forEach(
          tenant ->
              ZoneHelpers.awaitLeadersInZoneAfterRebalance(
                  liveBrokers, rebalanceActuator, tenant, ZONE_A, PARTITIONS_COUNT));
    }
  }

  private void assertTenantIsWritable(final String physicalTenantId) {
    try (final var client =
        TENANTS.newClientBuilder(cluster.availableGateway(), physicalTenantId).build()) {
      assertThatCode(() -> publishMessage(client))
          .as(
              "physical tenant '%s' remains writable after zone '%s' is force-removed",
              physicalTenantId, ZONE_A)
          .doesNotThrowAnyException();
    }
  }

  // reuses the per-physical-tenant topology endpoint, the same public contract clients rely on,
  // matching PhysicalTenantRejoinIT's approach to asserting recovery
  private void awaitTenantTopologyComplete(final String physicalTenantId) {
    try (final var client =
        TENANTS.newClientBuilder(cluster.availableGateway(), physicalTenantId).build()) {
      Awaitility.await(
              "physical tenant '%s' has a complete, healthy topology after zone '%s' is added back"
                  .formatted(physicalTenantId, ZONE_A))
          .atMost(Duration.ofSeconds(90))
          .ignoreExceptions()
          .untilAsserted(
              () ->
                  TopologyAssert.assertThat(client.newTopologyRequest().send().join())
                      .isComplete(RECOVERED_BROKERS_COUNT, PARTITIONS_COUNT, REPLICATION_FACTOR));
    }
  }

  private static void publishMessage(final CamundaClient client) {
    client
        .newPublishMessageCommand()
        .messageName("zone-failure-msg")
        .correlationKey(UUID.randomUUID().toString())
        .send()
        .join();
  }
}
