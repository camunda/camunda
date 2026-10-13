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
package io.camunda.client.impl.http;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.observation.HttpClientObservationSupport;
import org.apache.hc.client5.http.observation.MetricConfig;
import org.apache.hc.client5.http.observation.ObservingOptions;
import org.apache.hc.client5.http.observation.binder.ConnPoolMetersAsync;

/**
 * Binds the Apache HttpClient request and connection pool metrics to a {@link MeterRegistry}. This
 * class is the only place that references the optional {@code httpclient5-observation} module and
 * is loaded only when a registry is configured.
 */
final class HttpClientMetrics {

  static final String PREFIX = "camunda.client.http";

  private HttpClientMetrics() {}

  static void enable(final HttpAsyncClientBuilder builder, final MeterRegistry registry) {
    final MetricConfig metricConfig = MetricConfig.builder().prefix(PREFIX).build();
    HttpClientObservationSupport.enable(
        builder, null, registry, ObservingOptions.DEFAULT, metricConfig);
    ConnPoolMetersAsync.bindTo(builder, registry, metricConfig);
  }
}
