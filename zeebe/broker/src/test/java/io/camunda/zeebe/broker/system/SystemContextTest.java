/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import io.atomix.cluster.AtomixCluster;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.UserServices;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ConfigManagerCfg;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg.BackupStoreType;
import io.camunda.zeebe.broker.system.configuration.partitioning.FixedPartitionCfg;
import io.camunda.zeebe.broker.system.configuration.partitioning.FixedPartitionCfg.NodeCfg;
import io.camunda.zeebe.broker.system.configuration.partitioning.Scheme;
import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossiperConfig;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.io.File;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;

final class SystemContextTest {

  @Test
  void shouldThrowExceptionIfSnapshotPeriodIsNegative() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getData().setSnapshotPeriod(Duration.ofMinutes(-1));

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Snapshot period PT-1M needs to be larger then or equals to one minute.");
  }

  @Test
  void shouldThrowExceptionIfSnapshotPeriodIsTooSmall() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getData().setSnapshotPeriod(Duration.ofSeconds(1));

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Snapshot period PT1S needs to be larger then or equals to one minute.");
  }

  @Test
  void shouldThrowExceptionIfBatchSizeIsNegative() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getExperimental().setMaxAppendBatchSize(DataSize.of(-1, DataUnit.BYTES));

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected to have an append batch size maximum which is non negative and smaller then '2147483647', but was '-1B'.");
  }

  @Test
  void shouldThrowExceptionIfBatchSizeIsTooLarge() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getExperimental().setMaxAppendBatchSize(DataSize.of(3, DataUnit.GIGABYTES));

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected to have an append batch size maximum which is non negative and smaller then '2147483647', but was '3221225472B'.");
  }

  @Test
  void shouldNotThrowExceptionIfSnapshotPeriodIsEqualToOneMinute() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getData().setSnapshotPeriod(Duration.ofMinutes(1));

    // when
    final var systemContext = initSystemContext(brokerCfg);

    // then
    assertThat(systemContext.getBrokerConfiguration().getData().getSnapshotPeriod())
        .isEqualTo(Duration.ofMinutes(1));
  }

  @Test
  void shouldThrowExceptionIfFixedPartitioningSchemeDoesNotSpecifyAnyPartitions() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    final var config = brokerCfg.getExperimental().getPartitioning();
    config.setScheme(Scheme.FIXED);
    config.setFixed(List.of());

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Expected fixed partition scheme to define configurations for all partitions such that "
                + "they have 1 replicas, but partition 1 has 0 configured replicas: []");
  }

  @Test
  void shouldThrowExceptionIfFixedPartitioningSchemeIsNotExhaustive() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    final var config = brokerCfg.getExperimental().getPartitioning();
    final var fixedPartition = new FixedPartitionCfg();
    config.setScheme(Scheme.FIXED);
    config.setFixed(List.of(fixedPartition));
    fixedPartition.getNodes().add(new NodeCfg());
    brokerCfg.getCluster().setPartitionsCount(2);

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Expected fixed partition scheme to define configurations for all partitions such that "
                + "they have 1 replicas, but partition 2 has 0 configured replicas: []");
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 2})
  void shouldThrowExceptionIfFixedPartitioningSchemeUsesInvalidPartitionId(final int invalidId) {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    final var config = brokerCfg.getExperimental().getPartitioning();
    final var fixedPartition = new FixedPartitionCfg();
    config.setScheme(Scheme.FIXED);
    config.setFixed(List.of(fixedPartition));
    fixedPartition.setPartitionId(invalidId);

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Expected fixed partition scheme to define entries with a valid partitionId "
                + "between 1 and 1, but %d was given",
            invalidId);
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 2})
  void shouldThrowExceptionIfFixedPartitioningSchemeUsesInvalidNodeId(final int invalidId) {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    final var config = brokerCfg.getExperimental().getPartitioning();
    final var fixedPartition = new FixedPartitionCfg();
    final var node = new NodeCfg();
    config.setScheme(Scheme.FIXED);
    config.setFixed(List.of(fixedPartition));
    fixedPartition.getNodes().add(node);
    node.setNodeId(invalidId);

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Expected fixed partition scheme for partition 1 to define nodes with a "
                + "nodeId between 0 and 0, but it was %d",
            invalidId);
  }

  @Test
  void shouldThrowExceptionIfFixedPartitioningSchemeHasSamePriorities() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    final var config = brokerCfg.getExperimental().getPartitioning();
    final var fixedPartition = new FixedPartitionCfg();
    final var nodes = List.of(new NodeCfg(), new NodeCfg());
    brokerCfg.getCluster().getRaft().setEnablePriorityElection(true);
    brokerCfg.getCluster().setClusterSize(2);
    config.setScheme(Scheme.FIXED);
    config.setFixed(List.of(fixedPartition));
    fixedPartition.setNodes(nodes);
    nodes.get(0).setNodeId(0);
    nodes.get(0).setPriority(1);
    nodes.get(1).setNodeId(1);
    nodes.get(1).setPriority(1);

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Expected each node for a partition 1 to have a different priority, but at least two of"
                + " them have the same priorities: [NodeCfg{nodeId=0, priority=1},"
                + " NodeCfg{nodeId=1, priority=1}]");
  }

  @Test
  void shouldNotThrowExceptionIfFixedPartitioningSchemeHasWrongPrioritiesWhenPriorityDisabled() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    final var config = brokerCfg.getExperimental().getPartitioning();
    final var fixedPartition = new FixedPartitionCfg();
    final var nodes = List.of(new NodeCfg(), new NodeCfg());
    brokerCfg.getCluster().getRaft().setEnablePriorityElection(false);
    brokerCfg.getCluster().setClusterSize(2);
    config.setScheme(Scheme.FIXED);
    config.setFixed(List.of(fixedPartition));
    fixedPartition.setNodes(nodes);
    nodes.get(0).setNodeId(0);
    nodes.get(0).setPriority(1);
    nodes.get(1).setNodeId(1);
    nodes.get(1).setPriority(1);

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg)).doesNotThrowAnyException();
  }

  @Test
  void shouldThrowExceptionWithNetworkSecurityEnabledAndWrongCert() throws CertificateException {
    // given
    final var certificate = new SelfSignedCertificate();
    final var brokerCfg = new BrokerCfg();
    brokerCfg
        .getNetwork()
        .getSecurity()
        .setEnabled(true)
        .setPrivateKeyPath(certificate.privateKey())
        .setCertificateChainPath(new File("/tmp/i-dont-exist.crt"));

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected the configured network security certificate chain path "
                + "'/tmp/i-dont-exist.crt' to point to a readable file, but it does not");
  }

  @Test
  void shouldThrowExceptionWithNetworkSecurityEnabledAndWrongKey() throws CertificateException {
    // given
    final var certificate = new SelfSignedCertificate();
    final var brokerCfg = new BrokerCfg();
    brokerCfg
        .getNetwork()
        .getSecurity()
        .setEnabled(true)
        .setPrivateKeyPath(new File("/tmp/i-dont-exist.key"))
        .setCertificateChainPath(certificate.certificate());

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected the configured network security private key path "
                + "'/tmp/i-dont-exist.key' to point to a readable file, but it does not");
  }

  @Test
  void shouldThrowExceptionWithNetworkSecurityEnabledAndNoPrivateKey() throws CertificateException {
    // given
    final var certificate = new SelfSignedCertificate();
    final var brokerCfg = new BrokerCfg();
    brokerCfg
        .getNetwork()
        .getSecurity()
        .setEnabled(true)
        .setPrivateKeyPath(null)
        .setCertificateChainPath(certificate.certificate());

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected to have a valid private key path for network security, but none configured");
  }

  @Test
  void shouldThrowExceptionWithNetworkSecurityEnabledAndNoCert() throws CertificateException {
    // given
    final var certificate = new SelfSignedCertificate();
    final var brokerCfg = new BrokerCfg();
    brokerCfg
        .getNetwork()
        .getSecurity()
        .setEnabled(true)
        .setPrivateKeyPath(certificate.privateKey())
        .setCertificateChainPath(null);

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected to have a valid certificate chain path for network security, but none "
                + "configured");
  }

  @Test
  void shouldThrowExceptionWhenS3BucketIsNotProvided() {
    // given
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getData().getBackup().setStore(BackupStoreType.S3);

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class)
        .cause()
        .hasMessageContaining("Bucket name must not be empty");
  }

  @Test
  void shouldThrowExceptionWhenS3IsNotConfigured() {
    // given
    final var brokerCfg = new BrokerCfg();
    final var backupCfg = brokerCfg.getData().getBackup();
    backupCfg.setStore(BackupStoreType.S3);
    backupCfg.getS3().setBucketName("bucket");

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("Failed configuring backup store S3");
  }

  @RegressionTest("https://github.com/camunda/camunda/issues/12678")
  void shouldThrowExceptionWithInvalidExporters() {
    // given
    final var brokerCfg = new BrokerCfg();
    final List<String> exportersNames = Arrays.asList("unknown", "oops", "nope");
    final Map<String, ExporterCfg> exporters = HashMap.newHashMap(exportersNames.size());

    for (final String exporterName : exportersNames) {
      final ExporterCfg exporterCfg = new ExporterCfg();
      exporterCfg.setClassName(null);
      exporterCfg.setJarPath("unknown".equals(exporterName) ? null : exporterName);
      final Map<String, Object> args = HashMap.newHashMap(1);
      args.put("any_arg", 1);
      exporterCfg.setArgs(args);
      exporters.put(exporterName, exporterCfg);
    }
    brokerCfg.setExporters(exporters);

    // then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith(
            "Expected to find a 'className' configured for the exporter. Couldn't find a valid one for the following exporters ");
  }

  private SystemContext initSystemContext(final BrokerCfg brokerCfg) {
    return new SystemContext(
        brokerCfg,
        mock(ActorScheduler.class),
        mock(AtomixCluster.class),
        mock(BrokerClient.class),
        new SecurityConfiguration(),
        mock(UserServices.class),
        mock(PasswordEncoder.class),
        null);
  }

  @Test
  void shouldThrowInvalidConfigExceptionWhenConfigManagerGossiperHasNegativeValues() {
    // given
    final var brokerCfg = new BrokerCfg();
    final var clusterCfg = brokerCfg.getCluster();

    final var invalidconfigManagerCfg =
        new ConfigManagerCfg(
            new ClusterConfigurationGossiperConfig(
                Duration.ofSeconds(10).negated(), Duration.ofSeconds(10).negated(), -1));
    clusterCfg.setConfigManager(invalidconfigManagerCfg);

    // when
    assertThatCode(() -> initSystemContext(brokerCfg))
        // then
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageStartingWith("Invalid ConfigManager configuration:")
        .hasMessageContaining(
            String.format("syncDelay must be positive: configured value = %d ms", -10000))
        .hasMessageContaining(
            String.format("syncRequestTimeout must be positive: configured value = %s ms", -10000))
        .hasMessageContaining(
            String.format("gossipFanout must be greater than 1: configured value = %d", -1));
  }

  @Test
  void shouldThrowInvalidConfigExceptionWhenConfigManagerGossiperHasZeroValues() {
    // given
    final var brokerCfg = new BrokerCfg();
    final var clusterCfg = brokerCfg.getCluster();

    final var invalidConfigManagerCfg =
        new ConfigManagerCfg(
            new ClusterConfigurationGossiperConfig(
                Duration.ofSeconds(0), Duration.ofSeconds(0), 0));
    clusterCfg.setConfigManager(invalidConfigManagerCfg);

    // when
    assertThatCode(() -> initSystemContext(brokerCfg))
        // then
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageStartingWith("Invalid ConfigManager configuration:")
        .hasMessageContaining(
            String.format("syncDelay must be positive: configured value = %d ms", 0))
        .hasMessageContaining(
            String.format("syncRequestTimeout must be positive: configured value = %s ms", 0))
        .hasMessageContaining(
            String.format("gossipFanout must be greater than 1: configured value = %d", 0));
  }

  @Test
  void shouldThrowInvalidConfigExceptionWhenConfigManagerHasGossipFanoutTooSmall() {
    // given
    final var brokerCfg = new BrokerCfg();
    final var clusterCfg = brokerCfg.getCluster();

    final var invalidDynamicConfig =
        new ConfigManagerCfg(
            new ClusterConfigurationGossiperConfig(
                Duration.ofSeconds(1), Duration.ofSeconds(1), 1));
    clusterCfg.setConfigManager(invalidDynamicConfig);

    // when
    assertThatCode(() -> initSystemContext(brokerCfg))
        // then
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageStartingWith("Invalid ConfigManager configuration:")
        .hasMessageContaining(
            String.format("gossipFanout must be greater than 1: configured value = %d", 1));
  }
}
