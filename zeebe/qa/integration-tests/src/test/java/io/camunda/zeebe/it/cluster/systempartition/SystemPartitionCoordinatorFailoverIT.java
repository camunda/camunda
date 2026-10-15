/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.systempartition;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.raft.RaftServer.Role;
import io.camunda.configuration.Membership;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.system.syspartition.SystemPartition;
import io.camunda.zeebe.broker.system.syspartition.SystemPartitionKeyType;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlanStatus;
import io.camunda.zeebe.management.cluster.PlannedOperationsResponse;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import io.camunda.zeebe.util.buffer.BufferReader;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Verifies that configuration changes are coordinated by the lowest alive broker, which writes
 * every change, phase advancement and completion to the system partition, so that the next alive
 * broker continues from there once the coordinator is gone.
 */
@ZeebeIntegration
@Timeout(5 * 60)
final class SystemPartitionCoordinatorFailoverIT {

  private static final Duration TIMEOUT = Duration.ofMinutes(2);
  private static final MemberId BROKER_0 = MemberId.from("0");

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          // a standalone gateway, as broker 0 is shut down during the test
          .withGatewaysCount(1)
          .withEmbeddedGateway(false)
          .withBrokersCount(3)
          .withPartitionsCount(1)
          .withReplicationFactor(3)
          .withBrokerConfig(
              broker ->
                  broker.withUnifiedConfig(
                      cfg -> detectFailuresQuickly(cfg.getCluster().getMembership())))
          .withGatewayConfig(
              gateway ->
                  detectFailuresQuickly(gateway.unifiedConfig().getCluster().getMembership()))
          .build();

  @Test
  void shouldContinueConfigurationChangesOnNextAliveBroker() {
    // given - broker 0 is the lowest alive broker and coordinates the first change
    awaitSystemPartitionLeader();
    final var actuator = ClusterActuator.of(cluster.anyGateway());
    final var first = actuator.leavePartition(0, 1);
    awaitCompleted(first);
    awaitStoredLastChange(first.getChangeId(), cluster.brokers().values().stream().toList());

    // when - broker 0 is gone, so broker 1 becomes the coordinator
    cluster.brokers().get(BROKER_0).close();
    final var survivors =
        cluster.brokers().values().stream()
            .filter(broker -> !broker.nodeId().equals(BROKER_0))
            .toList();
    // retried until the gateway and broker 1 noticed broker 0 is gone, until which the request is
    // either sent to broker 0 or rejected by broker 1 as not the coordinator
    final var second =
        Awaitility.await("the change is accepted by the next alive broker")
            .atMost(TIMEOUT)
            .ignoreExceptions()
            .until(() -> actuator.leavePartition(2, 1), response -> response != null);

    // then - broker 1 continued from the change ids issued by broker 0
    assertThat(second.getChangeId()).isEqualTo(first.getChangeId() + 1);
    awaitCompleted(second);
    awaitStoredLastChange(second.getChangeId(), survivors);
  }

  private void awaitSystemPartitionLeader() {
    Awaitility.await("the system partition elected a leader")
        .atMost(TIMEOUT)
        .until(
            () ->
                cluster.brokers().values().stream()
                    .anyMatch(
                        broker ->
                            systemPartition(broker).raftPartition().getRole() == Role.LEADER));
  }

  private void awaitCompleted(final PlannedOperationsResponse response) {
    Awaitility.await("change %d is completed".formatted(response.getChangeId()))
        .atMost(TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> ClusterActuatorAssert.assertThat(cluster).hasCompletedChanges(response));
  }

  /** The completed change is the latest one stored, as replicated to each of the brokers. */
  private void awaitStoredLastChange(
      final long changeId, final List<TestStandaloneBroker> brokers) {
    Awaitility.await("the system partition stores change %d as completed".formatted(changeId))
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(brokers)
                    .allSatisfy(
                        broker -> {
                          final var stored = storedConfiguration(broker).orElseThrow();
                          final var changes = stored.phasedChangeState();
                          assertThat(changes.nextId()).isEqualTo(changeId + 1);
                          assertThat(changes.pending()).isEmpty();
                          assertThat(changes.lastChange())
                              .hasValueSatisfying(
                                  last -> {
                                    assertThat(last.id()).isEqualTo(changeId);
                                    assertThat(last.status())
                                        .isEqualTo(PhasedChangePlanStatus.COMPLETED);
                                  });
                        }));
  }

  private static Optional<CurrentClusterConfiguration> storedConfiguration(
      final TestStandaloneBroker broker) {
    return systemPartition(broker)
        .keyValueReader()
        .get(SystemPartitionKeyType.CLUSTER_CONFIGURATION, new BytesReader())
        .map(value -> new ProtoBufSerializer().decodeCurrentClusterConfiguration(value.bytes));
  }

  private static SystemPartition systemPartition(final TestStandaloneBroker broker) {
    final var systemPartition = broker.bean(Broker.class).getBrokerContext().getSystemPartition();
    assertThat(systemPartition).describedAs("system partition of %s", broker.nodeId()).isNotNull();
    return systemPartition;
  }

  private static void detectFailuresQuickly(final Membership membership) {
    membership.setSyncInterval(Duration.ofSeconds(1));
    membership.setFailureTimeout(Duration.ofSeconds(2));
  }

  private static final class BytesReader implements BufferReader {
    private byte[] bytes;

    @Override
    public void wrap(final DirectBuffer buffer, final int offset, final int length) {
      bytes = new byte[length];
      buffer.getBytes(offset, bytes);
    }
  }
}
