/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import static io.camunda.zeebe.broker.system.configuration.BrokerCfg.ENV_DEBUG_EXPORTER;
import static io.camunda.zeebe.broker.system.configuration.DataCfg.DEFAULT_DIRECTORY;
import static io.camunda.zeebe.broker.system.configuration.NetworkCfg.DEFAULT_COMMAND_API_PORT;
import static io.camunda.zeebe.broker.system.configuration.NetworkCfg.DEFAULT_INTERNAL_API_PORT;
import static io.camunda.zeebe.protocol.Protocol.START_PARTITION_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.exporter.debug.DebugLogExporter;
import io.camunda.zeebe.broker.exporter.metrics.MetricsExporter;
import io.camunda.zeebe.broker.system.configuration.backpressure.LimitCfg;
import io.camunda.zeebe.broker.system.configuration.backpressure.LimitCfg.LimitAlgorithm;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.Condition;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.boot.context.properties.bind.BindException;

public final class BrokerCfgTest {

  public static final String BROKER_BASE = "test";

  private static final String ZEEBE_BROKER_EXPERIMENTAL_MAX_APPENDS_PER_FOLLOWER =
      "zeebe.broker.experimental.maxAppendsPerFollower";
  private static final String ZEEBE_BROKER_EXPERIMENTAL_MAX_APPEND_BATCH_SIZE =
      "zeebe.broker.experimental.maxAppendBatchSize";
  private static final String ZEEBE_BROKER_EXPERIMENTAL_DISABLEEXPLICITRAFTFLUSH =
      "zeebe.broker.experimental.disableExplicitRaftFlush";
  private static final String ZEEBE_BROKER_CLUSTER_RAFT_ENABLEPRIORITYELECTION =
      "zeebe.broker.cluster.raft.enablePriorityElection";
  private static final String ZEEBE_BROKER_EXPERIMENTAL_QUERYAPI_ENABLED =
      "zeebe.broker.experimental.queryapi.enabled";
  private static final String ZEEBE_BROKER_DATA_DIRECTORY = "zeebe.broker.data.directory";

  private static final String ZEEBE_BROKER_NETWORK_HOST = "zeebe.broker.network.host";
  private static final String ZEEBE_BROKER_NETWORK_ADVERTISED_HOST =
      "zeebe.broker.network.advertisedHost";
  private static final String ZEEBE_BROKER_NETWORK_PORT_OFFSET = "zeebe.broker.network.portOffset";
  private static final String ZEEBE_BROKER_NETWORK_SOCKET_SEND_BUFFER =
      "zeebe.broker.network.socketSendBuffer";
  private static final String ZEEBE_BROKER_NETWORK_SOCKET_RECEIVE_BUFFER =
      "zeebe.broker.network.socketReceiveBuffer";
  private static final String ZEEBE_BROKER_EXECUTION_METRICS_EXPORTER_ENABLED =
      "zeebe.broker.executionMetricsExporterEnabled";

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  public final Map<String, String> environment = new HashMap<>();

  @Test
  public void shouldUseDefaultPorts() {
    assertDefaultPorts(DEFAULT_COMMAND_API_PORT, DEFAULT_INTERNAL_API_PORT);
  }

  @Test
  public void shouldUseSpecifiedPorts() {
    assertPorts("specific-ports", 1, 5);
  }

  @Test
  public void shouldUsePortOffset() {
    final int offset = 50;
    assertPorts(
        "port-offset", DEFAULT_COMMAND_API_PORT + offset, DEFAULT_INTERNAL_API_PORT + offset);
  }

  @Test
  public void shouldUsePortOffsetWithSpecifiedPorts() {
    final int offset = 30;
    assertPorts("specific-ports-offset", 1 + offset, 5 + offset);
  }

