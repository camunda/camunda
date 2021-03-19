/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.message.command;

import io.zeebe.protocol.impl.encoding.SbeBufferWriterReader;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class CloseProcessMessageSubscriptionCommand
    extends SbeBufferWriterReader<
        CloseProcessMessageSubscriptionEncoder, CloseProcessMessageSubscriptionDecoder> {

  private final CloseProcessMessageSubscriptionEncoder encoder =
      new CloseProcessMessageSubscriptionEncoder();
  private final CloseProcessMessageSubscriptionDecoder decoder =
      new CloseProcessMessageSubscriptionDecoder();

  private final DirectBuffer messageName = new UnsafeBuffer(0, 0);
  private int subscriptionPartitionId;
  private long processInstanceKey;
  private long elementInstanceKey;

  @Override
  protected CloseProcessMessageSubscriptionEncoder getBodyEncoder() {
    return encoder;
  }

  @Override
  protected CloseProcessMessageSubscriptionDecoder getBodyDecoder() {
    return decoder;
  }

  @Override
  public void reset() {
    subscriptionPartitionId =
        CloseProcessMessageSubscriptionDecoder.subscriptionPartitionIdNullValue();
    processInstanceKey = CloseProcessMessageSubscriptionDecoder.processInstanceKeyNullValue();
    elementInstanceKey = CloseProcessMessageSubscriptionDecoder.elementInstanceKeyNullValue();
    messageName.wrap(0, 0);
  }

  @Override
  public int getLength() {
    return super.getLength()
        + CloseProcessMessageSubscriptionDecoder.messageNameHeaderLength()
        + messageName.capacity();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    super.write(buffer, offset);

    encoder
        .subscriptionPartitionId(subscriptionPartitionId)
        .processInstanceKey(processInstanceKey)
        .elementInstanceKey(elementInstanceKey)
        .putMessageName(messageName, 0, messageName.capacity());
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    super.wrap(buffer, offset, length);

    subscriptionPartitionId = decoder.subscriptionPartitionId();
    processInstanceKey = decoder.processInstanceKey();
    elementInstanceKey = decoder.elementInstanceKey();
    decoder.wrapMessageName(messageName);
  }

  public int getSubscriptionPartitionId() {
    return subscriptionPartitionId;
  }

  public void setSubscriptionPartitionId(final int subscriptionPartitionId) {
    this.subscriptionPartitionId = subscriptionPartitionId;
  }

  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public void setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public void setElementInstanceKey(final long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
  }

  public DirectBuffer getMessageName() {
    return messageName;
  }

  public void setMessageName(final DirectBuffer messageName) {
    this.messageName.wrap(messageName);
  }
}
