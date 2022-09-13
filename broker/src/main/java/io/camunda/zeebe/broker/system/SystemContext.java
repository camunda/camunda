/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system;

import static io.camunda.zeebe.broker.system.partitions.impl.AsyncSnapshotDirector.MINIMUM_SNAPSHOT_PERIOD;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ClusterCfg;
import io.camunda.zeebe.broker.system.configuration.DataCfg;
import io.camunda.zeebe.broker.system.configuration.ExperimentalCfg;
import io.camunda.zeebe.broker.system.configuration.SecurityCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg.BackupStoreType;
import io.camunda.zeebe.broker.system.configuration.partitioning.FixedPartitionCfg;
import io.camunda.zeebe.broker.system.configuration.partitioning.Scheme;
import io.camunda.zeebe.scheduler.ActorScheduler;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;

public final class SystemContext {

  public static final Logger LOG = Loggers.SYSTEM_LOGGER;
  private static final String BROKER_ID_LOG_PROPERTY = "broker-id";
  private static final String NODE_ID_ERROR_MSG =
      "Node id %s needs to be non negative and smaller then cluster size %s.";
  private static final String REPLICATION_FACTOR_ERROR_MSG =
      "Replication factor %s needs to be larger then zero and not larger then cluster size %s.";
  private static final String SNAPSHOT_PERIOD_ERROR_MSG =
      "Snapshot period %s needs to be larger then or equals to one minute.";
  private static final String MAX_BATCH_SIZE_ERROR_MSG =
      "Expected to have an append batch size maximum which is non negative and smaller then '%d', but was '%s'.";
  private static final String REPLICATION_WITH_DISABLED_FLUSH_WARNING =
      "Disabling explicit flushing is an experimental feature and can lead to inconsistencies "
          + "and/or data loss! Please refer to the documentation whether or not you should use this!";

  private final BrokerCfg brokerCfg;
  private Map<String, String> diagnosticContext;
  private final ActorScheduler scheduler;

  public SystemContext(
      final BrokerCfg brokerCfg, final String basePath, final ActorScheduler scheduler) {
    this.brokerCfg = brokerCfg;
    this.scheduler = scheduler;
    initSystemContext(basePath);
  }

  private void initSystemContext(final String basePath) {
    LOG.debug("Initializing system with base path {}", basePath);

    brokerCfg.init(basePath);
    validateConfiguration();

    final var cluster = brokerCfg.getCluster();
    final String brokerId = String.format("Broker-%d", cluster.getNodeId());

    diagnosticContext = Collections.singletonMap(BROKER_ID_LOG_PROPERTY, brokerId);
  }

  private void validateConfiguration() {
    final ClusterCfg cluster = brokerCfg.getCluster();

    validateClusterConfig(cluster);

    validateDataConfig(brokerCfg.getData());

    validateExperimentalConfigs(cluster, brokerCfg.getExperimental());

    final var security = brokerCfg.getNetwork().getSecurity();
    if (security.isEnabled()) {
      validateNetworkSecurityConfig(security);
    }
  }

  private static void validateClusterConfig(final ClusterCfg cluster) {
    final int partitionCount = cluster.getPartitionsCount();
    if (partitionCount < 1) {
      throw new IllegalArgumentException("Partition count must not be smaller then 1.");
    }

    final int clusterSize = cluster.getClusterSize();
    final int nodeId = cluster.getNodeId();
    if (nodeId < 0 || nodeId >= clusterSize) {
      throw new IllegalArgumentException(String.format(NODE_ID_ERROR_MSG, nodeId, clusterSize));
    }

    final int replicationFactor = cluster.getReplicationFactor();
    if (replicationFactor < 1 || replicationFactor > clusterSize) {
      throw new IllegalArgumentException(
          String.format(REPLICATION_FACTOR_ERROR_MSG, replicationFactor, clusterSize));
    }

    final var heartbeatInterval = cluster.getHeartbeatInterval();
    final var electionTimeout = cluster.getElectionTimeout();
    if (heartbeatInterval.toMillis() < 1) {
      throw new IllegalArgumentException(
          String.format("heartbeatInterval %s must be at least 1ms", heartbeatInterval));
    }
    if (electionTimeout.toMillis() < 1) {
      throw new IllegalArgumentException(
          String.format("electionTimeout %s must be at least 1ms", electionTimeout));
    }
    if (electionTimeout.compareTo(heartbeatInterval) < 1) {
      throw new IllegalArgumentException(
          String.format(
              "electionTimeout %s must be greater than heartbeatInterval %s",
              electionTimeout, heartbeatInterval));
    }
  }

