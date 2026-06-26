/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring;

import static io.camunda.zeebe.protocol.Protocol.DEFAULT_PARTITION_GROUP_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.test.util.logging.RecordingAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class BrokerHealthCheckServiceTest {

  private static final String SECOND_PHYSICAL_TENANT = "tenant-2";

  private final MemberId member = MemberId.from("member-1");

  @RegisterExtension
  private final ControlledActorSchedulerExtension scheduler =
      new ControlledActorSchedulerExtension();

  @Test
  public void shouldNotBeReadyHealthyOrStartedBeforePartitionManagerIsRegistered() {
    // given
    final var healthCheckService = newHealthCheckService(DEFAULT_PARTITION_GROUP_NAME);

    // when

    // ... no partition manager is registered

    // ... and we ask about the health status
    final var healthyActual = healthCheckService.isBrokerHealthy();
    final var startedActual = healthCheckService.isBrokerStarted();
    final var readyActual = healthCheckService.isBrokerReady();

    // then
    assertThat(healthyActual).isFalse();
    assertThat(startedActual).isFalse();
    assertThat(readyActual).isFalse();
  }

  @Test
  public void shouldThrowIllegalStateExceptionIfStatusIsUpdatedBeforePartitionsAreKnown() {
    // given
    final var healthCheckService = newHealthCheckService(DEFAULT_PARTITION_GROUP_NAME);

    // when + then
    final var partitionId = new PartitionId(DEFAULT_PARTITION_GROUP_NAME, 0);
    assertThatThrownBy(() -> healthCheckService.onBecameRaftFollower(partitionId, 0))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> healthCheckService.onBecameRaftLeader(partitionId, 0))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldNotBeReadyUntilPartitionsFromAllPhysicalTenantsAreInstalled() {
    // given a broker started with one partition in each of two physical tenants. Each physical
    // tenant registers its own partitions, mirroring how PartitionManagerImpl#start does it.
    final var healthCheckService =
        newStartedHealthCheckService(DEFAULT_PARTITION_GROUP_NAME, SECOND_PHYSICAL_TENANT);
    healthCheckService.registerBootstrapPartitions(
        DEFAULT_PARTITION_GROUP_NAME, List.of(partition(DEFAULT_PARTITION_GROUP_NAME, 1)));
    healthCheckService.registerBootstrapPartitions(
        SECOND_PHYSICAL_TENANT, List.of(partition(SECOND_PHYSICAL_TENANT, 1)));
    scheduler.workUntilDone();

    // when only the second tenant's partition is installed
    healthCheckService.onBecameRaftLeader(new PartitionId(SECOND_PHYSICAL_TENANT, 1), 1);
    scheduler.workUntilDone();

    // then the broker is not ready: the default tenant's partition is still missing
    assertThat(healthCheckService.isBrokerReady()).isFalse();

    // when the default tenant's partition is installed too
    healthCheckService.onBecameRaftFollower(new PartitionId(DEFAULT_PARTITION_GROUP_NAME, 1), 1);
    scheduler.workUntilDone();

    // then the broker is ready, because every physical tenant's partition is installed
    assertThat(healthCheckService.isBrokerReady()).isTrue();
  }

  @Test
  public void shouldStayUnreadyWhenAPhysicalTenantRegistersAfterAnotherFinishedInstalling() {
    // given a broker where the default tenant registers and fully installs its partition before a
    // second physical tenant comes up. This is the ordering that a naive "all installed" latch gets
    // wrong: it would freeze on the first completion.
    final var healthCheckService =
        newStartedHealthCheckService(DEFAULT_PARTITION_GROUP_NAME, SECOND_PHYSICAL_TENANT);
    healthCheckService.registerBootstrapPartitions(
        DEFAULT_PARTITION_GROUP_NAME, List.of(partition(DEFAULT_PARTITION_GROUP_NAME, 1)));
    scheduler.workUntilDone();
    healthCheckService.onBecameRaftLeader(new PartitionId(DEFAULT_PARTITION_GROUP_NAME, 1), 1);
    scheduler.workUntilDone();

    // when a second physical tenant registers its not-yet-installed partition
    healthCheckService.registerBootstrapPartitions(
        SECOND_PHYSICAL_TENANT, List.of(partition(SECOND_PHYSICAL_TENANT, 1)));
    scheduler.workUntilDone();

    // then the broker is no longer ready: it must wait for the new tenant's partition
    assertThat(healthCheckService.isBrokerReady()).isFalse();

    // when the second tenant's partition is installed
    healthCheckService.onBecameRaftLeader(new PartitionId(SECOND_PHYSICAL_TENANT, 1), 1);
    scheduler.workUntilDone();

    // then the broker is ready again
    assertThat(healthCheckService.isBrokerReady()).isTrue();
  }

  @Test
  public void shouldNotBeReadyWhileAnExpectedPhysicalTenantNeverRegistered() {
    // given a broker configured with two physical tenants, but only the default tenant comes up and
    // installs its partition; the second tenant never registers (e.g. its startup stalled).
    final var healthCheckService =
        newStartedHealthCheckService(DEFAULT_PARTITION_GROUP_NAME, SECOND_PHYSICAL_TENANT);
    healthCheckService.registerBootstrapPartitions(
        DEFAULT_PARTITION_GROUP_NAME, List.of(partition(DEFAULT_PARTITION_GROUP_NAME, 1)));
    healthCheckService.onBecameRaftLeader(new PartitionId(DEFAULT_PARTITION_GROUP_NAME, 1), 1);
    scheduler.workUntilDone();

    // when + then the broker must not report ready: a configured tenant is entirely unaccounted
    // for, so an "all known partitions installed" check alone would falsely pass.
    assertThat(healthCheckService.isBrokerReady()).isFalse();

    // when the missing tenant finally registers and installs its partition
    healthCheckService.registerBootstrapPartitions(
        SECOND_PHYSICAL_TENANT, List.of(partition(SECOND_PHYSICAL_TENANT, 1)));
    healthCheckService.onBecameRaftLeader(new PartitionId(SECOND_PHYSICAL_TENANT, 1), 1);
    scheduler.workUntilDone();

    // then the broker becomes ready
    assertThat(healthCheckService.isBrokerReady()).isTrue();
  }

  @Test
  public void shouldBeReadyWhenAnExpectedPhysicalTenantHasNoLocalPartitions() {
    // given a broker with two physical tenants where the second tenant has no partitions on this
    // node, so it registers an empty set
    final var healthCheckService =
        newStartedHealthCheckService(DEFAULT_PARTITION_GROUP_NAME, SECOND_PHYSICAL_TENANT);
    healthCheckService.registerBootstrapPartitions(
        DEFAULT_PARTITION_GROUP_NAME, List.of(partition(DEFAULT_PARTITION_GROUP_NAME, 1)));
    healthCheckService.registerBootstrapPartitions(SECOND_PHYSICAL_TENANT, List.of());
    scheduler.workUntilDone();

    // when the default tenant's only partition is installed
    healthCheckService.onBecameRaftLeader(new PartitionId(DEFAULT_PARTITION_GROUP_NAME, 1), 1);
    scheduler.workUntilDone();

    // then the broker is ready: the second tenant started and simply had nothing to install
    assertThat(healthCheckService.isBrokerReady()).isTrue();
  }

  @Test
  public void shouldNotLogBrokerReadyWhileAnExpectedPhysicalTenantNeverRegistered() {
    // given a broker with two physical tenants where only the default tenant registers and fully
    // installs its partition; "all known partitions installed" is true but the broker is not ready
    final var recorder = new RecordingAppender();
    final var logger = (Logger) LogManager.getLogger("io.camunda.zeebe.broker.system");
    recorder.start();
    logger.addAppender(recorder);
    try {
      final var healthCheckService =
          newStartedHealthCheckService(DEFAULT_PARTITION_GROUP_NAME, SECOND_PHYSICAL_TENANT);
      healthCheckService.registerBootstrapPartitions(
          DEFAULT_PARTITION_GROUP_NAME, List.of(partition(DEFAULT_PARTITION_GROUP_NAME, 1)));
      scheduler.workUntilDone();

      // when the default tenant's only partition is installed
      healthCheckService.onBecameRaftLeader(new PartitionId(DEFAULT_PARTITION_GROUP_NAME, 1), 1);
      scheduler.workUntilDone();

      // then the broker must not announce readiness: a configured tenant has not registered yet
      assertThat(healthCheckService.isBrokerReady()).isFalse();
      assertThat(recorder.getAppendedEvents())
          .noneSatisfy(
              event -> assertThat(event.getMessage().getFormattedMessage()).contains("ready"));
    } finally {
      logger.removeAppender(recorder);
      recorder.stop();
    }
  }

  @Test
  public void shouldLogBrokerReadyOnceWhenEveryPhysicalTenantIsInstalled() {
    // given a broker with two physical tenants
    final var recorder = new RecordingAppender();
    final var logger = (Logger) LogManager.getLogger("io.camunda.zeebe.broker.system");
    recorder.start();
    logger.addAppender(recorder);
    try {
      final var healthCheckService =
          newStartedHealthCheckService(DEFAULT_PARTITION_GROUP_NAME, SECOND_PHYSICAL_TENANT);
      healthCheckService.registerBootstrapPartitions(
          DEFAULT_PARTITION_GROUP_NAME, List.of(partition(DEFAULT_PARTITION_GROUP_NAME, 1)));
      healthCheckService.registerBootstrapPartitions(
          SECOND_PHYSICAL_TENANT, List.of(partition(SECOND_PHYSICAL_TENANT, 1)));
      scheduler.workUntilDone();

      // when every tenant's partition is installed
      healthCheckService.onBecameRaftLeader(new PartitionId(DEFAULT_PARTITION_GROUP_NAME, 1), 1);
      healthCheckService.onBecameRaftLeader(new PartitionId(SECOND_PHYSICAL_TENANT, 1), 1);
      scheduler.workUntilDone();

      // then the broker logs that it is ready exactly once
      assertThat(healthCheckService.isBrokerReady()).isTrue();
      assertThat(recorder.getAppendedEvents())
          .filteredOn(event -> event.getMessage().getFormattedMessage().contains("Broker is ready"))
          .hasSize(1);
    } finally {
      logger.removeAppender(recorder);
      recorder.stop();
    }
  }

  private BrokerHealthCheckService newHealthCheckService(final String... expectedPhysicalTenants) {
    return new BrokerHealthCheckService(
        member, new HealthTreeMetrics(new SimpleMeterRegistry()), Set.of(expectedPhysicalTenants));
  }

  private BrokerHealthCheckService newStartedHealthCheckService(
      final String... expectedPhysicalTenants) {
    final var healthCheckService = newHealthCheckService(expectedPhysicalTenants);
    scheduler.submitActor(healthCheckService);
    healthCheckService.setBrokerStarted();
    scheduler.workUntilDone();
    return healthCheckService;
  }

  private static PartitionMetadata partition(final String partitionGroup, final int partitionId) {
    final var owner = MemberId.from("member-1");
    return new PartitionMetadata(
        new PartitionId(partitionGroup, partitionId), Set.of(owner), Map.of(owner, 1), 1, owner);
  }
}
