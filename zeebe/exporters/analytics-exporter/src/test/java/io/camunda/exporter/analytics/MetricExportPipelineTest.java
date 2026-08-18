/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.common.Attributes;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Asserts the metric leg of the production pipeline reaches the wire: a synthetic counter increment
 * plus {@link OtelSdkManager#flushMetrics()} drives the real {@link ManualMetricReader} and {@code
 * OtlpHttpMetricExporter} to POST {@code /v1/metrics} with the identity headers, and an empty
 * window POSTs nothing.
 *
 * <p>Counter-independent by construction: the increment uses a synthetic name, never a production
 * metric-name constant, so dropping a business counter cannot silently delete this coverage. The
 * differential pair (one POST vs zero POSTs on identical wiring) is falsifiable in both directions
 * without mutating production source: a dead pipeline fails the first assertion, an unconditional
 * one fails the second.
 *
 * <p>Uses the unmodified production {@link OtelSdkManager} (no subclass, no override) against an
 * in-JVM {@link HttpServer}, so serialisation and the OTLP HTTP transport are exercised. The
 * remaining gap, that no collector decodes the protobuf body, is tracked separately.
 */
final class MetricExportPipelineTest {

  private static final String METRICS_PATH = "/v1/metrics";
  private static final String COUNTER_NAME = "test.pipeline.counter";

  private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
  private HttpServer server;
  private OtelSdkManager manager;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          try (final InputStream body = exchange.getRequestBody()) {
            body.readAllBytes();
          }
          requests.add(
              new RecordedRequest(
                  exchange.getRequestURI().getPath(),
                  exchange
                      .getRequestHeaders()
                      .getFirst(AnalyticsExporterContext.HEADER_FINGERPRINT),
                  exchange
                      .getRequestHeaders()
                      .getFirst(AnalyticsExporterContext.HEADER_CLUSTER_ID)));
          // Empty body is a valid empty ExportMetricsServiceResponse, so the exporter does not
          // retry.
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    server.start();

    final var config =
        new AnalyticsExporterConfig()
            .setEndpoint("http://127.0.0.1:" + server.getAddress().getPort());
    final var context =
        AnalyticsExporterContext.create("test-license", "e2e-test-cluster", 1, "", "");
    manager = new OtelSdkManager().initialize(config, context, new AnalyticsExporterMetadata());
  }

  @AfterEach
  void tearDown() {
    if (manager != null) {
      manager.close();
    }
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  @Timeout(30)
  void shouldPostMetricsToOtlpEndpointWhenWindowHasEvents() {
    // given a recorded metric event in the window
    manager.incrementMetric(COUNTER_NAME, 100L, 1000L, Attributes.empty());

    // when the window is flushed through the production reader and exporter
    manager.flushMetrics();

    // then exactly one POST reaches /v1/metrics carrying the identity headers. Asserted before
    // close(), whose own flush must not be counted.
    Awaitility.await("OTLP metrics POST reaches the endpoint")
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(() -> assertThat(metricsRequests()).hasSize(1));

    final var request = metricsRequests().get(0);
    assertThat(request.fingerprint()).isNotBlank();
    assertThat(request.clusterId()).isEqualTo("e2e-test-cluster");
  }

  @Test
  @Timeout(30)
  void shouldNotPostWhenWindowIsEmpty() {
    // given no metric event recorded

    // when the empty window is flushed
    manager.flushMetrics();

    // then the reader short-circuits and nothing reaches the wire. pollDelay gives any async export
    // time to arrive before the negative assertion.
    Awaitility.await("no OTLP metrics POST is sent for an empty window")
        .pollDelay(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(metricsRequests()).isEmpty());
  }

  private List<RecordedRequest> metricsRequests() {
    return requests.stream().filter(r -> METRICS_PATH.equals(r.path())).toList();
  }

  private record RecordedRequest(String path, String fingerprint, String clusterId) {}
}