  @Test
  public void shouldUsePortOffsetFromEnvironment() {
    environment.put(ZEEBE_BROKER_NETWORK_PORT_OFFSET, "5");
    final int offset = 50;
    assertDefaultPorts(DEFAULT_COMMAND_API_PORT + offset, DEFAULT_INTERNAL_API_PORT + offset);
  }

  @Test
  public void shouldUsePortOffsetFromEnvironmentWithSpecifiedPorts() {
    environment.put(ZEEBE_BROKER_NETWORK_PORT_OFFSET, "3");
    final int offset = 30;
    assertPorts("specific-ports", 1 + offset, 5 + offset);
  }

  @Test
  public void shouldRejectInvalidPortOffsetFromEnvironment() {
    // given
    environment.put(ZEEBE_BROKER_NETWORK_PORT_OFFSET, "a");

    // when + then
    Assertions.assertThatThrownBy(
            () -> assertDefaultPorts(DEFAULT_COMMAND_API_PORT, DEFAULT_INTERNAL_API_PORT))
        .isInstanceOf(BindException.class);
  }

  @Test
  public void shouldOverridePortOffsetFromEnvironment() {
    environment.put(ZEEBE_BROKER_NETWORK_PORT_OFFSET, "7");
    final int offset = 70;
    assertPorts(
        "port-offset", DEFAULT_COMMAND_API_PORT + offset, DEFAULT_INTERNAL_API_PORT + offset);
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
    final var expectedId = DebugLogExporter.defaultExporterId();
    final var expectedConfig = DebugLogExporter.defaultConfig();
    environment.put(ENV_DEBUG_EXPORTER, "true");

    // then
    assertWithDefaultConfigurations(
        cfg -> assertThat(cfg.getExporters()).containsEntry(expectedId, expectedConfig));
  }

  @Test
  public void shouldNotRegisterDebugLogExporter() {
    // given
    environment.put(ENV_DEBUG_EXPORTER, "false");

    // when
    final String exporterId = DebugLogExporter.defaultExporterId();
    final BrokerCfg brokerCfg = TestConfigReader.readConfig("empty", environment);

    // then
    assertThat(brokerCfg.getExporters()).doesNotContainKey(exporterId);
  }

  @Test
  public void shouldHaveNoExportersByDefault() {
    assertWithDefaultConfigurations(cfg -> assertThat(cfg.getExporters()).isEmpty());
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
    assertDefaultHost("0.0.0.0");
  }

