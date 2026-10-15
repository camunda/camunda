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
package io.camunda.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.metrics.CamundaClientMetrics;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.client.impl.CamundaClientImpl;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.http.HttpClientFactory;
import io.camunda.client.impl.metrics.ClientMetrics;
import io.camunda.client.impl.metrics.MicrometerClientMetrics;
import io.camunda.client.impl.util.JobWorkerExecutors;
import io.camunda.client.protocol.rest.JobActivationResult;
import io.camunda.client.util.ClientRestTest;
import io.grpc.ManagedChannel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.search.Search;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class ClientMetricsRestTest extends ClientRestTest {

  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private URI restAddress;
  private CamundaClient meteredClient;

  @BeforeEach
  void setUp(final WireMockRuntimeInfo mockInfo) {
    restAddress = URI.create(mockInfo.getHttpBaseUrl());
    gatewayService.onStatusRequestHealthy();
    gatewayService.onActivateJobsRequest(new JobActivationResult());
  }

  @AfterEach
  void tearDown() {
    if (meteredClient != null) {
      meteredClient.close();
    }
    meterRegistry.close();
  }

  @Test
  void shouldRecordHttpRequestTimings() {
    // given
    meteredClient = newClientBuilder(meterRegistry).build();

    // when
    meteredClient.newStatusRequest().send().join();

    // then — the timer is recorded when the exchange completes, which may lag the response future
    await()
        .untilAsserted(
            () -> {
              final Timer timer =
                  clientMeters("http.client.request", "default")
                      .tag("method", "GET")
                      .tag("status", "200")
                      .timer();
              assertThat(timer).isNotNull();
              assertThat(timer.count()).isEqualTo(1);
              assertThat(clientMeters("http.client.response", "default").counter().count())
                  .isEqualTo(1);
              assertThat(
                      clientMeters("http.client.inflight", "default").tag("kind", "async").gauge())
                  .isNotNull();
            });
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http.client.pool.leased",
        "http.client.pool.pending",
        "http.client.pool.available"
      })
  void shouldExposeConnectionPoolGauge(final String gaugeName) {
    // when
    meteredClient = newClientBuilder(meterRegistry).build();

    // then
    assertThat(clientMeters(gaugeName, "default").gauge()).isNotNull();
  }

  @Test
  void shouldTagMetricsWithPhysicalTenantOfClient() {
    // when
    meteredClient = newClientBuilder(meterRegistry).physicalTenantId("risk").build();

    // then
    assertThat(clientMeters("http.client.pool.leased", "risk").gauge()).isNotNull();
  }

  @Test
  void shouldRecordNoMetricsWhenNoMetricsAreSet() {
    // given — the upstream observation module defaults to the global registry, so watch it
    final MeterRegistry globalRegistry = new SimpleMeterRegistry();
    Metrics.addRegistry(globalRegistry);
    try {
      meteredClient = newClientBuilder().build();

      // when
      meteredClient.newStatusRequest().send().join();
      try (final JobWorker ignored = openWorker(meteredClient, uniqueJobType())) {

        // then
        assertThat(globalRegistry.getMeters()).isEmpty();
      }
    } finally {
      Metrics.removeRegistry(globalRegistry);
      globalRegistry.close();
    }
  }

  @Test
  void shouldRemoveClientMetersWhenClientIsClosed() {
    // given
    final CamundaClient client = newClientBuilder(meterRegistry).build();
    client.newStatusRequest().send().join();
    final Counter unrelated = meterRegistry.counter("unrelated");

    // when
    client.close();

    // then
    assertThat(meterRegistry.getMeters()).containsExactly(unrelated);
  }

  @Test
  void shouldRemoveClientMetersWhenClientFailsToShutDown() throws Exception {
    // given — a gRPC channel that does not terminate in time, failing the close
    final CamundaClientConfiguration configuration =
        (CamundaClientConfiguration) newClientBuilder();
    final ManagedChannel channel = mock(ManagedChannel.class);
    final ClientMetrics clientMetrics = new MicrometerClientMetrics(meterRegistry, null);
    final HttpClient httpClient =
        new HttpClientFactory(configuration, clientMetrics).createClient();
    final CamundaClient client =
        new CamundaClientImpl(
            configuration,
            channel,
            CamundaClientImpl.buildGatewayStub(channel, configuration),
            new JobWorkerExecutors(Executors.newSingleThreadScheduledExecutor(), true),
            httpClient,
            clientMetrics);

    try {
      // when
      assertThatThrownBy(client::close).isInstanceOf(ClientException.class);

      // then
      assertThat(meterRegistry.getMeters()).isEmpty();
    } finally {
      httpClient.close();
    }
  }

  @Test
  void shouldBindLivePoolGaugesWhenClientIsRebuiltAfterClose() {
    // given
    final CamundaClient closedClient = newClientBuilder(meterRegistry).build();
    final Gauge closedClientGauge = clientMeters("http.client.pool.leased", "default").gauge();
    closedClient.close();

    // when
    meteredClient = newClientBuilder(meterRegistry).build();

    // then — the rebuilt client binds a gauge of its own, not the one of the closed client's pool
    final Gauge gauge = clientMeters("http.client.pool.leased", "default").gauge();
    assertThat(gauge).isNotNull().isNotSameAs(closedClientGauge);
    assertThat(gauge.value()).isZero();
  }

  @Test
  void shouldKeepMetersOfOtherPhysicalTenantWhenClientIsClosed() {
    // given
    final CamundaClient riskClient =
        newClientBuilder(meterRegistry).physicalTenantId("risk").build();
    meteredClient = newClientBuilder(meterRegistry).physicalTenantId("finance").build();

    // when
    riskClient.close();

    // then
    assertThat(clientMeters("http.client.pool.leased", "risk").gauge()).isNull();
    assertThat(clientMeters("http.client.pool.leased", "finance").gauge()).isNotNull();
  }

  @Test
  void shouldSeparateClientsOfSamePhysicalTenantWithDerivedRegistries() {
    // given — the pattern documented on CamundaClientMetrics
    final CamundaClient firstClient = newClientBuilder(derivedRegistry("first")).build();
    meteredClient = newClientBuilder(derivedRegistry("second")).build();
    assertThat(meterRegistry.find("http.client.pool.leased").gauges())
        .extracting(gauge -> gauge.getId().getTag("name"))
        .containsExactlyInAnyOrder("first", "second");

    // when
    firstClient.close();

    // then
    assertThat(meterRegistry.getMeters())
        .isNotEmpty()
        .allSatisfy(meter -> assertThat(meter.getId().getTag("name")).isEqualTo("second"));
  }

  private static JobWorker openWorker(final CamundaClient client, final String jobType) {
    return client.newWorker().jobType(jobType).handler((c, j) -> {}).open();
  }

  private Search clientMeters(final String name, final String physicalTenantId) {
    return meterRegistry
        .find(name)
        .tag("client", "camunda")
        .tag("physicalTenantId", physicalTenantId);
  }

  private MeterRegistry derivedRegistry(final String clientName) {
    final CompositeMeterRegistry derivedRegistry = new CompositeMeterRegistry();
    derivedRegistry.config().commonTags("name", clientName);
    derivedRegistry.add(meterRegistry);
    return derivedRegistry;
  }

  private CamundaClientBuilder newClientBuilder(final MeterRegistry registry) {
    return newClientBuilder()
        .metrics(CamundaClientMetrics.micrometer().withMeterRegistry(registry).build());
  }

  private CamundaClientBuilder newClientBuilder() {
    return CamundaClient.newClientBuilder()
        .preferRestOverGrpc(true)
        .restAddress(restAddress)
        .defaultJobPollInterval(Duration.ofMinutes(1));
  }

  private static String uniqueJobType() {
    return "job-" + UUID.randomUUID();
  }
}
