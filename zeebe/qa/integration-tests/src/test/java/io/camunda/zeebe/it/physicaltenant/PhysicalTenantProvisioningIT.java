/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.management.cluster.ConfigurationChange.StatusEnum;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.actuator.ExportersActuator;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestClusterBuilder;
import io.camunda.zeebe.qa.util.cluster.TestHealthProbe;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import java.time.Duration;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A physical tenant added to a broker's static configuration after the cluster has already been
 * bootstrapped once must be picked up on the next restart: the coordinator's {@code
 * PhysicalTenantProvisioningInitializer} provisions a new partition group for it directly, without
 * a fresh full-cluster bootstrap. This verifies the whole path end-to-end: a brand-new physical
 * tenant's partitions become visible via the client-facing (physical-tenant-scoped) topology
 * endpoint, and a process can be deployed to and instantiated within it.
 *
 * <p>Also covers the inverse: a physical tenant later removed from every broker's static
 * configuration is disabled rather than deleted (see {@code
 * PhysicalTenantAvailabilityInitializer}), and a cluster-wide operation that targets every physical
 * tenant when none is explicitly given must not target it — otherwise the resulting change plan
 * would stall forever waiting on an unreachable share of the plan that no broker runs any more.
 */
@ZeebeIntegration
final class PhysicalTenantProvisioningIT {

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";
  private static final int BROKERS_COUNT = 3;
  private static final int TENANT_B_PARTITIONS_COUNT = 3;

  // declared at cluster bootstrap time: only the default tenant and tenantA
  private static final PhysicalTenantsITHelper TENANTS_BEFORE =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .build();

  // the same tenants, plus tenantB — added to the static configuration only after the cluster has
  // already been running once, then applied on restart
  private static final PhysicalTenantsITHelper TENANTS_AFTER =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .withTenant(TENANT_B, Storage.none())
          .build();

  @TestZeebe
  private final TestCluster cluster =
      new TestClusterBuilder()
          .withBrokersCount(BROKERS_COUNT)
          .withReplicationFactor(BROKERS_COUNT)
          .withPartitionsCount(2)
          .withBrokerConfig(broker -> TENANTS_BEFORE.configure(broker.withUnauthenticatedAccess()))
          .build();

  @ParameterizedTest
  @MethodSource("restartStrategiesWithNewTenants")
  void shouldProvisionNewPhysicalTenantAddedAfterInitialBootstrapOnRestart(
      final Consumer<TestCluster> restarter) {
    // given - the cluster is up and running with only the default tenant and tenantA
    try (final var tenantAClient =
        TENANTS_BEFORE.newClientBuilder(cluster.availableGateway(), TENANT_A).build()) {
      await("tenantA's topology is complete before the restart")
          .atMost(Duration.ofSeconds(60))
          .untilAsserted(
              () ->
                  TopologyAssert.assertThat(tenantAClient.newTopologyRequest().send().join())
                      .isComplete(BROKERS_COUNT, 2, BROKERS_COUNT));
    }

    // when - tenantB is added to every broker's static configuration and the cluster is restarted
    restarter.accept(cluster);

    // then - tenantB's own partition group is visible through the client-facing, physical-tenant-
    // scoped topology endpoint (the same endpoint used to observe any other physical tenant),
    // with the partition count configured for tenantB specifically - not cloned from another
    // tenant's partition count
    try (final var tenantBClient =
        TENANTS_AFTER.newClientBuilder(cluster.availableGateway(), TENANT_B).build()) {
      await("tenantB's newly-provisioned partitions are visible and have a leader")
          .atMost(Duration.ofSeconds(60))
          .ignoreExceptions()
          .untilAsserted(
              () ->
                  TopologyAssert.assertThat(tenantBClient.newTopologyRequest().send().join())
                      .isComplete(BROKERS_COUNT, TENANT_B_PARTITIONS_COUNT, BROKERS_COUNT));

      // and - a process can be deployed to and instantiated within the newly-provisioned tenant
      final String processId = "provisioning-process";
      final BpmnModelInstance process =
          Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();

      await("deployment to the newly-provisioned tenantB succeeds")
          .atMost(Duration.ofSeconds(30))
          .ignoreExceptions()
          .untilAsserted(
              () ->
                  assertThat(
                          tenantBClient
                              .newDeployResourceCommand()
                              .addProcessModel(process, processId + ".bpmn")
                              .send()
                              .join()
                              .getProcesses())
                      .isNotEmpty());

      final long processInstanceKey =
          tenantBClient
              .newCreateInstanceCommand()
              .bpmnProcessId(processId)
              .latestVersion()
              .send()
              .join()
              .getProcessInstanceKey();
      assertThat(processInstanceKey).isPositive();
    }

    // and - tenantA, which already existed before the restart, is unaffected
    try (final var tenantAClient =
        TENANTS_AFTER.newClientBuilder(cluster.availableGateway(), TENANT_A).build()) {
      TopologyAssert.assertThat(tenantAClient.newTopologyRequest().send().join())
          .isComplete(BROKERS_COUNT, 2, BROKERS_COUNT);
    }
  }

