/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.api;

import io.atomix.cluster.BrokerMemberId;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationUpdateNotifier.ClusterConfigurationUpdateListener;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.protocol.Protocol;
import org.jspecify.annotations.NonNull;

public interface BrokerTopologyManager extends ClusterConfigurationUpdateListener {

  /**
   * Returns live topology for the given physical tenant (partition group). May return {@code null}
   * or an uninitialized topology if the group is not (yet) known; callers must handle both.
   */
  BrokerClusterState getTopology(@NonNull String physicalTenantId);

  /**
   * Returns live topology for the default partition group. Equivalent to {@code
   * getTopology("default")}.
   */
  default BrokerClusterState getTopology() {
    return getTopology(Protocol.DEFAULT_PARTITION_GROUP_NAME);
  }

  /**
   * Returns the current cluster topology. The topology contains the information about brokers which
   * are part of the cluster, and the partition distribution. Unlike {@link BrokerClusterState} this
   * also includes information about brokers which are currently unreachable.
   */
  ClusterConfiguration getClusterConfiguration();

  /**
   * Adds the topology listener. For each existing brokers, the listener will be notified via {@link
   * BrokerTopologyListener#brokerAdded(BrokerMemberId)}. After that, the listener gets notified of
   * every new broker added or removed events.
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
