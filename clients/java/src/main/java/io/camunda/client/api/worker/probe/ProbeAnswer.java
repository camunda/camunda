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
package io.camunda.client.api.worker.probe;

import io.camunda.client.api.response.ProbeOutcome;
import java.util.Objects;

/** The answer of a probe worker to a probe (alpha). */
public final class ProbeAnswer {

  /** The longest message an answer may carry; a longer one is cut. */
  public static final int MAX_MESSAGE_LENGTH = 500;

  /** The message of an {@code ERROR} answer whose message is empty, which the cluster refuses. */
  public static final String EMPTY_MESSAGE_FALLBACK = "The probe worker failed without a message.";

  private final ProbeOutcome outcome;
  private final Object data;
  private final String message;

  private ProbeAnswer(final ProbeOutcome outcome, final Object data, final String message) {
    this.outcome = outcome;
    this.data = data;
    this.message = message;
  }

  /**
   * The worker answered.
   *
   * @param data the result, a map or an object that serializes to a JSON object; its shape is what
   *     the worker of the probe ID defines
   */
  public static ProbeAnswer ok(final Object data) {
    return new ProbeAnswer(ProbeOutcome.OK, Objects.requireNonNull(data, "data"), null);
  }

  /** The worker cannot answer this input. */
  public static ProbeAnswer unsupported() {
    return new ProbeAnswer(ProbeOutcome.UNSUPPORTED, null, null);
  }

  /**
   * The worker failed to answer.
   *
   * @param message why, cut to {@value #MAX_MESSAGE_LENGTH} characters, or {@value
   *     #EMPTY_MESSAGE_FALLBACK} if empty; it ends up in front of a user, so it must not hold
   *     secret values
   */
  public static ProbeAnswer error(final String message) {
    final String text =
        Objects.requireNonNull(message, "message").isEmpty() ? EMPTY_MESSAGE_FALLBACK : message;
    return new ProbeAnswer(
        ProbeOutcome.ERROR,
        null,
        text.length() > MAX_MESSAGE_LENGTH ? text.substring(0, MAX_MESSAGE_LENGTH) : text);
  }

  public ProbeOutcome getOutcome() {
    return outcome;
  }

  /**
   * @return the result if the outcome is {@link ProbeOutcome#OK}; null otherwise
   */
  public Object getData() {
    return data;
  }

  /**
   * @return the reason if the outcome is {@link ProbeOutcome#ERROR}; null otherwise
   */
  public String getMessage() {
    return message;
  }

  @Override
  public String toString() {
    return "ProbeAnswer{outcome=" + outcome + ", message=" + message + "}";
  }
}
