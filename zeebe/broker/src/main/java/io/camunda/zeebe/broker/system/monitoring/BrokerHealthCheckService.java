/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.PartitionRaftListener;
import io.camunda.zeebe.broker.system.partitions.ZeebePartition;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.health.CriticalComponentsHealthMonitor;
import io.camunda.zeebe.util.health.HealthMonitor;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthStatus;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

/*
 * There's 2 ways BrokerHealthCheckService can monitor its current healthstatus:
 *
 *  - listening for failures: in which a subcomponent tells its parent component that a failure
 *   occurred, so that the healthstatus can be updated for all ancestor components. All of the
 *   subcomponents in the diagram below do this.
 *  - probing for healthstatus, in which the BrokerHealthCheckService just checks the healthstatus
 *   of its CriticalComponentsHealthMonitor.
 *
 * In turn, the CriticalComponentsHealthMonitors periodically probe their subcomponents for their
 *  healthstatus and update their own healthstatus when one of their subcomponents has become
 *  unhealthy.
 *
 * The ZeebePartition only probes its CriticalComponentsHealthMonitor when its healthstatus is
 *  probed by the CriticalComponentsHealthMonitor that monitors the ZeebePartition.
 *
 *       +--------------+
 *       | BrokerHealth |-----healthstatus
 *       | CheckService |
 *       +--------------+
 *    probes    |
 *    downwards |informs
 *              |upwards
 *    +--------------------+
 *    | CriticalComponents |----healthstatus
 *    | HealthMonitor      |
 *    +--------------------+
 * periodically |
 * monitors     |informs
 * downwards    |upwards   +----------------+
 *              |----------| ZeebePartition |----healthstatus
 *                   probes ----------------+
 *                   downwards     |
 *                   when probed   |informs
 *                                 |upwards
 *                       +--------------------+
 *                       | CriticalComponents |-----healthstatus
 *                       | HealthMonitor      |
 *                       +--------------------+
 *                    periodically |
 *                    monitors     |informs
 *                    downwards    |upwards   +------+
 *                                 |----------| Raft |
 *                                 |          +------+
 *                                 |informs
 *                                 |upwards   +-----------------+
 *                                 |----------| StreamProcessor |
 *                                 |          +-----------------+
 *                                 |informs
 *                                 |upwards   +-----+
 *                                 |----------| Log |
 *                                 |          +-----+
 *                                 |informs
 *                                 |upwards   +------------------+
 *                                 |----------| ExporterDirector |
 *                                 |          +------------------+
 *                                 |informs
 *                                 |upwards   +------------------+
 *                                 |----------| SnapshotDirector |
 *                                            +------------------+
 *                                 |informs
 *                                 |upwards   +---------------------+
 *                                 |----------| ZeebePartitionHealth|
 *                                            +---------------------+
 *
 * https://textik.com/#cb084adedb02d970
 */
public final class BrokerHealthCheckService extends Actor implements PartitionRaftListener {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;
  /* The physical tenants this broker is configured to run. The broker is only ready once every one
  of them has registered its bootstrap partitions, so that a tenant that never started (and thus
  never contributed its partitions) cannot be mistaken for "no partitions to wait for". */
  private final Set<String> expectedPhysicalTenants;
  private final Set<String> registeredPhysicalTenants = ConcurrentHashMap.newKeySet();
  /* Tracks the install status of every bootstrap partition the broker is responsible for, keyed by
  its full PartitionId (partition group + id). Each physical tenant registers its own partitions, so
  this map accumulates across all tenants. Stays null until the first registration so that an
  install status update before any partition is known fails fast. */
  private volatile Map<PartitionId, Boolean> partitionInstallStatus;
  /* Guards against logging "broker is ready" more than once. Only touched on the actor thread. */
  private boolean readyLogged = false;
  private volatile boolean brokerStarted = false;
  private final HealthMonitor healthMonitor;

