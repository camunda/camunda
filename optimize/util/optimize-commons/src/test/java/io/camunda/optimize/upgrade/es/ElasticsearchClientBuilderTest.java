/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.es;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.rest5_client.low_level.Request;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ElasticSearchConfiguration;
import io.camunda.optimize.service.util.configuration.ProxyConfiguration;
import io.camunda.optimize.service.util.configuration.db.DatabaseConnection;
import io.camunda.optimize.service.util.configuration.elasticsearch.DatabaseConnectionNodeConfiguration;
import io.camunda.optimize.upgrade.util.TestPlugin;
import io.camunda.search.connect.plugin.PluginConfiguration;
import io.camunda.search.connect.plugin.PluginRepository;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.impl.BasicEntityDetails;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ElasticsearchClientBuilderTest {

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void buildExtendedClientHappyPath(final boolean sslEnabled) {
    // given
    final ConfigurationService config = Mockito.mock(ConfigurationService.class);
    final ElasticSearchConfiguration esConfig = Mockito.mock(ElasticSearchConfiguration.class);
    final DatabaseConnection connection = Mockito.mock(DatabaseConnection.class);
    final DatabaseConnectionNodeConfiguration host = new DatabaseConnectionNodeConfiguration();
    host.setHost("localhost");
    host.setHttpPort(9876);
    final ProxyConfiguration proxyConfig = new ProxyConfiguration();
    proxyConfig.setEnabled(false);

    Mockito.when(config.getElasticSearchConfiguration()).thenReturn(esConfig);
    Mockito.when(esConfig.getProxyConfig()).thenReturn(proxyConfig);
    Mockito.when(esConfig.getInterceptorPlugins()).thenReturn(Map.of());
    Mockito.when(connection.getAwsEnabled()).thenReturn(false);
    Mockito.when(esConfig.getConnectionNodes()).thenReturn(List.of(host));
    Mockito.when(esConfig.getConnection()).thenReturn(connection);
    Mockito.when(esConfig.getSecuritySSLEnabled()).thenReturn(sslEnabled);
    Mockito.when(esConfig.getSecuritySSLCertificateAuthorities()).thenReturn(List.of());

    // when - building the client should not throw for either protocol
    final ElasticsearchClient extendedClient =
        ElasticsearchClientBuilder.build(config, new ObjectMapper(), new PluginRepository());

    // then
    Assertions.assertThat(extendedClient).isNotNull();
  }

  @Test
  void shouldApplyRequestInterceptorsFromPluginRepository() {
    // given
    final ConfigurationService config = Mockito.mock(ConfigurationService.class);
    final ElasticSearchConfiguration esConfig = Mockito.mock(ElasticSearchConfiguration.class);
    final ProxyConfiguration proxyConfig = new ProxyConfiguration();
    proxyConfig.setEnabled(false);
    Mockito.when(config.getElasticSearchConfiguration()).thenReturn(esConfig);
    Mockito.when(esConfig.getProxyConfig()).thenReturn(proxyConfig);

    final Map<String, PluginConfiguration> pluginConfigurations =
        Map.of("0", new PluginConfiguration("plg1", TestPlugin.class.getName(), null));
    Mockito.when(esConfig.getInterceptorPlugins()).thenReturn(pluginConfigurations);
    final PluginRepository pluginRepository = new PluginRepository();
    pluginRepository.load(List.copyOf(pluginConfigurations.values()));

    final var builder = Mockito.mock(HttpAsyncClientBuilder.class);

    // when
    ElasticsearchClientBuilder.applyHttpClientConfig(
        builder, config, pluginRepository.asRequestInterceptor());

    final var interceptorCaptor =
        ArgumentCaptor.forClass(org.apache.hc.core5.http.HttpRequestInterceptor.class);
    Mockito.verify(builder).addRequestInterceptorLast(interceptorCaptor.capture());
    final var context = HttpCoreContext.create();
    final var request = new BasicHttpRequest("GET", "localhost");
    try {
      interceptorCaptor.getValue().process(request, new BasicEntityDetails(0, null), context);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }

    // then
    Assertions.assertThat(request.getFirstHeader("foo").getValue()).isEqualTo("bar");
  }

  @Test
  void buildClientWithProxyEnabledDoesNotFail() {
    // given
    final ConfigurationService config = Mockito.mock(ConfigurationService.class);
    final ElasticSearchConfiguration esConfig = Mockito.mock(ElasticSearchConfiguration.class);
    final ProxyConfiguration proxyConfig =
        new ProxyConfiguration(true, "proxy.example.com", 8080, false, null, null);
    Mockito.when(config.getElasticSearchConfiguration()).thenReturn(esConfig);
    Mockito.when(esConfig.getProxyConfig()).thenReturn(proxyConfig);

    final var builder = Mockito.mock(HttpAsyncClientBuilder.class);

    // when - no exception thrown, proxy configured
    ElasticsearchClientBuilder.applyHttpClientConfig(builder, config);

    // then
    Mockito.verify(builder).setProxy(Mockito.any());
    Mockito.verify(builder, Mockito.never())
        .addRequestInterceptorFirst(
            Mockito.any(org.apache.hc.core5.http.HttpRequestInterceptor.class));
  }

  @Test
  void buildClientWithProxyAuthSetsProxyAuthorizationHeader() throws Exception {
    // given
    final ConfigurationService config = Mockito.mock(ConfigurationService.class);
    final ElasticSearchConfiguration esConfig = Mockito.mock(ElasticSearchConfiguration.class);
    final ProxyConfiguration proxyConfig =
        new ProxyConfiguration(true, "proxy.example.com", 8080, false, "proxyUser", "proxyPass");
    Mockito.when(config.getElasticSearchConfiguration()).thenReturn(esConfig);
    Mockito.when(esConfig.getProxyConfig()).thenReturn(proxyConfig);

    final var builder = Mockito.mock(HttpAsyncClientBuilder.class);

    // when
    ElasticsearchClientBuilder.applyHttpClientConfig(builder, config);

    final var interceptorCaptor =
        ArgumentCaptor.forClass(org.apache.hc.core5.http.HttpRequestInterceptor.class);
    Mockito.verify(builder).addRequestInterceptorFirst(interceptorCaptor.capture());
    final var request = new BasicHttpRequest("GET", "localhost");
    interceptorCaptor
        .getValue()
        .process(request, new BasicEntityDetails(0, null), HttpCoreContext.create());

    // then
    final String expectedEncoded =
        Base64.getEncoder().encodeToString("proxyUser:proxyPass".getBytes(StandardCharsets.UTF_8));
    Assertions.assertThat(request.getFirstHeader("Proxy-Authorization").getValue())
        .isEqualTo("Basic " + expectedEncoded);
  }

  @Test
  void buildClientWithProxyDisabledHasNoProxyAuthorizationHeader() {
    // given
    final ConfigurationService config = Mockito.mock(ConfigurationService.class);
    final ElasticSearchConfiguration esConfig = Mockito.mock(ElasticSearchConfiguration.class);
    // Proxy disabled, even though credentials are set - should not add header
    final ProxyConfiguration proxyConfig =
        new ProxyConfiguration(false, "proxy.example.com", 8080, false, "proxyUser", "proxyPass");
    Mockito.when(config.getElasticSearchConfiguration()).thenReturn(esConfig);
    Mockito.when(esConfig.getProxyConfig()).thenReturn(proxyConfig);

    final var builder = Mockito.mock(HttpAsyncClientBuilder.class);

    // when
    ElasticsearchClientBuilder.applyHttpClientConfig(builder, config);

    // then
    Mockito.verify(builder, Mockito.never()).setProxy(Mockito.any());
    Mockito.verify(builder, Mockito.never())
        .addRequestInterceptorFirst(
            Mockito.any(org.apache.hc.core5.http.HttpRequestInterceptor.class));
  }

  // The Elasticsearch server below never answers 401. A server that lets anonymous users through
  // for some requests behaves the same way, and the client must still send its credentials.
  @Test
  void shouldSendCredentialsWithRequestsTheServerNeverChallenges() throws Exception {
    // given
    final String username = "someUser";
    final String password = "somePassword";
    final var receivedAuthorizationHeaders = new CopyOnWriteArrayList<String>();
    final var server = startServerRecordingAuthorizationHeaders(receivedAuthorizationHeaders);
    try (final var restClient =
        ElasticsearchClientBuilder.restClient(
            configForPlainHttpServer(server, username, password), new PluginRepository())) {
      final var request = new Request("POST", "/some-index/_delete_by_query");
      request.setEntity(new StringEntity("{}", ContentType.APPLICATION_JSON));

      // when
      restClient.performRequest(request);

      // then
      final String expected =
          "Basic "
              + Base64.getEncoder()
                  .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
      assertThat(receivedAuthorizationHeaders).singleElement().isEqualTo(expected);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void shouldNotSendAuthorizationHeaderWhenNoCredentialsAreConfigured() throws Exception {
    // given
    final var receivedAuthorizationHeaders = new CopyOnWriteArrayList<String>();
    final var server = startServerRecordingAuthorizationHeaders(receivedAuthorizationHeaders);
    try (final var restClient =
        ElasticsearchClientBuilder.restClient(
            configForPlainHttpServer(server, null, null), new PluginRepository())) {

      // when
      restClient.performRequest(new Request("GET", "/"));

      // then
      assertThat(receivedAuthorizationHeaders).singleElement().isNull();
    } finally {
      server.stop(0);
    }
  }

  private static HttpServer startServerRecordingAuthorizationHeaders(
      final List<String> receivedAuthorizationHeaders) throws IOException {
    final var server =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/",
        exchange -> {
          // a null entry marks a request that carried no Authorization header
          receivedAuthorizationHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
          exchange.getRequestBody().readAllBytes();
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    server.start();
    return server;
  }

  private static ConfigurationService configForPlainHttpServer(
      final HttpServer server, final String username, final String password) {
    final ConfigurationService config = Mockito.mock(ConfigurationService.class);
    final ElasticSearchConfiguration esConfig = Mockito.mock(ElasticSearchConfiguration.class);
    final DatabaseConnectionNodeConfiguration host = new DatabaseConnectionNodeConfiguration();
    host.setHost(server.getAddress().getHostString());
    host.setHttpPort(server.getAddress().getPort());
    final ProxyConfiguration proxyConfig = new ProxyConfiguration();
    proxyConfig.setEnabled(false);
    Mockito.when(config.getElasticSearchConfiguration()).thenReturn(esConfig);
    Mockito.when(esConfig.getProxyConfig()).thenReturn(proxyConfig);
    Mockito.when(esConfig.getInterceptorPlugins()).thenReturn(Map.of());
    Mockito.when(esConfig.getConnectionNodes()).thenReturn(List.of(host));
    Mockito.when(esConfig.getSecuritySSLEnabled()).thenReturn(false);
    Mockito.when(esConfig.getConnectionTimeout()).thenReturn(5000);
    Mockito.when(esConfig.getSecurityUsername()).thenReturn(username);
    Mockito.when(esConfig.getSecurityPassword()).thenReturn(password);
    return config;
  }
}
