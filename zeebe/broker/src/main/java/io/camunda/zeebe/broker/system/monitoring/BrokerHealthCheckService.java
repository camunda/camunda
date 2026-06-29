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
import io.camunda.zeebe.util.health.HealthNodePosition;
import io.camunda.zeebe.util.health.HealthStatus;
import io.camunda.zeebe.util.health.HealthTreeListener;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

/*
 * The broker aggregates health as a tree that mirrors the domain: the broker node aggregates one
 * node per physical tenant, each tenant node aggregates its partition nodes, and each partition node
 * aggregates that partition's own components (Raft, stream processor, exporter, disk space, ...).
 * Every interior node is a CriticalComponentsHealthMonitor (CCHM) and is healthy only if all of its
 * children are. Health propagates upwards via failure listeners and is re-probed periodically.
 *
 *       +--------------+
 *       | BrokerHealth |
 *       | CheckService |
 *       +--------------+
 *              |
 *       Broker-<id> CCHM
 *              |
 *       Tenant-<tenant> CCHM   (one per expected physical tenant)
 *              |
 *       Partition-<n> CCHM
 *              |
 *       Raft / StreamProcessor / ExporterDirector / SnapshotDirector /
 *       MigrationSnapshotDirector / DiskSpace / PartitionTransition (leaves)
 *
 * Readiness (isBrokerReady) is deliberately independent of this tree; see below.
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
  private final HealthTreeListener healthTreeListener;
  private final HealthMonitor healthMonitor;
  /* One CCHM per expected physical tenant, created eagerly so the tree is complete and an empty
  but expected tenant is legitimately not-yet-healthy. Populated in the constructor and only read
  afterwards. Keyed by physical tenant id (== partition group). */
  private final Map<String, HealthMonitor> tenantMonitors = new HashMap<>();

  public BrokerHealthCheckService(
      final MemberId nodeId,
      final HealthTreeMetrics healthTreeListener,
      final Set<String> expectedPhysicalTenants) {
    this.expectedPhysicalTenants = Set.copyOf(expectedPhysicalTenants);
    this.healthTreeListener = healthTreeListener;
    final var brokerPosition = HealthNodePosition.broker("Broker-" + nodeId);
    healthMonitor =
        new CriticalComponentsHealthMonitor(actor, healthTreeListener, brokerPosition, LOG);
    // The broker is the root and has no parent to announce it, so announce it directly.
    healthTreeListener.onNodeRegistered(healthMonitor, brokerPosition);
    for (final var physicalTenantId : this.expectedPhysicalTenants) {
      final var tenantMonitor =
          new CriticalComponentsHealthMonitor(
              actor, healthTreeListener, brokerPosition.tenant(physicalTenantId), LOG);
      tenantMonitors.put(physicalTenantId, tenantMonitor);
      healthMonitor.registerComponent(tenantMonitor);
    }
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
    final var tenantMonitor = tenantMonitors.get(physicalTenantId);
    partitions.forEach(
        metadata -> {
          partitionInstallStatus.putIfAbsent(metadata.id(), false);
          if (tenantMonitor != null) {
            // Mark the partition as expected so the tenant node stays unhealthy until the partition
            // has actually registered and reported healthy.
            tenantMonitor.monitorComponent(ZeebePartition.componentName(metadata.id()));
          }
        });
    // A late-registering tenant can be the event that completes readiness, so re-check on the actor
    // thread (where readyLogged is owned).
    actor.run(this::logBrokerReadyOnce);
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

  /** The single projector of the health tree, shared with every node so metrics cannot drift. */
  public HealthTreeListener getHealthTreeListener() {
    return healthTreeListener;
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
          logBrokerReadyOnce();
        });
  }

  /**
   * Logs the "broker is ready" transition exactly once, when the broker first actually becomes
   * ready. Readiness can flip on any of three events (a partition install, a physical tenant
   * registering, or the broker startup completing), so this is invoked from all of them. Gated on
   * the full {@link #isBrokerReady()} criteria rather than just "all known partitions installed",
   * so it never claims readiness before every expected tenant has registered and the broker has
   * started. Must run on the actor thread, since {@code readyLogged} is not otherwise synchronized.
   */
  private void logBrokerReadyOnce() {
    if (!readyLogged && isBrokerReady()) {
      readyLogged = true;
      LOG.info("All partitions are installed. Broker is ready!");
    }
  }

  @Override
  public String getName() {
    return "HealthCheckService";
  }

  @Override
  protected void onActorStarted() {
    healthMonitor.startMonitoring();
    tenantMonitors.values().forEach(HealthMonitor::startMonitoring);
  }

  /**
   * Registers a partition's node under its physical tenant's node. The partition's own {@link
   * HealthMonitor} is the single node a partition contributes to the tree.
   */
  public void registerMonitoredPartition(
      final PartitionId partitionId, final HealthMonitor partitionMonitor) {
    actor.run(
        () -> {
          final var tenantMonitor = tenantMonitors.get(partitionId.group());
          if (tenantMonitor != null) {
            tenantMonitor.registerComponent(partitionMonitor);
          } else {
            LOG.warn(
                "Cannot register partition {} for monitoring: no node for physical tenant {}",
                partitionId,
                partitionId.group());
          }
        });
  }

  public void removeMonitoredPartition(
      final PartitionId partitionId, final HealthMonitor partitionMonitor) {
    actor.run(
        () -> {
          final var tenantMonitor = tenantMonitors.get(partitionId.group());
          if (tenantMonitor != null) {
            tenantMonitor.removeComponent(partitionMonitor);
          }
        });
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
    actor.run(
        () -> {
          brokerStarted = true;
          logBrokerReadyOnce();
        });
  }

  public boolean isBrokerStarted() {
    return brokerStarted;
  }
}