  @ParameterizedTest
  @MethodSource("restartStrategiesWithRemovingTenants")
  void shouldMarkPhysicalTenantDisabledOnActuatorWhenRemovedFromConfigAfterRestart(
      final Consumer<TestCluster> restarter) {
    // given - tenantB is provisioned by adding it to every broker's configuration and restarting
    restartClusterWithTenantB();

    try (final var tenantBClient =
        TENANTS_AFTER.newClientBuilder(cluster.availableGateway(), TENANT_B).build()) {
      await("tenantB's topology is complete after provisioning")
          .atMost(Duration.ofSeconds(60))
          .ignoreExceptions()
          .untilAsserted(
              () ->
                  TopologyAssert.assertThat(tenantBClient.newTopologyRequest().send().join())
                      .isComplete(BROKERS_COUNT, TENANT_B_PARTITIONS_COUNT, BROKERS_COUNT));
    }

    // when - tenantB is removed from every broker's local static configuration and the cluster is
    // restarted again

    restarter.accept(cluster);

    // then - the physical-tenant-scoped actuator reports tenantB as disabled, with no routing
    // state or partitions: it is retained in the configuration, just not running anywhere
    final var actuator = ClusterActuator.of(cluster.availableGateway());
    await("tenantB is reported as disabled via the actuator")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var topology = actuator.getTopology(TENANT_B);
              assertThat(topology.getPhysicalTenants())
                  .singleElement()
                  .satisfies(
                      info -> {
                        assertThat(info.getId()).isEqualTo(TENANT_B);
                        assertThat(info.getDisabled()).isTrue();
                        assertThat(info.getRouting()).isNull();
                      });
              assertThat(topology.getBrokers())
                  .allSatisfy(broker -> assertThat(broker.getPartitions()).isEmpty());
            });

    // and - tenantA, which is unaffected by tenantB's removal, is never explicitly marked enabled
    // but still resolves normally with its routing state intact
    final var tenantAInfo = actuator.getTopology(TENANT_A).getPhysicalTenants();
    assertThat(tenantAInfo)
        .singleElement()
        .satisfies(
            info -> {
              assertThat(info.getId()).isEqualTo(TENANT_A);
              assertThat(info.getDisabled()).isNull();
              assertThat(info.getRouting()).isNotNull();
            });
  }

  private void restartClusterWithTenantB() {
    restartCluster(
        cluster,
        broker -> {
          TENANTS_AFTER.configure(broker);
          broker.withPtConfig(
              TENANT_B,
              camunda -> camunda.getCluster().setPartitionCount(TENANT_B_PARTITIONS_COUNT));
        });
  }

  @Test
  void shouldCompleteClusterWideExporterDisableAfterTenantIsRemovedFromConfig() {
    // given - tenantB is provisioned by adding it to every broker's configuration and restarting
    restartClusterWithTenantB();
    try (final var tenantBClient =
        TENANTS_AFTER.newClientBuilder(cluster.availableGateway(), TENANT_B).build()) {
      await("tenantB's topology is complete after provisioning")
          .atMost(Duration.ofSeconds(60))
          .ignoreExceptions()
          .untilAsserted(
              () ->
                  TopologyAssert.assertThat(tenantBClient.newTopologyRequest().send().join())
                      .isComplete(BROKERS_COUNT, TENANT_B_PARTITIONS_COUNT, BROKERS_COUNT));
    }

    // when - tenantB is removed from every broker's local static configuration and the cluster is
    // restarted again, disabling it: its partition assignment is retained, but no broker runs its
    // partitions any more
    cluster.shutdown();
    cluster.brokers().values().forEach(broker -> broker.removePtConfig(TENANT_B));
    cluster.start().awaitCompleteTopology();

    // then - a cluster-wide exporter-disable request (no physical tenant explicitly targeted)
    // completes instead of stalling forever: if it were still generated for tenantB's retained
    // (but now unreachable) partition assignment, the change plan would never observe an ack for
    // that share and would stay IN_PROGRESS
    final var actuator = ClusterActuator.of(cluster.availableGateway());
    final var response =
        ExportersActuator.of(cluster.availableGateway())
            .disableExporter(TestStandaloneBroker.RECORDING_EXPORTER_ID);
    await("the cluster-wide exporter-disable change completes")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(actuator.getChange(response.getChangeId()).getStatus())
                    .isEqualTo(StatusEnum.COMPLETED));
  }

  public static Stream<Arguments> restartStrategiesWithNewTenants() {
    return restartStrategies(
        broker -> {
          TENANTS_AFTER.configure(broker);
          broker.withPtConfig(
              TENANT_B,
              camunda -> camunda.getCluster().setPartitionCount(TENANT_B_PARTITIONS_COUNT));
        });
  }

  public static Stream<Arguments> restartStrategiesWithRemovingTenants() {
    return restartStrategies(broker -> broker.removePtConfig(TENANT_B));
  }

  private static Stream<Arguments> restartStrategies(
      final Consumer<TestStandaloneBroker> brokerConfigurator) {
    return Stream.of(
        Arguments.of(
            Named.of(
                "Full restart",
                (Consumer<TestCluster>) cluster -> restartCluster(cluster, brokerConfigurator))),
        Arguments.of(
            Named.of(
                "Rolling restart (coordinator first)",
                (Consumer<TestCluster>)
                    cluster -> rollingRestart(cluster, true, brokerConfigurator))),
        Arguments.of(
            Named.of(
                "Rolling restart (coordinator last)",
                (Consumer<TestCluster>)
                    cluster -> rollingRestart(cluster, false, brokerConfigurator))));
  }

  private static void restartCluster(
      final TestCluster cluster, final Consumer<TestStandaloneBroker> brokerConfigurator) {
    cluster.shutdown();
    cluster.brokers().values().forEach(brokerConfigurator);
    cluster.start().awaitCompleteTopology();
  }

  private static void rollingRestart(
      final TestCluster cluster,
      final boolean ascending,
      final Consumer<TestStandaloneBroker> brokerConfigurator) {
    cluster.brokers().values().stream()
        .sorted(
            ascending
                ? Comparator.comparing(b -> b.nodeId(), MemberId.ID_COMPARATOR)
                : Comparator.comparing(b -> b.nodeId(), MemberId.ID_COMPARATOR.reversed()))
        .forEach(
            broker -> {
              broker.stop();
              brokerConfigurator.accept(broker);
              broker.start();
              cluster.await(TestHealthProbe.READY, Duration.ofSeconds(30));
            });
    cluster.start().awaitCompleteTopology();
  }
}
