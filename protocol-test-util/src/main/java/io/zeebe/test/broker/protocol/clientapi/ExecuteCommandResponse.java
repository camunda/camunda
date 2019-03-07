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

import io.zeebe.protocol.clientapi.ErrorResponseDecoder;
import io.zeebe.protocol.clientapi.ExecuteCommandResponseDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.util.buffer.BufferReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.LangUtil;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.io.DirectBufferInputStream;

public class ExecuteCommandResponse implements BufferReader {
  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
  private final ExecuteCommandResponseDecoder responseDecoder = new ExecuteCommandResponseDecoder();
  private final DirectBuffer responseBuffer = new UnsafeBuffer();
  protected final ErrorResponse errorResponse;

  protected final MsgPackHelper msgPackHelper;

  protected Map<String, Object> value;
  private int valueLengthOffset;
  private String rejectionReason;

  public ExecuteCommandResponse(MsgPackHelper msgPackHelper) {
    this.msgPackHelper = msgPackHelper;
    this.errorResponse = new ErrorResponse(msgPackHelper);
  }

  public Map<String, Object> getValue() {
    return value;
  }

  public DirectBuffer getRawValue() {
    responseDecoder.limit(valueLengthOffset);
    final int valueLength = responseDecoder.valueLength();
    final int valueOffset = valueLengthOffset + ExecuteCommandResponseDecoder.valueHeaderLength();

    final UnsafeBuffer buf = new UnsafeBuffer(responseDecoder.buffer(), valueOffset, valueLength);
    return buf;
  }

  public long getKey() {
    return responseDecoder.key();
  }

  public int getPartitionId() {
    return responseDecoder.partitionId();
  }

  public ValueType getValueType() {
    return responseDecoder.valueType();
  }

  public Intent getIntent() {
    return Intent.fromProtocolValue(responseDecoder.valueType(), responseDecoder.intent());
  }

  public RecordType getRecordType() {
    return responseDecoder.recordType();
  }

  public RejectionType getRejectionType() {
    return responseDecoder.rejectionType();
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  @Override
  public void wrap(DirectBuffer responseBuffer, int offset, int length) {
    messageHeaderDecoder.wrap(responseBuffer, offset);

    if (messageHeaderDecoder.templateId() != responseDecoder.sbeTemplateId()) {
      if (messageHeaderDecoder.templateId() == ErrorResponseDecoder.TEMPLATE_ID) {
        errorResponse.wrap(responseBuffer, offset + messageHeaderDecoder.encodedLength(), length);
        throw new RuntimeException(
            "Unexpected error response from broker: "
                + errorResponse.getErrorCode()
                + " - "
                + errorResponse.getErrorData());
      } else {
        throw new RuntimeException(
            "Unexpected response from broker. Template id " + messageHeaderDecoder.templateId());
      }
    }

    responseDecoder.wrap(
        responseBuffer,
        offset + messageHeaderDecoder.encodedLength(),
        messageHeaderDecoder.blockLength(),
        messageHeaderDecoder.version());

    valueLengthOffset = responseDecoder.limit();
    final int valueLength = responseDecoder.valueLength();
    final int valueOffset = valueLengthOffset + ExecuteCommandResponseDecoder.valueHeaderLength();
    this.responseBuffer.wrap(responseBuffer, valueOffset, valueLength);

    try (InputStream is = new DirectBufferInputStream(responseBuffer, valueOffset, valueLength)) {
      value = msgPackHelper.readMsgPack(is);
    } catch (IOException e) {
      LangUtil.rethrowUnchecked(e);
    }

    responseDecoder.limit(valueOffset + valueLength);
    rejectionReason = responseDecoder.rejectionReason();
  }

  public <T extends BufferReader> T readInto(T record) {
    record.wrap(responseBuffer, 0, responseBuffer.capacity());
    return record;
  }
}
