/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.message.command;

import io.zeebe.protocol.impl.encoding.SbeBufferWriterReader;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class CloseProcessInstanceSubscriptionCommand
    extends SbeBufferWriterReader<
        CloseProcessInstanceSubscriptionEncoder, CloseProcessInstanceSubscriptionDecoder> {

  private final CloseProcessInstanceSubscriptionEncoder encoder =
      new CloseProcessInstanceSubscriptionEncoder();
  private final CloseProcessInstanceSubscriptionDecoder decoder =
      new CloseProcessInstanceSubscriptionDecoder();

  private final DirectBuffer messageName = new UnsafeBuffer(0, 0);
  private int subscriptionPartitionId;
  private long processInstanceKey;
  private long elementInstanceKey;

  @Override
  protected CloseProcessInstanceSubscriptionEncoder getBodyEncoder() {
    return encoder;
  }

  @Override
  protected CloseProcessInstanceSubscriptionDecoder getBodyDecoder() {
    return decoder;
  }

  @Override
  public void reset() {
    subscriptionPartitionId =
        CloseProcessInstanceSubscriptionDecoder.subscriptionPartitionIdNullValue();
    processInstanceKey = CloseProcessInstanceSubscriptionDecoder.processInstanceKeyNullValue();
    elementInstanceKey = CloseProcessInstanceSubscriptionDecoder.elementInstanceKeyNullValue();
    messageName.wrap(0, 0);
  }

  @Override
  public int getLength() {
    return super.getLength()
        + CloseProcessInstanceSubscriptionDecoder.messageNameHeaderLength()
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
