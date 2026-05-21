/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.container.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

@SuppressWarnings("resource")
final class CamundaClusterBuilderTest {

  public static final String EXPECTED_IMAGE = "camunda/camunda:latest";

  @Test
  void shouldThrowIllegalArgumentIfBrokersCountIsNegative() {
    // given
    final CamundaClusterBuilder builder = new CamundaClusterBuilder();

    // then
    assertThatCode(() -> builder.withBrokersCount(-1))
        .as("the builder should not accept a negative number of brokers")
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldThrowIllegalArgumentWhenPartitionsIsNotStrictlyPositive() {
    // given
    final CamundaClusterBuilder builder = new CamundaClusterBuilder();

    // then
    assertThatCode(() -> builder.withPartitionsCount(0))
        .as("the builder should not accept no partitions")
        .isInstanceOf(IllegalArgumentException.class);
    assertThatCode(() -> builder.withPartitionsCount(-1))
        .as("the builder should not accept a negative partitions count")
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldThrowIllegalArgumentIfReplicationFactorIsNotStrictlyPositive() {
    // given
    final CamundaClusterBuilder builder = new CamundaClusterBuilder();

    // then
    assertThatCode(() -> builder.withReplicationFactor(0))
        .as("the builder should not accept 0 as a replication factor")
        .isInstanceOf(IllegalArgumentException.class);
    assertThatCode(() -> builder.withReplicationFactor(-1))
        .as("the builder should not accept a negative replication factor")
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldThrowNullExceptionIfNetworkIsNull() {
    // given
    final CamundaClusterBuilder builder = new CamundaClusterBuilder();

    // then
    assertThatCode(() -> builder.withNetwork(null))
        .as("the builder should not accept a null network")
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldThrowIllegalArgumentExceptionWhenNameIsNull() {
    // given
    final CamundaClusterBuilder builder = new CamundaClusterBuilder();

    // then
    assertThatCode(() -> builder.withName(null))
        .as("the builder should not accept a null cluster name")
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldThrowIllegalArgumentExceptionWhenNameIsTooShort() {
    // given
    final CamundaClusterBuilder builder = new CamundaClusterBuilder();

    // then
    assertThatCode(() -> builder.withName(""))
        .as("the builder should not accept an empty cluster name")
        .isInstanceOf(IllegalArgumentException.class);
    assertThatCode(() -> builder.withName("a"))
        .as("the builder should not accept a name which is too short")
        .isInstanceOf(IllegalArgumentException.class);
    assertThatCode(() -> builder.withName("aa"))
        .as("the builder should not accept a name which is too short")
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldThrowIllegalStateExceptionWhenReplicationFactorIsGreaterThanBrokersCount() {
    // given
    final CamundaClusterBuilder builder = new CamundaClusterBuilder();

    // when
    builder.withBrokersCount(1).withReplicationFactor(2);

    // then
    assertThatCode(builder::build)
        .as(
            "the builder should not accept a replication factor which is greater than the number of"
                + " brokers")
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldRespectBrokersCount() {
    // given
    final CamundaClusterBuilder builder = new CamundaClusterBuilder();

    // when
    builder.withBrokersCount(2);
    final CamundaCluster cluster = builder.build();

    // then
    final Map<Integer, BrokerNode<? extends GenericContainer<?>>> brokers = cluster.getBrokers();
    assertThat(brokers)
        .as("the builder created 2 brokers with the right IDs")
        .hasSize(2)
        .containsKeys(0, 1);
    assertThat(brokers.get(0).getConfiguration().getCluster().getSize())
        .as("the first broker has the right cluster size")
        .isEqualTo(2);
    assertThat(brokers.get(0).getConfiguration().getCluster().getNodeId())
        .as("the first broker has ID 0")
        .isZero();

    assertThat(brokers.get(1).getConfiguration().getCluster().getSize())
        .as("the second broker has the right cluster size")
        .isEqualTo(2);
    assertThat(brokers.get(1).getConfiguration().getCluster().getNodeId())
        .as("the second broker has ID 1 ")
        .isOne();
  }

  @Test
  void shouldZeroPartitionsAndReplicationFactorIfBrokersCountIsZero() {
    // given
    final CamundaClusterBuilder builder = new CamundaClusterBuilder();

    // when
    builder.withBrokersCount(0);
    final CamundaCluster cluster = builder.build();

    // then
    assertThat(cluster.getReplicationFactor())
        .as("there are no replication factor if no brokers are defined")
        .isZero();
    assertThat(cluster.getPartitionsCount())
        .as("there are no partitions if no brokers are defined")
        .isZero();
  }

  @Test
  void shouldResetPartitionsAndReplicationFactorToOneIfBrokersCountGoesFromZeroToPositive() {
    // given
    final CamundaClusterBuilder builder = new CamundaClusterBuilder();

    // when
    builder.withBrokersCount(0).withBrokersCount(3);
    final CamundaCluster cluster = builder.build();

    // then
    assertThat(cluster.getReplicationFactor())
        .as("there are is a default replication factor when the broker count is redefined")
        .isOne();
    assertThat(cluster.getPartitionsCount())
        .as("there are is a default partitions count when the broker count is redefined")
        .isOne();
  }

  @Test
  void shouldNotModifyPartitionsCountOrReplicationFactoryWhenSettingBrokersCount() {
    // given
    final CamundaClusterBuilder builder = new CamundaClusterBuilder();

    // when
    builder.withPartitionsCount(4).withReplicationFactor(1).withBrokersCount(3);
    final CamundaCluster cluster = builder.build();

    // then
    assertThat(cluster.getReplicationFactor())
        .as("the replication factor should be static even when the brokers count is changed after")
        .isOne();
    assertThat(cluster.getPartitionsCount())
        .as("the partitions should be static even when the brokers count is changed after")
        .isEqualTo(4);
  }

  @Test
  void shouldRespectPartitionsCount() {
    // given
    final CamundaClusterBuilder builder = new CamundaClusterBuilder();

    // when
    builder.withPartitionsCount(2);
    final CamundaCluster cluster = builder.build();

    // then
    final Map<Integer, BrokerNode<? extends GenericContainer<?>>> brokers = cluster.getBrokers();
    assertThat(cluster.getPartitionsCount())
        .as("the configure the partitions count correctly")
        .isEqualTo(2);
    assertThat(brokers.get(0).getConfiguration().getCluster().getPartitionCount())
        .as("the broker should report the correct environment variable as config")
        .isEqualTo(2);
  }

  @Test
  void shouldRespectReplicationFactor() {
    // given
    final CamundaClusterBuilder builder = new CamundaClusterBuilder();

    // when
    builder.withBrokersCount(2).withReplicationFactor(2);
    final CamundaCluster cluster = builder.build();

    // then
    final Map<Integer, BrokerNode<? extends GenericContainer<?>>> brokers = cluster.getBrokers();
    assertThat(cluster.getReplicationFactor())
        .as("the broker should report the correct replication factor")
        .isEqualTo(2);
    assertThat(brokers.get(0).getConfiguration().getCluster().getReplicationFactor())
        .as("the first broker should report the correct environment variable as config")
        .isEqualTo(2);
    assertThat(brokers.get(1).getConfiguration().getCluster().getReplicationFactor())
        .as("the second broker should report the correct environment variable as config")
        .isEqualTo(2);
  }

  @Test
  void shouldAssignDifferentInternalHostNamesToEveryNode() {
    // given
    final CamundaClusterBuilder builder = new CamundaClusterBuilder();

    // when
    builder.withEmbeddedGateway(false).withBrokersCount(2).withGatewaysCount(2);
    final CamundaCluster cluster = builder.build();

    // then
    final Set<String> internalHosts = new HashSet<>();
    cluster.getBrokers().values().stream()
        .map(ClusterNode::getInternalHost)
        .forEach(internalHosts::add);
    cluster.getGateways().values().stream()
        .map(ClusterNode::getInternalHost)
        .forEach(internalHosts::add);
    assertThat(internalHosts)
        .as("every node in the cluster has a unique internal host name")
        .hasSize(4)
        .doesNotContainNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldAssignDifferentClusterHostsToAllNodes() {
    // given
    final CamundaClusterBuilder builder = new CamundaClusterBuilder();

    // when
    builder.withEmbeddedGateway(false).withBrokersCount(2).withGatewaysCount(2);
    final CamundaCluster cluster = builder.build();

    // then
    final Set<String> advertisedHosts = new HashSet<>();
    cluster.getBrokers().values().stream()
        .map(ClusterNode::getAdditionalConfigs)
        .map(config -> (Map<String, Object>) config.get("zeebe"))
        .map(config -> (Map<String, Object>) config.get("broker"))
        .map(config -> (Map<String, Object>) config.get("network"))
        .map(config -> (String) config.get("advertised-host"))
        .forEach(advertisedHosts::add);
    cluster.getGateways().values().stream()
        .map(GatewayNode::getAdditionalConfigs)
        .map(config -> (Map<String, Object>) config.get("zeebe"))
        .map(config -> (Map<String, Object>) config.get("gateway"))
        .map(config -> (Map<String, Object>) config.get("cluster"))
        .map(config -> (String) config.get("host"))
        .forEach(advertisedHosts::add);
    assertThat(advertisedHosts)
        .as("every node in the cluster has a unique advertised host")
        .hasSize(4)
        .doesNotContainNull();
  }

  @Test
  void shouldRespectClusterName() {
    // given
    final CamundaClusterBuilder builder = new CamundaClusterBuilder();

    // when
    builder
        .withName("test-cluster")
        .withEmbeddedGateway(false)
        .withBrokersCount(2)
        .withGatewaysCount(2);
    final CamundaCluster cluster = builder.build();

    // then

    cluster
        .getBrokers()
        .values()
        .forEach(
            b ->
                assertThat(b.getConfiguration().getCluster().getName())
                    .as("every broker is configured with the correct cluster name")
                    .isEqualTo("test-cluster"));
    cluster
        .getGateways()
        .values()
        .forEach(
            g ->
                assertThat(g.getConfiguration().getCluster().getName())
                    .as("every gateway is configured with the correct cluster name")
                    .isEqualTo("test-cluster"));
  }

  @Test
  void shouldRespectNetwork() {
    // given
    final CamundaClusterBuilder builder = new CamundaClusterBuilder();
    final Network network = Network.newNetwork();

    // when
    builder
        .withNetwork(network)
        .withEmbeddedGateway(false)
        .withBrokersCount(2)
        .withGatewaysCount(2);
    final CamundaCluster cluster = builder.build();

    // then
    assertThat(cluster.getBrokers()).hasSize(2);
    assertThat(cluster.getGateways()).hasSize(2);

    cluster
        .getBrokers()
        .values()
        .forEach(
            b ->
                assertThat(b.self().getNetwork())
                    .as("every broker is configured with the correct network")
                    .isEqualTo(network));
    cluster
        .getGateways()
        .values()
        .forEach(
            g ->
                assertThat(g.self().getNetwork())
                    .as("every gateway is configured with the correct network")
                    .isEqualTo(network));
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldAssignUniqueMemberIdToEachGateway() {
    // given
    final CamundaClusterBuilder builder = new CamundaClusterBuilder();

    // when
    builder.withEmbeddedGateway(false).withGatewaysCount(2);
    final CamundaCluster cluster = builder.build();

    // then
    final Set<String> memberIds = cluster.getGateways().keySet();
    assertThat(memberIds).hasSize(2);
    for (final String memberId : memberIds) {
      final GatewayNode<? extends GenericContainer<?>> gateway =
          cluster.getGateways().get(memberId);
      final Map<String, Object> zeebe =
          (Map<String, Object>) gateway.getAdditionalConfigs().get("zeebe");
      final Map<String, Object> broker = (Map<String, Object>) zeebe.get("gateway");
      final Map<String, Object> gatewayCfg = (Map<String, Object>) broker.get("cluster");
      assertThat(gatewayCfg)
          .as("every gateway has a unique member configured via environment variable")
          .containsEntry("member-id", memberId);
    }
  }

  @Test
  void shouldAssignUniqueNodeIdToEachBroker() {
    // given
    final CamundaClusterBuilder builder = new CamundaClusterBuilder();

    // when
    builder.withEmbeddedGateway(false).withBrokersCount(2);
    final CamundaCluster cluster = builder.build();

    // then
    final Set<Integer> nodeIds = cluster.getBrokers().keySet();
    assertThat(nodeIds).hasSize(2);
    for (final Integer nodeId : nodeIds) {
      final BrokerNode<? extends GenericContainer<?>> broker = cluster.getBrokers().get(nodeId);
      assertThat(broker.getConfiguration().getCluster().getNodeId())
          .as("every broker has a unique node ID configured via environment variable")
          .isEqualTo(nodeId);
    }
  }

  @Test
  void shouldAssignAllBrokersAsInitialContactPoints() {
    // given
    final CamundaClusterBuilder builder = new CamundaClusterBuilder();

    // when
    builder.withEmbeddedGateway(false).withBrokersCount(2);
    final CamundaCluster cluster = builder.build();

    // then
    final Map<Integer, BrokerNode<? extends GenericContainer<?>>> brokers = cluster.getBrokers();
    final List<String> brokerZeroInitialContactPoints =
        brokers.get(0).getConfiguration().getCluster().getInitialContactPoints();
    final List<String> brokerOneInitialContactPoints =
        brokers.get(1).getConfiguration().getCluster().getInitialContactPoints();

    assertThat(brokerZeroInitialContactPoints)
        .as(
            "both broker 0 and broker 1 report each other as initial contact points via environment"
                + " variables")
        .isEqualTo(brokerOneInitialContactPoints)
        .containsOnlyOnce(brokers.get(0).getInternalClusterAddress())
        .containsOnlyOnce(brokers.get(1).getInternalClusterAddress());
  }

  @Test
  void shouldAssignABrokerAsContactPointForStandaloneGateway() {
    // given
    final CamundaClusterBuilder builder = new CamundaClusterBuilder();

    // when
    builder.withEmbeddedGateway(false).withGatewaysCount(1).withBrokersCount(1);
    final CamundaCluster cluster = builder.build();

    // then
    final BrokerNode<? extends GenericContainer<?>> broker = cluster.getBrokers().get(0);
    final GatewayNode<? extends GenericContainer<?>> gateway =
        cluster.getGateways().values().iterator().next();

    assertThat(gateway.getConfiguration().getCluster().getInitialContactPoints())
        .as("the gateway has the correct broker initial contact points configured")
        .contains(broker.getInternalClusterAddress());
  }

  @Test
  void shouldNotAssignContactPointToStandaloneGatewayIfNoBrokersAvailable() {
    // given
    final CamundaClusterBuilder builder = new CamundaClusterBuilder();

    // when
    builder.withGatewaysCount(1).withBrokersCount(0);
    final CamundaCluster cluster = builder.build();

    // then
    final GatewayNode<? extends GenericContainer<?>> gateway =
        cluster.getGateways().values().iterator().next();

    assertThat(gateway.getEnvMap())
        .as("the gateway has no initial contact points configured since there are no brokers")
        .doesNotContainKey("ZEEBE_GATEWAY_CLUSTER_INITIALCONTACTPOINTS");
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldConfigureEmbeddedGateway() {
    // given
    final CamundaClusterBuilder builder = new CamundaClusterBuilder();

    // when
    builder.withEmbeddedGateway(true).withBrokersCount(1);
    final CamundaCluster cluster = builder.build();

    // then
    assertThat(cluster.getGateways())
        .as("there is a gateway even if there is only a single node")
        .hasSize(1);
    assertThat(cluster.getGateways().get("0"))
        .as("the gateway is actual a broker as it is an embedded gateway")
        .isInstanceOf(BrokerNode.class);

    final Map<String, Object> zeebe =
        (Map<String, Object>) cluster.getGateways().get("0").getAdditionalConfigs().get("zeebe");
    final Map<String, Object> broker = (Map<String, Object>) zeebe.get("broker");
    final Map<String, Object> gateway = (Map<String, Object>) broker.get("gateway");
    assertThat(gateway)
        .as("the broker is configured to enable the embedded gateway")
        .containsEntry("enable", true);
  }

  @Test
  void shouldNotConfigureEmbeddedGateway() {
    // given
    final CamundaClusterBuilder builder = new CamundaClusterBuilder();

    // when
    builder.withEmbeddedGateway(false).withBrokersCount(1);
    final CamundaCluster cluster = builder.build();

    // then
    assertThat(cluster.getGateways()).as("there are no configured gateways").isEmpty();
    assertThat(cluster.getBrokers().get(0).getEnvMap())
        .as("the broker is not configured to enable the embedded gateway")
        .doesNotContainEntry("ZEEBE_BROKER_GATEWAY_ENABLE", "true");
  }

  @Test
  void shouldApplyBrokerConfigurationOnlyOnBrokers() {
    // given
    final String foreseeEnv = "IS_CONFIGURED_BY_BROKER_FUNCTION";
    final CamundaClusterBuilder builder =
        new CamundaClusterBuilder()
            .withBrokerConfig((id, broker) -> broker.addEnv(foreseeEnv, String.valueOf(id)))
            .withBrokersCount(1)
            .withGatewaysCount(1)
            .withEmbeddedGateway(false);

    // when
    final CamundaCluster cluster = builder.build();

    // then
    assertThat(cluster.getBrokers())
        .allSatisfy(
            (id, BrokerNode) ->
                assertThat(BrokerNode.getEnvMap())
                    .as("Broker node: %s must have %s environment variable", BrokerNode, foreseeEnv)
                    .containsEntry(foreseeEnv, String.valueOf(id)));
    assertThat(cluster.getGateways())
        .allSatisfy(
            (memberId, GatewayNode) ->
                assertThat(GatewayNode.getEnvMap())
                    .as(
                        "Gateway node: %s must not have %s environment variable",
                        GatewayNode, foreseeEnv)
                    .doesNotContainKey(foreseeEnv));
  }

  @Test
  void shouldApplyNodeConfigurationOnAllNodes() {
    // given
    final String foreseeEnv = "IS_CONFIGURED_BY_NODE_FUNCTION";
    final CamundaClusterBuilder builder =
        new CamundaClusterBuilder()
            .withNodeConfig(node -> node.addEnv(foreseeEnv, ""))
            .withBrokersCount(1)
            .withGatewaysCount(1)
            .withEmbeddedGateway(false);

    // when
    final CamundaCluster cluster = builder.build();

    // then
    assertThat(cluster.getBrokers())
        .allSatisfy(
            (integer, BrokerNode) ->
                assertThat(BrokerNode.getEnvMap())
                    .as("Broker node: %s must have %s environment variable", BrokerNode, foreseeEnv)
                    .containsKey(foreseeEnv));
    assertThat(cluster.getGateways())
        .allSatisfy(
            (integer, GatewayNode) ->
                assertThat(GatewayNode.getEnvMap())
                    .as(
                        "Gateway node: %s must have %s environment variable",
                        GatewayNode, foreseeEnv)
                    .containsKey(foreseeEnv));
  }

  @Test
  void shouldApplyGatewayConfigurationOnEmbeddedGateways() {
    // given
    final String foreseeEnv = "IS_CONFIGURED_BY_GATEWAY_FUNCTION";
    final CamundaClusterBuilder builder =
        new CamundaClusterBuilder()
            .withGatewayConfig((memberId, gateway) -> gateway.addEnv(foreseeEnv, memberId))
            .withBrokersCount(1)
            .withGatewaysCount(1)
            .withEmbeddedGateway(true);

    // when
    final CamundaCluster cluster = builder.build();

    // then
    assertThat(cluster.getBrokers())
        .allSatisfy(
            (id, BrokerNode) ->
                assertThat(BrokerNode.getEnvMap())
                    .as("Broker node: %s must have %s environment variable", BrokerNode, foreseeEnv)
                    .containsEntry(foreseeEnv, String.valueOf(id)));
    assertThat(cluster.getGateways())
        .allSatisfy(
            (memberId, GatewayNode) ->
                assertThat(GatewayNode.getEnvMap())
                    .as(
                        "Gateway node: %s must have %s environment variable",
                        GatewayNode, foreseeEnv)
                    .containsEntry(foreseeEnv, memberId));
  }

  @Test
  void shouldApplyGatewayConfigurationOnlyOnGateways() {
    // given
    final String foreseeEnv = "IS_CONFIGURED_BY_GATEWAY_FUNCTION";
    final CamundaClusterBuilder builder =
        new CamundaClusterBuilder()
            .withGatewayConfig((memberId, gateway) -> gateway.addEnv(foreseeEnv, memberId))
            .withBrokersCount(1)
            .withGatewaysCount(1)
            .withEmbeddedGateway(false);

    // when
    final CamundaCluster cluster = builder.build();

    // then
    assertThat(cluster.getBrokers())
        .allSatisfy(
            (id, BrokerNode) ->
                assertThat(BrokerNode.getEnvMap())
                    .as(
                        "Broker node: %s must not have %s environment variable",
                        BrokerNode, foreseeEnv)
                    .doesNotContainKey(foreseeEnv));
    assertThat(cluster.getGateways())
        .allSatisfy(
            (memberId, GatewayNode) ->
                assertThat(GatewayNode.getEnvMap())
                    .as(
                        "Gateway node: %s must have %s environment variable",
                        GatewayNode, foreseeEnv)
                    .containsEntry(foreseeEnv, memberId));
  }

  @Test
  void shouldBrokerConfigurationOverrideNodeConfiguration() {
    // given
    final String foreseeEnv = "IS_CONFIGURED";
    final String nodeValue = "NODE";
    final String brokerValue = "BROKER";

    final CamundaClusterBuilder builder =
        new CamundaClusterBuilder()
            .withNodeConfig(node -> node.addEnv(foreseeEnv, nodeValue))
            .withBrokerConfig(broker -> broker.addEnv(foreseeEnv, brokerValue))
            .withBrokersCount(1)
            .withGatewaysCount(1)
            .withEmbeddedGateway(false);

    // when
    final CamundaCluster cluster = builder.build();

    // then
    assertThat(cluster.getBrokers())
        .allSatisfy(
            (integer, BrokerNode) ->
                assertThat(BrokerNode.getEnvMap())
                    .as(
                        "Broker node: %s must not have %s environment variable",
                        BrokerNode, foreseeEnv)
                    .containsEntry(foreseeEnv, brokerValue));
    assertThat(cluster.getGateways())
        .allSatisfy(
            (s, GatewayNode) ->
                assertThat(GatewayNode.getEnvMap())
                    .as(
                        "Gateway node: %s must have %s environment variable",
                        GatewayNode, foreseeEnv)
                    .containsEntry(foreseeEnv, nodeValue));
  }

  @Test
  void shouldGatewayConfigurationOverrideNodeConfiguration() {
    // given
    final String foreseeEnv = "IS_CONFIGURED";
    final String nodeValue = "NODE";
    final String gatewayValue = "GATEWAY";

    final CamundaClusterBuilder builder =
        new CamundaClusterBuilder()
            .withNodeConfig(node -> node.addEnv(foreseeEnv, nodeValue))
            .withGatewayConfig(gateway -> gateway.addEnv(foreseeEnv, gatewayValue))
            .withBrokersCount(1)
            .withGatewaysCount(1)
            .withEmbeddedGateway(false);

    // when
    final CamundaCluster cluster = builder.build();

    // then
    assertThat(cluster.getBrokers())
        .allSatisfy(
            (integer, BrokerNode) ->
                assertThat(BrokerNode.getEnvMap())
                    .as(
                        "Broker node: %s must not have %s environment variable",
                        BrokerNode, foreseeEnv)
                    .containsEntry(foreseeEnv, nodeValue));
    assertThat(cluster.getGateways())
        .allSatisfy(
            (s, GatewayNode) ->
                assertThat(GatewayNode.getEnvMap())
                    .as(
                        "Gateway node: %s must have %s environment variable",
                        GatewayNode, foreseeEnv)
                    .containsEntry(foreseeEnv, gatewayValue));
  }

  @Test
  void shouldBrokerOverrideEmbeddedGatewayConfiguration() {
    // given
    final String foreseeEnv = "IS_CONFIGURED";
    final String brokerValue = "BROKER";
    final String gatewayValue = "GATEWAY";

    final CamundaClusterBuilder builder =
        new CamundaClusterBuilder()
            .withBrokerConfig(broker -> broker.addEnv(foreseeEnv, brokerValue))
            .withGatewayConfig(gateway -> gateway.addEnv(foreseeEnv, gatewayValue))
            .withBrokersCount(1)
            .withGatewaysCount(0)
            .withEmbeddedGateway(true);

    // when
    final CamundaCluster cluster = builder.build();

    // then
    assertThat(cluster.getBrokers())
        .allSatisfy(
            (integer, BrokerNode) ->
                assertThat(BrokerNode.getEnvMap())
                    .as(
                        "Broker node: %s must not have %s environment variable",
                        BrokerNode, foreseeEnv)
                    .containsEntry(foreseeEnv, brokerValue));
    assertThat(cluster.getGateways())
        .allSatisfy(
            (s, GatewayNode) ->
                assertThat(GatewayNode.getEnvMap())
                    .as(
                        "Gateway node: %s must have %s environment variable",
                        GatewayNode, foreseeEnv)
                    .doesNotContainEntry(foreseeEnv, gatewayValue));
  }

  @Test
  void shouldSetImageNameForGateways() {
    // given
    final CamundaClusterBuilder builder = new CamundaClusterBuilder();
    final String zeebeDockerImage = "camunda/camunda:latest";

    // when
    final DockerImageName gatewayImageName = DockerImageName.parse(zeebeDockerImage);
    builder.withGatewayImage(gatewayImageName).withGatewaysCount(1).withEmbeddedGateway(false);
    final CamundaCluster cluster = builder.build();

    // then
    assertThat(cluster.getGateways().entrySet())
        .as("the only gateway created has the right docker image")
        .singleElement()
        .satisfies(gatewayEntry -> verifyZeebeHasImageName(gatewayEntry.getValue()));
  }

  @Test
  void shouldSetImageNameForBrokers() {
    // given
    final CamundaClusterBuilder builder = new CamundaClusterBuilder();
    final String zeebeDockerImage = "camunda/camunda:latest";

    // when
    final DockerImageName gatewayImageName = DockerImageName.parse(zeebeDockerImage);
    builder.withBrokerImage(gatewayImageName).withBrokersCount(1);
    final CamundaCluster cluster = builder.build();

    // then
    assertThat(cluster.getBrokers().entrySet())
        .as("the only broker created has the right docker image")
        .singleElement()
        .satisfies(brokerEntry -> verifyZeebeHasImageName(brokerEntry.getValue()));
  }

  @Test
  void shouldSetImageNameForGatewaysAndBrokers() {
    // given
    final CamundaClusterBuilder builder = new CamundaClusterBuilder();
    final String zeebeDockerImage = "camunda/camunda:latest";

    // when
    final DockerImageName gatewayImageName = DockerImageName.parse(zeebeDockerImage);
    builder
        .withImage(gatewayImageName)
        .withBrokersCount(1)
        .withGatewaysCount(1)
        .withEmbeddedGateway(false);
    final CamundaCluster cluster = builder.build();

    // then
    assertThat(cluster.getBrokers().entrySet())
        .as("the only broker created has the right docker image")
        .singleElement()
        .satisfies(brokerEntry -> verifyZeebeHasImageName(brokerEntry.getValue()));
    assertThat(cluster.getGateways().entrySet())
        .as("the only standalone gateway created has the right docker image")
        .singleElement()
        .satisfies(gatewayEntry -> verifyZeebeHasImageName(gatewayEntry.getValue()));
  }

  private void verifyZeebeHasImageName(final ClusterNode<? extends GenericContainer<?>> zeebe) {
    assertThat(zeebe.getDockerImageName()).isEqualTo(EXPECTED_IMAGE);
  }
}
