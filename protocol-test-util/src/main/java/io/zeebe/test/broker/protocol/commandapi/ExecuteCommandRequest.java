/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.broker.protocol.commandapi;

import static io.zeebe.protocol.record.ExecuteCommandRequestEncoder.keyNullValue;
import static io.zeebe.protocol.record.ExecuteCommandRequestEncoder.partitionIdNullValue;

import io.zeebe.protocol.record.ExecuteCommandRequestEncoder;
import io.zeebe.protocol.record.MessageHeaderEncoder;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.transport.ClientRequest;
import io.zeebe.transport.ClientTransport;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.sched.future.ActorFuture;
import java.time.Duration;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class ExecuteCommandRequest implements ClientRequest {
  private final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
  private final ExecuteCommandRequestEncoder requestEncoder = new ExecuteCommandRequestEncoder();
  private final MsgPackHelper msgPackHelper;

  private final ClientTransport output;
  private final String target;

  private int partitionId = partitionIdNullValue();
  private long key = keyNullValue();
  private ValueType valueType = ValueType.NULL_VAL;
  private byte[] encodedCmd = new byte[0];
  private ActorFuture<DirectBuffer> responseFuture;
  private Intent intent = null;
  private byte[] spanContext = new byte[0];

  public ExecuteCommandRequest(
      final ClientTransport output, final String targetAddress, final MsgPackHelper msgPackHelper) {
    this.output = output;
    this.target = targetAddress;
    this.msgPackHelper = msgPackHelper;
  }

  public ExecuteCommandRequest partitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public ExecuteCommandRequest key(final long key) {
    this.key = key;
    return this;
  }

  public ExecuteCommandRequest valueType(final ValueType valueType) {
    this.valueType = valueType;
    return this;
  }

  public ExecuteCommandRequest intent(final Intent intent) {
    this.intent = intent;
    return this;
  }

  public ExecuteCommandRequest command(final Map<String, Object> command) {
    this.encodedCmd = msgPackHelper.encodeAsMsgPack(command);
    return this;
  }

  public ExecuteCommandRequest command(final BufferWriter command) {
    final int commandLength = command.getLength();
    this.encodedCmd = new byte[commandLength];
    command.write(new UnsafeBuffer(encodedCmd), 0);

    return this;
  }

  public ExecuteCommandRequest spanContext(final BufferWriter spanContext) {
    final int length = spanContext.getLength();
    this.spanContext = new byte[length];
    spanContext.write(new UnsafeBuffer(this.spanContext), 0);

    return this;
  }

  public ExecuteCommandRequest send() {
    if (responseFuture != null) {
      throw new RuntimeException("Cannot send request more than once");
    }

    responseFuture = output.sendRequestWithRetry(() -> target, this, Duration.ofSeconds(5));
    return this;
  }

  public ExecuteCommandResponse await() {
    final var responseBuffer = responseFuture.join();

    final ExecuteCommandResponse result = new ExecuteCommandResponse(msgPackHelper);

    result.wrap(responseBuffer, 0, responseBuffer.capacity());

    return result;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public int getLength() {
    return MessageHeaderEncoder.ENCODED_LENGTH
        + ExecuteCommandRequestEncoder.BLOCK_LENGTH
        + ExecuteCommandRequestEncoder.valueHeaderLength()
        + encodedCmd.length
        + ExecuteCommandRequestEncoder.spanContextHeaderLength()
        + spanContext.length;
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    messageHeaderEncoder
        .wrap(buffer, offset)
        .schemaId(requestEncoder.sbeSchemaId())
        .templateId(requestEncoder.sbeTemplateId())
        .blockLength(requestEncoder.sbeBlockLength())
        .version(requestEncoder.sbeSchemaVersion());

    requestEncoder
        .wrap(buffer, offset + messageHeaderEncoder.encodedLength())
        .partitionId(partitionId)
        .key(key)
        .valueType(valueType)
        .intent(intent.value())
        .putValue(encodedCmd, 0, encodedCmd.length)
        .putSpanContext(spanContext, 0, spanContext.length);
  }
}
