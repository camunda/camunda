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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

final class MicrometerClientMetricsTest {

  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  @Test
  void shouldTagHttpClientMetricsWithClientAndPhysicalTenant() {
    // given
    final ClientMetrics metrics = new MicrometerClientMetrics(meterRegistry, "risk");
    final HttpAsyncClientBuilder builder = newHttpClientBuilder();

    // when
    metrics.instrumentHttpClient(builder);

    // then
    assertThat(meterRegistry.getMeters())
        .isNotEmpty()
        .allSatisfy(
            meter -> {
              assertThat(meter.getId().getName()).startsWith("http.client.");
              assertThat(meter.getId().getTag("client")).isEqualTo("camunda");
              assertThat(meter.getId().getTag("physicalTenantId")).isEqualTo("risk");
            });
  }

  @Test
  void shouldSkipHttpClientMetricsWhenObservationModuleMismatchesHttpClient() {
    // given — e.g. httpclient5-observation 5.6 next to an httpclient5 5.5 pinned by Spring Boot 3.5
    final ClientMetrics metrics =
        new MicrometerClientMetrics(
            meterRegistry,
            null,
            (builder, registry, tags) -> {
              throw new NoSuchMethodError("HttpAsyncClientBuilder.getConnManager()");
            });

    // when / then — the client is still created, only without HTTP client metrics
    assertThatCode(() -> metrics.instrumentHttpClient(newHttpClientBuilder()))
        .doesNotThrowAnyException();
    assertThat(meterRegistry.getMeters()).isEmpty();
  }

  @Test
  void shouldReportIoThreadCount() {
    // given
    final ClientMetrics metrics = new MicrometerClientMetrics(meterRegistry, null);

    // when
    metrics.ioThreadCount(3);

    // then
    assertThat(
            meterRegistry
                .get("camunda.client.http.io.threads")
                .tag("client", "camunda")
                .tag("physicalTenantId", "default")
                .gauge()
                .value())
        .isEqualTo(3);
  }

  @Test
  void shouldRemoveOnlyMetersOfClosedClient() {
    // given
    final ClientMetrics metrics = new MicrometerClientMetrics(meterRegistry, "risk");
    final ClientMetrics otherTenantMetrics = new MicrometerClientMetrics(meterRegistry, "finance");
    metrics.instrumentHttpClient(newHttpClientBuilder());
    metrics.jobWorkerMetrics("payment").jobActivated(1);
    metrics.ioThreadCount(2);
    otherTenantMetrics.instrumentHttpClient(newHttpClientBuilder());
    // like the Spring starter's job worker meters: same name and tags, but no client tag
    final Counter starterWorkerCounter =
        meterRegistry.counter(
            "camunda.client.worker.job.activated", "type", "payment", "physicalTenantId", "risk");
    final Counter unrelated = meterRegistry.counter("unrelated");
    // an application's meter that happens to carry the same tags as the client's meters
    final Counter sameTags =
        meterRegistry.counter("orders", "client", "camunda", "physicalTenantId", "risk");

    // when
    metrics.close();

    // then
    assertThat(meterRegistry.getMeters())
        .contains(starterWorkerCounter, unrelated, sameTags)
        .anyMatch(meter -> "finance".equals(meter.getId().getTag("physicalTenantId")))
        .noneMatch(
            meter ->
                meter != sameTags
                    && "camunda".equals(meter.getId().getTag("client"))
                    && "risk".equals(meter.getId().getTag("physicalTenantId")));
  }

  @ParameterizedTest
  @CsvSource({"' risk ', risk", ", default"})
  void shouldTagJobWorkerMetricsWithJobTypeAndPhysicalTenant(
      final String physicalTenantId, final String expectedTag) {
    // given
    final ClientMetrics metrics = new MicrometerClientMetrics(meterRegistry, physicalTenantId);

    // when
    metrics.jobWorkerMetrics("payment").jobActivated(3);

    // then
    assertThat(
            meterRegistry
                .get("camunda.client.worker.job.activated")
                .tag("client", "camunda")
                .tag("type", "payment")
                .tag("physicalTenantId", expectedTag)
                .counter()
                .count())
        .isEqualTo(3);
  }

  private static HttpAsyncClientBuilder newHttpClientBuilder() {
    return HttpAsyncClients.custom()
        .setConnectionManager(PoolingAsyncClientConnectionManagerBuilder.create().build());
  }
}
