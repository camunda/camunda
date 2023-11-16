/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.metrics;

import io.camunda.zeebe.topology.state.ClusterChangePlan;
import io.camunda.zeebe.topology.state.ClusterChangePlan.Status;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.CompletedChange;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import io.prometheus.client.Counter;
import io.prometheus.client.Enumeration;
import io.prometheus.client.Gauge;
import java.util.List;

public final class TopologyMetrics {
  private static final String NAMESPACE = "zeebe";
  private static final String LABEL_OPERATION = "operation";
  private static final String LABEL_OUTCOME = "outcome";

  private static final Gauge TOPOLOGY_VERSION =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("cluster_topology_version")
          .help("The version of the cluster topology")
          .register();
  private static final Gauge CHANGE_ID =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("cluster_changes_id")
          .help("The id of the cluster topology change plan")
          .register();
  private static final Enumeration CHANGE_STATUS =
      Enumeration.build()
          .namespace(NAMESPACE)
          .name("cluster_changes_status")
          .help("The state of the current cluster topology")
          .states(ClusterChangePlan.Status.class)
          .register();
  private static final Gauge CHANGE_VERSION =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("cluster_changes_version")
          .help("The version of the cluster topology change plan")
          .register();

  private static final Gauge PENDING_OPERATIONS =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("cluster_changes_operations_pending")
          .help("Number of pending changes in the current change plan")
          .register();
  private static final Gauge COMPLETED_OPERATIONS =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("cluster_changes_operations_completed")
          .help("Number of completed changes in the current change plan")
          .register();
  private static final Counter OPERATION_ATTEMPTS =
      Counter.build()
          .namespace(NAMESPACE)
          .name("cluster_changes_operation_attempts")
          .help("Number of retries per operation type")
          .labelNames(LABEL_OPERATION, LABEL_OUTCOME)
          .register();

  public static void updateFromTopology(final ClusterTopology topology) {
    TOPOLOGY_VERSION.set(topology.version());
    CHANGE_STATUS.state(
        topology
            .pendingChanges()
            .map(ClusterChangePlan::status)
            .or(() -> topology.lastChange().map(CompletedChange::status))
            .orElse(Status.COMPLETED));
    CHANGE_ID.set(topology.pendingChanges().map(ClusterChangePlan::id).orElse(0L));
    CHANGE_VERSION.set(topology.pendingChanges().map(ClusterChangePlan::version).orElse(0));
    PENDING_OPERATIONS.set(
        topology
            .pendingChanges()
            .map(ClusterChangePlan::pendingOperations)
            .map(List::size)
            .orElse(0));
    COMPLETED_OPERATIONS.set(
        topology
            .pendingChanges()
            .map(ClusterChangePlan::completedOperations)
            .map(List::size)
            .orElse(0));
  }

  public static void failedOperation(final TopologyChangeOperation operation) {
    OPERATION_ATTEMPTS.labels(operation.getClass().getSimpleName(), "failed").inc();
  }

  public static void appliedOperation(final TopologyChangeOperation operation) {
    OPERATION_ATTEMPTS.labels(operation.getClass().getSimpleName(), "applied").inc();
  }
}
