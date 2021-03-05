/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.broker.protocol.brokerapi;

import io.zeebe.protocol.record.ExecuteCommandRequestDecoder;
import io.zeebe.protocol.record.MessageHeaderDecoder;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.util.buffer.BufferReader;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.io.DirectBufferInputStream;

public final class ExecuteCommandRequest implements BufferReader {

  protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  protected final ExecuteCommandRequestDecoder bodyDecoder = new ExecuteCommandRequestDecoder();

  protected final MsgPackHelper msgPackHelper;

  protected Map<String, Object> command;

  public ExecuteCommandRequest(final MsgPackHelper msgPackHelper) {
    this.msgPackHelper = msgPackHelper;
  }

  public long key() {
    return bodyDecoder.key();
  }

  public int partitionId() {
    return bodyDecoder.partitionId();
  }

  public ValueType valueType() {
    return bodyDecoder.valueType();
  }

  public Intent intent() {
    return Intent.fromProtocolValue(valueType(), bodyDecoder.intent());
  }

  public Map<String, Object> getCommand() {
    return command;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    headerDecoder.wrap(buffer, offset);

    bodyDecoder.wrap(
        buffer,
        offset + headerDecoder.encodedLength(),
        headerDecoder.blockLength(),
        headerDecoder.version());

    final int commandLength = bodyDecoder.valueLength();
    final int commandOffset =
        bodyDecoder.limit() + ExecuteCommandRequestDecoder.valueHeaderLength();

    command =
        msgPackHelper.readMsgPack(
            new DirectBufferInputStream(buffer, commandOffset, commandLength));
  }
}
