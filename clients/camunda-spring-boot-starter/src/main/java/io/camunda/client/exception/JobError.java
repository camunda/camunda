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

public class JobError extends CamundaError {
  private final String errorMessage;
  private final Object variables;
  private final Integer retries;
  private final Duration retryBackoff;

  public JobError(
      final String errorMessage,
      final Object variables,
      final Integer retries,
      final Duration retryBackoff,
      final Throwable cause) {
    super(errorMessage, cause);
    this.errorMessage = errorMessage;
    this.variables = variables;
    this.retries = retries;
    this.retryBackoff = retryBackoff;
  }

  public JobError(final String errorMessage) {
    this(errorMessage, null, null, null, null);
  }

  public Object getVariables() {
    return variables;
  }

  public Integer getRetries() {
    return retries;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public Duration getRetryBackoff() {
    return retryBackoff;
  }
}
