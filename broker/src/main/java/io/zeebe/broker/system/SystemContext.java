/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system;

import static io.zeebe.broker.system.partitions.impl.AsyncSnapshotDirector.MINIMUM_SNAPSHOT_PERIOD;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.ClusterCfg;
import io.zeebe.broker.system.configuration.DataCfg;
import io.zeebe.broker.system.configuration.ThreadsCfg;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.clock.ActorClock;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
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

  protected final BrokerCfg brokerCfg;
  private Map<String, String> diagnosticContext;
  private ActorScheduler scheduler;
  private Duration stepTimeout;

  public SystemContext(final BrokerCfg brokerCfg, final String basePath, final ActorClock clock) {
    this.brokerCfg = brokerCfg;

    initSystemContext(clock, basePath);
  }

  private void initSystemContext(final ActorClock clock, final String basePath) {
    LOG.debug("Initializing system with base path {}", basePath);

    brokerCfg.init(basePath);
    validateConfiguration();

    stepTimeout = brokerCfg.getStepTimeout();

    final var cluster = brokerCfg.getCluster();
    final String brokerId = String.format("Broker-%d", cluster.getNodeId());

    diagnosticContext = Collections.singletonMap(BROKER_ID_LOG_PROPERTY, brokerId);
    scheduler = initScheduler(clock, brokerId);
    setStepTimeout(stepTimeout);
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

  public Duration getStepTimeout() {
    return stepTimeout;
  }

  private void setStepTimeout(final Duration stepTimeout) {
    this.stepTimeout = stepTimeout;
  }
}
