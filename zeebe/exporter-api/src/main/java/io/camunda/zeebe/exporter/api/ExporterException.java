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
package io.camunda.zeebe.exporter.api;

public final class ExporterException extends RuntimeException {
  private static final long serialVersionUID = 9144017472787012481L;

  private final boolean recoverable;

  public ExporterException(final String message) {
    super(message);
    recoverable = true;
  }

  public ExporterException(final String message, final Throwable cause) {
    super(message, cause);
    recoverable = true;
  }

  /**
   * Creates a new ExporterException with the given message and cause.
   *
   * @param message the exception message
   * @param cause the underlying cause
   * @param recoverable if {@code false}, the exporter must be closed and reopened to recover;
   *     retrying the same export call will not succeed
   */
  public ExporterException(final String message, final Throwable cause, final boolean recoverable) {
    super(message, cause);
    this.recoverable = recoverable;
  }

  /**
   * Creates a new ExporterException with the given message.
   *
   * @param message the exception message
   * @param recoverable if {@code false}, the exporter must be closed and reopened to recover;
   *     retrying the same export call will not succeed
   */
  public ExporterException(final String message, final boolean recoverable) {
    super(message);
    this.recoverable = recoverable;
  }

  /**
   * Returns whether this exception can be recovered by retrying the failed export call.
   *
   * <p>When {@code false}, the exporter must be closed and reopened before it can process records
   * again. Simply retrying the same export call will not succeed.
   *
   * @return {@code true} if retry is sufficient; {@code false} if the exporter needs to be reopened
   */
  public boolean isRecoverable() {
    return recoverable;
  }
}
