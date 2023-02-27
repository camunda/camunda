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
import io.camunda.zeebe.broker.system.configuration.ThreadsCfg;
import io.camunda.zeebe.broker.system.configuration.partitioning.FixedPartitionCfg;
import io.camunda.zeebe.broker.system.configuration.partitioning.Scheme;
import io.camunda.zeebe.util.sched.ActorScheduler;
import io.camunda.zeebe.util.sched.clock.ActorClock;
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
  private static final String REPLICATION_FACTOR_WARN_MSG =
      "Expected to have odd replication factor, but was even ({}). Even replication factor has no benefit over "
          + "the previous odd value and is weaker than next odd. Quorum is calculated as:"
          + " quorum = floor(replication factor / 2) + 1. In this current case the quorum will be"
          + " quorum = {}. If you want to ensure high fault-tolerance and availability,"
          + " make sure to use an odd replication factor.";
  private static final String SNAPSHOT_PERIOD_ERROR_MSG =
      "Snapshot period %s needs to be larger then or equals to one minute.";
  private static final String MAX_BATCH_SIZE_ERROR_MSG =
      "Expected to have an append batch size maximum which is non negative and smaller then '%d', but was '%s'.";
  private static final String REPLICATION_WITH_DISABLED_FLUSH_WARNING =
      "Disabling explicit flushing is an experimental feature and can lead to inconsistencies "
          + "and/or data loss! Please refer to the documentation whether or not you should use this!";

  private final BrokerCfg brokerCfg;
  private Map<String, String> diagnosticContext;
  private ActorScheduler scheduler;

  public SystemContext(final BrokerCfg brokerCfg, final String basePath, final ActorClock clock) {
    this.brokerCfg = brokerCfg;

    initSystemContext(clock, basePath);
  }

  private void initSystemContext(final ActorClock clock, final String basePath) {
    LOG.debug("Initializing system with base path {}", basePath);

    brokerCfg.init(basePath);
    validateConfiguration();

    final var cluster = brokerCfg.getCluster();
    final String brokerId = String.format("Broker-%d", cluster.getNodeId());

    diagnosticContext = Collections.singletonMap(BROKER_ID_LOG_PROPERTY, brokerId);
    scheduler = initScheduler(clock, brokerId);
  }

  private void validateConfiguration() {
    final ClusterCfg cluster = brokerCfg.getCluster();
    final DataCfg data = brokerCfg.getData();
    final var experimental = brokerCfg.getExperimental();

    final int partitionCount = cluster.getPartitionsCount();
    if (partitionCount < 1) {
      throw new IllegalArgumentException("Partition count must not be smaller then 1.");
    }

    final int clusterSize = cluster.getClusterSize();
    final int nodeId = cluster.getNodeId();
    if (nodeId < 0 || nodeId >= clusterSize) {
      throw new IllegalArgumentException(String.format(NODE_ID_ERROR_MSG, nodeId, clusterSize));
    }

    final var maxAppendBatchSize = experimental.getMaxAppendBatchSize();
    if (maxAppendBatchSize.isNegative() || maxAppendBatchSize.toBytes() >= Integer.MAX_VALUE) {
      throw new IllegalArgumentException(
          String.format(MAX_BATCH_SIZE_ERROR_MSG, Integer.MAX_VALUE, maxAppendBatchSize));
    }

    final int replicationFactor = cluster.getReplicationFactor();
    if (replicationFactor < 1 || replicationFactor > clusterSize) {
      throw new IllegalArgumentException(
          String.format(REPLICATION_FACTOR_ERROR_MSG, replicationFactor, clusterSize));
    }

    final var dataCfg = brokerCfg.getData();

    if (replicationFactor % 2 == 0) {
      LOG.warn(REPLICATION_FACTOR_WARN_MSG, replicationFactor, (replicationFactor / 2) + 1);
    }

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

    if (data.isDiskUsageMonitoringEnabled()
        && diskUsageCommandWatermark >= diskUsageReplicationWatermark) {
      throw new IllegalArgumentException(
          String.format(
              "diskUsageCommandWatermark (%f) must be less than diskUsageReplicationWatermark (%f)",
              diskUsageCommandWatermark, diskUsageReplicationWatermark));
    }

    if (experimental.isDisableExplicitRaftFlush()) {
      LOG.warn(REPLICATION_WITH_DISABLED_FLUSH_WARNING);
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

    final var partitioningConfig = experimental.getPartitioning();
    if (partitioningConfig.getScheme() == Scheme.FIXED) {
      validateFixedPartitioningScheme(cluster, experimental);
    }

    final var security = brokerCfg.getNetwork().getSecurity();
    if (security.isEnabled()) {
      validateNetworkSecurityConfig(security);
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

  private ActorScheduler initScheduler(final ActorClock clock, final String brokerId) {
    final ThreadsCfg cfg = brokerCfg.getThreads();

    final int cpuThreads = cfg.getCpuThreadCount();
    final int ioThreads = cfg.getIoThreadCount();

    return ActorScheduler.newActorScheduler()
        .setActorClock(clock)
        .setCpuBoundActorThreadCount(cpuThreads)
        .setIoBoundActorThreadCount(ioThreads)
        .setSchedulerName(brokerId)
        .build();
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
