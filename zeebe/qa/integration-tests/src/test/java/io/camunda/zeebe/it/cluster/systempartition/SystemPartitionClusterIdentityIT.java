/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.systempartition;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.raft.RaftServer.Role;
import io.camunda.configuration.Camunda;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.system.syspartition.ClusterIdentity;
import io.camunda.zeebe.broker.system.syspartition.SystemKeyValueWriter;
import io.camunda.zeebe.broker.system.syspartition.SystemPartition;
import io.camunda.zeebe.broker.system.syspartition.SystemPartitionKeyType;
import io.camunda.zeebe.broker.system.syspartition.SystemPartitionListener;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the system partition records the cluster's identity once, when the cluster is first
 * bootstrapped, and that every broker reads the same identity even after the leader changes or the
 * whole cluster restarts.
 */
@ZeebeIntegration
final class SystemPartitionClusterIdentityIT {

  private static final Duration TIMEOUT = Duration.ofMinutes(1);

  private static TestCluster cluster(final Consumer<Camunda> config) {
    return TestCluster.builder()
        .withBrokersCount(3)
        .withEmbeddedGateway(true)
        .withPartitionsCount(1)
        .withReplicationFactor(3)
        .withBrokerConfig(
            broker ->
                broker.withUnifiedConfig(
                    cfg -> {
                      cfg.getCluster().getSystemPartition().setEnabled(true);
                      config.accept(cfg);
                    }))
        .build();
  }

  private static ClusterIdentity awaitSameIdentityOnEveryBroker(final TestCluster cluster) {
    return Awaitility.await("every broker reads the same cluster identity")
        .atMost(TIMEOUT)
        .until(
            () -> {
              final var identities =
                  cluster.brokers().values().stream()
                      .filter(TestStandaloneBroker::isStarted)
                      .map(SystemPartitionClusterIdentityIT::identity)
                      .toList();
              return identities.stream().allMatch(Optional::isPresent)
                      && identities.stream().distinct().count() == 1
                  ? identities.getFirst()
                  : Optional.<ClusterIdentity>empty();
            },
            Optional::isPresent)
        .orElseThrow();
  }

