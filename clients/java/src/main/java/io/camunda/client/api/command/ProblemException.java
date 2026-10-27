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
package io.camunda.client.api.command;

import io.camunda.client.api.ProblemDetail;

public class ProblemException extends ClientHttpException {
  private final ProblemDetail details;

  public ProblemException(final int code, final String reason, final ProblemDetail details) {
    super(code, String.format("%s'. Details: '%s", reason, details));
    this.details = details;
  }

  public ProblemDetail details() {
    return details;
  }
}
