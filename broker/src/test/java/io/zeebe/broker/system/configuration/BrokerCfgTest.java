/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.configuration;

import static io.zeebe.broker.system.configuration.BrokerCfg.ENV_DEBUG_EXPORTER;
import static io.zeebe.broker.system.configuration.ClusterCfg.DEFAULT_CLUSTER_SIZE;
import static io.zeebe.broker.system.configuration.ClusterCfg.DEFAULT_CONTACT_POINTS;
import static io.zeebe.broker.system.configuration.ClusterCfg.DEFAULT_NODE_ID;
import static io.zeebe.broker.system.configuration.ClusterCfg.DEFAULT_PARTITIONS_COUNT;
import static io.zeebe.broker.system.configuration.ClusterCfg.DEFAULT_REPLICATION_FACTOR;
import static io.zeebe.broker.system.configuration.DataCfg.DEFAULT_DIRECTORY;
import static io.zeebe.broker.system.configuration.NetworkCfg.DEFAULT_COMMAND_API_PORT;
import static io.zeebe.broker.system.configuration.NetworkCfg.DEFAULT_HOST;
import static io.zeebe.broker.system.configuration.NetworkCfg.DEFAULT_INTERNAL_API_PORT;
import static io.zeebe.broker.system.configuration.NetworkCfg.DEFAULT_MONITORING_API_PORT;
import static io.zeebe.protocol.Protocol.START_PARTITION_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.broker.exporter.debug.DebugLogExporter;
import io.zeebe.broker.exporter.metrics.MetricsExporter;
import io.zeebe.broker.system.configuration.backpressure.BackpressureCfg;
import io.zeebe.broker.system.configuration.backpressure.BackpressureCfg.LimitAlgorithm;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.Condition;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.boot.context.properties.bind.BindException;

public final class BrokerCfgTest {

  public static final String BROKER_BASE = "test";

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

  private static final String ZEEBE_BROKER_DATA_DIRECTORIES = "zeebe.broker.data.directories";

  private static final String ZEEBE_BROKER_NETWORK_HOST = "zeebe.broker.network.host";
  private static final String ZEEBE_BROKER_NETWORK_ADVERTISED_HOST =
      "zeebe.broker.network.advertisedHost";
  private static final String ZEEBE_BROKER_NETWORK_PORT_OFFSET = "zeebe.broker.network.portOffset";
  private static final String ZEEBE_BROKER_EXECUTION_METRICS_EXPORTER_ENABLED =
      "zeebe.broker.executionMetricsExporterEnabled";

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  public final Map<String, String> environment = new HashMap<>();

  @Test
  public void shouldUseSpecifiedClusterName() {
    assertClusterName("specific-cluster-name", "cluster-name");
  }

  @Test
  public void shouldUseClusterNameFromEnvironment() {
    environment.put(ZEEBE_BROKER_CLUSTER_CLUSTER_NAME, "test-cluster");
    assertDefaultClusterName("test-cluster");
  }

  @Test
  public void shouldUseDefaultStepTimeout() {
    assertDefaultStepTimeout(Duration.ofMinutes(5));
  }

  @Test
  public void shouldUseStepTimeout() {
    assertStepTimeout("step-timeout-cfg", Duration.ofMinutes(2));
  }

  @Test
  public void shouldUseStepTimeoutFromEnv() {
    environment.put("zeebe.broker.stepTimeout", Duration.ofMinutes(1).toString());
    assertDefaultStepTimeout(Duration.ofMinutes(1));
  }

  @Test
  public void shouldUseSpecifiedNodeId() {
    assertNodeId("specific-node-id", 123);
  }

  @Test
  public void shouldUseNodeIdFromEnvironment() {
    environment.put(ZEEBE_BROKER_CLUSTER_NODE_ID, "42");
    assertDefaultNodeId(42);
  }

