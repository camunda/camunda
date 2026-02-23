/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration.ProxyConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.nio.client.HttpAsyncClient;
import org.apache.http.protocol.BasicHttpContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.opensearch.client.RestClient;

@Execution(ExecutionMode.SAME_THREAD)
final class RestClientFactoryProxyTest {

  private static final String PROXY_USERNAME = "proxyuser";
  private static final String PROXY_PASSWORD = "proxypass";

  // Simulated OS cluster info response
  private static final String CLUSTER_INFO_RESPONSE =
      """
      {
        "name": "wiremock-node",
        "cluster_name": "wiremock-cluster",
        "cluster_uuid": "test-uuid",
        "version": {
          "number": "2.11.0",
          "build_type": "docker",
          "build_hash": "abc123",
          "build_date": "2024-01-01T00:00:00.000Z",
          "build_snapshot": false,
          "lucene_version": "9.7.0",
          "minimum_wire_compatibility_version": "7.10.0",
          "minimum_index_compatibility_version": "7.0.0"
        },
        "tagline": "The OpenSearch Project: https://opensearch.org/"
      }
      """;

  private final OpensearchExporterConfiguration config = new OpensearchExporterConfiguration();

  // WireMock acts as a proxy endpoint
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

  @Test
  void shouldRouteRequestsThroughProxy() {
    // given - proxy that accepts all requests
    stubSuccessResponse();

    configureProxy(false);

    // when
    sendRequest();

    // then - verify that request arrived at the proxy endpoint
    proxyServer.verify(anyRequestedFor(anyUrl()));
  }

  @Test
  void shouldSendProxyAuthorizationHeaderPreemptively() {
    // given
    stubSuccessResponse();

    configureProxy(true);

    final String expectedAuthValue =
        "Basic "
            + Base64.getEncoder()
                .encodeToString(
                    (PROXY_USERNAME + ":" + PROXY_PASSWORD).getBytes(StandardCharsets.UTF_8));

    // when
    sendRequest();

    // then - verify that the request arrived with Proxy-Authorization header (preemptively)
    proxyServer.verify(
        anyRequestedFor(anyUrl()).withHeader("Proxy-Authorization", equalTo(expectedAuthValue)));
  }

  @Test
  void shouldNotSendProxyAuthorizationHeaderWhenNoCredentials() {
    // given - proxy that accepts all requests without auth
    stubSuccessResponse();

    configureProxy(false);

    // when
    sendRequest();

    // then - verify that request arrived WITHOUT Proxy-Authorization header
    proxyServer.verify(anyRequestedFor(anyUrl()).withoutHeader("Proxy-Authorization"));
  }

  private void configureProxy(final boolean withAuth) {
    final ProxyConfiguration proxy = config.getProxy();
    proxy.setEnabled(true);
    proxy.setHost("localhost");
    proxy.setPort(proxyServer.port());
    if (withAuth) {
      proxy.setUsername(PROXY_USERNAME);
      proxy.setPassword(PROXY_PASSWORD);
    }

    // Use a non-routable IP so the test fails if proxy config is ignored
    config.url = "http://192.0.2.1:9200";
  }

  /** Stub that returns a successful OS-like response for all requests. */
  private void stubSuccessResponse() {
    proxyServer.stubFor(
        any(anyUrl())
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(CLUSTER_INFO_RESPONSE)));
  }

  private void sendRequest() {
    try (final var client = RestClientFactory.of(config)) {
      final HttpAsyncClient httpClient = getHttpClient(client);
      final var context = new BasicHttpContext();
      httpClient
          .execute(HttpHost.create("http://192.0.2.1:9200"), new HttpGet("/"), context, null)
          .get();
    } catch (final Exception e) {
      // The request might fail with connection issues, but that's fine -
      // what matters is that it was routed through the proxy (WireMock)
    }
  }

  private static HttpAsyncClient getHttpClient(final RestClient client) throws Exception {
    final var field = client.getClass().getDeclaredField("client");
    field.setAccessible(true);
    return (HttpAsyncClient) field.get(client);
  }
}
