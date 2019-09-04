/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.base.topology;

import io.zeebe.transport.SocketAddress;
import org.agrona.collections.IntHashSet;

public class NodeInfo {
  private final int nodeId;
  private final SocketAddress commandApiAddress;

  private final IntHashSet leaders = new IntHashSet();
  private final IntHashSet followers = new IntHashSet();

  public NodeInfo(int nodeId, final SocketAddress commandApiAddress) {
    this.nodeId = nodeId;
    this.commandApiAddress = commandApiAddress;
  }

  public int getNodeId() {
    return nodeId;
  }

  public SocketAddress getCommandApiAddress() {
    return commandApiAddress;
  }

  public IntHashSet getLeaders() {
    return leaders;
  }

  public boolean addLeader(final int partitionId) {
    return leaders.add(partitionId);
  }

  public boolean removeLeader(final int partitionId) {
    return leaders.remove(partitionId);
  }

  public IntHashSet getFollowers() {
    return followers;
  }

  public boolean addFollower(final int partitionId) {
    return followers.add(partitionId);
  }

  public boolean removeFollower(final int partitionId) {
    return followers.remove(partitionId);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((commandApiAddress == null) ? 0 : commandApiAddress.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final NodeInfo other = (NodeInfo) obj;
    if (commandApiAddress == null) {
      return other.commandApiAddress == null;
    } else {
      return commandApiAddress.equals(other.commandApiAddress);
    }
  }

  @Override
  public String toString() {
    return String.format("Node{nodeId=%d, commandApi=%s}", nodeId, commandApiAddress);
  }
}
