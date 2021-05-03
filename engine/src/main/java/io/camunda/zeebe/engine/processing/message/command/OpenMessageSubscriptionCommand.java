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

public final class OpenMessageSubscriptionCommand
    extends SbeBufferWriterReader<OpenMessageSubscriptionEncoder, OpenMessageSubscriptionDecoder> {

  private final OpenMessageSubscriptionEncoder encoder = new OpenMessageSubscriptionEncoder();
  private final OpenMessageSubscriptionDecoder decoder = new OpenMessageSubscriptionDecoder();
  private final UnsafeBuffer messageName = new UnsafeBuffer(0, 0);
  private final UnsafeBuffer correlationKey = new UnsafeBuffer(0, 0);
  private final UnsafeBuffer bpmnProcessId = new UnsafeBuffer(0, 0);
  private int subscriptionPartitionId;
  private long processInstanceKey;
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
    processInstanceKey = OpenMessageSubscriptionDecoder.processInstanceKeyNullValue();
    elementInstanceKey = OpenMessageSubscriptionDecoder.elementInstanceKeyNullValue();
    messageName.wrap(0, 0);
    correlationKey.wrap(0, 0);
    bpmnProcessId.wrap(0, 0);
  }

  @Override
  public int getLength() {
    return super.getLength()
        + OpenMessageSubscriptionDecoder.messageNameHeaderLength()
        + messageName.capacity()
        + OpenMessageSubscriptionDecoder.correlationKeyHeaderLength()
        + correlationKey.capacity()
        + OpenMessageSubscriptionDecoder.bpmnProcessIdHeaderLength()
        + bpmnProcessId.capacity();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    super.write(buffer, offset);

    encoder
        .subscriptionPartitionId(subscriptionPartitionId)
        .processInstanceKey(processInstanceKey)
        .elementInstanceKey(elementInstanceKey)
        .closeOnCorrelate(closeOnCorrelate ? BooleanType.TRUE : BooleanType.FALSE)
        .putMessageName(messageName, 0, messageName.capacity())
        .putCorrelationKey(correlationKey, 0, correlationKey.capacity())
        .putBpmnProcessId(bpmnProcessId, 0, bpmnProcessId.capacity());
  }

  @Override
  public void wrap(final DirectBuffer buffer, int offset, final int length) {
    super.wrap(buffer, offset, length);

    subscriptionPartitionId = decoder.subscriptionPartitionId();
    processInstanceKey = decoder.processInstanceKey();
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

    offset += OpenMessageSubscriptionDecoder.bpmnProcessIdHeaderLength();
    final int bpmnProcessIdLength = decoder.bpmnProcessIdLength();
    bpmnProcessId.wrap(buffer, offset, bpmnProcessIdLength);
    offset += bpmnProcessIdLength;
    decoder.limit(offset);
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

  public DirectBuffer getCorrelationKey() {
    return correlationKey;
  }

  public boolean shouldCloseOnCorrelate() {
    return closeOnCorrelate;
  }

  public void setCloseOnCorrelate(final boolean closeOnCorrelate) {
    this.closeOnCorrelate = closeOnCorrelate;
  }

  public DirectBuffer getBpmnProcessId() {
    return bpmnProcessId;
  }
}
