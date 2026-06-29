/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.health;

/**
 * Observes structural changes of the health tree so that a projection of it (such as the {@code
 * zeebe_broker_health_nodes} metric) can be derived from the single source of truth.
 *
 * <p>An aggregator ({@link HealthMonitor}) reports when a node enters or leaves the tree and hands
 * over the node's {@link HealthNodePosition}. The listener is the single owner of the projection
 * and derives all per-node data (path, tenant, partition) from that position, so it can neither
 * drift from the tree nor emit a node twice.
 */
public interface HealthTreeListener {

  /**
   * Called when a node is added to the tree (including the root). The position fully describes
   * where the node sits, so the listener never has to walk the tree to attribute it.
   *
   * @param node the node that was added
   * @param position the node's position in the tree
   */
  void onNodeRegistered(HealthMonitorable node, HealthNodePosition position);

  /**
   * Called when a node is removed from the tree.
   *
   * @param node the node that was removed
   */
  void onNodeRemoved(HealthMonitorable node);

  /** A listener that does nothing; useful for tests and components without a metric projection. */
  static HealthTreeListener noop() {
    return new HealthTreeListener() {
      @Override
      public void onNodeRegistered(
          final HealthMonitorable node, final HealthNodePosition position) {}

      @Override
      public void onNodeRemoved(final HealthMonitorable node) {}
    };
  }
}
