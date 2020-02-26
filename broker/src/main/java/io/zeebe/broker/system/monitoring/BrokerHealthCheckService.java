/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.monitoring;

import static io.zeebe.broker.clustering.atomix.AtomixFactory.GROUP_NAME;

import io.atomix.cluster.MemberId;
import io.atomix.core.Atomix;
import io.atomix.protocols.raft.partition.RaftPartitionGroup;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.PartitionListener;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.protocol.impl.encoding.BrokerInfo;
import io.zeebe.util.health.CriticalComponentsHealthMonitor;
import io.zeebe.util.health.HealthMonitor;
import io.zeebe.util.health.HealthMonitorable;
import io.zeebe.util.health.HealthStatus;
import io.zeebe.util.sched.Actor;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public final class BrokerHealthCheckService extends Actor implements PartitionListener {

  private static final String PARTITION_COMPONENT_NAME_FORMAT = "Partition-%d";
  private static final Logger LOG = Loggers.SYSTEM_LOGGER;
  private final Atomix atomix;
  private final String actorName;
  private Map<Integer, Boolean> partitionInstallStatus;
  /* set to true when all partitions are installed. Once set to true, it is never
  changed. */
  private volatile boolean brokerStarted = false;
  private final HealthMonitor healthMonitor;

  public BrokerHealthCheckService(final BrokerInfo localBroker, final Atomix atomix) {
    this.atomix = atomix;
    this.actorName = buildActorName(localBroker.getNodeId(), "HealthCheckService");
    this.healthMonitor = new CriticalComponentsHealthMonitor(actor, LOG);
    initializePartitionInstallStatus();
    initializePartitionHealthStatus();
  }

  private void initializePartitionHealthStatus() {
    final RaftPartitionGroup partitionGroup =
        (RaftPartitionGroup) atomix.getPartitionService().getPartitionGroup(GROUP_NAME);
    final MemberId nodeId = atomix.getMembershipService().getLocalMember().id();

    partitionGroup.getPartitions().stream()
        .filter(partition -> partition.members().contains(nodeId))
        .map(partition -> partition.id().id())
        .forEach(
            partitionId ->
                healthMonitor.monitorComponent(
                    String.format(PARTITION_COMPONENT_NAME_FORMAT, partitionId)));
  }

  boolean isBrokerReady() {
    return brokerStarted;
  }

  @Override
  public void onBecomingFollower(
      final int partitionId, final long term, final LogStream logStream) {
    updateBrokerReadyStatus(partitionId);
  }

  @Override
  public void onBecomingLeader(final int partitionId, final long term, final LogStream logStream) {
    updateBrokerReadyStatus(partitionId);
  }

  private void updateBrokerReadyStatus(final int partitionId) {
    actor.call(
        () -> {
          if (!brokerStarted) {
            partitionInstallStatus.put(partitionId, true);
            brokerStarted = !partitionInstallStatus.containsValue(false);

            if (brokerStarted) {
              LOG.debug("All partitions are installed. Broker is ready!");
            }
          }
        });
  }

  private void initializePartitionInstallStatus() {
    final RaftPartitionGroup partitionGroup =
        (RaftPartitionGroup) atomix.getPartitionService().getPartitionGroup(GROUP_NAME);
    final MemberId nodeId = atomix.getMembershipService().getLocalMember().id();

    partitionInstallStatus =
        partitionGroup.getPartitions().stream()
            .filter(partition -> partition.members().contains(nodeId))
            .map(partition -> partition.id().id())
            .collect(Collectors.toMap(Function.identity(), p -> false));
  }

  @Override
  public String getName() {
    return actorName;
  }

  @Override
  protected void onActorStarted() {
    healthMonitor.startMonitoring();
  }

  private void registerComponent(final String componentName, final HealthMonitorable component) {
    actor.run(() -> healthMonitor.registerComponent(componentName, component));
  }

  public void registerMonitoredPartition(final int partitionId, final HealthMonitorable partition) {
    final String componentName = String.format(PARTITION_COMPONENT_NAME_FORMAT, partitionId);
    registerComponent(componentName, partition);
  }

  public boolean isBrokerHealthy() {
    return !actor.isClosed() && getBrokerHealth() == HealthStatus.HEALTHY;
  }

  private HealthStatus getBrokerHealth() {
    if (!isBrokerReady()) {
      return HealthStatus.UNHEALTHY;
    }
    return healthMonitor.getHealthStatus();
  }
}