  @Test
  public void shouldUseSpecifiedHosts() {
    assertHost(
        "specific-hosts",
        "0.0.0.0",
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
  public void shouldUseDefaultDirectory() {
    // given
    final String expectedDataDirectory = Paths.get(BROKER_BASE, DEFAULT_DIRECTORY).toString();

    // then
    assertWithDefaultConfigurations(
        config -> assertThat(config.getData().getDirectory()).isEqualTo(expectedDataDirectory));
  }

  @Test
  public void shouldUseSpecifiedDirectory() {
    // given
    final BrokerCfg config = TestConfigReader.readConfig("directory", environment);
    final String expectedDataDirectory = Paths.get(BROKER_BASE, "foo").toString();

    // then
    assertThat(config.getData().getDirectory()).isEqualTo(expectedDataDirectory);
  }

  @Test
  public void shouldUseDirectoryFromEnvironment() {
    // given
    final String expectedDataDirectory = Paths.get(BROKER_BASE, "foo").toString();
    environment.put(ZEEBE_BROKER_DATA_DIRECTORY, "foo");

    // then
    assertWithDefaultConfigurations(
        config -> assertThat(config.getData().getDirectory()).isEqualTo(expectedDataDirectory));
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
  public void shouldOverrideMaxAppendsViaEnvironment() {
    // given
    environment.put(ZEEBE_BROKER_EXPERIMENTAL_MAX_APPENDS_PER_FOLLOWER, "8");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("cluster-cfg", environment);
    final ExperimentalCfg experimentalCfg = cfg.getExperimental();

    // then
    assertThat(experimentalCfg.getMaxAppendsPerFollower()).isEqualTo(8);
  }

  @Test
  public void shouldOverrideUsageMetricsViaEnvironment() {
    // given
    environment.put("zeebe.broker.experimental.engine.usageMetrics.exportInterval", "1s");

    // when
    final var cfg = TestConfigReader.readConfig("cluster-cfg", environment);
    final var experimentalCfg = cfg.getExperimental();
    final var engine = experimentalCfg.getEngine();
    final var usageMetrics = engine.getUsageMetrics();

    // then
    assertThat(usageMetrics.getExportInterval()).isEqualTo(Duration.ofSeconds(1));
  }

  @Test
  public void shouldOverrideMaxAppendBatchSizeViaEnvironment() {
    // given
    environment.put(ZEEBE_BROKER_EXPERIMENTAL_MAX_APPEND_BATCH_SIZE, "256KB");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("cluster-cfg", environment);
    final ExperimentalCfg experimentalCfg = cfg.getExperimental();

    // then
    assertThat(experimentalCfg.getMaxAppendBatchSizeInBytes()).isEqualTo(256 * 1024);
  }

  @Test
  public void shouldOverrideDisableExplicitRaftFlushViaEnvironment() {
    // given
    environment.put(ZEEBE_BROKER_EXPERIMENTAL_DISABLEEXPLICITRAFTFLUSH, "true");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("cluster-cfg", environment);
    final ExperimentalCfg experimentalCfg = cfg.getExperimental();

    // then
    assertThat(experimentalCfg.isDisableExplicitRaftFlush()).isTrue();
  }

  @Test
  public void shouldOverrideEnablePriorityElectionViaEnvironment() {
    // given
    environment.put(ZEEBE_BROKER_CLUSTER_RAFT_ENABLEPRIORITYELECTION, "true");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("cluster-cfg", environment);
    final ClusterCfg clusterCfg = cfg.getCluster();

    // then
    assertThat(clusterCfg.getRaft().isEnablePriorityElection()).isTrue();
  }

  @Test
  public void shouldEnablePriorityElectionByDefault() {
    // given
    final BrokerCfg cfg = TestConfigReader.readConfig("cluster-cfg", environment);

    // when
    final ClusterCfg clusterCfg = cfg.getCluster();

    // then
    assertThat(clusterCfg.getRaft().isEnablePriorityElection()).isTrue();
  }

  @Test
  public void shouldSetEnablePriorityElectionFromConfig() {
    // given
    final BrokerCfg cfg = TestConfigReader.readConfig("experimental-cfg", environment);

    // when
    final ClusterCfg clusterCfg = cfg.getCluster();

    // then
    assertThat(clusterCfg.getRaft().isEnablePriorityElection()).isTrue();
  }

  @Test
  public void shouldDisableQueryApiByDefault() {
    // given
    final BrokerCfg cfg = TestConfigReader.readConfig("cluster-cfg", environment);

    // when
    final ExperimentalCfg experimentalCfg = cfg.getExperimental();

    // then
    assertThat(experimentalCfg.getQueryApi().isEnabled()).isFalse();
  }

  @Test
  public void shouldSetEnableQueryApiFromConfig() {
    // given
    final BrokerCfg cfg = TestConfigReader.readConfig("experimental-cfg", environment);

    // when
    final ExperimentalCfg experimentalCfg = cfg.getExperimental();

    // then
    assertThat(experimentalCfg.getQueryApi().isEnabled()).isTrue();
  }

  @Test
  public void shouldOverrideSetEnableQueryApiViaEnvironment() {
    // given
    environment.put(ZEEBE_BROKER_EXPERIMENTAL_QUERYAPI_ENABLED, "true");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("cluster-cfg", environment);
    final ExperimentalCfg experimentalCfg = cfg.getExperimental();

    // then
    assertThat(experimentalCfg.getQueryApi().isEnabled()).isTrue();
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
    final LimitCfg backpressure = cfg.getBackpressure();

    // then
    assertThat(backpressure.isEnabled()).isTrue();
    assertThat(backpressure.useWindowed()).isFalse();
    assertThat(backpressure.getAlgorithm()).isEqualTo(LimitAlgorithm.GRADIENT);
  }

  @Test
  public void shouldUseConfiguredBackpressureAlgorithms() {

    final LimitCfg backpressure = new LimitCfg();

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
    expected.setClassName("io.camunda.zeebe.exporter.ElasticsearchExporter");

    final BrokerCfg actual = TestConfigReader.readConfig("exporters", environment);

    // then
    assertThat(actual.getExporters())
        .hasSize(1)
        .containsKey("elasticsearch")
        .containsEntry("elasticsearch", expected);
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

  @Test
  public void shouldUseSocketSendBufferFromEnvironment() {
    // given no value is defined

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("cluster-cfg", environment);

    // then
    assertThat(cfg.getNetwork().getSocketSendBuffer()).isNull();
  }

  @Test
  public void shouldOverrideSpecifiedSocketSendBufferFromEnvironment() {
    // given
    environment.put(ZEEBE_BROKER_NETWORK_SOCKET_SEND_BUFFER, "2048KB");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("cluster-cfg", environment);

    // then
    assertThat(cfg.getNetwork().getSocketSendBuffer().toBytes()).isEqualTo(2097152);
  }

  @Test
  public void shouldUseDefaultSocketReceiveBufferFromEnvironment() {
    // given no value is defined

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("cluster-cfg", environment);

    // then
    assertThat(cfg.getNetwork().getSocketReceiveBuffer()).isNull();
  }

  @Test
  public void shouldOverrideSpecifiedSocketReceiveBufferFromEnvironment() {
    // given
    environment.put(ZEEBE_BROKER_NETWORK_SOCKET_RECEIVE_BUFFER, "2MB");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("cluster-cfg", environment);

    // then
    assertThat(cfg.getNetwork().getSocketReceiveBuffer().toKilobytes()).isEqualTo(2048);
  }

  private void assertDefaultPorts(final int command, final int internal) {
    assertPorts("default", command, internal);
    assertPorts("empty", command, internal);
  }

  private void assertPorts(final String configFileName, final int command, final int internal) {
    final BrokerCfg brokerCfg = TestConfigReader.readConfig(configFileName, environment);
    final NetworkCfg network = brokerCfg.getNetwork();
    assertThat(network.getCommandApi().getAddress().getPort()).isEqualTo(command);
    assertThat(network.getCommandApi().getAdvertisedAddress().getPort()).isEqualTo(command);
    assertThat(network.getInternalApi().getPort()).isEqualTo(internal);
  }

  private void assertDefaultHost(final String host) {
    assertHost("default", host);
    assertHost("empty", host);
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

  private void assertDefaultEmbeddedGatewayEnabled(final boolean enabled) {
    assertEmbeddedGatewayEnabled("default", enabled);
    assertEmbeddedGatewayEnabled("empty", enabled);
  }

  private void assertEmbeddedGatewayEnabled(final String configFileName, final boolean enabled) {
    final EmbeddedGatewayCfg gatewayCfg =
        TestConfigReader.readConfig(configFileName, environment).getGateway();
    assertThat(gatewayCfg.isEnable()).isEqualTo(enabled);
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

  private void assertWithDefaultConfigurations(final Consumer<BrokerCfg> assertions) {
    Stream.of("default", "empty")
        .forEach(configFileName -> assertWithConfiguration(assertions, configFileName));
  }

  private void assertWithConfiguration(
      final Consumer<BrokerCfg> assertions, final String configFileName) {
    final BrokerCfg cfg = TestConfigReader.readConfig(configFileName, environment);
    assertions.accept(cfg);
  }
}
