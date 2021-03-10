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

public final class OpenProcessInstanceSubscriptionCommand
    extends SbeBufferWriterReader<
        OpenProcessInstanceSubscriptionEncoder, OpenProcessInstanceSubscriptionDecoder> {

  private final OpenProcessInstanceSubscriptionEncoder encoder =
      new OpenProcessInstanceSubscriptionEncoder();
  private final OpenProcessInstanceSubscriptionDecoder decoder =
      new OpenProcessInstanceSubscriptionDecoder();
  private final UnsafeBuffer messageName = new UnsafeBuffer(0, 0);
  private int subscriptionPartitionId;
  private long processInstanceKey;
  private long elementInstanceKey;
  private boolean closeOnCorrelate;

  @Override
  protected OpenProcessInstanceSubscriptionEncoder getBodyEncoder() {
    return encoder;
  }

  @Override
  protected OpenProcessInstanceSubscriptionDecoder getBodyDecoder() {
    return decoder;
  }

  @Override
  public void reset() {
    subscriptionPartitionId =
        OpenProcessInstanceSubscriptionDecoder.subscriptionPartitionIdNullValue();
    processInstanceKey = OpenProcessInstanceSubscriptionDecoder.processInstanceKeyNullValue();
    elementInstanceKey = OpenProcessInstanceSubscriptionDecoder.elementInstanceKeyNullValue();
    messageName.wrap(0, 0);
  }

  @Override
  public int getLength() {
    return super.getLength()
        + OpenProcessInstanceSubscriptionDecoder.messageNameHeaderLength()
        + messageName.capacity();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    super.write(buffer, offset);

    encoder
        .subscriptionPartitionId(subscriptionPartitionId)
        .processInstanceKey(processInstanceKey)
        .elementInstanceKey(elementInstanceKey)
        .closeOnCorrelate(closeOnCorrelate ? BooleanType.TRUE : BooleanType.FALSE)
        .putMessageName(messageName, 0, messageName.capacity());
  }

  @Override
  public void wrap(final DirectBuffer buffer, int offset, final int length) {
    super.wrap(buffer, offset, length);

    subscriptionPartitionId = decoder.subscriptionPartitionId();
    processInstanceKey = decoder.processInstanceKey();
    elementInstanceKey = decoder.elementInstanceKey();
    closeOnCorrelate = decoder.closeOnCorrelate() == BooleanType.TRUE;

    offset = decoder.limit();

    offset += OpenProcessInstanceSubscriptionDecoder.messageNameHeaderLength();
    final int messageNameLength = decoder.messageNameLength();
    messageName.wrap(buffer, offset, messageNameLength);
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

  public boolean shouldCloseOnCorrelate() {
    return closeOnCorrelate;
  }

  public void setCloseOnCorrelate(final boolean closeOnCorrelate) {
    this.closeOnCorrelate = closeOnCorrelate;
  }
}
