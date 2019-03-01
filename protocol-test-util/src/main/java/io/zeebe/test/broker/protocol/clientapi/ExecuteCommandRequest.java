/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.test.broker.protocol.clientapi;

import static io.zeebe.protocol.clientapi.ExecuteCommandRequestEncoder.keyNullValue;
import static io.zeebe.protocol.clientapi.ExecuteCommandRequestEncoder.partitionIdNullValue;

import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.ExecuteCommandRequestEncoder;
import io.zeebe.protocol.clientapi.MessageHeaderEncoder;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.transport.ClientOutput;
import io.zeebe.transport.ClientResponse;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.sched.future.ActorFuture;
import java.time.Duration;
import java.util.Map;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ExecuteCommandRequest implements BufferWriter {
  protected final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
  protected final ExecuteCommandRequestEncoder requestEncoder = new ExecuteCommandRequestEncoder();
  protected final MsgPackHelper msgPackHelper;

  protected final ClientOutput output;
  protected final int target;

  protected int partitionId = partitionIdNullValue();
  protected long key = keyNullValue();
  protected ValueType valueType = ValueType.NULL_VAL;
  private Intent intent = null;
  protected byte[] encodedCmd;

  protected ActorFuture<ClientResponse> responseFuture;

  public ExecuteCommandRequest(ClientOutput output, int target, final MsgPackHelper msgPackHelper) {
    this.output = output;
    this.target = target;
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

  public ExecuteCommandRequest intent(Intent intent) {
    this.intent = intent;
    return this;
  }

  public ExecuteCommandRequest command(final Map<String, Object> command) {
    this.encodedCmd = msgPackHelper.encodeAsMsgPack(command);
    return this;
  }

  public ExecuteCommandRequest command(BufferWriter command) {
    final int commandLength = command.getLength();
    this.encodedCmd = new byte[commandLength];
    command.write(new UnsafeBuffer(encodedCmd), 0);

    return this;
  }

  public ExecuteCommandRequest send() {
    return send(this::shouldRetryRequest);
  }

  public ExecuteCommandRequest send(Predicate<DirectBuffer> retryFunction) {
    if (responseFuture != null) {
      throw new RuntimeException("Cannot send request more than once");
    }

    responseFuture =
        output.sendRequestWithRetry(() -> target, retryFunction, this, Duration.ofSeconds(5));
    return this;
  }

  public ExecuteCommandResponse await() {
    final ClientResponse response = responseFuture.join();
    final DirectBuffer responseBuffer = response.getResponseBuffer();

    final ExecuteCommandResponse result = new ExecuteCommandResponse(msgPackHelper);

    result.wrap(responseBuffer, 0, responseBuffer.capacity());

    return result;
  }

  public ErrorResponse awaitError() {
    final ClientResponse response = responseFuture.join();
    final DirectBuffer responseBuffer = response.getResponseBuffer();

    final ErrorResponse result = new ErrorResponse(msgPackHelper);
    result.wrap(responseBuffer, 0, responseBuffer.capacity());
    return result;
  }

  private boolean shouldRetryRequest(final DirectBuffer responseBuffer) {
    final ErrorResponse error = new ErrorResponse(msgPackHelper);
    try {
      error.wrap(responseBuffer, 0, responseBuffer.capacity());
      return error.getErrorCode() == ErrorCode.PARTITION_LEADER_MISMATCH;
    } catch (final Exception e) {
      // ignore
      return false;
    }
  }

  @Override
  public int getLength() {
    return MessageHeaderEncoder.ENCODED_LENGTH
        + ExecuteCommandRequestEncoder.BLOCK_LENGTH
        + ExecuteCommandRequestEncoder.valueHeaderLength()
        + encodedCmd.length;
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
        .putValue(encodedCmd, 0, encodedCmd.length);
  }
}
