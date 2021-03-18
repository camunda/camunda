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

public final class CorrelateProcessMessageSubscriptionCommand
    extends SbeBufferWriterReader<
        CorrelateProcessMessageSubscriptionEncoder, CorrelateProcessMessageSubscriptionDecoder> {

  private final CorrelateProcessMessageSubscriptionEncoder encoder =
      new CorrelateProcessMessageSubscriptionEncoder();
  private final CorrelateProcessMessageSubscriptionDecoder decoder =
      new CorrelateProcessMessageSubscriptionDecoder();
  private final UnsafeBuffer messageName = new UnsafeBuffer(0, 0);
  private final UnsafeBuffer variables = new UnsafeBuffer(0, 0);
  private final UnsafeBuffer bpmnProcessId = new UnsafeBuffer(0, 0);
  private final UnsafeBuffer correlationKey = new UnsafeBuffer(0, 0);
  private int subscriptionPartitionId;
  private long processInstanceKey;
  private long elementInstanceKey;
  private long messageKey;

  @Override
  protected CorrelateProcessMessageSubscriptionEncoder getBodyEncoder() {
    return encoder;
  }

  @Override
  protected CorrelateProcessMessageSubscriptionDecoder getBodyDecoder() {
    return decoder;
  }

  @Override
  public void reset() {
    subscriptionPartitionId =
        CorrelateProcessMessageSubscriptionDecoder.subscriptionPartitionIdNullValue();
    processInstanceKey = CorrelateProcessMessageSubscriptionDecoder.processInstanceKeyNullValue();
    elementInstanceKey = CorrelateProcessMessageSubscriptionDecoder.elementInstanceKeyNullValue();
    messageKey = CorrelateProcessMessageSubscriptionDecoder.messageKeyNullValue();

    messageName.wrap(0, 0);
    variables.wrap(0, 0);
    bpmnProcessId.wrap(0, 0);
    correlationKey.wrap(0, 0);
  }

  @Override
  public int getLength() {
    return super.getLength()
        + CorrelateProcessMessageSubscriptionDecoder.messageNameHeaderLength()
        + messageName.capacity()
        + CorrelateProcessMessageSubscriptionDecoder.variablesHeaderLength()
        + variables.capacity()
        + CorrelateProcessMessageSubscriptionDecoder.bpmnProcessIdHeaderLength()
        + bpmnProcessId.capacity()
        + CorrelateProcessMessageSubscriptionDecoder.correlationKeyHeaderLength()
        + correlationKey.capacity();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    super.write(buffer, offset);

    encoder
        .subscriptionPartitionId(subscriptionPartitionId)
        .processInstanceKey(processInstanceKey)
        .elementInstanceKey(elementInstanceKey)
        .messageKey(messageKey)
        .putMessageName(messageName, 0, messageName.capacity())
        .putVariables(variables, 0, variables.capacity())
        .putBpmnProcessId(bpmnProcessId, 0, bpmnProcessId.capacity())
        .putCorrelationKey(correlationKey, 0, correlationKey.capacity());
  }

  @Override
  public void wrap(final DirectBuffer buffer, int offset, final int length) {
    super.wrap(buffer, offset, length);

    subscriptionPartitionId = decoder.subscriptionPartitionId();
    processInstanceKey = decoder.processInstanceKey();
    elementInstanceKey = decoder.elementInstanceKey();
    messageKey = decoder.messageKey();

    offset = decoder.limit();

    offset += CorrelateProcessMessageSubscriptionDecoder.messageNameHeaderLength();
    final int messageNameLength = decoder.messageNameLength();
    messageName.wrap(buffer, offset, messageNameLength);
    offset += messageNameLength;
    decoder.limit(offset);

    offset += CorrelateProcessMessageSubscriptionDecoder.variablesHeaderLength();
    final int variablesLength = decoder.variablesLength();
    variables.wrap(buffer, offset, variablesLength);
    offset += variablesLength;
    decoder.limit(offset);

    offset += CorrelateProcessMessageSubscriptionDecoder.bpmnProcessIdHeaderLength();
    final int bpmnProcessIdLength = decoder.bpmnProcessIdLength();
    bpmnProcessId.wrap(buffer, offset, bpmnProcessIdLength);
    offset += bpmnProcessIdLength;
    decoder.limit(offset);

    offset += CorrelateProcessMessageSubscriptionDecoder.correlationKeyHeaderLength();
    final int correlationKeyLength = decoder.correlationKeyLength();
    correlationKey.wrap(buffer, offset, correlationKeyLength);
    offset += correlationKeyLength;
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

  public long getMessageKey() {
    return messageKey;
  }

  public void setMessageKey(final long messageKey) {
    this.messageKey = messageKey;
  }

  public DirectBuffer getMessageName() {
    return messageName;
  }

  public DirectBuffer getVariables() {
    return variables;
  }

  public DirectBuffer getBpmnProcessId() {
    return bpmnProcessId;
  }

  public DirectBuffer getCorrelationKey() {
    return correlationKey;
  }
}
