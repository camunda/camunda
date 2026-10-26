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

import java.util.Map;
import java.util.Objects;

/** A probe worker's answer to a probe (alpha). */
public final class ProbeAnswer {

  /** The longest message an answer may carry. */
  public static final int MAX_MESSAGE_LENGTH = 500;

  private final Outcome outcome;
  private final Map<String, Object> data;
  private final String message;

  private ProbeAnswer(final Outcome outcome, final Map<String, Object> data, final String message) {
    this.outcome = outcome;
    this.data = data;
    this.message = message;
  }

  /** The worker answered; {@code data} is the result of the operation. */
  public static ProbeAnswer ok(final Map<String, Object> data) {
    return new ProbeAnswer(Outcome.OK, Objects.requireNonNull(data, "data"), null);
  }

  /** The worker cannot answer this probe. */
  public static ProbeAnswer unsupported() {
    return new ProbeAnswer(Outcome.UNSUPPORTED, null, null);
  }

  /** The handler failed; the message is cut to {@value #MAX_MESSAGE_LENGTH} characters. */
  public static ProbeAnswer error(final String message) {
    final String text = message == null ? "" : message;
    return new ProbeAnswer(
        Outcome.ERROR,
        null,
        text.length() > MAX_MESSAGE_LENGTH ? text.substring(0, MAX_MESSAGE_LENGTH) : text);
  }

  public Outcome getOutcome() {
    return outcome;
  }

  public Map<String, Object> getData() {
    return data;
  }

  public String getMessage() {
    return message;
  }

  public enum Outcome {
    OK,
    UNSUPPORTED,
    ERROR
  }
}
