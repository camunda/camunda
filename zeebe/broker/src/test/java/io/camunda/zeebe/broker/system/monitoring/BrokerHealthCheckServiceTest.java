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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    final var healthCheckService = newHealthCheckService();

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
    final var healthCheckService = newHealthCheckService();

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
    final var healthCheckService = newStartedHealthCheckService();
    healthCheckService.registerBootstrapPartitions(
        List.of(partition(DEFAULT_PARTITION_GROUP_NAME, 1)));
    healthCheckService.registerBootstrapPartitions(List.of(partition(SECOND_PHYSICAL_TENANT, 1)));
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
    final var healthCheckService = newStartedHealthCheckService();
    healthCheckService.registerBootstrapPartitions(
        List.of(partition(DEFAULT_PARTITION_GROUP_NAME, 1)));
    scheduler.workUntilDone();
    healthCheckService.onBecameRaftLeader(new PartitionId(DEFAULT_PARTITION_GROUP_NAME, 1), 1);
    scheduler.workUntilDone();

    // when a second physical tenant registers its not-yet-installed partition
    healthCheckService.registerBootstrapPartitions(List.of(partition(SECOND_PHYSICAL_TENANT, 1)));
    scheduler.workUntilDone();

    // then the broker is no longer ready: it must wait for the new tenant's partition
    assertThat(healthCheckService.isBrokerReady()).isFalse();

    // when the second tenant's partition is installed
    healthCheckService.onBecameRaftLeader(new PartitionId(SECOND_PHYSICAL_TENANT, 1), 1);
    scheduler.workUntilDone();

    // then the broker is ready again
    assertThat(healthCheckService.isBrokerReady()).isTrue();
  }

  private BrokerHealthCheckService newHealthCheckService() {
    return new BrokerHealthCheckService(member, new HealthTreeMetrics(new SimpleMeterRegistry()));
  }

  private BrokerHealthCheckService newStartedHealthCheckService() {
    final var healthCheckService = newHealthCheckService();
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
