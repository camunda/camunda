/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.api;

import io.atomix.cluster.BrokerMemberId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationUpdateNotifier.ClusterConfigurationUpdateListener;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;

public interface BrokerTopologyManager extends ClusterConfigurationUpdateListener {

  /**
   * Returns live topology for the given physical tenant (partition group). Never returns {@code
   * null}: a group that is not (yet) known is represented by an uninitialized {@link
   * BrokerClusterState}.
   */
  BrokerClusterState getTopology(String physicalTenantId);

  /**
   * Returns live topology for the default partition group. Equivalent to {@code
   * getTopology("default")}.
   */
  default BrokerClusterState getTopology() {
    return getTopology(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
  }

  /**
   * Returns the current cluster topology. The topology contains the information about brokers which
   * are part of the cluster, and the partition distribution. Unlike {@link BrokerClusterState} this
   * also includes information about brokers which are currently unreachable.
   */
  CurrentClusterConfiguration getClusterConfiguration();

  /**
   * Returns whether the given physical tenant has any broker in {@link Mode#RECOVERING}, or whether
   * its mode cannot be determined at all.
   *
   * <p>A mode change flips brokers one at a time, so "any" covers the whole transition window: the
   * tenant counts as recovering from the moment the first broker enters recovery until the last one
   * has left it. Callers that must not act on a half-transitioned tenant can rely on that.
   *
   * <p>The unknown half of the name covers a tenant the cluster configuration holds no brokers for
   * — not configured, or no configuration gossiped to this broker yet. Recovery cannot be ruled out
   * there either, so callers gating work on this method hold off and retry rather than act on a
   * tenant whose state they cannot see.
   */
  default boolean isRecovering(final String physicalTenantId) {
    final var partitionGroup = getClusterConfiguration().partitionGroup(physicalTenantId);
    return partitionGroup == null
        || partitionGroup.members().isEmpty()
        || partitionGroup.members().values().stream()
            .anyMatch(member -> member.mode() == Mode.RECOVERING);
  }

  /**
   * Adds the topology listener. For each existing broker-group pair, the listener will be notified
   * via {@link BrokerTopologyListener#brokerAdded(BrokerMemberId, String)}. After that, the
   * listener gets notified of every new broker added or removed events.
   *
   * @param listener the topology listener
   */
  void addTopologyListener(final BrokerTopologyListener listener);

  /**
   * Removes the given topology listener by identity.
   *
   * @param listener the listener to remove
   */
  void removeTopologyListener(final BrokerTopologyListener listener);
}
