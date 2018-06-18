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
package io.zeebe.client.impl;

import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.ErrorResponseDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public class ErrorResponseHandler {
  protected ErrorResponseDecoder decoder = new ErrorResponseDecoder();

  protected DirectBuffer errorMessage;

  public boolean handlesResponse(MessageHeaderDecoder responseHeader) {
    return ErrorResponseDecoder.SCHEMA_ID == responseHeader.schemaId()
        && ErrorResponseDecoder.TEMPLATE_ID == responseHeader.templateId();
  }

  public void wrap(DirectBuffer body, int offset, int length, int version) {
    decoder.wrap(body, offset, length, version);

    final int errorDataLength = decoder.errorDataLength();
    final byte[] errorData = new byte[errorDataLength];
    decoder.getErrorData(errorData, 0, errorDataLength);
    this.errorMessage = BufferUtil.wrapArray(errorData);
  }

  public ErrorCode getErrorCode() {
    return decoder.errorCode();
  }

  public DirectBuffer getErrorMessage() {
    return errorMessage;
  }
}