  @Test
  public void shouldUseNodeIdFromEnvironmentWithSpecifiedNodeId() {
    environment.put(ZEEBE_BROKER_CLUSTER_NODE_ID, "42");
    assertNodeId("specific-node-id", 42);
  }

  @Test
  public void shouldRejectInvalidNodeIdFromEnvironment() {
    // given
    environment.put(ZEEBE_BROKER_CLUSTER_NODE_ID, "a");

    // when + then
    Assertions.assertThatThrownBy(() -> assertDefaultNodeId(DEFAULT_NODE_ID))
        .isInstanceOf(BindException.class);
  }

  @Test
  public void shouldUseDefaultPorts() {
    assertDefaultPorts(
        DEFAULT_COMMAND_API_PORT, DEFAULT_INTERNAL_API_PORT, DEFAULT_MONITORING_API_PORT);
  }

  @Test
  public void shouldUseSpecifiedPorts() {
    assertPorts("specific-ports", 1, 5, 6);
  }

  @Test
  public void shouldUsePortOffset() {
    final int offset = 50;
    assertPorts(
        "port-offset",
        DEFAULT_COMMAND_API_PORT + offset,
        DEFAULT_INTERNAL_API_PORT + offset,
        DEFAULT_MONITORING_API_PORT + offset);
  }

  @Test
  public void shouldUsePortOffsetWithSpecifiedPorts() {
    final int offset = 30;
    assertPorts("specific-ports-offset", 1 + offset, 5 + offset, 6 + offset);
  }

  @Test
  public void shouldUsePortOffsetFromEnvironment() {
    environment.put(ZEEBE_BROKER_NETWORK_PORT_OFFSET, "5");
    final int offset = 50;
    assertDefaultPorts(
        DEFAULT_COMMAND_API_PORT + offset,
        DEFAULT_INTERNAL_API_PORT + offset,
        DEFAULT_MONITORING_API_PORT + offset);
  }

  @Test
  public void shouldUsePortOffsetFromEnvironmentWithSpecifiedPorts() {
    environment.put(ZEEBE_BROKER_NETWORK_PORT_OFFSET, "3");
    final int offset = 30;
    assertPorts("specific-ports", 1 + offset, 5 + offset, 6 + offset);
  }

  @Test
  public void shouldRejectInvalidPortOffsetFromEnvironment() {
    // given
    environment.put(ZEEBE_BROKER_NETWORK_PORT_OFFSET, "a");

    // when + then
    Assertions.assertThatThrownBy(
            () ->
                assertDefaultPorts(
                    DEFAULT_COMMAND_API_PORT,
                    DEFAULT_INTERNAL_API_PORT,
                    DEFAULT_MONITORING_API_PORT))
        .isInstanceOf(BindException.class);
  }

  @Test
  public void shouldOverridePortOffsetFromEnvironment() {
    environment.put(ZEEBE_BROKER_NETWORK_PORT_OFFSET, "7");
    final int offset = 70;
    assertPorts(
        "port-offset",
        DEFAULT_COMMAND_API_PORT + offset,
        DEFAULT_INTERNAL_API_PORT + offset,
        DEFAULT_MONITORING_API_PORT + offset);
  }

  @Test
  public void shouldExpandExporterJarPathRelativeToBrokerBaseIffPresent() {
    // given
    final ExporterCfg exporterCfgExternal = new ExporterCfg();
    exporterCfgExternal.setJarPath("exporters/exporter.jar");

    final ExporterCfg exporterCfgInternal1 = new ExporterCfg();
    exporterCfgInternal1.setJarPath("");

    final ExporterCfg exporterCfgInternal2 = new ExporterCfg();

    final BrokerCfg config = new BrokerCfg();
    config.getExporters().put("external", exporterCfgExternal);
    config.getExporters().put("internal-1", exporterCfgInternal1);
    config.getExporters().put("internal-2", exporterCfgInternal2);

    final String base = temporaryFolder.getRoot().getAbsolutePath();
    final String jarFile = Paths.get(base, "exporters", "exporter.jar").toAbsolutePath().toString();

    // when
    config.init(base);

    // then
    assertThat(config.getExporters()).hasSize(3);
    assertThat(config.getExporters().get("external"))
        .hasFieldOrPropertyWithValue("jarPath", jarFile)
        .is(new Condition<>(ExporterCfg::isExternal, "is external"));
    assertThat(config.getExporters().get("internal-1").isExternal()).isFalse();
    assertThat(config.getExporters().get("internal-2").isExternal()).isFalse();
  }

