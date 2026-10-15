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
import io.camunda.client.impl.Loggers;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.slf4j.Logger;

/**
 * {@link ClientMetrics} backed by a Micrometer registry. Only loaded when a registry was set on the
 * client builder, so Micrometer is guaranteed to be on the class path.
 */
public final class MicrometerClientMetrics implements ClientMetrics {

  /** Tag added to the HTTP client meters, to tell them apart from other HTTP clients' meters. */
  static final String CLIENT_TAG_KEY = "client";

  static final String CLIENT_TAG_VALUE = "camunda";

  /**
   * Tag added to all client meters, so that the clients of different physical tenants, e.g. one per
   * tenant in the Spring Boot starter, can share a registry. Same key as the starter's job worker
   * metrics.
   */
  static final String PHYSICAL_TENANT_TAG_KEY = "physicalTenantId";

  /**
   * Physical tenant tag value when the client has none, matching the worker metrics of the Spring
   * Boot starter so both can share a registry.
   */
  static final String DEFAULT_PHYSICAL_TENANT_ID = "default";

  /** Prefix of the meters of the observation module, its default in {@code MetricConfig}. */
  static final String HTTP_METER_PREFIX = "http.client.";

  /** Prefix of the job worker meters, see {@code MicrometerJobWorkerMetricsBuilder.Names}. */
  static final String WORKER_METER_PREFIX = "camunda.client.worker.";

  private static final Logger LOG = Loggers.LOGGER;

  private static final String HTTP_OBSERVATION_CLASS =
      "org.apache.hc.client5.http.observation.HttpClientObservationSupport";

  // log the reason for missing HTTP client metrics once, not once per client
  private static final AtomicBoolean HTTP_METRICS_UNAVAILABLE_LOGGED = new AtomicBoolean();

  private final MeterRegistry meterRegistry;
  private final String physicalTenantId;
  private final Tags clientTags;
  private final HttpMetricsBinder httpMetricsBinder;

  public MicrometerClientMetrics(final MeterRegistry meterRegistry, final String physicalTenantId) {
    // a lambda rather than a method reference, so that the observation binder is only linked when
    // the observation module was found
    this(
        meterRegistry,
        physicalTenantId,
        (builder, registry, tags) -> HttpClientObservationBinder.bind(builder, registry, tags));
  }

  /** Lets tests replace the binding of the observation module, e.g. to fail like a mismatch. */
  MicrometerClientMetrics(
      final MeterRegistry meterRegistry,
      final String physicalTenantId,
      final HttpMetricsBinder httpMetricsBinder) {
    this.meterRegistry = Objects.requireNonNull(meterRegistry, "must specify a meter registry");
    this.httpMetricsBinder = httpMetricsBinder;
    this.physicalTenantId =
        physicalTenantId == null || physicalTenantId.trim().isEmpty()
            ? DEFAULT_PHYSICAL_TENANT_ID
            : physicalTenantId.trim();
    clientTags =
        Tags.of(CLIENT_TAG_KEY, CLIENT_TAG_VALUE, PHYSICAL_TENANT_TAG_KEY, this.physicalTenantId);
  }

  @Override
  public void instrumentHttpClient(final HttpAsyncClientBuilder builder) {
    if (!isHttpObservationAvailable()) {
      logHttpMetricsUnavailable(
          "Exposing no HTTP client metrics, as org.apache.httpcomponents.client5:httpclient5-observation is not on the class path",
          null);
      return;
    }

    try {
      httpMetricsBinder.bind(builder, meterRegistry, clientTags);
    } catch (final LinkageError e) {
      // the observation module and httpclient5 are released together; a mismatch, e.g. an older
      // httpclient5 pinned by a dependency management, must not prevent creating the client
      logHttpMetricsUnavailable(
          "Exposing no HTTP client metrics, as the httpclient5-observation version on the class path does not match the httpclient5 version; use the same version for both",
          e);
    }
  }

  @Override
  public JobWorkerMetrics jobWorkerMetrics(final String jobType) {
    return JobWorkerMetrics.micrometer()
        .withMeterRegistry(meterRegistry)
        // tagged as client meters too, so that closing the client removes them, but not the
        // worker meters of the Spring starter, which share name and the other tags
        .withTags(clientTags.and("type", jobType))
        .build();
  }

  @Override
  public void close() {
    // all meters of this client carry both tags, whether registered here or by the observation
    // module, but so may meters of the application: only remove those with the names of client
    // meters. getMeters() returns a copy, so removing while iterating is safe.
    for (final Meter meter : meterRegistry.getMeters()) {
      final Meter.Id id = meter.getId();
      if (isClientMeterName(id.getName())
          && CLIENT_TAG_VALUE.equals(id.getTag(CLIENT_TAG_KEY))
          && physicalTenantId.equals(id.getTag(PHYSICAL_TENANT_TAG_KEY))) {
        meterRegistry.remove(meter);
      }
    }
  }

  private static boolean isClientMeterName(final String name) {
    return name.startsWith(HTTP_METER_PREFIX) || name.startsWith(WORKER_METER_PREFIX);
  }

  private static boolean isHttpObservationAvailable() {
    try {
      Class.forName(HTTP_OBSERVATION_CLASS, false, MicrometerClientMetrics.class.getClassLoader());
      return true;
    } catch (final ClassNotFoundException e) {
      return false;
    }
  }

  private static void logHttpMetricsUnavailable(final String message, final Throwable cause) {
    if (HTTP_METRICS_UNAVAILABLE_LOGGED.compareAndSet(false, true)) {
      if (cause == null) {
        LOG.info(message);
      } else {
        LOG.warn(message, cause);
      }
    }
  }

  /** Binds the HTTP client meters of the observation module to a client builder. */
  @FunctionalInterface
  interface HttpMetricsBinder {
    void bind(HttpAsyncClientBuilder builder, MeterRegistry meterRegistry, Iterable<Tag> tags);
  }
}
