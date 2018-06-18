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

import io.zeebe.protocol.clientapi.ControlMessageRequestEncoder;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.MessageHeaderEncoder;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.transport.ClientOutput;
import io.zeebe.transport.ClientResponse;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.sched.future.ActorFuture;
import java.time.Duration;
import java.util.Map;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class ControlMessageRequest implements BufferWriter {
  protected final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
  protected final ControlMessageRequestEncoder requestEncoder = new ControlMessageRequestEncoder();
  protected final MsgPackHelper msgPackHelper;
  protected final ClientOutput output;
  protected final RemoteAddress target;

  protected ControlMessageType messageType = ControlMessageType.NULL_VAL;
  protected int partitionId = ControlMessageRequestEncoder.partitionIdNullValue();
  protected byte[] encodedData = new byte[0];

  protected ActorFuture<ClientResponse> responseFuture;

  public ControlMessageRequest(
      ClientOutput output, RemoteAddress target, MsgPackHelper msgPackHelper) {
    this.output = output;
    this.target = target;
    this.msgPackHelper = msgPackHelper;
  }

  public ControlMessageRequest messageType(ControlMessageType messageType) {
    this.messageType = messageType;
    return this;
  }

  public ControlMessageRequest partitionId(int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public ControlMessageRequest data(Map<String, Object> data) {
    this.encodedData = msgPackHelper.encodeAsMsgPack(data);
    return this;
  }

  public ControlMessageRequest send() {
    return send(this::shouldRetryRequest);
  }

  public ControlMessageRequest send(Predicate<DirectBuffer> retryFunction) {
    if (responseFuture != null) {
      throw new RuntimeException("Cannot send request more than once");
    }

    responseFuture =
        output.sendRequestWithRetry(() -> target, retryFunction, this, Duration.ofSeconds(5));
    return this;
  }

  public ControlMessageResponse await() {
    final ClientResponse response = responseFuture.join();
    final DirectBuffer responseBuffer = response.getResponseBuffer();

    final ControlMessageResponse result = new ControlMessageResponse(msgPackHelper);

    result.wrap(responseBuffer, 0, responseBuffer.capacity());

    return result;
  }

  public ErrorResponse awaitError() {
    final ClientResponse response = responseFuture.join();
    final DirectBuffer responseBuffer = response.getResponseBuffer();

    final ErrorResponse errorResponse = new ErrorResponse(msgPackHelper);
    errorResponse.wrap(responseBuffer, 0, responseBuffer.capacity());
    return errorResponse;
  }

  private boolean shouldRetryRequest(final DirectBuffer responseBuffer) {
    final ErrorResponse error = new ErrorResponse(msgPackHelper);
    try {
      error.wrap(responseBuffer, 0, responseBuffer.capacity());
      final ErrorCode errorCode = error.getErrorCode();
      final String message = error.getErrorData();
      return errorCode == ErrorCode.PARTITION_NOT_FOUND
          || errorCode == ErrorCode.REQUEST_PROCESSING_FAILURE
              && (message.matches(".*Partition .* not found.*")
                  || message.matches(
                      ".*No subscription management processor registered for partition.*"));
    } catch (final Exception e) {
      // ignore
      return false;
    }
  }

  @Override
  public int getLength() {
    return MessageHeaderEncoder.ENCODED_LENGTH
        + ControlMessageRequestEncoder.BLOCK_LENGTH
        + ControlMessageRequestEncoder.dataHeaderLength()
        + encodedData.length;
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    messageHeaderEncoder
        .wrap(buffer, offset)
        .schemaId(requestEncoder.sbeSchemaId())
        .templateId(requestEncoder.sbeTemplateId())
        .blockLength(requestEncoder.sbeBlockLength())
        .version(requestEncoder.sbeSchemaVersion());

    requestEncoder
        .wrap(buffer, offset + messageHeaderEncoder.encodedLength())
        .messageType(messageType)
        .partitionId(partitionId)
        .putData(encodedData, 0, encodedData.length);
  }
}