  @Test
  public void shouldEnableDebugLogExporter() {
    // given
    environment.put(ENV_DEBUG_EXPORTER, "true");

    // then
    assertDefaultDebugLogExporter(false);
  }

  @Test
  public void shouldEnableDebugLogExporterWithPrettyOption() {
    // given
    environment.put(ENV_DEBUG_EXPORTER, "pretty");

    // then
    assertDefaultDebugLogExporter(true);
  }

  @Test
  public void shouldEnableMetricsExporter() {
    // given
    environment.put(ZEEBE_BROKER_EXECUTION_METRICS_EXPORTER_ENABLED, "true");

    // then
    assertMetricsExporter();
  }

  @Test
  public void shouldUseDefaultHost() {
    assertDefaultHost(DEFAULT_HOST);
  }

  @Test
  public void shouldUseSpecifiedHosts() {
    assertHost(
        "specific-hosts",
        DEFAULT_HOST,
        "gatewayHost",
        "commandHost",
        "internalHost",
        "monitoringHost");
  }

  @Test
  public void shouldUseGlobalHost() {
    assertHost("host", "1.1.1.1");
  }

  @Test
  public void shouldUseHostFromEnvironment() {
    environment.put(ZEEBE_BROKER_NETWORK_HOST, "2.2.2.2");
    assertDefaultHost("2.2.2.2");
  }

  @Test
  public void shouldUseHostFromEnvironmentWithGlobalHost() {
    environment.put(ZEEBE_BROKER_NETWORK_HOST, "myHost");
    assertHost("host", "myHost");
  }

  @Test
  public void shouldNotOverrideSpecifiedHostsFromEnvironment() {
    environment.put(ZEEBE_BROKER_NETWORK_HOST, "myHost");
    assertHost(
        "specific-hosts", "myHost", "gatewayHost", "commandHost", "internalHost", "monitoringHost");
  }

  @Test
  public void shouldUseDefaultContactPoints() {
    assertDefaultContactPoints(DEFAULT_CONTACT_POINTS);
  }

  @Test
  public void shouldUseSpecifiedContactPoints() {
    assertContactPoints("contact-points", "broker1", "broker2", "broker3");
  }

  @Test
  public void shouldUseContactPointsFromEnvironment() {
    environment.put(ZEEBE_BROKER_CLUSTER_INITIAL_CONTACT_POINTS, "foo,bar");
    assertDefaultContactPoints("foo", "bar");
  }

  @Test
  public void shouldUseContactPointsFromEnvironmentWithSpecifiedContactPoints() {
    environment.put(ZEEBE_BROKER_CLUSTER_INITIAL_CONTACT_POINTS, "1.1.1.1,2.2.2.2");
    assertContactPoints("contact-points", "1.1.1.1", "2.2.2.2");
  }

  @Test
  public void shouldUseSingleContactPointFromEnvironment() {
    environment.put(ZEEBE_BROKER_CLUSTER_INITIAL_CONTACT_POINTS, "hello");
    assertContactPoints("contact-points", "hello");
  }

  @Test
  public void shouldClearContactPointFromEnvironment() {
    environment.put(ZEEBE_BROKER_CLUSTER_INITIAL_CONTACT_POINTS, "");
    assertContactPoints("contact-points");
  }

