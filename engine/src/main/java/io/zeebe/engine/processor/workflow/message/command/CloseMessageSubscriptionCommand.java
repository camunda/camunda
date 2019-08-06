/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.message.command;

import io.zeebe.engine.util.SbeBufferWriterReader;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class CloseMessageSubscriptionCommand
    extends SbeBufferWriterReader<
        CloseMessageSubscriptionEncoder, CloseMessageSubscriptionDecoder> {

  private final CloseMessageSubscriptionEncoder encoder = new CloseMessageSubscriptionEncoder();
  private final CloseMessageSubscriptionDecoder decoder = new CloseMessageSubscriptionDecoder();

  private final DirectBuffer messageName = new UnsafeBuffer(0, 0);

  private int subscriptionPartitionId;
  private long workflowInstanceKey;
  private long elementInstanceKey;

  @Override
  protected CloseMessageSubscriptionEncoder getBodyEncoder() {
    return encoder;
  }

  @Override
  protected CloseMessageSubscriptionDecoder getBodyDecoder() {
    return decoder;
  }

  @Override
  public void reset() {
    subscriptionPartitionId = CloseMessageSubscriptionDecoder.subscriptionPartitionIdNullValue();
    workflowInstanceKey = CloseMessageSubscriptionDecoder.workflowInstanceKeyNullValue();
    elementInstanceKey = CloseMessageSubscriptionDecoder.elementInstanceKeyNullValue();
    messageName.wrap(0, 0);
  }

  @Override
  public int getLength() {
    return super.getLength()
        + CloseMessageSubscriptionDecoder.messageNameHeaderLength()
        + messageName.capacity();
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    super.write(buffer, offset);

    encoder
        .subscriptionPartitionId(subscriptionPartitionId)
        .workflowInstanceKey(workflowInstanceKey)
        .elementInstanceKey(elementInstanceKey)
        .putMessageName(messageName, 0, messageName.capacity());
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    super.wrap(buffer, offset, length);

    subscriptionPartitionId = decoder.subscriptionPartitionId();
    workflowInstanceKey = decoder.workflowInstanceKey();
    elementInstanceKey = decoder.elementInstanceKey();
    decoder.wrapMessageName(messageName);
  }

  public int getSubscriptionPartitionId() {
    return subscriptionPartitionId;
  }

  public void setSubscriptionPartitionId(int subscriptionPartitionId) {
    this.subscriptionPartitionId = subscriptionPartitionId;
  }

  public long getWorkflowInstanceKey() {
    return workflowInstanceKey;
  }

  public void setWorkflowInstanceKey(long workflowInstanceKey) {
    this.workflowInstanceKey = workflowInstanceKey;
  }

  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public void setElementInstanceKey(long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
  }

  public DirectBuffer getMessageName() {
    return messageName;
  }

  public void setMessageName(DirectBuffer messageName) {
    this.messageName.wrap(messageName);
  }
}
