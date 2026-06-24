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
package io.camunda.client.exception;

/**
 * Indicates an error in sense of BPMN occured, that should be handled by the BPMN process, see <a
 * href="https://docs.camunda.io/docs/reference/bpmn-processes/error-events/error-events/">...</a>
 */
public class BpmnError extends CamundaError {
  private final String errorCode;
  private final String errorMessage;
  private final Object variables;

  public BpmnError(
      final String errorCode,
      final String errorMessage,
      final Object variables,
      final Throwable cause) {
    super("[" + errorCode + "] " + errorMessage, cause);
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
    this.variables = variables;
  }

  public BpmnError(final String errorCode, final String errorMessage) {
    this(errorCode, errorMessage, null, null);
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public Object getVariables() {
    return variables;
  }
}
