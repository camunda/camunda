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

import java.time.Duration;

public abstract class CamundaError extends RuntimeException {
  protected CamundaError(final String message, final Throwable cause) {
    super(message, cause);
  }

  public static BpmnError bpmnError(
      final String errorCode,
      final String errorMessage,
      final Object variables,
      final Throwable cause) {
    return new BpmnError(errorCode, errorMessage, variables, cause);
  }

  public static BpmnError bpmnError(
      final String errorCode, final String errorMessage, final Object variables) {
    return bpmnError(errorCode, errorMessage, variables, null);
  }

  public static BpmnError bpmnError(final String errorCode, final String errorMessage) {
    return bpmnError(errorCode, errorMessage, null, null);
  }

  public static JobError jobError(final String errorMessage) {
    return jobError(errorMessage, null, null, null, null);
  }

  public static JobError jobError(final String errorMessage, final Object variables) {
    return jobError(errorMessage, variables, null, null, null);
  }

  public static JobError jobError(
      final String errorMessage, final Object variables, final Integer retries) {
    return jobError(errorMessage, variables, retries, null, null);
  }

  public static JobError jobError(
      final String errorMessage,
      final Object variables,
      final Integer retries,
      final Duration retryBackoff) {
    return jobError(errorMessage, variables, retries, retryBackoff, null);
  }

  public static JobError jobError(
      final String errorMessage,
      final Object variables,
      final Integer retries,
      final Duration retryBackoff,
      final Throwable cause) {
    return new JobError(errorMessage, variables, retries, retryBackoff, cause);
  }
}
