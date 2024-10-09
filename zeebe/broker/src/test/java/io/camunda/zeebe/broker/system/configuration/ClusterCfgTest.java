/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossiperConfig;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public final class ClusterCfgTest {

  private static final String ZEEBE_BROKER_CLUSTER_NODE_ID = "zeebe.broker.cluster.nodeId";
  private static final String ZEEBE_BROKER_CLUSTER_INITIAL_CONTACT_POINTS =
      "zeebe.broker.cluster.initialContactPoints";
  private static final String ZEEBE_BROKER_CLUSTER_PARTITIONS_COUNT =
      "zeebe.broker.cluster.partitionsCount";
  private static final String ZEEBE_BROKER_CLUSTER_REPLICATION_FACTOR =
      "zeebe.broker.cluster.replicationFactor";
  private static final String ZEEBE_BROKER_CLUSTER_CLUSTER_SIZE =
      "zeebe.broker.cluster.clusterSize";
  private static final String ZEEBE_BROKER_CLUSTER_CLUSTER_NAME =
      "zeebe.broker.cluster.clusterName";
  private static final String ZEEBE_BROKER_CLUSTER_CONFIG_MANAGER_GOSSIP_ENABLE_SYNC =
      "zeebe.broker.cluster.configManager.gossip.enableSync";
  private static final String ZEEBE_BROKER_CLUSTER_CONFIG_MANAGER_GOSSIP_SYNC_DELAY =
      "zeebe.broker.cluster.configManager.gossip.syncDelay";
  private static final String ZEEBE_BROKER_CLUSTER_CONFIG_MANAGER_GOSSIP_SYNC_REQUEST_TIMEOUT =
      "zeebe.broker.cluster.configManager.gossip.syncRequestTimeout";
  private static final String ZEEBE_BROKER_CLUSTER_CONFIG_MANAGER_GOSSIP_GOSSIP_FANOUT =
      "zeebe.broker.cluster.configManager.gossip.gossipFanout";

  @Test
  public void shouldUseDefaults() {
    // given

    // when
    final ClusterCfg emptyCfg = TestConfigReader.readConfig("empty", Map.of()).getCluster();
    final ClusterCfg defaultCfg = TestConfigReader.readConfig("default", Map.of()).getCluster();

    // then
    assertThat(emptyCfg.getNodeId()).isEqualTo(0);
    assertThat(emptyCfg.getPartitionsCount()).isEqualTo(1);
    assertThat(emptyCfg.getReplicationFactor()).isEqualTo(1);
    assertThat(emptyCfg.getClusterSize()).isEqualTo(1);
    assertThat(emptyCfg.getInitialContactPoints()).isEqualTo(List.of());

    assertThat(emptyCfg.getConfigManager()).isEqualTo(ConfigManagerCfg.defaultConfig());

    assertThat(defaultCfg.getNodeId()).isEqualTo(0);
    assertThat(defaultCfg.getPartitionsCount()).isEqualTo(1);
    assertThat(defaultCfg.getReplicationFactor()).isEqualTo(1);
    assertThat(defaultCfg.getClusterSize()).isEqualTo(1);
    assertThat(defaultCfg.getInitialContactPoints()).isEqualTo(List.of());
    assertThat(defaultCfg.getConfigManager()).isEqualTo(ConfigManagerCfg.defaultConfig());
  }

  @Test
  public void shouldOverrideAllClusterPropertiesViaEnvironment() {
    // given
    final var environment = new HashMap<String, String>();
    environment.put(ZEEBE_BROKER_CLUSTER_CLUSTER_SIZE, "3");
    environment.put(ZEEBE_BROKER_CLUSTER_PARTITIONS_COUNT, "2");
    environment.put(ZEEBE_BROKER_CLUSTER_REPLICATION_FACTOR, "3");
    environment.put(ZEEBE_BROKER_CLUSTER_NODE_ID, "2");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("cluster-cfg", environment);
    final ClusterCfg cfgCluster = cfg.getCluster();

    // then
    assertThat(cfgCluster.getClusterSize()).isEqualTo(3);
    assertThat(cfgCluster.getPartitionsCount()).isEqualTo(2);
    assertThat(cfgCluster.getReplicationFactor()).isEqualTo(3);
    assertThat(cfgCluster.getNodeId()).isEqualTo(2);
  }

  @Test
  public void shouldUseClusterNameFromEnvironment() {
    // given
    final var environment = new HashMap<String, String>();
    environment.put(ZEEBE_BROKER_CLUSTER_CLUSTER_NAME, "test-cluster");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("cluster-cfg", environment);
    final ClusterCfg cfgCluster = cfg.getCluster();

    // then
    assertThat(cfgCluster.getClusterName()).isEqualTo("test-cluster");
  }

  @Test
  public void shouldUseSpecifiedClusterName() {
    // given

    // when
    final BrokerCfg cfg =
        TestConfigReader.readConfig("specific-cluster-name", Collections.emptyMap());
    final ClusterCfg cfgCluster = cfg.getCluster();

    // then
    assertThat(cfgCluster.getClusterName()).isEqualTo("cluster-name");
  }

  @Test
  public void shouldUseNodeIdFromEnvironment() {
    // given
    final var environment = new HashMap<String, String>();
    environment.put(ZEEBE_BROKER_CLUSTER_NODE_ID, "2");
    // cluster size must be larger than node id
    environment.put(ZEEBE_BROKER_CLUSTER_CLUSTER_SIZE, "6");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("cluster-cfg", environment);
    final ClusterCfg cfgCluster = cfg.getCluster();

    // then
    assertThat(cfgCluster.getNodeId()).isEqualTo(2);
  }

  @Test
  public void shouldUseSpecifiedNodeId() {
    // given

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("specific-node-id", Collections.emptyMap());
    final ClusterCfg cfgCluster = cfg.getCluster();

    // then
    assertThat(cfgCluster.getNodeId()).isEqualTo(2);
  }

  @Test
  public void shouldUseNodeIdFromEnvironmentWithSpecifiedNodeIdInConfig() {
    // given
    final var environment = new HashMap<String, String>();
    environment.put(ZEEBE_BROKER_CLUSTER_NODE_ID, "2");
    // cluster size must be larger than node id
    environment.put(ZEEBE_BROKER_CLUSTER_CLUSTER_SIZE, "6");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("specific-node-id", environment);
    final ClusterCfg cfgCluster = cfg.getCluster();

    // then
    assertThat(cfgCluster.getNodeId()).isEqualTo(2);
  }

  @Test
  public void shouldUseSpecifiedConfigManagerCfg() {
    // given

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("config-manager", Collections.emptyMap());
    final ClusterCfg cfgCluster = cfg.getCluster();

    // then
    assertThat(cfgCluster.getConfigManager())
        .isEqualTo(
            new ConfigManagerCfg(
                new ClusterConfigurationGossiperConfig(
                    false, Duration.ofSeconds(10), Duration.ofSeconds(30), 10)));
  }

  @Test
  public void shouldUseConfigManagerCfgFromEnvironment() {
    // given
    final var environment = new HashMap<String, String>();
    environment.put(ZEEBE_BROKER_CLUSTER_CONFIG_MANAGER_GOSSIP_ENABLE_SYNC, "true");
    environment.put(ZEEBE_BROKER_CLUSTER_CONFIG_MANAGER_GOSSIP_SYNC_DELAY, "5s");
    // cluster size must be larger than node id
    environment.put(ZEEBE_BROKER_CLUSTER_CONFIG_MANAGER_GOSSIP_SYNC_REQUEST_TIMEOUT, "6s");
    environment.put(ZEEBE_BROKER_CLUSTER_CONFIG_MANAGER_GOSSIP_GOSSIP_FANOUT, "6");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("config-manager", environment);
    final ClusterCfg cfgCluster = cfg.getCluster();

    // then
    assertThat(cfgCluster.getConfigManager())
        .isEqualTo(
            new ConfigManagerCfg(
                new ClusterConfigurationGossiperConfig(
                    true, Duration.ofSeconds(5), Duration.ofSeconds(6), 6)));
  }

  @Test
  public void shouldSanitizeContactPoints() {
    // given
    final ClusterCfg sutClusterConfig = new ClusterCfg();
    final List<String> input = Arrays.asList("", "foo ", null, "   ", "bar");
    final List<String> expected = Arrays.asList("foo", "bar");

    // when
    sutClusterConfig.setInitialContactPoints(input);

    // then
    final List<String> actual = sutClusterConfig.getInitialContactPoints();

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void shouldGeneratePartitionIds() {
    // given
    final ClusterCfg clusterCfg = new ClusterCfg();
    clusterCfg.setPartitionsCount(8);

    // when
    clusterCfg.init(new BrokerCfg(), "");

    // then
    assertThat(clusterCfg.getPartitionIds()).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
  }

  @Test
  void shouldThrowExceptionIfNodeIdIsNegative() {
    // given
    final var environment = Collections.singletonMap("zeebe.broker.cluster.nodeId", "-1");

    // when - then
    assertThatCode(() -> TestConfigReader.readConfig("default", environment))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Node id must be positive");
  }

  @Test
  public void shouldThrowExceptionIfNodeIdIsInvalid() {
    // given
    final var environment = Collections.singletonMap(ZEEBE_BROKER_CLUSTER_NODE_ID, "a");

    // when - then
    assertThatCode(() -> TestConfigReader.readConfig("default", environment))
        .hasRootCauseInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("to int"); // spring related exception
  }

  @Test
  void shouldThrowExceptionIfReplicationFactorIsNegative() {
    // given
    final var environment =
        Collections.singletonMap("zeebe.broker.cluster.replicationFactor", "-1");

    // when - then
    assertThatCode(() -> TestConfigReader.readConfig("default", environment))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Replication factor -1 needs to be larger then zero and not larger then cluster size 1.");
  }

  @Test
  void shouldThrowExceptionIfReplicationFactorIsLargerThenClusterSize() {
    // given
    final var environment = Collections.singletonMap("zeebe.broker.cluster.replicationFactor", "2");

    // when - then
    assertThatCode(() -> TestConfigReader.readConfig("default", environment))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Replication factor 2 needs to be larger then zero and not larger then cluster size 1.");
  }

  @Test
  void shouldThrowExceptionIfPartitionsCountIsNegative() {
    // given
    final var environment = Collections.singletonMap("zeebe.broker.cluster.partitionsCount", "-1");

    // when - then
    assertThatCode(() -> TestConfigReader.readConfig("default", environment))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Partition count must not be smaller than 1.");
  }

  @Test
  void shouldThrowExceptionIfElectionTimeoutIsSmallerThanOneMs() {
    // given
    final var environment = Collections.singletonMap("zeebe.broker.cluster.electionTimeout", "0ms");

    // when - then
    assertThatCode(() -> TestConfigReader.readConfig("default", environment))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("electionTimeout PT0S must be at least 1ms");
  }

  @Test
  void shouldThrowExceptionIfElectionTimeoutIsSmallerThanHeartbeatInterval() {
    // given
    final var environment = Collections.singletonMap("zeebe.broker.cluster.electionTimeout", "1ms");

    // when - then
    assertThatCode(() -> TestConfigReader.readConfig("default", environment))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("electionTimeout PT0.001S must be greater than heartbeatInterval PT0.25S");
  }

  @Test
  void shouldThrowExceptionIfHeartbeatIntervalIsSmallerThanOneMs() {
    // given
    final var environment =
        Collections.singletonMap("zeebe.broker.cluster.heartbeatInterval", "0ms");

    // when - then
    assertThatCode(() -> TestConfigReader.readConfig("default", environment))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("heartbeatInterval PT0S must be at least 1ms");
  }

  @Test
  public void shouldOverrideReplicationFactorViaEnvironment() {
    // given
    final var environment = Collections.singletonMap(ZEEBE_BROKER_CLUSTER_REPLICATION_FACTOR, "2");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("cluster-cfg", environment);
    final ClusterCfg cfgCluster = cfg.getCluster();

    // then
    assertThat(cfgCluster.getReplicationFactor()).isEqualTo(2);
  }

  @Test
  public void shouldOverridePartitionsCountViaEnvironment() {
    // given
    final var environment = Collections.singletonMap(ZEEBE_BROKER_CLUSTER_PARTITIONS_COUNT, "2");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("cluster-cfg", environment);
    final ClusterCfg cfgCluster = cfg.getCluster();

    // then
    assertThat(cfgCluster.getPartitionsCount()).isEqualTo(2);
  }

  @Test
  public void shouldOverrideClusterSizeViaEnvironment() {
    // given
    final var environment = Collections.singletonMap(ZEEBE_BROKER_CLUSTER_CLUSTER_SIZE, "8");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("cluster-cfg", environment);
    final ClusterCfg cfgCluster = cfg.getCluster();

    // then
    assertThat(cfgCluster.getClusterSize()).isEqualTo(8);
  }

  @Test
  public void shouldUseDefaultContactPoints() {
    // given
    final List<String> defaultContactPoints = Collections.emptyList();

    // when
    final ClusterCfg emptyCfg = TestConfigReader.readConfig("empty", Map.of()).getCluster();
    final ClusterCfg defaultCfg = TestConfigReader.readConfig("default", Map.of()).getCluster();

    // then
    assertThat(emptyCfg.getInitialContactPoints()).containsExactlyElementsOf(defaultContactPoints);
    assertThat(defaultCfg.getInitialContactPoints())
        .containsExactlyElementsOf(defaultContactPoints);
  }

  @Test
  public void shouldUseSpecifiedContactPoints() {
    // given
    final List<String> expectedContactPoints = List.of("broker1", "broker2", "broker3");

    // when
    final ClusterCfg contactPointsCfg =
        TestConfigReader.readConfig("contact-points", Map.of()).getCluster();

    // then
    assertThat(contactPointsCfg.getInitialContactPoints())
        .containsExactlyElementsOf(expectedContactPoints);
  }

  @Test
  public void shouldUseContactPointsFromEnvironment() {
    // given
    final var environment =
        Collections.singletonMap(ZEEBE_BROKER_CLUSTER_INITIAL_CONTACT_POINTS, "foo,bar");

    // when
    final ClusterCfg emptyCfg = TestConfigReader.readConfig("empty", environment).getCluster();
    final ClusterCfg defaultCfg = TestConfigReader.readConfig("default", environment).getCluster();

    // then
    assertThat(emptyCfg.getInitialContactPoints()).containsExactlyElementsOf(List.of("foo", "bar"));
    assertThat(defaultCfg.getInitialContactPoints())
        .containsExactlyElementsOf(List.of("foo", "bar"));
  }

  @Test
  public void shouldUseContactPointsFromEnvironmentWithSpecifiedContactPoints() {
    // given
    final var environment =
        Collections.singletonMap(ZEEBE_BROKER_CLUSTER_INITIAL_CONTACT_POINTS, "1.1.1.1,2.2.2.2");

    // when
    final ClusterCfg contactPointsCfg =
        TestConfigReader.readConfig("contact-points", environment).getCluster();

    // then
    assertThat(contactPointsCfg.getInitialContactPoints())
        .containsExactlyElementsOf(List.of("1.1.1.1", "2.2.2.2"));
  }

  @Test
  public void shouldUseSingleContactPointFromEnvironment() {
    // given
    final var environment =
        Collections.singletonMap(ZEEBE_BROKER_CLUSTER_INITIAL_CONTACT_POINTS, "hello");

    // when
    final ClusterCfg contactPointsCfg =
        TestConfigReader.readConfig("contact-points", environment).getCluster();

    // then
    assertThat(contactPointsCfg.getInitialContactPoints())
        .containsExactlyElementsOf(List.of("hello"));
  }

  @Test
  public void shouldClearContactPointFromEnvironment() {
    // given
    final var environment =
        Collections.singletonMap(ZEEBE_BROKER_CLUSTER_INITIAL_CONTACT_POINTS, "");

    // when
    final ClusterCfg contactPointsCfg =
        TestConfigReader.readConfig("contact-points", environment).getCluster();

    // then
    assertThat(contactPointsCfg.getInitialContactPoints()).containsExactlyElementsOf(List.of());
  }
}