  private void validateExperimentalConfigs(
      final ClusterCfg cluster, final ExperimentalCfg experimental) {
    if (experimental.isDisableExplicitRaftFlush()) {
      LOG.warn(REPLICATION_WITH_DISABLED_FLUSH_WARNING);
    }

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

    final var diskUsageCommandWatermark = dataCfg.getDiskUsageCommandWatermark();
    if (!(diskUsageCommandWatermark > 0 && diskUsageCommandWatermark <= 1)) {
      throw new IllegalArgumentException(
          String.format(
              "Expected diskUsageCommandWatermark to be in the range (0,1], but found %f",
              diskUsageCommandWatermark));
    }

    final var diskUsageReplicationWatermark = dataCfg.getDiskUsageReplicationWatermark();
    if (!(diskUsageReplicationWatermark > 0 && diskUsageReplicationWatermark <= 1)) {
      throw new IllegalArgumentException(
          String.format(
              "Expected diskUsageReplicationWatermark to be in the range (0,1], but found %f",
              diskUsageReplicationWatermark));
    }

    if (dataCfg.isDiskUsageMonitoringEnabled()
        && diskUsageCommandWatermark >= diskUsageReplicationWatermark) {
      throw new IllegalArgumentException(
          String.format(
              "diskUsageCommandWatermark (%f) must be less than diskUsageReplicationWatermark (%f)",
              diskUsageCommandWatermark, diskUsageReplicationWatermark));
    }

    validateBackupCfg(dataCfg.getBackup());
  }

  private void validateBackupCfg(final BackupStoreCfg backup) {
    if (backup.getStore() == BackupStoreType.S3) {
      final var s3Config = backup.getS3();
      if (s3Config.getBucketName() == null || s3Config.getBucketName().isEmpty()) {
        throw new IllegalArgumentException(
            "Configuration for S3 backup store is incomplete. bucketName must not be empty.");
      }
      if (s3Config.getRegion() == null) {
        LOG.warn(
            "No region configured for S3 backup store. Region will be determined from environment (see https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/region-selection.html#automatically-determine-the-aws-region-from-the-environment)");
      }
      if (s3Config.getEndpoint() == null) {
        LOG.warn(
            "No endpoint configured for S3 backup store. Endpoint will be determined from the region");
      }
      if (s3Config.getAccessKey() == null || s3Config.getSecretKey() == null) {
        LOG.warn(
            "Access credentials (accessKey, secretKey) not configured for S3 backup store. Credentials will be determined from environment (see https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html#credentials-chain)");
      }
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
    final var certificateChainPath = security.getCertificateChainPath();
    final var privateKeyPath = security.getPrivateKeyPath();

    if (certificateChainPath == null) {
      throw new IllegalArgumentException(
          "Expected to have a valid certificate chain path for network security, but none "
              + "configured");
    }

    if (privateKeyPath == null) {
      throw new IllegalArgumentException(
          "Expected to have a valid private key path for network security, but none configured");
    }

    if (!certificateChainPath.canRead()) {
      throw new IllegalArgumentException(
          String.format(
              "Expected the configured network security certificate chain path '%s' to point to a"
                  + " readable file, but it does not",
              certificateChainPath));
    }

    if (!privateKeyPath.canRead()) {
      throw new IllegalArgumentException(
          String.format(
              "Expected the configured network security private key path '%s' to point to a "
                  + "readable file, but it does not",
              privateKeyPath));
    }
  }

  public ActorScheduler getScheduler() {
    return scheduler;
  }

  public BrokerCfg getBrokerConfiguration() {
    return brokerCfg;
  }

  public Map<String, String> getDiagnosticContext() {
    return diagnosticContext;
  }
}
