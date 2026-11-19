/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.unauthorized;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Node;
import co.elastic.clients.transport.rest5_client.low_level.Request;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class ElasticsearchClientFactoryTest {
  // an instance field, so every test gets its own server: the class runs its tests concurrently
  // and WireMock resets stubs and the request journal before each test
  @RegisterExtension
  final WireMockExtension esServer =
      WireMockExtension.newInstance()
          .options(WireMockConfiguration.wireMockConfig().dynamicPort())
          .build();

  private final ElasticsearchExporterConfiguration config =
      new ElasticsearchExporterConfiguration();

  private static Rest5Client extractRestClient(final ElasticsearchClient client) {
    return ((Rest5ClientTransport) client._transport()).restClient();
  }

  @Test
  void shouldNotExposeUrlCredentialsInErrorForInvalidUrl() {
    // given
    config.url = "http://elastic:s3cr3t@localhost:notaport";

    // when / then
    assertThatThrownBy(() -> ElasticsearchClientFactory.of(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid Elasticsearch url at position 0")
        .hasNoCause();
  }

  @Test
  void shouldRejectAUrlWithoutAnyHost() {
    // given - a value made only of separators splits into no host at all; the connector would
    // silently use its default host (localhost:9200) instead of failing
    config.url = ",";

    // when / then
    assertThatThrownBy(() -> ElasticsearchClientFactory.of(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid Elasticsearch url: no host is configured");
  }

  @Test
  void shouldConfigureMultipleHosts() throws URISyntaxException {
    // given
    config.url = "http://localhost:9201,https://localhost:9202";

    // when
    final var esClient = ElasticsearchClientFactory.of(config);
    final var restClient = extractRestClient(esClient);

    // then
    assertThat(restClient.getNodes())
        .hasSize(2)
        .map(Node::getHost)
        .containsExactly(
            HttpHost.create("http://localhost:9201"), HttpHost.create("https://localhost:9202"));
  }

  @Test
  void shouldReportRejectedCredentialsAsUnauthorizedResponse() throws IOException {
    // given: with the credentials sent up front, HttpClient must report a rejected request as an
    // ordinary 401 response.
    config.url = esServer.getRuntimeInfo().getHttpBaseUrl();
    config.getAuthentication().setUsername("someUser");
    config.getAuthentication().setPassword("wrongPassword");
    esServer.stubFor(
        get(urlEqualTo("/rejected-credentials"))
            .willReturn(unauthorized().withHeader("WWW-Authenticate", "Basic realm=\"security\"")));

    // when
    try (final var client = ElasticsearchClientFactory.of(config)) {
      final var response =
          extractRestClient(client).performRequest(new Request("GET", "/rejected-credentials"));

      // then
      assertThat(response.getStatusCode()).isEqualTo(401);
    }
  }

  // The server never answers 401. A server that lets anonymous users through for some requests
  // behaves the same way, and the client must still send its credentials.
  @Test
  void shouldSendCredentialsWithRequestsTheServerNeverChallenges() throws IOException {
    // given
    config.url = esServer.getRuntimeInfo().getHttpBaseUrl();
    config.getAuthentication().setUsername("someUser");
    config.getAuthentication().setPassword("somePassword");
    esServer.stubFor(post(urlEqualTo("/with-credentials/_delete_by_query")).willReturn(ok()));
    final var request = new Request("POST", "/with-credentials/_delete_by_query");
    request.setEntity(new StringEntity("{}", ContentType.APPLICATION_JSON));

    // when
    try (final var client = ElasticsearchClientFactory.of(config)) {
      extractRestClient(client).performRequest(request);
    }

    // then
    final var expected =
        "Basic "
            + Base64.getEncoder()
                .encodeToString("someUser:somePassword".getBytes(StandardCharsets.UTF_8));
    esServer.verify(
        postRequestedFor(urlEqualTo("/with-credentials/_delete_by_query"))
            .withHeader("Authorization", equalTo(expected)));
  }

  @Test
  void shouldNotSendAuthorizationHeaderWhenNoCredentialsAreConfigured() throws IOException {
    // given
    config.url = esServer.getRuntimeInfo().getHttpBaseUrl();
    esServer.stubFor(get(urlEqualTo("/no-credentials")).willReturn(ok()));

    // when
    try (final var client = ElasticsearchClientFactory.of(config)) {
      extractRestClient(client).performRequest(new Request("GET", "/no-credentials"));
    }

    // then
    esServer.verify(getRequestedFor(urlEqualTo("/no-credentials")).withoutHeader("Authorization"));
  }

  @Test
  void shouldApplyRequestInterceptorsInOrder() throws IOException {
    // given - both interceptors set the same header, so the last one wins
    config.url = esServer.getRuntimeInfo().getHttpBaseUrl();
    esServer.stubFor(get(urlEqualTo("/interceptors")).willReturn(ok()));

    // when
    try (final var client =
        ElasticsearchClientFactory.of(
            config,
            (request, entity, context) -> request.setHeader("foo", "bar"),
            (request, entity, context) -> request.setHeader("foo", "baz"))) {
      extractRestClient(client).performRequest(new Request("GET", "/interceptors"));
    }

    // then
    esServer.verify(getRequestedFor(urlEqualTo("/interceptors")).withHeader("foo", equalTo("baz")));
  }

  @Test
  void shouldUseTheRequestTimeoutForBothConnectAndSocketTimeouts() {
    // given
    config.requestTimeoutMs = 12_345;

    // when
    final var connect = ElasticsearchClientFactory.toConnectConfiguration(config);

    // then
    assertThat(connect.getConnectTimeout()).isEqualTo(12_345);
    assertThat(connect.getSocketTimeout()).isEqualTo(12_345);
  }

  @Test
  void shouldMapEveryUrlOfTheCommaSeparatedList() {
    // given
    config.url = "http://localhost:9201,https://localhost:9202";

    // when
    final var connect = ElasticsearchClientFactory.toConnectConfiguration(config);

    // then
    assertThat(connect.getUrls())
        .containsExactly("http://localhost:9201", "https://localhost:9202");
  }

  @Test
  void shouldMapCredentialsAndLeaveTheOtherConnectionOptionsUnset() {
    // given
    config.getAuthentication().setUsername("someUser");
    config.getAuthentication().setPassword("somePassword");

    // when
    final var connect = ElasticsearchClientFactory.toConnectConfiguration(config);

    // then - the exporter never configured TLS or a connection pool
    assertThat(connect.getUsername()).isEqualTo("someUser");
    assertThat(connect.getPassword()).isEqualTo("somePassword");
    assertThat(connect.getSecurity().isEnabled()).isFalse();
    assertThat(connect.getMaxConnections()).isNull();
    assertThat(connect.getMaxConnectionsPerRoute()).isNull();
  }

  @Test
  void shouldMapTheProxy() {
    // given
    config.getProxy().setEnabled(true);
    config.getProxy().setHost("proxy.local");
    config.getProxy().setPort(8080);
    config.getProxy().setSslEnabled(true);
    config.getProxy().setUsername("proxyUser");
    config.getProxy().setPassword("proxyPassword");

    // when
    final var proxy = ElasticsearchClientFactory.toConnectConfiguration(config).getProxy();

    // then
    assertThat(proxy.isEnabled()).isTrue();
    assertThat(proxy.getHost()).isEqualTo("proxy.local");
    assertThat(proxy.getPort()).isEqualTo(8080);
    assertThat(proxy.isSslEnabled()).isTrue();
    assertThat(proxy.getUsername()).isEqualTo("proxyUser");
    assertThat(proxy.getPassword()).isEqualTo("proxyPassword");
  }

  @Test
  void shouldRejectAnEnabledProxyWithoutHost() {
    // given
    config.getProxy().setEnabled(true);
    config.getProxy().setPort(8080);

    // when / then
    assertThatThrownBy(() -> ElasticsearchClientFactory.of(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Elasticsearch exporter proxy is enabled but no proxy host is configured");
  }

  @Test
  void shouldRejectAnEnabledProxyWithoutPort() {
    // given
    config.getProxy().setEnabled(true);
    config.getProxy().setHost("proxy.local");

    // when / then
    assertThatThrownBy(() -> ElasticsearchClientFactory.of(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Elasticsearch exporter proxy is enabled but no proxy port is configured");
  }

  @Test
  void shouldRejectAnEnabledProxyWithAnOutOfRangePort() {
    // given
    config.getProxy().setEnabled(true);
    config.getProxy().setHost("proxy.local");
    config.getProxy().setPort(70_000);

    // when / then
    assertThatThrownBy(() -> ElasticsearchClientFactory.of(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Elasticsearch exporter proxy port must be between 1 and 65535, but was: 70000");
  }
}
