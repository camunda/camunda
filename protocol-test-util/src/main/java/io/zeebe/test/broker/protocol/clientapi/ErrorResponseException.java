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

public class ErrorResponseException extends RuntimeException {

  private final ErrorResponse errorResponse;

  public ErrorResponseException(ErrorResponse errorResponse) {
    super(
        String.format(
            "Unexpected error from broker (code: '%s'): %s",
            errorResponse.getErrorCode().name(), errorResponse.getErrorData()));
    this.errorResponse = errorResponse;
  }

  public ErrorResponse getErrorResponse() {
    return errorResponse;
  }

  public ErrorCode getErrorCode() {
    return errorResponse.getErrorCode();
  }

  public String getErrorMessage() {
    return errorResponse.getErrorData();
  }
}
