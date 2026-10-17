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
package io.camunda.client.api.metrics;

import io.camunda.client.impl.metrics.ClientMetricsFactory;
import io.camunda.client.impl.metrics.MicrometerCamundaClientMetricsBuilderImpl;

/**
 * The metrics a client records, set via {@link
 * io.camunda.client.CamundaClientBuilder#metrics(CamundaClientMetrics)}. Create them with {@link
 * #micrometer()} or {@link #noop()}; other implementations are not supported.
 *
 * <p>With {@link #micrometer() Micrometer} metrics, the client records:
 *
 * <ul>
 *   <li>REST request metrics of the httpclient5-observation module: the timer {@code
 *       http.client.request} and the counter {@code http.client.response}, both tagged with the
 *       request {@code method} and response {@code status} ({@code 599} without a response), and
 *       the gauge {@code http.client.inflight} of the requests in flight, tagged {@code
 *       kind=async}. All of them are measured from before a pooled connection is leased.
 *   <li>connection pool gauges of the same module: {@code http.client.pool.leased}, {@code
 *       http.client.pool.pending} and {@code http.client.pool.available}.
 * </ul>
 *
 * <p>These require {@code org.apache.httpcomponents.client5:httpclient5-observation} on the class
 * path, in the same version as {@code httpclient5}; without it, the client logs it once and records
 * none of them.
 *
 * <p>The client also records job worker metrics ({@code camunda.client.worker.*}) for every worker
 * that does not set its own via {@link
 * io.camunda.client.api.worker.JobWorkerBuilderStep1.JobWorkerBuilderStep3#metrics(io.camunda.client.api.worker.JobWorkerMetrics)}.
 *
 * <p>The gauge {@code camunda.client.http.io.threads} reports the number of I/O threads of the REST
 * client.
 *
 * <p>All these meters are tagged with {@code physicalTenantId}: the client's {@link
 * io.camunda.client.CamundaClientBuilder#physicalTenantId(String) physical tenant}, or {@code
 * default} without one. Clients of different physical tenants can therefore share a registry.
 *
 * <p>Clients of the same physical tenant must not share one registry: their meters would have
 * identical IDs, so only the first client's gauges would be reported and the request timers of all
 * of them would merge. Give each such client a registry derived from the shared one, which adds a
 * tag of its own and forwards to the shared registry:
 *
 * <pre>{@code
 * CompositeMeterRegistry clientRegistry = new CompositeMeterRegistry();
 * clientRegistry.config().commonTags("name", "orders");
 * clientRegistry.add(sharedRegistry);
 * CamundaClient client =
 *     CamundaClient.newClientBuilder()
 *         .metrics(CamundaClientMetrics.micrometer().withMeterRegistry(clientRegistry).build())
 *         .build();
 * }</pre>
 *
 * <p>Closing the client removes its meters from its registry: all meters tagged with {@code
 * client=camunda} and its physical tenant. Removing them from a derived registry as above also
 * removes them from the shared one, while the meters of the other clients stay.
 */
public interface CamundaClientMetrics {

  /**
   * Returns a new builder for metrics recorded to a Micrometer registry.
   *
   * @throws UnsupportedOperationException if Micrometer is not found in the class path
   */
  static MicrometerCamundaClientMetricsBuilder micrometer() {
    try {
      Class.forName("io.micrometer.core.instrument.MeterRegistry");
    } catch (final ClassNotFoundException e) {
      throw new UnsupportedOperationException(
          "Expected to create Micrometer client metrics, but it seems Micrometer is not in your classpath",
          e);
    }

    return new MicrometerCamundaClientMetricsBuilderImpl();
  }

  /** Returns metrics which record nothing, the default. */
  static CamundaClientMetrics noop() {
    return ClientMetricsFactory.noop();
  }
}