  @Test
  public void shouldUseDefaultDirectories() {
    assertDefaultDirectories(DEFAULT_DIRECTORY);
  }

  @Test
  public void shouldUseSpecifiedDirectories() {
    assertDirectories("directories", "data1", "data2", "data3");
  }

  @Test
  public void shouldUseDirectoriesFromEnvironment() {
    environment.put(ZEEBE_BROKER_DATA_DIRECTORIES, "foo,bar");
    assertDefaultDirectories("foo", "bar");
  }

  @Test
  public void shouldUseDirectoriesFromEnvironmentWithSpecifiedDirectories() {
    environment.put(ZEEBE_BROKER_DATA_DIRECTORIES, "foo,bar");
    assertDirectories("directories", "foo", "bar");
  }

  @Test
  public void shouldUseSingleDirectoryFromEnvironment() {
    environment.put(ZEEBE_BROKER_DATA_DIRECTORIES, "hello");
    assertDirectories("directories", "hello");
  }

  @Test
  public void shouldReadDefaultSystemClusterConfiguration() {
    assertDefaultSystemClusterConfiguration(
        DEFAULT_NODE_ID,
        DEFAULT_PARTITIONS_COUNT,
        DEFAULT_REPLICATION_FACTOR,
        DEFAULT_CLUSTER_SIZE,
        Collections.emptyList());
  }

  @Test
  public void shouldReadSpecificSystemClusterConfiguration() {
    // given
    final BrokerCfg cfg = TestConfigReader.readConfig("cluster-cfg", environment);
    final ClusterCfg cfgCluster = cfg.getCluster();

    // when - then
    assertThat(cfgCluster.getInitialContactPoints()).isEmpty();
    assertThat(cfgCluster.getNodeId()).isEqualTo(2);
    assertThat(cfgCluster.getPartitionsCount()).isEqualTo(3);
    assertThat(cfgCluster.getReplicationFactor()).isEqualTo(4);
    assertThat(cfgCluster.getClusterSize()).isEqualTo(5);
  }

  @Test
  public void shouldCreatePartitionIds() {
    // given
    final BrokerCfg cfg = TestConfigReader.readConfig("cluster-cfg", environment);
    final ClusterCfg cfgCluster = cfg.getCluster();

    // when - then
    assertThat(cfgCluster.getPartitionsCount()).isEqualTo(3);
    final List<Integer> partitionIds = cfgCluster.getPartitionIds();
    final int startId = START_PARTITION_ID;
    assertThat(partitionIds).contains(startId, startId + 1, startId + 2);
  }

  @Test
  public void shouldOverrideReplicationFactorViaEnvironment() {
    // given
    environment.put(ZEEBE_BROKER_CLUSTER_REPLICATION_FACTOR, "2");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("cluster-cfg", environment);
    final ClusterCfg cfgCluster = cfg.getCluster();

    // then
    assertThat(cfgCluster.getReplicationFactor()).isEqualTo(2);
  }

  @Test
  public void shouldOverridePartitionsCountViaEnvironment() {
    // given
    environment.put(ZEEBE_BROKER_CLUSTER_PARTITIONS_COUNT, "2");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("cluster-cfg", environment);
    final ClusterCfg cfgCluster = cfg.getCluster();

    // then
    assertThat(cfgCluster.getPartitionsCount()).isEqualTo(2);
  }

  @Test
  public void shouldOverrideClusterSizeViaEnvironment() {
    // given
    environment.put(ZEEBE_BROKER_CLUSTER_CLUSTER_SIZE, "2");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("cluster-cfg", environment);
    final ClusterCfg cfgCluster = cfg.getCluster();

    // then
    assertThat(cfgCluster.getClusterSize()).isEqualTo(2);
  }

