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

import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.ErrorResponseDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.util.buffer.BufferReader;
import org.agrona.DirectBuffer;

public class ErrorResponse implements BufferReader {
  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
  private final ErrorResponseDecoder bodyDecoder = new ErrorResponseDecoder();

  protected final MsgPackHelper msgPackHelper;

  protected String errorData;

  public ErrorResponse(MsgPackHelper msgPackHelper) {
    this.msgPackHelper = msgPackHelper;
  }

  public ErrorCode getErrorCode() {
    return bodyDecoder.errorCode();
  }

  public String getErrorData() {
    return errorData;
  }

  @Override
  public void wrap(DirectBuffer responseBuffer, int offset, int length) {
    messageHeaderDecoder.wrap(responseBuffer, 0);

    if (messageHeaderDecoder.templateId() != bodyDecoder.sbeTemplateId()) {
      throw new RuntimeException("Unexpected response from broker.");
    }

    bodyDecoder.wrap(
        responseBuffer,
        messageHeaderDecoder.encodedLength(),
        messageHeaderDecoder.blockLength(),
        messageHeaderDecoder.version());

    final int errorDataLength = bodyDecoder.errorDataLength();
    final int errorDataOffset =
        messageHeaderDecoder.encodedLength()
            + messageHeaderDecoder.blockLength()
            + ErrorResponseDecoder.errorDataHeaderLength();

    errorData = responseBuffer.getStringWithoutLengthUtf8(errorDataOffset, errorDataLength);

    bodyDecoder.limit(errorDataOffset + errorDataLength);
  }
}
