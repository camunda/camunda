/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.broker.protocol.commandapi;

import static io.camunda.zeebe.protocol.record.ExecuteCommandRequestEncoder.keyNullValue;
import static io.camunda.zeebe.protocol.record.ExecuteCommandRequestEncoder.partitionIdNullValue;

import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.record.ExecuteCommandRequestEncoder;
import io.camunda.zeebe.protocol.record.MessageHeaderEncoder;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.test.broker.protocol.MsgPackHelper;
import io.camunda.zeebe.transport.ClientRequest;
import io.camunda.zeebe.transport.ClientTransport;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.util.buffer.BufferWriter;
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
  private byte[] encodedCmd;
  private ActorFuture<DirectBuffer> responseFuture;
  private Intent intent = null;
  private final AuthInfo authorization = new AuthInfo();

  public ExecuteCommandRequest(
      final ClientTransport output, final String targetAddress, final MsgPackHelper msgPackHelper) {
    this.output = output;
    target = targetAddress;
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

  public AuthInfo getAuthorization() {
    return authorization;
  }

  public ExecuteCommandRequest setAuthorization(final AuthInfo authorization) {
    this.authorization.copyFrom(authorization);
    return this;
  }

  public ExecuteCommandRequest command(final Map<String, Object> command) {
    encodedCmd = msgPackHelper.encodeAsMsgPack(command);
    return this;
  }

  public ExecuteCommandRequest command(final BufferWriter command) {
    final int commandLength = command.getLength();
    encodedCmd = new byte[commandLength];
    command.write(new UnsafeBuffer(encodedCmd), 0);

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
  public RequestType getRequestType() {
    return RequestType.COMMAND;
  }

  @Override
  public int getLength() {
    return MessageHeaderEncoder.ENCODED_LENGTH
        + ExecuteCommandRequestEncoder.BLOCK_LENGTH
        + ExecuteCommandRequestEncoder.valueHeaderLength()
        + encodedCmd.length;
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    requestEncoder
        .wrapAndApplyHeader(buffer, offset, messageHeaderEncoder)
        .partitionId(partitionId)
        .key(key)
        .valueType(valueType)
        .intent(intent.value())
        .putValue(encodedCmd, 0, encodedCmd.length);
    return requestEncoder.encodedLength() + messageHeaderEncoder.encodedLength();
  }
}
