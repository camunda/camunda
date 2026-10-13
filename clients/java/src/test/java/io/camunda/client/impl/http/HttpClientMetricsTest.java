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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

import com.sun.net.httpserver.HttpServer;
import io.camunda.client.CamundaClient;
import io.camunda.client.impl.CamundaClientBuilderImpl;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class HttpClientMetricsTest {

  private HttpServer server;
  private URI restAddress;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          final byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    restAddress = URI.create("http://localhost:" + server.getAddress().getPort());
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  private CamundaClient newClient(final MeterRegistry registry) {
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    builder
        .restAddress(restAddress)
        .grpcAddress(URI.create("http://localhost:1"))
        .preferRestOverGrpc(true);
    if (registry != null) {
      builder.withMeterRegistry(registry);
    }
    return builder.build();
  }

  private static void sendRequest(final CamundaClient client) {
    try {
      client.newTopologyRequest().send().join();
    } catch (final RuntimeException ignored) {
      // the response content is irrelevant, only that a request went through the HTTP client
    }
  }

  private static List<String> meterNames(final MeterRegistry registry) {
    return registry.getMeters().stream()
        .map(Meter::getId)
        .map(Meter.Id::getName)
        .collect(Collectors.toList());
  }

  @Test
  void shouldRecordRequestTimerWithPrefixAfterRequest() {
    // given
    final MeterRegistry registry = new SimpleMeterRegistry();

    try (final CamundaClient client = newClient(registry)) {
      // when
      sendRequest(client);

      // then
      assertThat(meterNames(registry))
          .isNotEmpty()
          .allMatch(name -> name.startsWith(HttpClientMetrics.PREFIX));
      await()
          .untilAsserted(
              () ->
                  assertThat(registry.find(HttpClientMetrics.PREFIX + ".request").timers())
                      .as("a request timer records the request")
                      .isNotEmpty()
                      .allSatisfy(timer -> assertThat(timer.count()).isPositive()));
    }
  }

  @Test
  void shouldRegisterPoolMetersWithoutAnyRequest() {
    // given
    final MeterRegistry registry = new SimpleMeterRegistry();

    // when
    try (final CamundaClient ignored = newClient(registry)) {
      // then
      assertThat(meterNames(registry)).anyMatch(name -> name.contains("pool"));
    }
  }

  @Test
  void shouldAllowTwoClientsToShareOneRegistry() {
    // given
    final MeterRegistry registry = new SimpleMeterRegistry();

    // when / then
    assertThatCode(
            () -> {
              try (final CamundaClient first = newClient(registry);
                  final CamundaClient second = newClient(registry)) {
                sendRequest(first);
                sendRequest(second);
              }
            })
        .doesNotThrowAnyException();
  }
}
