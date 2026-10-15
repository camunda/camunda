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
package io.camunda.client.impl.metrics;

import io.camunda.client.api.worker.JobWorkerMetrics;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;

/**
 * Client-wide metrics, backed by the meter registry set on the client builder.
 *
 * <p>Micrometer is an optional dependency of the client, so this type must not mention any
 * Micrometer class: it is referenced from classes that are always loaded (and possibly introspected
 * by frameworks such as Spring), while the Micrometer-backed implementation is only loaded when a
 * registry was set.
 */
public interface ClientMetrics {

  /**
   * Instruments the REST client: request timings and connection pool gauges. Must be called after
   * the connection manager was set on the builder.
   */
  default void instrumentHttpClient(final HttpAsyncClientBuilder builder) {}

  /**
   * Reports the number of I/O threads of the REST client, which bounds how many connections it can
   * serve concurrently.
   */
  default void ioThreadCount(final int ioThreadCount) {}

  /**
   * Removes the meters of the client from the registry; called once the client is closed. Without
   * it, the gauges stay bound to the closed client and a client rebuilt with the same tags would
   * get them back instead of its own.
   */
  default void close() {}

  /**
   * Metrics for a job worker that does not configure its own.
   *
   * @param jobType the type of jobs the worker handles
   */
  default JobWorkerMetrics jobWorkerMetrics(final String jobType) {
    return JobWorkerMetrics.noop();
  }

  /** Returns metrics which record nothing; used when no meter registry is set. */
  static ClientMetrics noop() {
    return Noop.INSTANCE;
  }

  /** Holder for the shared no-op instance. */
  final class Noop {
    private static final ClientMetrics INSTANCE = new ClientMetrics() {};

    private Noop() {}
  }
}
