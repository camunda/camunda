/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering;

import static io.camunda.zeebe.it.cluster.clustering.dynamic.Utils.createInstanceWithAJobOnAllPartitions;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import java.time.Duration;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@ZeebeIntegration
public class RaftPartitionForwardCompatibilityIT {

  static final int PARTITION_COUNT = 3;

  @ParameterizedTest(name = "{0}")
  @MethodSource("provideTestClusters")
  public void shouldCreateProcessInstances(final String description, final TestCluster cluster) {
    // given test cluster

    // when
    cluster.start().awaitCompleteTopology(Duration.ofSeconds(20));

    // then
    try (final var camundaClient = cluster.availableGateway().newClientBuilder().build()) {
      createInstanceWithAJobOnAllPartitions(camundaClient, "foo", PARTITION_COUNT);
    }
  }

  static Stream<Arguments> provideTestClusters() {

    return Stream.of(
        Arguments.of(
            "8.9 Mode",
            createTestCluster(RaftPartitionForwardCompatibilityIT::configureRunning89Cluster)),
        Arguments.of(
            "8.9 to 8.10 Upgrade",
            createTestCluster(RaftPartitionForwardCompatibilityIT::configureRollingUpgradeTo810)),
        Arguments.of(
            "8.10 Mode",
            createTestCluster(RaftPartitionForwardCompatibilityIT::configureFuture810Mode)),
        Arguments.of(
            "8.10 to 8.11 Upgrade",
            createTestCluster(RaftPartitionForwardCompatibilityIT::configureFuture810To811Upgrade)),
        Arguments.of(
            "8.11 Mode",
            createTestCluster(RaftPartitionForwardCompatibilityIT::configureFuture811Mode)));
  }

  static TestCluster createTestCluster(final BiConsumer<MemberId, TestStandaloneBroker> modifier) {
    return TestCluster.builder()
        .withBrokersCount(3)
        .withPartitionsCount(PARTITION_COUNT)
        .withReplicationFactor(3)
        .withBrokerConfig(modifier)
        .build();
  }

  static void configureRunning89Cluster(
      final MemberId memberId, final TestStandaloneBroker broker) {
    // noop => all listens to the legacy subjects and "default-*" subjects
  }

  static void configureRollingUpgradeTo810(
      final MemberId memberId, final TestStandaloneBroker broker) {
    // one node simulates the future 8.10 case in which one node
    // only sends messages with subject "default-*", but all listen
    // to the legacy subjects and "default-*"
    if (memberId.id().equals("0")) {
      broker.withClusterConfig(c -> c.setLegacySenderSubjectsEnabled(false));
    }
  }

  static void configureFuture810Mode(final MemberId memberId, final TestStandaloneBroker broker) {
    // all listen to the legacy subjects and "default-*" subjects
    // all send only "default-*" subjects
    broker.withClusterConfig(c -> c.setLegacySenderSubjectsEnabled(false));
  }

  static void configureFuture810To811Upgrade(
      final MemberId memberId, final TestStandaloneBroker broker) {
    broker.withClusterConfig(c -> c.setLegacySenderSubjectsEnabled(false));
    if (memberId.id().equals("0")) {
      broker.withClusterConfig(
          c -> {
            c.setLegacyReceiverSubjectsEnabled(false);
          });
    }
  }

  static void configureFuture811Mode(final MemberId memberId, final TestStandaloneBroker broker) {
    // all listen to the "default-*" subjects
    // all send only "default-*" subjects
    broker.withClusterConfig(
        c -> {
          c.setLegacySenderSubjectsEnabled(false);
          c.setLegacyReceiverSubjectsEnabled(false);
        });
  }
}
