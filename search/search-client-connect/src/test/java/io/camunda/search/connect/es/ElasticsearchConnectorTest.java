/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.es;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.unauthorized;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.camunda.search.connect.plugin.util.TestDatabaseCustomHeaderSupplierImpl.KEY_CUSTOM_HEADER;
import static io.camunda.search.connect.plugin.util.TestDatabaseCustomHeaderSupplierImpl.VALUE_CUSTOM_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Request;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.plugin.PluginConfiguration;
import io.camunda.search.connect.plugin.PluginRepository;
import io.camunda.search.connect.plugin.util.TestDatabaseCustomHeaderSupplierImpl;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import net.bytebuddy.ByteBuddy;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ElasticsearchConnectorTest {

  @RegisterExtension
  // the native client only runs its interceptors once an actual HTTP connection is available
  static WireMockExtension esServer =
      WireMockExtension.newInstance()
          .options(WireMockConfiguration.wireMockConfig().dynamicPort())
          .build();

  @Test
  void shouldApplyRequestInterceptorsWithinClasspathForNativeRestClient() {
    final var context = HttpClientContext.create();
    final var configuration = new ConnectConfiguration();
    configuration.setInterceptorPlugins(
        List.of(
            new PluginConfiguration(
                "my-plg", TestDatabaseCustomHeaderSupplierImpl.class.getName(), null)));
    final PluginRepository pluginRepository = new PluginRepository();
    final var connector =
        Mockito.spy(
            new ElasticsearchConnector(configuration, new ObjectMapper(), pluginRepository));

    // when
    final var asyncResp =
        getHttpClient(connector.createClient())
            .execute(
                SimpleHttpRequest.create("GET", esServer.getRuntimeInfo().getHttpBaseUrl()),
                context,
                NoopCallback.INSTANCE);
    try {
      asyncResp.get();
    } catch (final Exception e) {
      // ignore as we don't really care about the outcome
    }

    // then
    final var reqWrapper = context.getRequest();

    assertThat(reqWrapper.getFirstHeader("foo").getValue()).isEqualTo("bar");
  }

  @Test
  void shouldApplyExternalRequestInterceptorsForNativeRestClient() throws IOException {
    final var context = HttpClientContext.create();
    final var jar =
        new ByteBuddy()
            .subclass(TestDatabaseCustomHeaderSupplierImpl.class)
            .name("com.acme.Foo")
            .make()
            .toJar(Files.createTempDirectory("plugin").resolve("plugin.jar").toFile())
            .toPath();
    final var plugin = new PluginConfiguration("test", "com.acme.Foo", jar);
    final var configuration = new ConnectConfiguration();
    configuration.setInterceptorPlugins(List.of(plugin));
    final PluginRepository pluginRepository = new PluginRepository();
    final var connector =
        Mockito.spy(
            new ElasticsearchConnector(configuration, new ObjectMapper(), pluginRepository));

    // when
    final var asyncResp =
        getHttpClient(connector.createClient())
            .execute(
                SimpleHttpRequest.create("GET", esServer.getRuntimeInfo().getHttpBaseUrl()),
                context,
                NoopCallback.INSTANCE);
    try {
      asyncResp.get();
    } catch (final Exception e) {
      // ignore as we don't really care about the outcome
    }

    // then
    final var reqWrapper = context.getRequest();

    assertThat(reqWrapper.getFirstHeader(KEY_CUSTOM_HEADER).getValue())
        .isEqualTo(VALUE_CUSTOM_HEADER);
  }

  @Test
  void shouldConfigureConnectionPoolLimitsWhenSet() {
    // given
    final var configuration = new ConnectConfiguration();
    configuration.setMaxConnections(75);
    configuration.setMaxConnectionsPerRoute(40);
    final var connector =
        new ElasticsearchConnector(configuration, new ObjectMapper(), new PluginRepository());
    final var builder = Mockito.mock(PoolingAsyncClientConnectionManagerBuilder.class);

    // when
    connector.configureConnectionManager(builder, configuration);

    // then
    Mockito.verify(builder).setMaxConnTotal(75);
    Mockito.verify(builder).setMaxConnPerRoute(40);
  }

  @Test
  void shouldNotConfigureConnectionPoolLimitsWhenUnset() {
    // given
    final var configuration = new ConnectConfiguration();
    final var connector =
        new ElasticsearchConnector(configuration, new ObjectMapper(), new PluginRepository());
    final var builder = Mockito.mock(PoolingAsyncClientConnectionManagerBuilder.class);

    // when
    connector.configureConnectionManager(builder, configuration);

    // then
    Mockito.verify(builder, never()).setMaxConnTotal(anyInt());
    Mockito.verify(builder, never()).setMaxConnPerRoute(anyInt());
  }

  @Test
  void shouldConfigureTimeoutsWhenSet() {
    // given
    final var configuration = new ConnectConfiguration();
    configuration.setSocketTimeout(125456);
    configuration.setConnectTimeout(654321);

    final var connector =
        new ElasticsearchConnector(configuration, new ObjectMapper(), new PluginRepository());
    final var connectionConfigBuilder = Mockito.mock(ConnectionConfig.Builder.class);
    final var requestConfigBuilder = Mockito.mock(RequestConfig.Builder.class);

    // when
    connector.setConnectionTimeouts(connectionConfigBuilder, configuration);
    connector.setRequestTimeouts(requestConfigBuilder);

    // then
    Mockito.verify(connectionConfigBuilder).setSocketTimeout(Timeout.ofMilliseconds(125456));
    Mockito.verify(connectionConfigBuilder).setConnectTimeout(Timeout.ofMilliseconds(654321));
    Mockito.verify(requestConfigBuilder)
        .setConnectionRequestTimeout(Timeout.ofMilliseconds(180_000));
  }

  @Test
  void shouldConfigureDefaultTimeouts() {
    // given
    final var configuration = new ConnectConfiguration();
    final var connector =
        new ElasticsearchConnector(configuration, new ObjectMapper(), new PluginRepository());
    final var connectionConfigBuilder = Mockito.mock(ConnectionConfig.Builder.class);
    final var requestConfigBuilder = Mockito.mock(RequestConfig.Builder.class);

    // when
    connector.setConnectionTimeouts(connectionConfigBuilder, configuration);
    connector.setRequestTimeouts(requestConfigBuilder);

    // then
    Mockito.verify(connectionConfigBuilder).setSocketTimeout(Timeout.ofMilliseconds(30_000));
    Mockito.verify(connectionConfigBuilder).setConnectTimeout(Timeout.ofMilliseconds(5_000));
    Mockito.verify(requestConfigBuilder)
        .setConnectionRequestTimeout(Timeout.ofMilliseconds(180_000));
  }

  @Test
  void shouldApplyTimeoutsWhenCreatingClientWithoutConfiguredTimeouts() throws IOException {
    // given
    final var configuration = new ConnectConfiguration();
    final var connector =
        Mockito.spy(
            new ElasticsearchConnector(configuration, new ObjectMapper(), new PluginRepository()));

    // when
    try (final var ignored = connector.createClient()) {
      // then
      Mockito.verify(connector)
          .setConnectionTimeouts(any(ConnectionConfig.Builder.class), eq(configuration));
      Mockito.verify(connector).setRequestTimeouts(any(RequestConfig.Builder.class));
    }
  }

  @Test
  void shouldEnableTcpKeepAlive() {
    // given
    final var configuration = new ConnectConfiguration();
    final var connector =
        new ElasticsearchConnector(configuration, new ObjectMapper(), new PluginRepository());
    final var builder = Mockito.mock(HttpAsyncClientBuilder.class);

    // when
    connector.configureHttpClient(builder, configuration);

    // then
    final var captor = ArgumentCaptor.forClass(IOReactorConfig.class);
    Mockito.verify(builder).setIOReactorConfig(captor.capture());
    assertThat(captor.getValue().isSoKeepAlive()).isTrue();
  }

  // The server never answers 401. A server that lets anonymous users through for some requests
  // behaves the same way, and the client must still send its credentials.
  @Test
  void shouldSendCredentialsWithRequestsTheServerNeverChallenges() throws IOException {
    // given
    final var configuration = new ConnectConfiguration();
    configuration.setUrl(esServer.getRuntimeInfo().getHttpBaseUrl());
    configuration.setUsername("someUser");
    configuration.setPassword("somePassword");
    final var connector =
        new ElasticsearchConnector(configuration, new ObjectMapper(), new PluginRepository());
    esServer.stubFor(post(urlEqualTo("/some-index/_delete_by_query")).willReturn(ok()));
    final var request = new Request("POST", "/some-index/_delete_by_query");
    request.setEntity(new StringEntity("{}", ContentType.APPLICATION_JSON));

    // when
    try (final var client = connector.createClient()) {
      restClient(client).performRequest(request);
    }

    // then
    final var expected =
        "Basic "
            + Base64.getEncoder()
                .encodeToString("someUser:somePassword".getBytes(StandardCharsets.UTF_8));
    esServer.verify(
        postRequestedFor(urlEqualTo("/some-index/_delete_by_query"))
            .withHeader("Authorization", equalTo(expected)));
  }

  @Test
  void shouldNotSendAuthorizationHeaderWhenNoCredentialsAreConfigured() throws IOException {
    // given
    final var configuration = new ConnectConfiguration();
    configuration.setUrl(esServer.getRuntimeInfo().getHttpBaseUrl());
    final var connector =
        new ElasticsearchConnector(configuration, new ObjectMapper(), new PluginRepository());
    esServer.stubFor(get(urlEqualTo("/no-credentials")).willReturn(ok()));

    // when
    try (final var client = connector.createClient()) {
      restClient(client).performRequest(new Request("GET", "/no-credentials"));
    }

    // then
    esServer.verify(getRequestedFor(urlEqualTo("/no-credentials")).withoutHeader("Authorization"));
  }

  // With the credentials sent up front, HttpClient must report a rejected request as an ordinary
  // 401 response.
  @Test
  void shouldReportRejectedCredentialsAsUnauthorizedResponse() throws IOException {
    // given
    final var configuration = new ConnectConfiguration();
    configuration.setUrl(esServer.getRuntimeInfo().getHttpBaseUrl());
    configuration.setUsername("someUser");
    configuration.setPassword("wrongPassword");
    final var connector =
        new ElasticsearchConnector(configuration, new ObjectMapper(), new PluginRepository());
    esServer.stubFor(
        get(urlEqualTo("/rejected-credentials"))
            .willReturn(unauthorized().withHeader("WWW-Authenticate", "Basic realm=\"security\"")));

    // when
    try (final var client = connector.createClient()) {
      final var response =
          restClient(client).performRequest(new Request("GET", "/rejected-credentials"));

      // then
      assertThat(response.getStatusCode()).isEqualTo(401);
    }
  }

  private static Rest5Client restClient(final ElasticsearchClient client) {
    return ((Rest5ClientTransport) client._transport()).restClient();
  }

  private static CloseableHttpAsyncClient getHttpClient(final ElasticsearchClient client) {
    return (CloseableHttpAsyncClient)
        ((Rest5ClientTransport) client._transport()).restClient().getHttpClient();
  }

  private static final class NoopCallback implements FutureCallback<SimpleHttpResponse> {
    private static final NoopCallback INSTANCE = new NoopCallback();

    @Override
    public void completed(final SimpleHttpResponse result) {}

    @Override
    public void failed(final Exception ex) {}

    @Override
    public void cancelled() {}
  }
}
