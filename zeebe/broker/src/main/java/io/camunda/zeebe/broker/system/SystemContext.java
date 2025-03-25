/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system;

import static io.camunda.zeebe.broker.system.partitions.impl.AsyncSnapshotDirector.MINIMUM_SNAPSHOT_PERIOD;

import io.atomix.cluster.AtomixCluster;
import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.search.clients.SecondaryDbQueryService;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.UserServices;
import io.camunda.zeebe.backup.azure.AzureBackupStore;
import io.camunda.zeebe.backup.filesystem.FilesystemBackupStore;
import io.camunda.zeebe.backup.gcs.GcsBackupStore;
import io.camunda.zeebe.backup.s3.S3BackupStore;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ClusterCfg;
import io.camunda.zeebe.broker.system.configuration.DataCfg;
import io.camunda.zeebe.broker.system.configuration.DiskCfg.FreeSpaceCfg;
import io.camunda.zeebe.broker.system.configuration.ExperimentalCfg;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.broker.system.configuration.SecurityCfg;
import io.camunda.zeebe.broker.system.configuration.backup.AzureBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg;
import io.camunda.zeebe.broker.system.configuration.backup.FilesystemBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.GcsBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.S3BackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.partitioning.FixedPartitionCfg;
import io.camunda.zeebe.broker.system.configuration.partitioning.Scheme;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.util.TlsConfigUtil;
import io.camunda.zeebe.util.VisibleForTesting;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.slf4j.Logger;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

public final class SystemContext {

