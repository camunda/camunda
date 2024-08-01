/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.relocation;

import io.camunda.zeebe.engine.state.mutable.MutableRelocationState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import org.agrona.DirectBuffer;

public class DbRelocationState implements MutableRelocationState {
  private RoutingInfo routingInfo;
  private final Set<String> relocatingCorrelationKeys = new HashSet<>();
  private final Set<String> completedCorrelationKeys = new HashSet<>();
  private final LinkedList<MessageRecord> queueMessages = new LinkedList<>();

  public DbRelocationState(final int partitionCount) {
    if (getRoutingInfo() == null) {
      setRoutingInfo(new RoutingInfo(partitionCount, partitionCount, new HashSet<>()));
    }
  }

  @Override
  public RoutingInfo getRoutingInfo() {
    return routingInfo;
  }

  @Override
  public boolean isRelocating(final DirectBuffer correlationKey) {
    return relocatingCorrelationKeys.contains(BufferUtil.bufferAsString(correlationKey));
  }

  @Override
  public boolean isRelocated(final DirectBuffer correlationKey) {
    return completedCorrelationKeys.contains(BufferUtil.bufferAsString(correlationKey));
  }

  @Override
  public Collection<MessageRecord> getQueuedMessages() {
    return queueMessages;
  }

  @Override
  public void setRoutingInfo(final RoutingInfo routingInfo) {
    this.routingInfo = routingInfo;
  }

  @Override
  public void markAsRelocating(final DirectBuffer correlationKey) {
    relocatingCorrelationKeys.add(BufferUtil.bufferAsString(correlationKey));
  }

  @Override
  public void markAsDone(final DirectBuffer correlationKey) {
    final var key = BufferUtil.bufferAsString(correlationKey);
    relocatingCorrelationKeys.remove(key);
    completedCorrelationKeys.add(key);
    queueMessages.clear();
  }

  @Override
  public void enqueue(final MessageRecord messageRecord) {
    final var copy = new MessageRecord();
    BufferUtil.copy(messageRecord, copy);
    queueMessages.add(copy);
  }
}
