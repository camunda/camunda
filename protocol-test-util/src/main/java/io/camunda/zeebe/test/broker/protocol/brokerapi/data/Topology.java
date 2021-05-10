/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.broker.protocol.brokerapi.data;

import static io.camunda.zeebe.test.broker.protocol.brokerapi.data.BrokerPartitionState.LEADER_STATE;

import io.camunda.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class Topology {

  protected Map<Integer, TopologyBroker> brokers = new HashMap<>();

  public Topology() {}

  public Topology(final Topology other) {
    brokers = new HashMap<>(other.brokers);
  }

  private TopologyBroker getBroker(final int nodeId, final InetSocketAddress brokerAddress) {
    TopologyBroker topologyBroker = brokers.get(nodeId);
    if (topologyBroker == null) {
      topologyBroker =
          new TopologyBroker(nodeId, brokerAddress.getHostName(), brokerAddress.getPort());
      brokers.put(nodeId, topologyBroker);
    }
    return topologyBroker;
  }

  public Topology addLeader(final StubBrokerRule brokerRule, final int partition) {
    return addLeader(brokerRule.getNodeId(), brokerRule.getSocketAddress(), partition);
  }

  public Topology addLeader(
      final int nodeId, final InetSocketAddress address, final int partition) {
    getBroker(nodeId, address).addPartition(new BrokerPartitionState(LEADER_STATE, partition, 1));
    return this;
  }

  public Set<TopologyBroker> getBrokers() {
    return new HashSet<>(brokers.values());
  }

  @Override
  public String toString() {
    return "Topology{" + "brokers=" + brokers.values() + '}';
  }
}
