/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.base.topology;

import io.zeebe.util.sched.ActorControl;
import org.agrona.collections.Int2ObjectHashMap;

public class TopologyPartitionListenerImpl implements TopologyPartitionListener {

  private final Int2ObjectHashMap<NodeInfo> partitionLeaders = new Int2ObjectHashMap<>();
  private final ActorControl actor;

  public TopologyPartitionListenerImpl(final ActorControl actor) {
    this.actor = actor;
  }

  @Override
  public void onPartitionUpdated(final int partitionId, final NodeInfo member) {
    if (member.getLeaders().contains(partitionId)) {
      actor.submit(() -> updatePartitionLeader(partitionId, member));
    }
  }

  private void updatePartitionLeader(final int partitionId, final NodeInfo member) {
    final NodeInfo currentLeader = partitionLeaders.get(partitionId);

    if (currentLeader == null || !currentLeader.equals(member)) {
      partitionLeaders.put(partitionId, member);
    }
  }

  public Int2ObjectHashMap<NodeInfo> getPartitionLeaders() {
    return partitionLeaders;
  }
}
