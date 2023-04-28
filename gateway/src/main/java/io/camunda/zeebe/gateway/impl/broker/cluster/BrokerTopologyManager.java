/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.broker.cluster;

import io.atomix.cluster.MemberId;

public interface BrokerTopologyManager {

  BrokerClusterState getTopology();

  /**
   * Adds the topology listener. For each existing brokers, the listener will be notified via {@link
   * BrokerTopologyListener#brokerAdded(MemberId)}. After that, the listener gets notified of every
   * new broker added or removed events.
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
