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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.impl.BasicEntityDetails;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

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
  void shouldApplyRequestInterceptorsInOrder()
      throws IOException, org.apache.hc.core5.http.HttpException {
    // given
    final var context = HttpCoreContext.create();
    final var builder = Mockito.mock(HttpAsyncClientBuilder.class);

    // when
    ElasticsearchClientFactory.INSTANCE.configureHttpClient(
        config,
        builder,
        (req, entity, ctx) -> ctx.setAttribute("foo", "bar"),
        (req, entity, ctx) -> ctx.setAttribute("foo", "baz"));

    final var interceptorCaptor =
        ArgumentCaptor.forClass(org.apache.hc.core5.http.HttpRequestInterceptor.class);
    Mockito.verify(builder, Mockito.times(2))
        .addRequestInterceptorLast(interceptorCaptor.capture());
    for (final var interceptor : interceptorCaptor.getAllValues()) {
      interceptor.process(
          new BasicHttpRequest("GET", "localhost"), new BasicEntityDetails(0, null), context);
    }

    // then
    assertThat(context.getAttribute("foo")).isEqualTo("baz");
  }

  @Test
  void shouldEnableTcpKeepAlive() {
    // given
    final var builder = mock(HttpAsyncClientBuilder.class);

    // when
    ElasticsearchClientFactory.INSTANCE.configureHttpClient(config, builder);

    // then
    final var captor = ArgumentCaptor.forClass(IOReactorConfig.class);
    verify(builder).setIOReactorConfig(captor.capture());
    assertThat(captor.getValue().isSoKeepAlive()).isTrue();
  }
}
