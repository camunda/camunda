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
package io.camunda.client.impl.backpressure;

import io.grpc.Status;

/**
 * Decides whether a failed attempt is a backpressure signal of the cluster, and whether it is worth
 * another attempt.
 *
 * <p>Only rejections that the cluster issues before it processes a command are retried, so a retry
 * cannot run a command twice. Failures that leave it open whether the command was processed, like a
 * timeout or a connection that broke while the request was in flight, are never retried here.
 */
public final class BackpressureSignals {

  static final String RESOURCE_EXHAUSTED = "RESOURCE_EXHAUSTED";

  private BackpressureSignals() {}

  /** What to do about a failed attempt. */
  public enum Failure {
    /** The cluster signals backpressure: retry, and let the signal shrink the concurrency limit. */
    BACKPRESSURE,
    /** Retrying would not help or is not safe. */
    PERMANENT
  }

  /**
   * Classifies a failed REST response.
   *
   * <ul>
   *   <li>429 is backpressure.
   *   <li>503 is backpressure if the problem title is {@code RESOURCE_EXHAUSTED}, which is how the
   *       gateway reports a rejection by the broker. Any other 503 is not retried: it can be the
   *       timeout of the gateway that fires after the command was already started.
   *   <li>500 is backpressure if the problem title or detail contains {@code RESOURCE_EXHAUSTED}.
   * </ul>
   *
   * @param title the title of the problem the response carried, or {@code null}
   * @param detail the detail of the problem the response carried, or {@code null}
   */
  public static Failure classifyHttp(final int status, final String title, final String detail) {
    switch (status) {
      case 429:
        return Failure.BACKPRESSURE;
      case 503:
        return RESOURCE_EXHAUSTED.equals(title) ? Failure.BACKPRESSURE : Failure.PERMANENT;
      case 500:
        return contains(title, RESOURCE_EXHAUSTED) || contains(detail, RESOURCE_EXHAUSTED)
            ? Failure.BACKPRESSURE
            : Failure.PERMANENT;
      default:
        return Failure.PERMANENT;
    }
  }

  /**
   * Classifies a failed gRPC call: {@code RESOURCE_EXHAUSTED} is backpressure, unless it reports
   * that a message exceeded the size limit of the client or the server, which is a property of the
   * request and not of the load.
   */
  public static Failure classifyGrpc(final Status status) {
    if (status.getCode() != Status.Code.RESOURCE_EXHAUSTED
        || contains(status.getDescription(), "exceeds maximum size")) {
      return Failure.PERMANENT;
    }
    return Failure.BACKPRESSURE;
  }

  private static boolean contains(final String text, final String part) {
    return text != null && text.contains(part);
  }
}
