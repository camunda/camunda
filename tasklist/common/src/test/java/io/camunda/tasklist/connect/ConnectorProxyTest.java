/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.connect;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.search.connect.plugin.PluginRepository;
import io.camunda.tasklist.os.OpenSearchConnector;
import io.camunda.tasklist.property.ProxyProperties;
import io.camunda.tasklist.property.TasklistElasticsearchProperties;
import io.camunda.tasklist.property.TasklistOpenSearchProperties;
import io.camunda.tasklist.property.TasklistProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;
import org.opensearch.client.opensearch.OpenSearchClient;

/**
 * Integration tests for proxy support in Tasklist's Elasticsearch and OpenSearch connectors.
 *
 * <p>These tests verify that both connectors correctly configure HTTP proxy settings, including
 * proxy authentication. Uses WireMock to simulate a proxy endpoint and verify that requests arrive
 * with the expected proxy-related headers.
 *
 * <p>Note: These tests verify the connector's proxy configuration, not actual proxy forwarding
 * behavior. The tests confirm that when proxy settings are configured, the HTTP client sends
 * requests to the proxy host with appropriate authentication headers.
 *
 * <p>Tests are executed sequentially to avoid port conflicts.
 */
@Execution(ExecutionMode.SAME_THREAD)
class ConnectorProxyTest {

  private static final String PROXY_USERNAME = "proxyuser";
  private static final String PROXY_PASSWORD = "proxypass";
  private static final String OS_ADMIN_USER = "admin";
  private static final String OS_ADMIN_PASSWORD = "admin";

  private static final String AUTH_SCENARIO = "proxy-auth";
  private static final String AUTH_CHALLENGED = "challenged";

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

    final var proxy = new ProxyProperties();
    proxy.setEnabled(true);
    proxy.setHost("localhost");
    proxy.setPort(proxyServer.port());

    // when
    pingDatabase(databaseType, proxy);

    // then - verify that request arrived at the proxy endpoint
    proxyServer.verify(anyRequestedFor(anyUrl()));
  }

  @ParameterizedTest
  @EnumSource(
      value = DatabaseType.class,
      names = {"ELASTICSEARCH", "OPENSEARCH"})
  void shouldSendProxyAuthorizationHeaderAfter407Challenge(final DatabaseType databaseType)
      throws Exception {
    // given - proxy that requires authentication (407 challenge-response flow)
    // First request gets 407, second request with credentials succeeds
    stubProxyAuthChallenge();

    final var proxy = new ProxyProperties();
    proxy.setEnabled(true);
    proxy.setHost("localhost");
    proxy.setPort(proxyServer.port());
    proxy.setUsername(PROXY_USERNAME);
    proxy.setPassword(PROXY_PASSWORD);

    final String expectedAuthValue =
        "Basic "
            + Base64.getEncoder()
                .encodeToString(
                    (PROXY_USERNAME + ":" + PROXY_PASSWORD).getBytes(StandardCharsets.UTF_8));

    // when
    pingDatabase(databaseType, proxy);

    // then - verify that the retry request arrived with Proxy-Authorization header
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

    final var proxy = new ProxyProperties();
    proxy.setEnabled(true);
    proxy.setHost("localhost");
    proxy.setPort(proxyServer.port());

    // when
    pingDatabase(databaseType, proxy);

    // then - verify that request arrived WITHOUT Proxy-Authorization header
    proxyServer.verify(anyRequestedFor(anyUrl()).withoutHeader("Proxy-Authorization"));
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

  /**
   * Stub that simulates proxy authentication using 407 challenge-response flow.
   *
   * <p>Apache HttpClient doesn't send Proxy-Authorization preemptively. Instead, it waits for a 407
   * response with Proxy-Authenticate header, then retries with credentials. This stub uses
   * WireMock's scenario feature to return 407 on the first request, then 200 on subsequent
   * requests.
   */
  private void stubProxyAuthChallenge() {
    // First request: return 407 Proxy Authentication Required
    proxyServer.stubFor(
        any(anyUrl())
            .inScenario(AUTH_SCENARIO)
            .whenScenarioStateIs(STARTED)
            .willReturn(
                aResponse()
                    .withStatus(407)
                    .withHeader("Proxy-Authenticate", "Basic realm=\"proxy\""))
            .willSetStateTo(AUTH_CHALLENGED));

    // Subsequent requests: return success
    proxyServer.stubFor(
        any(anyUrl())
            .inScenario(AUTH_SCENARIO)
            .whenScenarioStateIs(AUTH_CHALLENGED)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withHeader("X-Elastic-Product", "Elasticsearch")
                    .withBody(CLUSTER_INFO_RESPONSE)));
  }

  private void pingDatabase(final DatabaseType databaseType, final ProxyProperties proxy)
      throws Exception {
    // The connector is configured to use a fake backend URL, but with proxy pointing to WireMock.
    // Since the proxy is set, Apache HttpClient will connect to the proxy (WireMock) instead.
    // We use a non-routable IP to ensure the test fails if proxy config is ignored.
    final var fakeBackendUrl = "http://192.0.2.1:9200"; // TEST-NET-1, non-routable

    switch (databaseType) {
      case ELASTICSEARCH -> {
        final var tasklistProperties = new TasklistProperties();
        final var esProperties = new TasklistElasticsearchProperties();
        esProperties.setUrl(fakeBackendUrl);
        esProperties.setProxy(proxy);
        esProperties.setHealthCheckEnabled(false);
        tasklistProperties.setElasticsearch(esProperties);

        final var connector = Mockito.spy(new ElasticsearchConnector());
        Mockito.doReturn(true).when(connector).checkHealth(Mockito.any(RestHighLevelClient.class));
        connector.setTasklistProperties(tasklistProperties);
        final var client = connector.createEsClient(esProperties, new PluginRepository());

        assertThat(client.ping(RequestOptions.DEFAULT)).isTrue();
      }
      case OPENSEARCH -> {
        final var tasklistProperties = new TasklistProperties();
        final var osProperties = new TasklistOpenSearchProperties();
        osProperties.setUrl(fakeBackendUrl);
        osProperties.setUsername(OS_ADMIN_USER);
        osProperties.setPassword(OS_ADMIN_PASSWORD);
        osProperties.setProxy(proxy);
        osProperties.setHealthCheckEnabled(false);
        tasklistProperties.setOpenSearch(osProperties);

        final var connector = Mockito.spy(new OpenSearchConnector());
        Mockito.doReturn(true).when(connector).isHealthy(Mockito.any(OpenSearchClient.class));
        connector.setTasklistProperties(tasklistProperties);
        connector.setTasklistObjectMapper(new ObjectMapper());
        final OpenSearchClient client =
            connector.createOsClient(osProperties, new PluginRepository());

        assertThat(client.ping().value()).isTrue();
      }
      default -> throw new IllegalArgumentException("Unsupported database type: " + databaseType);
    }
  }
}