  /**
   * Puts the cluster id the current leader reads back through its writer, which changes nothing. A
   * leader is handed its writer only after it applied every committed entry and its identity check
   * ran, so any identity it puts is committed and applied before this put is.
   */
  private static void putThroughLeader(final TestCluster cluster) {
    final var leader = systemPartition(brokerWithRole(cluster, Role.LEADER));
    final var writer = new CompletableFuture<SystemKeyValueWriter>();
    final SystemPartitionListener listener =
        new SystemPartitionListener() {
          @Override
          public void onBecomingLeader(final long term, final SystemKeyValueWriter w) {
            writer.complete(w);
          }
        };
    leader.addListener(listener);
    try {
      writer
          .get(TIMEOUT.toSeconds(), TimeUnit.SECONDS)
          .put(
              SystemPartitionKeyType.CLUSTER_ID,
              leader
                  .keyValueReader()
                  .get(SystemPartitionKeyType.CLUSTER_ID, new RawValue())
                  .orElseThrow())
          .join(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    } finally {
      leader.removeListener(listener);
    }
  }

  private static TestStandaloneBroker brokerWithRole(final TestCluster cluster, final Role role) {
    return Awaitility.await("system partition has a %s".formatted(role))
        .atMost(TIMEOUT)
        .until(
            () ->
                cluster.brokers().values().stream()
                    .filter(TestStandaloneBroker::isStarted)
                    .filter(broker -> systemPartition(broker).raftPartition().getRole() == role)
                    .findFirst(),
            Optional::isPresent)
        .orElseThrow();
  }

  private static Optional<ClusterIdentity> identity(final TestStandaloneBroker broker) {
    return ClusterIdentity.read(systemPartition(broker).keyValueReader());
  }

  private static SystemPartition systemPartition(final TestStandaloneBroker broker) {
    final var systemPartition = broker.bean(Broker.class).getBrokerContext().getSystemPartition();
    assertThat(systemPartition).describedAs("system partition of %s", broker.nodeId()).isNotNull();
    return systemPartition;
  }

  @Nested
  final class WithConfiguredClusterId {
    private final String clusterId = "cluster-" + UUID.randomUUID();

    @TestZeebe
    private final TestCluster cluster = cluster(cfg -> cfg.getCluster().setClusterId(clusterId));

    @Test
    void shouldRecordConfiguredClusterId() {
      // given
      final var startedBefore = Instant.now();

      // when
      final var identity = awaitSameIdentityOnEveryBroker(cluster);

      // then
      assertThat(identity.clusterId()).isEqualTo(clusterId);
      assertThat(identity.creationTime())
          .isBefore(Instant.now())
          .isAfter(startedBefore.minus(TIMEOUT));
    }

    @Test
    void shouldReplaceRecordedClusterIdWithDifferentConfiguredOne() {
      // given
      final var identity = awaitSameIdentityOnEveryBroker(cluster);
      final var newClusterId = "cluster-" + UUID.randomUUID();

      // when
      cluster.brokers().values().forEach(TestStandaloneBroker::stop);
      cluster
          .brokers()
          .values()
          .forEach(
              broker ->
                  broker
                      .withUnifiedConfig(cfg -> cfg.getCluster().setClusterId(newClusterId))
                      .start());

      // then
      Awaitility.await("every broker reads the configured cluster id")
          .atMost(TIMEOUT)
          .untilAsserted(
              () ->
                  assertThat(awaitSameIdentityOnEveryBroker(cluster))
                      .isEqualTo(new ClusterIdentity(newClusterId, identity.creationTime())));
    }
  }

  @Nested
  final class WithoutConfiguredClusterId {

    @TestZeebe private final TestCluster cluster = cluster(cfg -> {});

    @Test
    void shouldRecordRandomClusterId() {
      // when
      final var identity = awaitSameIdentityOnEveryBroker(cluster);

      // then
      assertThat(UUID.fromString(identity.clusterId())).isNotNull();
    }

    @Test
    void shouldKeepIdentityWhenLeaderChanges() {
      // given - a new leader would record another random id and a later creation time
      final var identity = awaitSameIdentityOnEveryBroker(cluster);
      final var oldLeader = brokerWithRole(cluster, Role.LEADER);

      // when
      oldLeader.stop();
      Awaitility.await("the new leader accepts puts")
          .atMost(TIMEOUT)
          .ignoreExceptions()
          .untilAsserted(() -> putThroughLeader(cluster));

      // then
      assertThat(awaitSameIdentityOnEveryBroker(cluster)).isEqualTo(identity);
    }

    @Test
    void shouldKeepIdentityWhenClusterRestarts() {
      // given
      final var identity = awaitSameIdentityOnEveryBroker(cluster);

      // when
      cluster.brokers().values().forEach(TestStandaloneBroker::stop);
      cluster.brokers().values().forEach(TestStandaloneBroker::start);
      Awaitility.await("the restarted leader accepts puts")
          .atMost(TIMEOUT)
          .ignoreExceptions()
          .untilAsserted(() -> putThroughLeader(cluster));

      // then
      assertThat(awaitSameIdentityOnEveryBroker(cluster)).isEqualTo(identity);
    }
  }

  /** Holds a value as raw bytes, so it can be put back unchanged. */
  private static final class RawValue implements BufferReader, BufferWriter {
    private final MutableDirectBuffer bytes = new UnsafeBuffer();

    @Override
    public void wrap(final DirectBuffer buffer, final int offset, final int length) {
      bytes.wrap(BufferUtil.cloneBuffer(buffer, offset, length));
    }

    @Override
    public int getLength() {
      return bytes.capacity();
    }

    @Override
    public int write(final MutableDirectBuffer buffer, final int offset) {
      buffer.putBytes(offset, bytes, 0, bytes.capacity());
      return bytes.capacity();
    }
  }
}
