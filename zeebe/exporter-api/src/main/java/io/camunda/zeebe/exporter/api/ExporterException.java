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

import org.jspecify.annotations.NonNull;

public class ExporterException extends RuntimeException {
  private static final long serialVersionUID = 9144017472787012481L;
  private final Compensation compensation;

  public ExporterException(final String message) {
    super(message);
    compensation = Compensation.RETRY;
  }

  public ExporterException(final String message, final Throwable cause) {
    super(message, cause);
    compensation = Compensation.RETRY;
  }

  /**
   * Creates an exporter exception with an explicit compensation hint. When {@code compensation} is
   * {@link Compensation#REOPEN}, the {@link
   * io.camunda.zeebe.broker.exporter.stream.ExporterContainer} will close and reopen the exporter
   * instead of simply logging the failure and skipping the record.
   */
  public ExporterException(
      final String message, final Throwable cause, @NonNull final Compensation compensation) {
    super(message, cause);
    this.compensation = compensation;
  }

  /**
   * Creates an exporter exception with an explicit compensation hint and no cause. When {@code
   * compensation} is {@link Compensation#REOPEN}, the {@link
   * io.camunda.zeebe.broker.exporter.stream.ExporterContainer} will close and reopen the exporter
   * instead of simply logging the failure and skipping the record.
   */
  public ExporterException(final String message, @NonNull final Compensation compensation) {
    super(message);
    this.compensation = compensation;
  }

  /**
   * Returns the compensation action the caller should take, or {@code null} if no specific action
   * is requested (the exception will be logged and the record skipped).
   */
  public Compensation getCompensation() {
    return compensation;
  }

  /**
   * Describes the compensation action the {@link
   * io.camunda.zeebe.broker.exporter.stream.ExporterContainer} should take when this exception is
   * caught.
   */
  public enum Compensation {
    /**
     * Close and reopen the exporter to re-synchronise its state (e.g. re-read the authoritative DB
     * position after a detected divergence).
     */
    REOPEN,

    /**
     * Retry the failed operation without reopening the exporter (e.g. transient network
     * connectivity issues).
     */
    RETRY,
  }
}
