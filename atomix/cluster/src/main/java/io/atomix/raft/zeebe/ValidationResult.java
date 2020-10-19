/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.raft.zeebe;

public final class ValidationResult {
  private final boolean successful;
  private final String errorMessage;

  private ValidationResult(final boolean successful, final String errorMessage) {
    this.successful = successful;
    this.errorMessage = errorMessage;
  }

  public static ValidationResult success() {
    return new ValidationResult(true, null);
  }

  public static ValidationResult failure(final String errorMessage) {
    return new ValidationResult(false, errorMessage);
  }

  public boolean failed() {
    return !successful;
  }

  public String getErrorMessage() {
    return errorMessage;
  }
}
