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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.util.EnumSet;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.observation.HttpClientObservationSupport;
import org.apache.hc.client5.http.observation.MetricConfig;
import org.apache.hc.client5.http.observation.ObservingOptions;
import org.apache.hc.client5.http.observation.ObservingOptions.MetricSet;
import org.apache.hc.client5.http.observation.binder.ConnPoolMetersAsync;

/**
 * The only class touching the optional httpclient5-observation module. Only loaded once {@link
 * MicrometerClientMetrics} checked that the module is on the class path.
 */
final class HttpClientObservationBinder {

  private HttpClientObservationBinder() {}

  /**
   * Registers the connection pool gauges and the request timer of the upstream module, with its
   * meter names ({@code http.client.*}) and the given common tags.
   *
   * @throws LinkageError if the observation module does not match the httpclient5 version; nothing
   *     was registered then
   */
  static void bind(
      final HttpAsyncClientBuilder builder,
      final MeterRegistry meterRegistry,
      final Iterable<Tag> commonTags) {
    final MetricConfig metricConfig = MetricConfig.builder().addCommonTags(commonTags).build();

    // Bind the pool first: it needs HttpAsyncClientBuilder#getConnManager, which only exists since
    // httpclient5 5.6. Against an older httpclient5 this fails before anything was added to the
    // builder, so the client is created without HTTP metrics instead of half-instrumented.
    ConnPoolMetersAsync.bindTo(builder, meterRegistry, metricConfig);

    // no observation registry: only the timer is wanted, not observations for tracing
    HttpClientObservationSupport.enable(
        builder,
        null,
        meterRegistry,
        ObservingOptions.builder().metrics(EnumSet.of(MetricSet.BASIC)).build(),
        metricConfig);
  }
}
