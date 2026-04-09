/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.broker.partitioning.topology.ClusterConfigurationService;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbResources;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbResources.RuntimeInfo;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import org.agrona.CloseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Allocates broker-wide RocksDB resources once at startup, stores it on the {@link
 * BrokerStartupContext}, and closes it on shutdown. Every {@link PartitionManagerStep} consumes the
 * same shared resources, so adding physical tenants does not multiply RocksDB memory usage.
 */
final class RocksDbResourcesStep extends AbstractBrokerStartupStep {

  private static final Logger LOG = LoggerFactory.getLogger(RocksDbResourcesStep.class);

  @Override
  public String getName() {
    return "RocksDB Resources";
  }

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {
    concurrencyControl.run(
        () -> {
          try {
            final var partitionsPerBroker =
                getPartitionsPerBroker(
                    brokerStartupContext.getClusterConfigurationService(),
                    brokerStartupContext
                        .getClusterServices()
                        .getMembershipService()
                        .getLocalMember()
                        .id(),
                    brokerStartupContext.getBrokerConfiguration());
            final var resources =
                RocksDbResources.of(
                    brokerStartupContext
                        .getBrokerConfiguration()
                        .getExperimental()
                        .getRocksdb()
                        .createRocksDbConfiguration(),
                    new RuntimeInfo(partitionsPerBroker));
            brokerStartupContext.setRocksDbResources(resources);
            startupFuture.complete(brokerStartupContext);
          } catch (final Exception e) {
            startupFuture.completeExceptionally(e);
          }
        });
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {
    concurrencyControl.run(
        () -> {
          final var resources = brokerShutdownContext.getRocksDbResources();
          if (resources instanceof final RocksDbResources.Shared shared) {
            CloseHelper.closeAll(shared.getSharedWriteBufferManager(), shared.getSharedCache());
          }
          brokerShutdownContext.setRocksDbResources(null);
          shutdownFuture.complete(brokerShutdownContext);
        });
  }

  /**
   * Determines the number of partitions this broker is responsible for. Attempts to get the actual
   * partition count from the cluster topology, with a static-config estimate as last resort.
   */
  static int getPartitionsPerBroker(
      final ClusterConfigurationService clusterConfigurationService,
      final MemberId localMemberId,
      final BrokerCfg brokerCfg) {
    final long partitionCount =
        clusterConfigurationService.getMemberPartitions(localMemberId).size();
    if (partitionCount > 0) {
      return (int) partitionCount;
    }

    // Fall back to counting JOINING partitions — this happens during scale-up when a broker is
    // being asked to join a partition for the first time, and the partition distribution does not
    // include the partitions that are in JOINING state yet.
    final int joiningPartitionCount =
        clusterConfigurationService.getJoiningMemberPartitionCount(localMemberId);
    if (joiningPartitionCount > 0) {
      return joiningPartitionCount;
    }

    // Last-resort fallback: the cluster configuration hasn't been updated yet (e.g. join called
    // before the dynamic config reflects the JOINING state). Estimate the per-broker partition
    // count from the static cluster config so the RocksDB cache is sized appropriately.
    final var clusterCfg = brokerCfg.getCluster();
    final int estimate =
        (int)
            Math.ceil(
                (double) clusterCfg.getPartitionsCount()
                    * clusterCfg.getReplicationFactor()
                    / clusterCfg.getClusterSize());
    LOG.warn(
        "Could not determine partition count for broker {} from topology; "
            + "falling back to static config estimate of {} partition(s) for RocksDB cache sizing. "
            + "This is expected when joining a partition before the cluster configuration is updated.",
        localMemberId,
        estimate);
    return estimate;
  }
}
