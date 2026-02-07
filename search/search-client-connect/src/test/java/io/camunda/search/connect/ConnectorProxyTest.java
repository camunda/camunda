/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.search.connect.configuration.ProxyConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.connect.plugin.PluginRepository;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

@Execution(ExecutionMode.SAME_THREAD)
class ConnectorProxyTest {

  private static final String PROXY_USERNAME = "proxyuser";
  private static final String PROXY_PASSWORD = "proxypass";
  private static final String OS_ADMIN_USER = "admin";
  private static final String OS_ADMIN_PASSWORD = "admin";

  // Simulated ES/OS cluster info response
  private static final String CLUSTER_INFO_RESPONSE =
      """
      {
        "name": "wiremock-node",
        "cluster_name": "wiremock-cluster",
        "cluster_uuid": "test-uuid",
        "version": {
          "number": "8.15.0",
          "build_type": "docker",
          "build_hash": "abc123",
          "build_date": "2024-01-01T00:00:00.000Z",
          "build_snapshot": false,
          "lucene_version": "9.10.0",
          "minimum_wire_compatibility_version": "7.17.0",
          "minimum_index_compatibility_version": "7.0.0"
        },
        "tagline": "You Know, for Search"
      }
      """;

  // WireMock acts as a proxy endpoint - receives the requests and verifies headers
  private WireMockServer proxyServer;

  @BeforeEach
  void setUp() {
    proxyServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    proxyServer.start();
  }

  @AfterEach
  void tearDown() {
    if (proxyServer != null) {
      proxyServer.stop();
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = DatabaseType.class,
      names = {"ELASTICSEARCH", "OPENSEARCH"})
  void shouldRouteRequestsThroughProxy(final DatabaseType databaseType) throws Exception {
    // given - proxy that accepts all requests
    stubSuccessResponse();

    final var proxy = createProxyConfig(false);

    // when
    pingDatabase(databaseType, proxy);

    // then - verify that request arrived at the proxy endpoint
    proxyServer.verify(anyRequestedFor(anyUrl()));
  }

  @ParameterizedTest
  @EnumSource(
      value = DatabaseType.class,
      names = {"ELASTICSEARCH", "OPENSEARCH"})
  void shouldSendProxyAuthorizationHeaderPreemptively(final DatabaseType databaseType)
      throws Exception {
    // given
    stubSuccessResponse();

    final var proxy = createProxyConfig(true);

    final String expectedAuthValue =
        "Basic "
            + Base64.getEncoder()
                .encodeToString(
                    (PROXY_USERNAME + ":" + PROXY_PASSWORD).getBytes(StandardCharsets.UTF_8));

    // when
    pingDatabase(databaseType, proxy);

    // then - verify that the request arrived with Proxy-Authorization header (preemptively)
    proxyServer.verify(
        anyRequestedFor(anyUrl()).withHeader("Proxy-Authorization", equalTo(expectedAuthValue)));
  }

  @ParameterizedTest
  @EnumSource(
      value = DatabaseType.class,
      names = {"ELASTICSEARCH", "OPENSEARCH"})
  void shouldNotSendProxyAuthorizationHeaderWhenNoCredentials(final DatabaseType databaseType)
      throws Exception {
    // given - proxy that accepts all requests without auth
    stubSuccessResponse();

    final var proxy = createProxyConfig(false);

    // when
    pingDatabase(databaseType, proxy);

    // then - verify that request arrived WITHOUT Proxy-Authorization header
    proxyServer.verify(anyRequestedFor(anyUrl()).withoutHeader("Proxy-Authorization"));
  }

  private ProxyConfiguration createProxyConfig(final boolean withAuth) {
    final var proxy = new ProxyConfiguration();
    proxy.setEnabled(true);
    proxy.setHost("localhost");
    proxy.setPort(proxyServer.port());
    if (withAuth) {
      proxy.setUsername(PROXY_USERNAME);
      proxy.setPassword(PROXY_PASSWORD);
    }
    return proxy;
  }

  /** Stub that returns a successful ES/OS-like response for all requests. */
  private void stubSuccessResponse() {
    // X-Elastic-Product header is required by the ES client to verify it's talking to Elasticsearch
    proxyServer.stubFor(
        any(anyUrl())
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withHeader("X-Elastic-Product", "Elasticsearch")
                    .withBody(CLUSTER_INFO_RESPONSE)));
  }

  private void pingDatabase(final DatabaseType databaseType, final ProxyConfiguration proxy)
      throws Exception {
    // The connector is configured to use a fake backend URL, but with proxy pointing to WireMock.
    // Since the proxy is set, Apache HttpClient will connect to the proxy (WireMock) instead.
    // We use a non-routable IP to ensure the test fails if proxy config is ignored.
    final var fakeBackendUrl = "http://192.0.2.1:9200"; // TEST-NET-1, non-routable

    final var configuration = new ConnectConfiguration();
    configuration.setUrl(fakeBackendUrl);
    configuration.setProxy(proxy);

    switch (databaseType) {
      case ELASTICSEARCH -> {
        final var connector =
            new ElasticsearchConnector(configuration, new ObjectMapper(), new PluginRepository());
        final var client = connector.createClient();

        assertThat(client.ping().value()).isTrue();
      }
      case OPENSEARCH -> {
        configuration.setUsername(OS_ADMIN_USER);
        configuration.setPassword(OS_ADMIN_PASSWORD);

        // Use null for AWS credentials provider to avoid AWS transport
        final AwsCredentialsProvider nullAwsCredentials = null;
        final var connector =
            new OpensearchConnector(
                configuration, new ObjectMapper(), nullAwsCredentials, new PluginRepository());
        final var client = connector.createClient();

        assertThat(client.ping().value()).isTrue();
      }
      default -> throw new IllegalArgumentException("Unsupported database type: " + databaseType);
    }
  }
}