  @Test
  public void shouldOverrideAllClusterPropertiesViaEnvironment() {
    // given
    environment.put(ZEEBE_BROKER_CLUSTER_CLUSTER_SIZE, "1");
    environment.put(ZEEBE_BROKER_CLUSTER_PARTITIONS_COUNT, "2");
    environment.put(ZEEBE_BROKER_CLUSTER_REPLICATION_FACTOR, "3");
    environment.put(ZEEBE_BROKER_CLUSTER_NODE_ID, "4");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("cluster-cfg", environment);
    final ClusterCfg cfgCluster = cfg.getCluster();

    // then
    assertThat(cfgCluster.getClusterSize()).isEqualTo(1);
    assertThat(cfgCluster.getPartitionsCount()).isEqualTo(2);
    assertThat(cfgCluster.getReplicationFactor()).isEqualTo(3);
    assertThat(cfgCluster.getNodeId()).isEqualTo(4);
  }

  @Test
  public void shouldReadDefaultEmbedGateway() {
    assertDefaultEmbeddedGatewayEnabled(true);
  }

  @Test
  public void shouldReadEmbedGateway() {
    assertEmbeddedGatewayEnabled("disabled-gateway", false);
  }

  @Test
  public void shouldSetEmbedGatewayViaEnvironment() {
    // given
    environment.put("zeebe.broker.gateway.enable", "true");
    // then
    assertEmbeddedGatewayEnabled("disabled-gateway", true);
  }

  @Test
  public void shouldSetBackpressureConfig() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("backpressure-cfg", environment);
    final BackpressureCfg backpressure = cfg.getBackpressure();

