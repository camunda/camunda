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

public final class RejectCorrelateMessageSubscriptionCommand
    extends SbeBufferWriterReader<
        RejectCorrelateMessageSubscriptionEncoder, RejectCorrelateMessageSubscriptionDecoder> {

  private final RejectCorrelateMessageSubscriptionEncoder encoder =
      new RejectCorrelateMessageSubscriptionEncoder();
  private final RejectCorrelateMessageSubscriptionDecoder decoder =
      new RejectCorrelateMessageSubscriptionDecoder();
  private final UnsafeBuffer messageName = new UnsafeBuffer(0, 0);
  private final UnsafeBuffer correlationKey = new UnsafeBuffer(0, 0);
  private final UnsafeBuffer bpmnProcessId = new UnsafeBuffer(0, 0);
  private int subscriptionPartitionId;
  private long processInstanceKey;
  private long messageKey;

  @Override
  protected RejectCorrelateMessageSubscriptionEncoder getBodyEncoder() {
    return encoder;
  }

  @Override
  protected RejectCorrelateMessageSubscriptionDecoder getBodyDecoder() {
    return decoder;
  }

  @Override
  public void reset() {
    subscriptionPartitionId =
        RejectCorrelateMessageSubscriptionDecoder.subscriptionPartitionIdNullValue();
    processInstanceKey = RejectCorrelateMessageSubscriptionDecoder.processInstanceKeyNullValue();
    messageKey = RejectCorrelateMessageSubscriptionDecoder.messageKeyNullValue();
    messageName.wrap(0, 0);
    correlationKey.wrap(0, 0);
    bpmnProcessId.wrap(0, 0);
  }

  @Override
  public int getLength() {
    return super.getLength()
        + RejectCorrelateMessageSubscriptionDecoder.messageNameHeaderLength()
        + messageName.capacity()
        + RejectCorrelateMessageSubscriptionDecoder.correlationKeyHeaderLength()
        + correlationKey.capacity()
        + RejectCorrelateMessageSubscriptionDecoder.bpmnProcessIdHeaderLength()
        + bpmnProcessId.capacity();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    super.write(buffer, offset);

    encoder
        .subscriptionPartitionId(subscriptionPartitionId)
        .processInstanceKey(processInstanceKey)
        .messageKey(messageKey)
        .putMessageName(messageName, 0, messageName.capacity())
        .putCorrelationKey(correlationKey, 0, correlationKey.capacity())
        .putBpmnProcessId(bpmnProcessId, 0, bpmnProcessId.capacity());
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    super.wrap(buffer, offset, length);

    subscriptionPartitionId = decoder.subscriptionPartitionId();
    processInstanceKey = decoder.processInstanceKey();
    messageKey = decoder.messageKey();

    decoder.wrapMessageName(messageName);
    decoder.wrapCorrelationKey(correlationKey);
    decoder.wrapBpmnProcessId(bpmnProcessId);
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

  public long getMessageKey() {
    return messageKey;
  }

  public void setMessageKey(final long messageKey) {
    this.messageKey = messageKey;
  }

  public DirectBuffer getMessageName() {
    return messageName;
  }

  public DirectBuffer getCorrelationKey() {
    return correlationKey;
  }

  public DirectBuffer getBpmnProcessId() {
    return bpmnProcessId;
  }
}
