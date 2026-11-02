/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.os;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.direct.DirectCallHttpServerFactory;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.OpenSearchConfiguration;
import io.camunda.optimize.service.util.configuration.db.DatabaseConnection;
import io.camunda.optimize.service.util.configuration.elasticsearch.DatabaseConnectionNodeConfiguration;
import io.camunda.search.connect.plugin.PluginRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Reproduces the Optimize 8.6.28 OpenSearch startup hang: with httpclient5 5.6.4 the client fails
 * to decode a gzip-encoded response from OpenSearch ({@code ZipException: Not in GZIP format}), so
 * {@code cluster().health()} never succeeds.
 */
class OpenSearchGzipResponseTest {

  private static final String HEALTH_JSON =
      "{\"cluster_name\":\"opensearch\",\"status\":\"green\",\"timed_out\":false,"
          + "\"number_of_nodes\":1,\"number_of_data_nodes\":1,\"active_primary_shards\":0,"
          + "\"active_shards\":0,\"relocating_shards\":0,\"initializing_shards\":0,"
          + "\"unassigned_shards\":0,\"delayed_unassigned_shards\":0,"
          + "\"number_of_pending_tasks\":0,\"number_of_in_flight_fetch\":0,"
          + "\"task_max_waiting_in_queue_millis\":0,\"active_shards_percent_as_number\":100.0}";

  private static WireMockServer wireMockServer;

  @BeforeAll
  static void before() throws IOException {
    final DirectCallHttpServerFactory factory = new DirectCallHttpServerFactory();
    wireMockServer =
        new WireMockServer(
            WireMockConfiguration.wireMockConfig().dynamicPort().httpServerFactory(factory));
    wireMockServer.start();
    wireMockServer.stubFor(
        get(urlPathEqualTo("/_cluster/health"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json; charset=UTF-8")
                    .withHeader("Content-Encoding", "gzip")
                    .withBody(gzip(HEALTH_JSON))));
  }

  @AfterAll
  static void after() {
    wireMockServer.stop();
    wireMockServer.shutdown();
  }

  @Test
  void clusterHealth_decodesGzipResponseFromOpenSearch() throws Exception {
    // given
    final var config = mock(ConfigurationService.class);
    final var osConfig = mock(OpenSearchConfiguration.class);
    final var connection = mock(DatabaseConnection.class);
    final var host = new DatabaseConnectionNodeConfiguration();
    host.setHost("localhost");
    host.setHttpPort(wireMockServer.port());

    when(config.getOpenSearchConfiguration()).thenReturn(osConfig);
    when(osConfig.getInterceptorPlugins()).thenReturn(null);
    when(connection.getAwsEnabled()).thenReturn(false);
    when(osConfig.getConnectionNodes()).thenReturn(List.of(host));
    when(osConfig.getConnection()).thenReturn(connection);

    final var client =
        OpenSearchClientBuilder.buildOpenSearchClientFromConfig(config, new PluginRepository());

    // when
    final var numberOfNodes = client.cluster().health().numberOfNodes();

    // then
    Assertions.assertThat(numberOfNodes).isEqualTo(1);
  }

  private static byte[] gzip(final String value) throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (final GZIPOutputStream gzip = new GZIPOutputStream(out)) {
      gzip.write(value.getBytes(StandardCharsets.UTF_8));
    }
    return out.toByteArray();
  }
}
