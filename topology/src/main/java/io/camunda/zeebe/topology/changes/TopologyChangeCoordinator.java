/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.changes;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import java.util.List;

public interface TopologyChangeCoordinator {

  /**
   * Applies the given operations to the current cluster topology. The operations are applied
   * asynchronously. The future is completed when the topology change has started successfully, but
   * before completing. To query the status of the topology change, use {@link #hasCompletedChanges}
   * with the version of the returned ClusterTopology.
   *
   * <pre>
   *  final ActorFuture<ClusterTopology> topologyWithPendingChanges =
   *         coordinator.applyOperations(operations).join();
   *  final var topologyChangeIsCompleted =
   *         coordinator.hasCompletedChanges(topologyWithPendingChanges.version()).join();
   * </pre>
   *
   * @param operations the operations to apply
   * @return a future which is completed with the intermediate topology with pending operations
   */
  ActorFuture<ClusterTopology> applyOperations(List<TopologyChangeOperation> operations);

  /**
   * @param version version of the ClusterTopology returned by {@link #applyOperations(List)}
   * @return a future that completes with true if the topology change has completed, false otherwise
   */
  ActorFuture<Boolean> hasCompletedChanges(final long version);
}