  public BrokerHealthCheckService(
      final MemberId nodeId,
      final HealthTreeMetrics healthGraphMetrics,
      final Set<String> expectedPhysicalTenants) {
    this.expectedPhysicalTenants = Set.copyOf(expectedPhysicalTenants);
    healthMonitor =
        new CriticalComponentsHealthMonitor(
            "Broker-" + nodeId, actor, healthGraphMetrics, Optional.empty(), LOG);
  }

  public void registerBootstrapPartitions(
      final String physicalTenantId, final Collection<PartitionMetadata> partitions) {
    // Called once per physical tenant during startup. Record the tenant even when it has no local
    // partitions, so readiness can tell "tenant started with nothing to install" apart from "tenant
    // never started". Accumulate rather than replace so a later tenant's call does not drop the
    // partitions registered by previous tenants.
    registeredPhysicalTenants.add(physicalTenantId);
    if (partitionInstallStatus == null) {
      partitionInstallStatus = new ConcurrentHashMap<>();
    }
    partitions.forEach(
        metadata -> {
          partitionInstallStatus.putIfAbsent(metadata.id(), false);
          healthMonitor.monitorComponent(ZeebePartition.componentName(metadata.id().number()));
        });
  }

  public boolean isBrokerReady() {
    // Ready once every expected physical tenant has registered and every one of their partitions
    // has been installed. Both conditions are evaluated live: requiring all tenants prevents a
    // missing tenant from being silently ignored, and reading the map directly lets partitions
    // registered by a later tenant still gate readiness even if an earlier tenant already finished.
    final var status = partitionInstallStatus;
    return brokerStarted
        && registeredPhysicalTenants.containsAll(expectedPhysicalTenants)
        && status != null
        && !status.containsValue(false);
  }

  public String componentName() {
    // Broker-{id}, different from the actor name
    return healthMonitor.componentName();
  }

  @Override
  public void onBecameRaftFollower(final PartitionId partitionId, final long term) {
    checkState();
    updateBrokerReadyStatus(partitionId);
  }

  @Override
  public void onBecameRaftLeader(final PartitionId partitionId, final long term) {
    checkState();
    updateBrokerReadyStatus(partitionId);
  }

  private void checkState() {
    if (partitionInstallStatus == null) {
      throw new IllegalStateException("PartitionInstallStatus must not be null.");
    }
  }

  private ActorFuture<Void> updateBrokerReadyStatus(final PartitionId partitionId) {
    return actor.call(
        () -> {
          partitionInstallStatus.put(partitionId, true);
          if (!readyLogged && !partitionInstallStatus.containsValue(false)) {
            readyLogged = true;
            LOG.info("All partitions are installed. Broker is ready!");
          }
        });
  }

  @Override
  public String getName() {
    return "HealthCheckService";
  }

  @Override
  protected void onActorStarted() {
    healthMonitor.startMonitoring();
  }

  private void registerComponent(final HealthMonitorable component) {
    actor.run(() -> healthMonitor.registerComponent(component));
  }

  public void registerMonitoredPartition(final int partitionId, final HealthMonitorable partition) {
    registerComponent(partition);
  }

  public void removeMonitoredPartition(final HealthMonitorable partition) {
    removeComponent(partition);
  }

  private void removeComponent(final HealthMonitorable component) {
    actor.run(() -> healthMonitor.removeComponent(component));
  }

  public boolean isBrokerHealthy() {
    return !actor.isClosed() && getBrokerHealth() == HealthStatus.HEALTHY;
  }

  private HealthStatus getBrokerHealth() {
    if (!isBrokerReady()) {
      return HealthStatus.UNHEALTHY;
    }
    return healthMonitor.getHealthReport().getStatus();
  }

  public void setBrokerStarted() {
    actor.run(() -> brokerStarted = true);
  }

  public boolean isBrokerStarted() {
    return brokerStarted;
  }
}
