/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.broker.protocol.brokerapi.data;

import java.net.InetSocketAddress;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class TopologyBroker {
  protected final int nodeId;
  protected final String host;
  protected final int port;
  private final Set<BrokerPartitionState> partitions = new LinkedHashSet<>();
  private final InetSocketAddress address;

  public TopologyBroker(final int nodeId, final String host, final int port) {
    this.nodeId = nodeId;
    this.host = host;
    this.port = port;
    address = new InetSocketAddress(host, port);
  }

  public int getNodeId() {
    return nodeId;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  public Set<BrokerPartitionState> getPartitions() {
    return partitions;
  }

  public TopologyBroker addPartition(final BrokerPartitionState brokerPartitionState) {
    partitions.add(brokerPartitionState);
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(nodeId);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TopologyBroker that = (TopologyBroker) o;
    return nodeId == that.nodeId;
  }

  @Override
  public String toString() {
    return "TopologyBroker{"
        + "partitions="
        + partitions
        + ", nodeId="
        + nodeId
        + ", host='"
        + host
        + '\''
        + ", port="
        + port
        + ", address="
        + address
        + '}';
  }
}
