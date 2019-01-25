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
package io.zeebe.gateway.impl.broker.request;

import io.zeebe.gateway.Loggers;
import io.zeebe.gateway.cmd.UnsupportedBrokerResponseException;
import io.zeebe.gateway.impl.broker.response.BrokerError;
import io.zeebe.gateway.impl.broker.response.BrokerErrorResponse;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.protocol.clientapi.ErrorResponseDecoder;
import io.zeebe.protocol.clientapi.ErrorResponseEncoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.protocol.impl.encoding.ErrorResponse;
import io.zeebe.transport.ClientResponse;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public abstract class BrokerRequest<T> implements BufferWriter {

  protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  protected final ErrorResponse errorResponse = new ErrorResponse();

  protected final int schemaId;
  protected final int templateId;

  public BrokerRequest(int schemaId, int templateId) {
    this.schemaId = schemaId;
    this.templateId = templateId;
  }

  public abstract int getPartitionId();

  public abstract void setPartitionId(int partitionId);

  public abstract boolean addressesSpecificPartition();

  public abstract boolean requiresPartitionId();

  // public so we can do assertions in tests
  public abstract BufferWriter getRequestWriter();

  public void serializeValue() {
    final BufferWriter valueWriter = getRequestWriter();
    if (valueWriter != null) {
      final int valueLength = valueWriter.getLength();
      final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[valueLength]);
      valueWriter.write(buffer, 0);
      setSerializedValue(buffer);
    }
  }

  protected abstract void setSerializedValue(DirectBuffer buffer);

  protected abstract void wrapResponse(DirectBuffer buffer);

  protected abstract BrokerResponse<T> readResponse();

  protected abstract T toResponseDto(DirectBuffer buffer);

  public BrokerResponse<T> getResponse(ClientResponse clientResponse) {
    final DirectBuffer responseBuffer = clientResponse.getResponseBuffer();
    try {
      if (isValidResponse(responseBuffer)) {
        wrapResponse(responseBuffer);
        return readResponse();
      } else if (isErrorResponse(responseBuffer)) {
        wrapErrorResponse(responseBuffer);
        final BrokerError error = new BrokerError(errorResponse);
        return new BrokerErrorResponse<>(error);
      } else {
        throw new UnsupportedBrokerResponseException(
            headerDecoder.schemaId(), headerDecoder.templateId(), schemaId, templateId);
      }
    } catch (Exception e) {
      // Log response buffer for debugging purpose
      Loggers.GATEWAY_LOGGER.error(
          "Failed to read response: {}{}{}",
          e.getMessage(),
          System.lineSeparator(),
          BufferUtil.bufferAsHexString(responseBuffer));
      throw e;
    }
  }

  protected void wrapResponseHeader(DirectBuffer buffer) {
    headerDecoder.wrap(buffer, 0);
  }

  protected boolean isErrorResponse(DirectBuffer buffer) {
    wrapResponseHeader(buffer);

    return headerDecoder.schemaId() == ErrorResponseEncoder.SCHEMA_ID
        && headerDecoder.templateId() == ErrorResponseDecoder.TEMPLATE_ID;
  }

  protected void wrapErrorResponse(DirectBuffer buffer) {
    errorResponse.wrap(buffer, 0, buffer.capacity());
  }

  protected boolean isValidResponse(DirectBuffer buffer) {
    wrapResponseHeader(buffer);

    return headerDecoder.schemaId() == schemaId && headerDecoder.templateId() == templateId;
  }
}
