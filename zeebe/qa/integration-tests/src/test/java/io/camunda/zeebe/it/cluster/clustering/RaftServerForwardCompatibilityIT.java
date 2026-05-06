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
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@ZeebeIntegration
@Timeout(3 * 60)
public class RaftServerForwardCompatibilityIT {

  private static final int PARTITION_COUNT = 3;
  private static final int REPLICATION_FACTOR = 2;
  private static final String MEMBER_NODE_ID_0 = "0";

  @ParameterizedTest(name = "{0}")
  @MethodSource("provideTestClusters")
  public void shouldCreateProcessInstances(final String description, final TestCluster cluster) {
    // given test cluster

    // when
    cluster.start().awaitCompleteTopology();

    // then
    try (final var camundaClient = cluster.availableGateway().newClientBuilder().build()) {
      createInstanceWithAJobOnAllPartitions(camundaClient, "foo", PARTITION_COUNT);
    }
  }

  static Stream<Arguments> provideTestClusters() {

    return Stream.of(
        Arguments.of(
            "8.9 Mode with Embedded Gateway",
            createTestClusterWithBrokerConfiguration(
                RaftServerForwardCompatibilityIT::configure89Mode)),
        Arguments.of(
            "8.9 to 8.10 Upgrade with Embedded Gateway",
            createTestClusterWithBrokerConfiguration(
                RaftServerForwardCompatibilityIT::configure89To810Mode)),
        Arguments.of(
            "8.10 Mode with Embedded Gateway",
            createTestClusterWithBrokerConfiguration(
                RaftServerForwardCompatibilityIT::configure810Mode)),
        Arguments.of(
            "8.10 to 8.11 Upgrade with Embedded Gateway",
            createTestClusterWithBrokerConfiguration(
                RaftServerForwardCompatibilityIT::configure810To811Mode)),
        Arguments.of(
            "8.11 Mode with Embedded Gateway",
            createTestClusterWithBrokerConfiguration(
                RaftServerForwardCompatibilityIT::configure811Mode)),
        Arguments.of(
            "8.9 Mode with Standalone Gateway",
            createTestClusterWithStandaloneGateway(
                RaftServerForwardCompatibilityIT::configure89Mode, true)),
        Arguments.of(
            "8.9 to 8.10 Upgrade with Standalone Gateway (sendOnLegacySubject = true)",
            createTestClusterWithStandaloneGateway(
                RaftServerForwardCompatibilityIT::configure89To810Mode, true)),
        Arguments.of(
            "8.9 to 8.10 Upgrade with Standalone Gateway (sendOnLegacySubject = false)",
            createTestClusterWithStandaloneGateway(
                RaftServerForwardCompatibilityIT::configure89To810Mode, false)),
        Arguments.of(
            "8.10 Mode with Standalone Gateway",
            createTestClusterWithStandaloneGateway(
                RaftServerForwardCompatibilityIT::configure810Mode, false)),
        Arguments.of(
            "8.10 to 8.11 Upgrade with Standalone Gateway",
            createTestClusterWithStandaloneGateway(
                RaftServerForwardCompatibilityIT::configure810To811Mode, false)),
        Arguments.of(
            "8.11 Mode with Standalone Gateway",
            createTestClusterWithStandaloneGateway(
                RaftServerForwardCompatibilityIT::configure811Mode, false)));
  }

  static TestCluster createTestClusterWithBrokerConfiguration(
      final BiConsumer<MemberId, TestStandaloneBroker> brokerConfigurationApplier) {
    return TestCluster.builder()
        .withBrokersCount(PARTITION_COUNT)
        .withPartitionsCount(PARTITION_COUNT)
        .withReplicationFactor(REPLICATION_FACTOR)
        .withBrokerConfig(brokerConfigurationApplier)
        .build();
  }

  static TestCluster createTestClusterWithStandaloneGateway(
      final BiConsumer<MemberId, TestStandaloneBroker> brokerConfigurationApplier,
      final boolean gatewaySendOnLegacySubject) {
    return TestCluster.builder()
        .withBrokersCount(PARTITION_COUNT)
        .withPartitionsCount(PARTITION_COUNT)
        .withReplicationFactor(REPLICATION_FACTOR)
        .withBrokerConfig(brokerConfigurationApplier)
        .withEmbeddedGateway(false)
        .withGatewaysCount(1)
        .withGatewayConfig(
            m -> m.withClusterConfig(c -> c.setSendOnLegacySubject(gatewaySendOnLegacySubject)))
        .build();
  }

  /**
   * 8.9.x cluster with the following configuration:
   *
   * <ul>
   *   <li>Sending: All nodes send with subject: "raft-partition-partition-*"
   *   <li>Receiving: All nodes listen to subjects: "default-partition-*" and
   *       "raft-partition-partition-*"
   * </ul>
   */
  static void configure89Mode(final MemberId memberId, final TestStandaloneBroker broker) {
    // noop (default configuration)
  }

  /**
   * Simulating upgrade path from 8.9 to 8.10 cluster with the following configuration:
   *
   * <ul>
   *   <li>Sending (Node "0"): Node sends with subject: "default-partition-*"
   *   <li>Sending (Node "1" and "2"): Nodes send with subject: "raft-partition-partition-*"
   *   <li>Receiving: All nodes listen to subjects: "default-partition-*" and
   *       "raft-partition-partition-*"
   * </ul>
   */
  static void configure89To810Mode(final MemberId memberId, final TestStandaloneBroker broker) {
    if (memberId.id().equals(MEMBER_NODE_ID_0)) {
      broker.withClusterConfig(c -> c.setSendOnLegacySubject(false));
    }
  }

  /**
   * Simulating future 8.10 cluster with the following configuration:
   *
   * <ul>
   *   <li>Sending: All nodes send with subject: "default-partition-*"
   *   <li>Receiving: All nodes listen to subjects: "default-partition-*" and
   *       "raft-partition-partition-*"
   * </ul>
   */
  static void configure810Mode(final MemberId memberId, final TestStandaloneBroker broker) {
    broker.withClusterConfig(c -> c.setSendOnLegacySubject(false));
  }

  /**
   * Simulating future upgrade path from 8.10 to 8.11 with the following configuration:
   *
   * <ul>
   *   <li>Sending: All nodes send with subject: "default-partition-*"
   *   <li>Receiving (Node "0" / 8.11): Node listens to subject: "default-partition-*"
   *   <li>Receiving (Node "1" and "2" / 8.10): Nodes listen to subject: "default -partition-*" and
   *       "raft-partition-partition-*"
   * </ul>
   */
  static void configure810To811Mode(final MemberId memberId, final TestStandaloneBroker broker) {
    broker.withClusterConfig(c -> c.setSendOnLegacySubject(false));
    if (memberId.id().equals(MEMBER_NODE_ID_0)) {
      broker.withClusterConfig(
          c -> {
            c.setReceiveOnLegacySubject(false);
          });
    }
  }

  /**
   * Simulating future 8.11 cluster with the following configuration:
   *
   * <ul>
   *   <li>Sending: All nodes send with subject: "default-partition-*"
   *   <li>Receiving: All nodes listen to subject: "default -partition-*"
   * </ul>
   */
  static void configure811Mode(final MemberId memberId, final TestStandaloneBroker broker) {
    broker.withClusterConfig(
        c -> {
          c.setSendOnLegacySubject(false);
          c.setReceiveOnLegacySubject(false);
        });
  }
}
