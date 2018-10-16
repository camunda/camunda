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
package io.zeebe.gateway.impl.broker.response;

import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.impl.encoding.ErrorResponse;
import io.zeebe.util.buffer.BufferUtil;

public class BrokerError {

  private final ErrorCode code;
  private final String message;

  public BrokerError(ErrorResponse errorResponse) {
    this(errorResponse.getErrorCode(), BufferUtil.bufferAsString(errorResponse.getErrorData()));
  }

  public BrokerError(ErrorCode code, String message) {
    this.code = code;
    this.message = message;
  }

  public ErrorCode getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }

  @Override
  public String toString() {
    return "BrokerError{" + "code=" + code + ", message='" + message + '\'' + '}';
  }
}