  public static final Logger LOG = Loggers.SYSTEM_LOGGER;
  public static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);
  private static final String BROKER_ID_LOG_PROPERTY = "broker-id";
  private static final String SNAPSHOT_PERIOD_ERROR_MSG =
      "Snapshot period %s needs to be larger then or equals to one minute.";
  private static final String MAX_BATCH_SIZE_ERROR_MSG =
      "Expected to have an append batch size maximum which is non negative and smaller then '%d', but was '%s'.";

  private final Duration shutdownTimeout;
  private final BrokerCfg brokerCfg;
  private final IdentityConfiguration identityConfiguration;
  private Map<String, String> diagnosticContext;
  private final ActorScheduler scheduler;
  private final AtomixCluster cluster;
  private final BrokerClient brokerClient;
  private final MeterRegistry meterRegistry;
  private final SecurityConfiguration securityConfiguration;
  private final UserServices userServices;
  private final PasswordEncoder passwordEncoder;
  private final JwtDecoder jwtDecoder;
  private final SecondaryDbQueryService secondaryDbQueryService;

  public SystemContext(
      final Duration shutdownTimeout,
      final BrokerCfg brokerCfg,
      final IdentityConfiguration identityConfiguration,
      final ActorScheduler scheduler,
      final AtomixCluster cluster,
      final BrokerClient brokerClient,
      final MeterRegistry meterRegistry,
      final SecurityConfiguration securityConfiguration,
      final UserServices userServices,
      final PasswordEncoder passwordEncoder,
      final JwtDecoder jwtDecoder,
      final SecondaryDbQueryService secondaryDbQueryService) {
    this.shutdownTimeout = shutdownTimeout;
    this.brokerCfg = brokerCfg;
    this.identityConfiguration = identityConfiguration;
    this.scheduler = scheduler;
    this.cluster = cluster;
    this.brokerClient = brokerClient;
    this.meterRegistry = meterRegistry;
    this.securityConfiguration = securityConfiguration;
    this.userServices = userServices;
    this.passwordEncoder = passwordEncoder;
    this.jwtDecoder = jwtDecoder;
    this.secondaryDbQueryService = secondaryDbQueryService;
    initSystemContext();
  }

  @VisibleForTesting
  public SystemContext(
      final BrokerCfg brokerCfg,
      final ActorScheduler scheduler,
      final AtomixCluster cluster,
      final BrokerClient brokerClient,
      final SecurityConfiguration securityConfiguration,
      final UserServices userServices,
      final PasswordEncoder passwordEncoder,
      final JwtDecoder jwtDecoder) {
    this(
        DEFAULT_SHUTDOWN_TIMEOUT,
        brokerCfg,
        null,
        scheduler,
        cluster,
        brokerClient,
        new SimpleMeterRegistry(),
        securityConfiguration,
        userServices,
        passwordEncoder,
        jwtDecoder,
        null);
  }

  private void initSystemContext() {
    validateConfiguration();

    final var cluster = brokerCfg.getCluster();
    final String brokerId = String.format("Broker-%d", cluster.getNodeId());

    diagnosticContext = Collections.singletonMap(BROKER_ID_LOG_PROPERTY, brokerId);
  }

  private void validateConfiguration() {
    final ClusterCfg cluster = brokerCfg.getCluster();

    validateDataConfig(brokerCfg.getData());

    validClusterConfigs(cluster);
    validateExperimentalConfigs(cluster, brokerCfg.getExperimental());

    validateExporters(brokerCfg.getExporters());

    final var security = brokerCfg.getNetwork().getSecurity();
    if (security.isEnabled()) {
      validateNetworkSecurityConfig(security);
    }
  }

  private void validClusterConfigs(final ClusterCfg cluster) {
    final var gossiper = cluster.getConfigManager().gossip();

    final var errors = new ArrayList<String>(0);

    if (!gossiper.syncDelay().isPositive()) {
      errors.add(
          String.format(
              "syncDelay must be positive: configured value = %d ms",
              gossiper.syncDelay().toMillis()));
    }
    if (!gossiper.syncRequestTimeout().isPositive()) {
      errors.add(
          String.format(
              "syncRequestTimeout must be positive: configured value = %d ms",
              gossiper.syncRequestTimeout().toMillis()));
    }
    if (gossiper.gossipFanout() < 2) {
      errors.add(
          String.format(
              "gossipFanout must be greater than 1: configured value = %d",
              gossiper.gossipFanout()));
    }

    if (!errors.isEmpty()) {
      throw new InvalidConfigurationException(
          "Invalid ConfigManager configuration: " + String.join(", ", errors), null);
    }
  }

  private void validateExporters(final Map<String, ExporterCfg> exporters) {
    final Set<Entry<String, ExporterCfg>> entries = exporters.entrySet();
    final var badExportersNames =
        entries.stream()
            .filter(entry -> entry.getValue().getClassName() == null)
            .map(Entry::getKey)
            .toList();

    if (!badExportersNames.isEmpty()) {
      throw new IllegalArgumentException(
          "Expected to find a 'className' configured for the exporter. Couldn't find a valid one for the following exporters "
              + badExportersNames);
    }
  }

  private void validateExperimentalConfigs(
      final ClusterCfg cluster, final ExperimentalCfg experimental) {
    final var maxAppendBatchSize = experimental.getMaxAppendBatchSize();
    if (maxAppendBatchSize.isNegative() || maxAppendBatchSize.toBytes() >= Integer.MAX_VALUE) {
      throw new IllegalArgumentException(
          String.format(MAX_BATCH_SIZE_ERROR_MSG, Integer.MAX_VALUE, maxAppendBatchSize));
    }

    final var partitioningConfig = experimental.getPartitioning();
    if (partitioningConfig.getScheme() == Scheme.FIXED) {
      validateFixedPartitioningScheme(cluster, experimental);
    }
  }

  private void validateDataConfig(final DataCfg dataCfg) {
    final var snapshotPeriod = dataCfg.getSnapshotPeriod();
    if (snapshotPeriod.isNegative() || snapshotPeriod.minus(MINIMUM_SNAPSHOT_PERIOD).isNegative()) {
      throw new IllegalArgumentException(String.format(SNAPSHOT_PERIOD_ERROR_MSG, snapshotPeriod));
    }

    if (dataCfg.getDisk().isEnableMonitoring()) {
      try {
        final FreeSpaceCfg freeSpaceCfg = dataCfg.getDisk().getFreeSpace();
        final var processingFreeSpace = freeSpaceCfg.getProcessing().toBytes();
        final var replicationFreeSpace = freeSpaceCfg.getReplication().toBytes();
        if (processingFreeSpace <= replicationFreeSpace) {
          throw new IllegalArgumentException(
              "Minimum free space for processing (%d) must be greater than minimum free space for replication (%d). Configured values are %s"
                  .formatted(processingFreeSpace, replicationFreeSpace, freeSpaceCfg));
        }
      } catch (final Exception e) {
        throw new InvalidConfigurationException("Failed to parse disk monitoring configuration", e);
      }
    }

    validateBackupCfg(dataCfg.getBackup());
  }

  private void validateBackupCfg(final BackupStoreCfg backup) {
    try {
      switch (backup.getStore()) {
        case NONE -> LOG.warn("No backup store is configured. Backups will not be taken");
        case S3 -> S3BackupStore.validateConfig(S3BackupStoreConfig.toStoreConfig(backup.getS3()));
        case GCS ->
            GcsBackupStore.validateConfig(GcsBackupStoreConfig.toStoreConfig(backup.getGcs()));
        case AZURE ->
            AzureBackupStore.validateConfig(
                AzureBackupStoreConfig.toStoreConfig(backup.getAzure()));
        case FILESYSTEM ->
            FilesystemBackupStore.validateConfig(
                FilesystemBackupStoreConfig.toStoreConfig(backup.getFilesystem()));
        default ->
            throw new UnsupportedOperationException(
                "Does not support validating configuration of backup store %s"
                    .formatted(backup.getStore()));
      }
    } catch (final Exception e) {
      throw new InvalidConfigurationException(
          "Failed configuring backup store %s".formatted(backup.getStore()), e);
    }
  }

  private void validateFixedPartitioningScheme(
      final ClusterCfg cluster, final ExperimentalCfg experimental) {
    final var partitioning = experimental.getPartitioning();
    final var partitions = partitioning.getFixed();
    final var replicationFactor = cluster.getReplicationFactor();
    final var partitionsCount = cluster.getPartitionsCount();

    final var partitionMembers = new HashMap<Integer, Set<Integer>>();
    for (final var partition : partitions) {
      final var members =
          validateFixedPartitionMembers(
              cluster, partition, cluster.getRaft().isEnablePriorityElection());
      partitionMembers.put(partition.getPartitionId(), members);
    }

    for (int partitionId = 1; partitionId <= partitionsCount; partitionId++) {
      final var members = partitionMembers.getOrDefault(partitionId, Collections.emptySet());
      if (members.size() < replicationFactor) {
        throw new IllegalArgumentException(
            String.format(
                "Expected fixed partition scheme to define configurations for all partitions such "
                    + "that they have %d replicas, but partition %d has %d configured replicas: %s",
                replicationFactor, partitionId, members.size(), members));
      }
    }
  }

  private Set<Integer> validateFixedPartitionMembers(
      final ClusterCfg cluster,
      final FixedPartitionCfg partitionConfig,
      final boolean isPriorityElectionEnabled) {
    final var members = new HashSet<Integer>();
    final var clusterSize = cluster.getClusterSize();
    final var partitionsCount = cluster.getPartitionsCount();
    final var partitionId = partitionConfig.getPartitionId();

    if (partitionId < 1 || partitionId > partitionsCount) {
      throw new IllegalArgumentException(
          String.format(
              "Expected fixed partition scheme to define entries with a valid partitionId between 1"
                  + " and %d, but %d was given",
              partitionsCount, partitionId));
    }

    final var observedPriorities = new HashSet<Integer>();
    for (final var node : partitionConfig.getNodes()) {
      final var nodeId = node.getNodeId();
      if (nodeId < 0 || nodeId >= clusterSize) {
        throw new IllegalArgumentException(
            String.format(
                "Expected fixed partition scheme for partition %d to define nodes with a nodeId "
                    + "between 0 and %d, but it was %d",
                partitionId, clusterSize - 1, nodeId));
      }

      if (isPriorityElectionEnabled && !observedPriorities.add(node.getPriority())) {
        throw new IllegalArgumentException(
            String.format(
                "Expected each node for a partition %d to have a different priority, but at least "
                    + "two of them have the same priorities: %s",
                partitionId, partitionConfig.getNodes()));
      }

      members.add(nodeId);
    }

    return members;
  }

  private void validateNetworkSecurityConfig(final SecurityCfg security) {
    TlsConfigUtil.validateTlsConfig(
        security.getCertificateChainPath(),
        security.getPrivateKeyPath(),
        security.getKeyStore().getFilePath());
  }

  public ActorScheduler getScheduler() {
    return scheduler;
  }

  public BrokerCfg getBrokerConfiguration() {
    return brokerCfg;
  }

  public IdentityConfiguration getIdentityConfiguration() {
    return identityConfiguration;
  }

  public AtomixCluster getCluster() {
    return cluster;
  }

  public Map<String, String> getDiagnosticContext() {
    return diagnosticContext;
  }

  public BrokerClient getBrokerClient() {
    return brokerClient;
  }

  public Duration getShutdownTimeout() {
    return shutdownTimeout;
  }

  public MeterRegistry getMeterRegistry() {
    return meterRegistry;
  }

  public SecurityConfiguration getSecurityConfiguration() {
    return securityConfiguration;
  }

  public UserServices getUserServices() {
    return userServices;
  }

  public PasswordEncoder getPasswordEncoder() {
    return passwordEncoder;
  }

  public JwtDecoder getJwtDecoder() {
    return jwtDecoder;
  }

  public SecondaryDbQueryService getSecondaryDbQueryService() {
    return secondaryDbQueryService;
  }
}
