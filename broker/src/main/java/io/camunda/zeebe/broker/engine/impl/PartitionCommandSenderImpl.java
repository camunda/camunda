/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.engine.impl;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.broker.partitioning.topology.TopologyPartitionListenerImpl;
import io.camunda.zeebe.engine.processing.message.command.PartitionCommandSender;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.concurrent.UnsafeBuffer;

public final class PartitionCommandSenderImpl implements PartitionCommandSender {

  private final ClusterCommunicationService communicationService;

  private final TopologyPartitionListenerImpl partitionListener;

  public PartitionCommandSenderImpl(
      final ClusterCommunicationService communicationService,
      final TopologyPartitionListenerImpl partitionListener) {
    this.communicationService = communicationService;
    this.partitionListener = partitionListener;
  }

  @Override
  public boolean sendCommand(final int receiverPartitionId, final BufferWriter command) {

    final Int2IntHashMap partitionLeaders = partitionListener.getPartitionLeaders();
    if (!partitionLeaders.containsKey(receiverPartitionId)) {
      return true;
    }
    final int partitionLeader = partitionLeaders.get(receiverPartitionId);

    final byte bytes[] = new byte[command.getLength()];
    final MutableDirectBuffer buffer = new UnsafeBuffer(bytes);
    command.write(buffer, 0);

    communicationService.unicast("subscription", bytes, MemberId.from("" + partitionLeader));
    return true;
  }
}
