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

public class OpenMessageSubscriptionCommand
    extends SbeBufferWriterReader<OpenMessageSubscriptionEncoder, OpenMessageSubscriptionDecoder> {

  private final OpenMessageSubscriptionEncoder encoder = new OpenMessageSubscriptionEncoder();
  private final OpenMessageSubscriptionDecoder decoder = new OpenMessageSubscriptionDecoder();
  private final UnsafeBuffer messageName = new UnsafeBuffer(0, 0);
  private final UnsafeBuffer correlationKey = new UnsafeBuffer(0, 0);
  private int subscriptionPartitionId;
  private long workflowInstanceKey;
  private long elementInstanceKey;
  private boolean closeOnCorrelate;

  @Override
  protected OpenMessageSubscriptionEncoder getBodyEncoder() {
    return encoder;
  }

  @Override
  protected OpenMessageSubscriptionDecoder getBodyDecoder() {
    return decoder;
  }

  @Override
  public void reset() {
    subscriptionPartitionId = OpenMessageSubscriptionDecoder.subscriptionPartitionIdNullValue();
    workflowInstanceKey = OpenMessageSubscriptionDecoder.workflowInstanceKeyNullValue();
    elementInstanceKey = OpenMessageSubscriptionDecoder.elementInstanceKeyNullValue();
    messageName.wrap(0, 0);
    correlationKey.wrap(0, 0);
  }

  @Override
  public int getLength() {
    return super.getLength()
        + OpenMessageSubscriptionDecoder.messageNameHeaderLength()
        + messageName.capacity()
        + OpenMessageSubscriptionDecoder.correlationKeyHeaderLength()
        + correlationKey.capacity();
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    super.write(buffer, offset);

    encoder
        .subscriptionPartitionId(subscriptionPartitionId)
        .workflowInstanceKey(workflowInstanceKey)
        .elementInstanceKey(elementInstanceKey)
        .closeOnCorrelate(closeOnCorrelate ? BooleanType.TRUE : BooleanType.FALSE)
        .putMessageName(messageName, 0, messageName.capacity())
        .putCorrelationKey(correlationKey, 0, correlationKey.capacity());
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    super.wrap(buffer, offset, length);

    subscriptionPartitionId = decoder.subscriptionPartitionId();
    workflowInstanceKey = decoder.workflowInstanceKey();
    elementInstanceKey = decoder.elementInstanceKey();
    closeOnCorrelate = decoder.closeOnCorrelate() == BooleanType.TRUE;

    offset = decoder.limit();

    offset += OpenMessageSubscriptionDecoder.messageNameHeaderLength();
    final int messageNameLength = decoder.messageNameLength();
    messageName.wrap(buffer, offset, messageNameLength);
    offset += messageNameLength;
    decoder.limit(offset);

    offset += OpenMessageSubscriptionDecoder.correlationKeyHeaderLength();
    final int correlationKeyLength = decoder.correlationKeyLength();
    correlationKey.wrap(buffer, offset, correlationKeyLength);
    offset += correlationKeyLength;
    decoder.limit(offset);
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

  public DirectBuffer getCorrelationKey() {
    return correlationKey;
  }

  public boolean shouldCloseOnCorrelate() {
    return closeOnCorrelate;
  }

  public void setCloseOnCorrelate(boolean closeOnCorrelate) {
    this.closeOnCorrelate = closeOnCorrelate;
  }
}
