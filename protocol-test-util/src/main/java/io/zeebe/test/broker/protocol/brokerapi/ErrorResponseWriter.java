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
package io.zeebe.test.broker.protocol.brokerapi;

import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.ErrorResponseEncoder;
import io.zeebe.protocol.clientapi.MessageHeaderEncoder;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import java.nio.charset.StandardCharsets;
import org.agrona.MutableDirectBuffer;

public class ErrorResponseWriter<R> extends AbstractMessageBuilder<R> {
  protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  protected final ErrorResponseEncoder bodyEncoder = new ErrorResponseEncoder();
  protected final MsgPackHelper msgPackHelper;

  protected ErrorCode errorCode;
  protected byte[] errorData;

  public ErrorResponseWriter(MsgPackHelper msgPackHelper) {
    this.msgPackHelper = msgPackHelper;
  }

  @Override
  public int getLength() {
    return MessageHeaderEncoder.ENCODED_LENGTH
        + ErrorResponseEncoder.BLOCK_LENGTH
        + ErrorResponseEncoder.errorDataHeaderLength()
        + errorData.length;
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    // protocol header
    headerEncoder
        .wrap(buffer, offset)
        .blockLength(bodyEncoder.sbeBlockLength())
        .templateId(bodyEncoder.sbeTemplateId())
        .schemaId(bodyEncoder.sbeSchemaId())
        .version(bodyEncoder.sbeSchemaVersion());

    offset += headerEncoder.encodedLength();

    // protocol message
    bodyEncoder
        .wrap(buffer, offset)
        .errorCode(errorCode)
        .putErrorData(errorData, 0, errorData.length);
  }

  @Override
  public void initializeFrom(R context) {}

  public void setErrorCode(ErrorCode errorCode) {
    this.errorCode = errorCode;
  }

  public void setErrorData(String errorData) {
    this.errorData = errorData.getBytes(StandardCharsets.UTF_8);
  }
}
