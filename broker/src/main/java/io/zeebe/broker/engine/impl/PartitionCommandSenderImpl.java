/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.engine.impl;

import io.atomix.cluster.MemberId;
import io.atomix.core.Atomix;
import io.zeebe.broker.clustering.topology.TopologyManager;
import io.zeebe.broker.clustering.topology.TopologyPartitionListenerImpl;
import io.zeebe.engine.processor.workflow.message.command.PartitionCommandSender;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.sched.ActorControl;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.concurrent.UnsafeBuffer;

public class PartitionCommandSenderImpl implements PartitionCommandSender {

  private final Atomix atomix;

  private final TopologyPartitionListenerImpl partitionListener;

  public PartitionCommandSenderImpl(
      Atomix atomix, final TopologyManager topologyManager, final ActorControl actor) {
    this.atomix = atomix;
    this.partitionListener = new TopologyPartitionListenerImpl(actor);
    topologyManager.addTopologyPartitionListener(partitionListener);
  }

  public boolean sendCommand(final int receiverPartitionId, final BufferWriter command) {

    final Int2IntHashMap partitionLeaders = partitionListener.getPartitionLeaders();
    if (!partitionLeaders.containsKey(receiverPartitionId)) {
      return true;
    }
    final int partitionLeader = partitionLeaders.get(receiverPartitionId);

    final byte bytes[] = new byte[command.getLength()];
    final MutableDirectBuffer buffer = new UnsafeBuffer(bytes);
    command.write(buffer, 0);

    atomix
        .getCommunicationService()
        .send("subscription", bytes, MemberId.from("" + partitionLeader));
    return true;
  }
}