    // then
    assertThat(backpressure.isEnabled()).isTrue();
    assertThat(backpressure.useWindowed()).isFalse();
    assertThat(backpressure.getAlgorithm()).isEqualTo(LimitAlgorithm.GRADIENT);
  }

  @Test
  public void shouldUseConfiguredBackpressureAlgorithms() {

    final BackpressureCfg backpressure = new BackpressureCfg();

    // when
    backpressure.setAlgorithm("gradient");
    // then;
    assertThat(backpressure.getAlgorithm()).isEqualTo(LimitAlgorithm.GRADIENT);

    // when
    backpressure.setAlgorithm("gradient");
    // then;
    assertThat(backpressure.getAlgorithm()).isEqualTo(LimitAlgorithm.GRADIENT);

    // when
    backpressure.setAlgorithm("gradient2");
    // then;
    assertThat(backpressure.getAlgorithm()).isEqualTo(LimitAlgorithm.GRADIENT2);

    // when
    backpressure.setAlgorithm("vegas");
    // then;
    assertThat(backpressure.getAlgorithm()).isEqualTo(LimitAlgorithm.VEGAS);

    // when
    backpressure.setAlgorithm("fixed");
    // then;
    assertThat(backpressure.getAlgorithm()).isEqualTo(LimitAlgorithm.FIXED);

    // when
    backpressure.setAlgorithm("aimd");
    // then;
    assertThat(backpressure.getAlgorithm()).isEqualTo(LimitAlgorithm.AIMD);
  }

  @Test
  public void shouldUseDefaultAdvertisedHost() {
    // when - then
    assertAdvertisedAddress(
        "default-advertised-host-cfg", "zeebe.io", NetworkCfg.DEFAULT_COMMAND_API_PORT);
    assertHost("default-advertised-host-cfg", "0.0.0.0");
  }

  @Test
  public void shouldUseAdvertisedHost() {
    // when - then
    assertAdvertisedAddress("advertised-host-cfg", "zeebe.io", NetworkCfg.DEFAULT_COMMAND_API_PORT);
    assertHost("advertised-host-cfg", "0.0.0.0");
  }

  @Test
  public void shouldUseAdvertisedAddress() {
    // when - then
    assertAdvertisedAddress("advertised-address-cfg", "zeebe.io", 8080);
  }

  @Test
  public void shouldUseDefaultAdvertisedHostFromEnv() {
    // given
    environment.put(ZEEBE_BROKER_NETWORK_ADVERTISED_HOST, "zeebe.io");

    // then
    assertAdvertisedAddress("default", "zeebe.io", NetworkCfg.DEFAULT_COMMAND_API_PORT);
    assertAdvertisedAddress("empty", "zeebe.io", NetworkCfg.DEFAULT_COMMAND_API_PORT);
  }

  @Test
  public void shouldReadExporterConfigWithMinimalInfo() {
    // given
    final ExporterCfg expected = new ExporterCfg();
    expected.setClassName("io.zeebe.exporter.ElasticsearchExporter");

    final BrokerCfg actual = TestConfigReader.readConfig("exporters", environment);

    // then
    assertThat(actual.getExporters()).hasSize(1);
    assertThat(actual.getExporters()).containsKey("elasticsearch");
    assertThat(actual.getExporters().get("elasticsearch")).isEqualTo(expected);
  }

  @Test
  public void shouldUseMmap() {
    // given
    environment.put("zeebe.broker.data.useMmap", "true");

    // then
    assertUseMmap(true);
  }

  @Test
  public void shouldNotPrintConfidentialInformation() throws Exception {
    // given
    final var brokerCfg = TestConfigReader.readConfig("elasticexporter", environment);

    // when
    final var json = brokerCfg.toJson();

    // then
    final var objectMapper = new ObjectMapper();
    final var jsonNode = objectMapper.readTree(json);

    final var arguments =
        jsonNode.get("exporters").get("elasticsearch").get("args").get("authentication");

    assertThat(arguments.get("password").asText()).isEqualTo("***");
    assertThat(arguments.get("username").asText()).isEqualTo("***");
  }

  @Test
  public void shouldSetCustomMembershipConfig() {
    // when
    final BrokerCfg brokerCfg = TestConfigReader.readConfig("membership-cfg", environment);

    // then
    final var membershipCfg = brokerCfg.getCluster().getMembership();

    assertThat(membershipCfg.isBroadcastDisputes()).isFalse();
    assertThat(membershipCfg.isBroadcastUpdates()).isTrue();
    assertThat(membershipCfg.isNotifySuspect()).isTrue();
    assertThat(membershipCfg.getGossipInterval()).isEqualTo(Duration.ofSeconds(2));
    assertThat(membershipCfg.getGossipFanout()).isEqualTo(3);
    assertThat(membershipCfg.getProbeInterval()).isEqualTo(Duration.ofSeconds(3));
    assertThat(membershipCfg.getProbeTimeout()).isEqualTo(Duration.ofSeconds(5));
    assertThat(membershipCfg.getSuspectProbes()).isEqualTo(5);
    assertThat(membershipCfg.getFailureTimeout()).isEqualTo(Duration.ofSeconds(20));
    assertThat(membershipCfg.getSyncInterval()).isEqualTo(Duration.ofSeconds(25));
  }

  private void assertDefaultNodeId(final int nodeId) {
    assertNodeId("default", nodeId);
    assertNodeId("empty", nodeId);
  }

  private void assertNodeId(final String configFileName, final int nodeId) {
    final BrokerCfg cfg = TestConfigReader.readConfig(configFileName, environment);
    assertThat(cfg.getCluster().getNodeId()).isEqualTo(nodeId);
  }

  private void assertDefaultClusterName(final String clusterName) {
    assertClusterName("default", clusterName);
    assertClusterName("empty", clusterName);
  }

  private void assertClusterName(final String configFileName, final String clusterName) {
    final BrokerCfg cfg = TestConfigReader.readConfig(configFileName, environment);
    assertThat(cfg.getCluster().getClusterName()).isEqualTo(clusterName);
  }

  private void assertDefaultStepTimeout(final Duration stepTimeout) {
    assertStepTimeout("default", stepTimeout);
    assertStepTimeout("empty", stepTimeout);
  }

  private void assertStepTimeout(final String configFileName, final Duration stepTimeout) {
    final BrokerCfg cfg = TestConfigReader.readConfig(configFileName, environment);
    assertThat(cfg.getStepTimeout()).isEqualTo(stepTimeout);
  }

  private void assertDefaultPorts(final int command, final int internal, final int monitoring) {
    assertPorts("default", command, internal, monitoring);
    assertPorts("empty", command, internal, monitoring);
  }

  private void assertPorts(
      final String configFileName, final int command, final int internal, final int monitoring) {
    final BrokerCfg brokerCfg = TestConfigReader.readConfig(configFileName, environment);
    final NetworkCfg network = brokerCfg.getNetwork();
    assertThat(network.getCommandApi().getAddress().getPort()).isEqualTo(command);
    assertThat(network.getCommandApi().getAdvertisedAddress().getPort()).isEqualTo(command);
    assertThat(network.getInternalApi().getPort()).isEqualTo(internal);
    assertThat(network.getMonitoringApi().getPort()).isEqualTo(monitoring);
  }

  private void assertDefaultHost(final String host) {
    assertHost("default", host);
    assertHost("empty", host);
  }

  private void assertUseMmap(final boolean useMmap) {
    assertUseMmap("default", useMmap);
    assertUseMmap("empty", useMmap);
  }

  private void assertUseMmap(final String configFileName, final boolean useMmap) {
    final var config = TestConfigReader.readConfig(configFileName, environment);
    final var data = config.getData();
    assertThat(data.useMmap()).isEqualTo(useMmap);
  }

  private void assertHost(final String configFileName, final String host) {
    assertHost(configFileName, host, host, host, host, host);
  }

  private void assertHost(
      final String configFileName,
      final String host,
      final String gateway,
      final String command,
      final String internal,
      final String monitoring) {
    final BrokerCfg brokerCfg = TestConfigReader.readConfig(configFileName, environment);
    final NetworkCfg networkCfg = brokerCfg.getNetwork();
    assertThat(networkCfg.getHost()).isEqualTo(host);
    assertThat(brokerCfg.getGateway().getNetwork().getHost()).isEqualTo(gateway);
    assertThat(networkCfg.getCommandApi().getAddress().getHostString()).isEqualTo(command);
    assertThat(networkCfg.getInternalApi().getHost()).isEqualTo(internal);
    assertThat(networkCfg.getMonitoringApi().getHost()).isEqualTo(monitoring);
  }

  private void assertAdvertisedHost(final String configFileName, final String host) {
    final BrokerCfg brokerCfg = TestConfigReader.readConfig(configFileName, environment);
    final NetworkCfg networkCfg = brokerCfg.getNetwork();
    assertThat(networkCfg.getCommandApi().getAdvertisedAddress().getHostName()).isEqualTo(host);
  }

  private void assertAdvertisedAddress(
      final String configFileName, final String host, final int port) {
    final BrokerCfg brokerCfg = TestConfigReader.readConfig(configFileName, environment);
    final NetworkCfg networkCfg = brokerCfg.getNetwork();
    assertThat(networkCfg.getCommandApi().getAdvertisedAddress().getHostName()).isEqualTo(host);
    assertThat(networkCfg.getCommandApi().getAdvertisedAddress().getPort()).isEqualTo(port);
  }

  private void assertDefaultContactPoints(final String... contactPoints) {
    assertDefaultContactPoints(Arrays.asList(contactPoints));
  }

  private void assertDefaultContactPoints(final List<String> contactPoints) {
    assertContactPoints("default", contactPoints);
    assertContactPoints("empty", contactPoints);
  }

  private void assertContactPoints(final String configFileName, final String... contactPoints) {
    assertContactPoints(configFileName, Arrays.asList(contactPoints));
  }

  private void assertContactPoints(final String configFileName, final List<String> contactPoints) {
    final ClusterCfg cfg = TestConfigReader.readConfig(configFileName, environment).getCluster();
    assertThat(cfg.getInitialContactPoints()).containsExactlyElementsOf(contactPoints);
  }

  private void assertDefaultDirectories(final String... directories) {
    assertDirectories("default", directories);
    assertDirectories("empty", directories);
  }

  private void assertDirectories(final String configFileName, final String... directories) {
    assertDirectories(configFileName, Arrays.asList(directories));
  }

  private void assertDirectories(final String configFileName, final List<String> directories) {
    final DataCfg cfg = TestConfigReader.readConfig(configFileName, environment).getData();
    final List<String> expected =
        directories.stream()
            .map(d -> Paths.get(BROKER_BASE, d).toString())
            .collect(Collectors.toList());
    assertThat(cfg.getDirectories()).containsExactlyElementsOf(expected);
  }

  private void assertDefaultEmbeddedGatewayEnabled(final boolean enabled) {
    assertEmbeddedGatewayEnabled("default", enabled);
    assertEmbeddedGatewayEnabled("empty", enabled);
  }

  private void assertEmbeddedGatewayEnabled(final String configFileName, final boolean enabled) {
    final EmbeddedGatewayCfg gatewayCfg =
        TestConfigReader.readConfig(configFileName, environment).getGateway();
    assertThat(gatewayCfg.isEnable()).isEqualTo(enabled);
  }

  private void assertDefaultDebugLogExporter(final boolean prettyPrint) {
    assertDebugLogExporter("default", prettyPrint);
    assertDebugLogExporter("empty", prettyPrint);
  }

  private void assertDebugLogExporter(final String configFileName, final boolean prettyPrint) {
    final ExporterCfg exporterCfg = DebugLogExporter.defaultConfig(prettyPrint);
    final BrokerCfg brokerCfg = TestConfigReader.readConfig(configFileName, environment);

    assertThat(brokerCfg.getExporters().values())
        .usingRecursiveFieldByFieldElementComparator()
        .contains(exporterCfg);
  }

  private void assertMetricsExporter() {
    assertMetricsExporter("default");
    assertMetricsExporter("empty");
  }

  private void assertMetricsExporter(final String configFileName) {
    final ExporterCfg exporterCfg = MetricsExporter.defaultConfig();
    final BrokerCfg brokerCfg = TestConfigReader.readConfig(configFileName, environment);

    assertThat(brokerCfg.getExporters().values())
        .usingRecursiveFieldByFieldElementComparator()
        .contains(exporterCfg);
  }

  private void assertDefaultSystemClusterConfiguration(
      final int nodeId,
      final int partitionsCount,
      final int replicationFactor,
      final int clusterSize,
      final List<String> initialContactPoints) {
    assertSystemClusterConfiguration(
        "default", nodeId, partitionsCount, replicationFactor, clusterSize, initialContactPoints);
    assertSystemClusterConfiguration(
        "empty", nodeId, partitionsCount, replicationFactor, clusterSize, initialContactPoints);
  }

  private void assertSystemClusterConfiguration(
      final String configFileName,
      final int nodeId,
      final int partitionsCount,
      final int replicationFactor,
      final int clusterSize,
      final List<String> initialContactPoints) {
    final BrokerCfg cfg = TestConfigReader.readConfig(configFileName, environment);
    final ClusterCfg cfgCluster = cfg.getCluster();

    assertThat(cfgCluster.getNodeId()).isEqualTo(nodeId);
    assertThat(cfgCluster.getPartitionsCount()).isEqualTo(partitionsCount);
    assertThat(cfgCluster.getReplicationFactor()).isEqualTo(replicationFactor);
    assertThat(cfgCluster.getClusterSize()).isEqualTo(clusterSize);
    assertThat(cfgCluster.getInitialContactPoints()).isEqualTo(initialContactPoints);
  }
}
