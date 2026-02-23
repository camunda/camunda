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
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.BasicCredentials;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.camunda.plugin.search.header.CustomHeader;
import io.camunda.plugin.search.header.DatabaseCustomHeaderSupplier;
import io.camunda.search.connect.plugin.PluginConfiguration;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration.ProxyConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.apache.hc.core5.http.HttpHost;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5Transport;
import org.opensearch.client.transport.httpclient5.internal.Node;

@Execution(ExecutionMode.SAME_THREAD)
final class OpensearchConnectorTest {

  private static final String BASIC_USERNAME = "username";
  private static final String BASIC_PASSWORD = "password";
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
          "distribution" : "opensearch",
          "number": "2.19.0",
          "build_type": "docker",
          "build_hash": "abc123",
          "build_date": "2024-01-01T00:00:00.000Z",
          "build_snapshot": false,
          "lucene_version": "9.11.0",
          "minimum_wire_compatibility_version": "7.10.0",
          "minimum_index_compatibility_version": "7.0.0"
        },
        "tagline": "The OpenSearch Project: https://opensearch.org/"
      }
      """;

  private final OpensearchExporterConfiguration config = new OpensearchExporterConfiguration();

  @Test
  void shouldConfigureMultipleHosts() throws Exception {
    // given
    config.url = "http://localhost:9201,http://localhost:9202";

    // when
    final var client = OpensearchConnector.of(config).createClient();
    final var transport = (ApacheHttpClient5Transport) client._transport();

    // then
    assertThat(transport.getNodes())
        .hasSize(2)
        .extracting(Map::keySet)
        .asInstanceOf(InstanceOfAssertFactories.set(Node.class))
        .map(Node::getHost)
        .containsExactlyInAnyOrder(
            HttpHost.create("http://localhost:9201"), HttpHost.create("http://localhost:9202"));
  }

  @Nested
  final class WithWireMockTests {

    // WireMock acts as a proxy endpoint
    private WireMockServer proxyServer;

    @BeforeEach
    void setUp() {
      proxyServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
      proxyServer.start();

      // Use a non-routable IP so the test fails if proxy config is ignored
      config.url = "http://192.0.2.1:9200";

      // ensure no basic auth is configured by default
      config.getAuthentication().setUsername(null);
      config.getAuthentication().setPassword(null);
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
      configureProxyWithoutProxyAuth();

      // when
      sendRequest();

      // then - verify that request arrived at the proxy endpoint
      proxyServer.verify(anyRequestedFor(anyUrl()));
    }

    @Test
    void shouldNotSendProxyAuthorizationHeaderWhenNoCredentials() {
      // given - proxy that accepts all requests without auth
      stubSuccessResponse();
      configureProxyWithoutProxyAuth();

      // when
      sendRequest();

      // then - verify that request arrived WITHOUT Proxy-Authorization header
      proxyServer.verify(anyRequestedFor(anyUrl()).withoutHeader("Proxy-Authorization"));
    }

    @Test
    void shouldSendProxyAuthorizationHeaderPreemptively() {
      // given
      stubSuccessResponse();
      configureProxyWithProxyAuth();

      // when
      sendRequest();

      // then - verify that the request arrived with Proxy-Authorization header (preemptively)
      final String expectedAuthValue =
          "Basic "
              + Base64.getEncoder()
                  .encodeToString(
                      (PROXY_USERNAME + ":" + PROXY_PASSWORD).getBytes(StandardCharsets.UTF_8));

      proxyServer.verify(
          anyRequestedFor(anyUrl()).withHeader("Proxy-Authorization", equalTo(expectedAuthValue)));
    }

    @Test
    void shouldNotSendBasicAuthorizationHeaderByDefault() {
      // given
      // config.authentication.username & config.authentication.password are null by default
      stubSuccessResponse();
      configureProxyWithoutProxyAuth();

      // when
      sendRequest();

      // then - verify that the request arrived without an Authorization header
      proxyServer.verify(anyRequestedFor(anyUrl()).withoutHeader("Authorization"));
    }

    @Test
    void shouldSendBasicAuthorizationHeader() {
      // given
      config.getAuthentication().setUsername(BASIC_USERNAME);
      config.getAuthentication().setPassword(BASIC_PASSWORD);
      stubSuccessResponse();
      configureProxyWithoutProxyAuth();

      // when
      sendRequest();

      // then - verify that the request arrived with Basic Auth header
      proxyServer.verify(
          anyRequestedFor(anyUrl())
              .withBasicAuth(new BasicCredentials(BASIC_USERNAME, BASIC_PASSWORD)));
    }

    @Test
    void shouldSendBasicAuthorizationHeaderAndProxyAuthorizationHeaderPreemptively() {
      // given
      config.getAuthentication().setUsername(BASIC_USERNAME);
      config.getAuthentication().setPassword(BASIC_PASSWORD);
      stubSuccessResponse();
      configureProxyWithProxyAuth();

      // when
      sendRequest();

      // then - verify that the request arrived with Basic Auth header and the
      // Proxy-Authorization header (preemptively)
      final String expectedAuthValue =
          "Basic "
              + Base64.getEncoder()
                  .encodeToString(
                      (PROXY_USERNAME + ":" + PROXY_PASSWORD).getBytes(StandardCharsets.UTF_8));

      proxyServer.verify(
          anyRequestedFor(anyUrl())
              .withBasicAuth(new BasicCredentials(BASIC_USERNAME, BASIC_PASSWORD))
              .withHeader("Proxy-Authorization", equalTo(expectedAuthValue)));
    }

    @Test
    void shouldApplyRequestInterceptorsInOrder() {
      // given
      stubSuccessResponse();
      configureProxyWithoutProxyAuth();

      // add first interceptor plugin
      config.interceptorPlugins.add(
          new PluginConfiguration(
              "first-interceptor", TestRequestInterceptorFirst.class.getName(), null));

      // when
      sendRequest();

      // then - verify that the request arrived with the header from the first interceptor
      proxyServer.verify(anyRequestedFor(anyUrl()).withHeader("foo", equalTo("bar-first")));

      // and given
      // add another interceptor plugin that should override the header from the first one
      config.interceptorPlugins.add(
          new PluginConfiguration(
              "second-interceptor", TestRequestInterceptorSecond.class.getName(), null));

      // when
      sendRequest();

      // then - verify that the request arrived with the header from the second interceptor
      // that overrides the header from the first interceptor, i.e. value should be "baz-second"
      proxyServer.verify(anyRequestedFor(anyUrl()).withHeader("foo", equalTo("baz-second")));
    }

    private void configureProxyWithoutProxyAuth() {
      configureProxy(false);
    }

    private void configureProxyWithProxyAuth() {
      configureProxy(true);
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
      final OpenSearchClient client = OpensearchConnector.of(config).createClient();
      try {
        client.info();
      } catch (final OpenSearchException | IOException e) {
        throw new RuntimeException(e);
      } finally {
        try {
          client._transport().close();
        } catch (final IOException e) {
          // ignore any IO exceptions during close
        }
      }
    }

    public static class TestRequestInterceptorFirst implements DatabaseCustomHeaderSupplier {

      @Override
      public CustomHeader getSearchDatabaseCustomHeader() {
        return new CustomHeader("foo", "bar-first");
      }
    }

    public static class TestRequestInterceptorSecond implements DatabaseCustomHeaderSupplier {

      @Override
      public CustomHeader getSearchDatabaseCustomHeader() {
        return new CustomHeader("foo", "baz-second");
      }
    }
  }
}
