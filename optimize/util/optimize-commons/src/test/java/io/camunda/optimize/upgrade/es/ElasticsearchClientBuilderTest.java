/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ElasticSearchConfiguration;
import io.camunda.optimize.service.util.configuration.ProxyConfiguration;
import io.camunda.optimize.service.util.configuration.db.DatabaseConnection;
import io.camunda.optimize.service.util.configuration.elasticsearch.DatabaseConnectionNodeConfiguration;
import io.camunda.optimize.upgrade.util.TestPlugin;
import io.camunda.search.connect.plugin.PluginConfiguration;
import io.camunda.search.connect.plugin.PluginRepository;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.protocol.BasicHttpContext;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

class ElasticsearchClientBuilderTest {

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void buildExtendedClientHappyPath(final boolean sslEnabled) {
    // given
    final BasicHttpContext context = new BasicHttpContext();
    final ConfigurationService config = Mockito.mock(ConfigurationService.class);
    final ElasticSearchConfiguration esConfig = Mockito.mock(ElasticSearchConfiguration.class);
    final DatabaseConnection connection = Mockito.mock(DatabaseConnection.class);
    final DatabaseConnectionNodeConfiguration host = new DatabaseConnectionNodeConfiguration();
    host.setHost("localhost");
    host.setHttpPort(9876);
    final ProxyConfiguration proxyConfig = new ProxyConfiguration();
    proxyConfig.setEnabled(false);
    final Map<String, PluginConfiguration> pluginConfigurations =
        Map.of("0", new PluginConfiguration("plg1", TestPlugin.class.getName(), null));

    Mockito.when(config.getElasticSearchConfiguration()).thenReturn(esConfig);
    Mockito.when(esConfig.getProxyConfig()).thenReturn(proxyConfig);
    Mockito.when(esConfig.getInterceptorPlugins()).thenReturn(pluginConfigurations);
    Mockito.when(connection.getAwsEnabled()).thenReturn(false);
    Mockito.when(esConfig.getConnectionNodes()).thenReturn(List.of(host));
    Mockito.when(esConfig.getConnection()).thenReturn(connection);
    Mockito.when(esConfig.getSecuritySSLEnabled()).thenReturn(sslEnabled);

    final ElasticsearchClient extendedClient =
        ElasticsearchClientBuilder.build(config, new ObjectMapper(), new PluginRepository());
    ((RestClientTransport) extendedClient._transport())
        .restClient()
        .getHttpClient()
        .execute(HttpHost.create("localhost:9200"), new HttpGet(), context, NoopCallback.INSTANCE);

    // then
    final HttpRequestWrapper reqWrapper = (HttpRequestWrapper) context.getAttribute("http.request");

    Assertions.assertThat(reqWrapper.getFirstHeader("foo").getValue()).isEqualTo("bar");
  }

  @Test
  void buildClientWithNoPluginsDoesNotFail() {
    // given
    final BasicHttpContext context = new BasicHttpContext();
    final ConfigurationService config = Mockito.mock(ConfigurationService.class);
    final ElasticSearchConfiguration esConfig = Mockito.mock(ElasticSearchConfiguration.class);
    final DatabaseConnection connection = Mockito.mock(DatabaseConnection.class);
    final DatabaseConnectionNodeConfiguration host = new DatabaseConnectionNodeConfiguration();
    host.setHost("localhost");
    host.setHttpPort(9876);
    final ProxyConfiguration proxyConfig = new ProxyConfiguration();
    proxyConfig.setEnabled(false);
    final Map<String, PluginConfiguration> pluginConfigurations = Map.of();

    Mockito.when(config.getElasticSearchConfiguration()).thenReturn(esConfig);
    Mockito.when(esConfig.getProxyConfig()).thenReturn(proxyConfig);
    Mockito.when(esConfig.getInterceptorPlugins()).thenReturn(pluginConfigurations);
    Mockito.when(connection.getAwsEnabled()).thenReturn(false);
    Mockito.when(esConfig.getConnectionNodes()).thenReturn(List.of(host));
    Mockito.when(esConfig.getConnection()).thenReturn(connection);

    final ElasticsearchClient extendedClient =
        ElasticsearchClientBuilder.build(config, new ObjectMapper(), new PluginRepository());
    ((RestClientTransport) extendedClient._transport())
        .restClient()
        .getHttpClient()
        .execute(HttpHost.create("localhost:9200"), new HttpGet(), context, NoopCallback.INSTANCE);

    // then
    final HttpRequestWrapper reqWrapper = (HttpRequestWrapper) context.getAttribute("http.request");

    Assertions.assertThat(reqWrapper.getFirstHeader("foo")).isNull();
  }

  @Test
  void buildClientWithAllEmptyPluginsDoesNotFail() {
    // given
    final BasicHttpContext context = new BasicHttpContext();
    final ConfigurationService config = Mockito.mock(ConfigurationService.class);
    final ElasticSearchConfiguration esConfig = Mockito.mock(ElasticSearchConfiguration.class);
    final DatabaseConnection connection = Mockito.mock(DatabaseConnection.class);
    final DatabaseConnectionNodeConfiguration host = new DatabaseConnectionNodeConfiguration();
    host.setHost("localhost");
    host.setHttpPort(9876);
    final ProxyConfiguration proxyConfig = new ProxyConfiguration();
    proxyConfig.setEnabled(false);
    final Map<String, PluginConfiguration> pluginConfigurations =
        Map.of(
            "0",
            new PluginConfiguration(null, null, null),
            "1",
            new PluginConfiguration(null, null, null));

    Mockito.when(config.getElasticSearchConfiguration()).thenReturn(esConfig);
    Mockito.when(esConfig.getProxyConfig()).thenReturn(proxyConfig);
    Mockito.when(esConfig.getInterceptorPlugins()).thenReturn(pluginConfigurations);
    Mockito.when(connection.getAwsEnabled()).thenReturn(false);
    Mockito.when(esConfig.getConnectionNodes()).thenReturn(List.of(host));
    Mockito.when(esConfig.getConnection()).thenReturn(connection);

    final ElasticsearchClient extendedClient =
        ElasticsearchClientBuilder.build(config, new ObjectMapper(), new PluginRepository());
    ((RestClientTransport) extendedClient._transport())
        .restClient()
        .getHttpClient()
        .execute(HttpHost.create("localhost:9200"), new HttpGet(), context, NoopCallback.INSTANCE);

    // then
    final HttpRequestWrapper reqWrapper = (HttpRequestWrapper) context.getAttribute("http.request");

    Assertions.assertThat(reqWrapper.getFirstHeader("foo")).isNull();
  }

  @Test
  void buildClientWithProxyEnabledDoesNotFail() {
    // given
    final BasicHttpContext context = new BasicHttpContext();
    final ConfigurationService config = Mockito.mock(ConfigurationService.class);
    final ElasticSearchConfiguration esConfig = Mockito.mock(ElasticSearchConfiguration.class);
    final DatabaseConnection connection = Mockito.mock(DatabaseConnection.class);
    final DatabaseConnectionNodeConfiguration host = new DatabaseConnectionNodeConfiguration();
    host.setHost("localhost");
    host.setHttpPort(9876);
    final ProxyConfiguration proxyConfig =
        new ProxyConfiguration(true, "proxy.example.com", 8080, false, null, null);
    final Map<String, PluginConfiguration> pluginConfigurations = Map.of();

    Mockito.when(config.getElasticSearchConfiguration()).thenReturn(esConfig);
    Mockito.when(esConfig.getProxyConfig()).thenReturn(proxyConfig);
    Mockito.when(esConfig.getInterceptorPlugins()).thenReturn(pluginConfigurations);
    Mockito.when(connection.getAwsEnabled()).thenReturn(false);
    Mockito.when(esConfig.getConnectionNodes()).thenReturn(List.of(host));
    Mockito.when(esConfig.getConnection()).thenReturn(connection);

    // when
    final ElasticsearchClient extendedClient =
        ElasticsearchClientBuilder.build(config, new ObjectMapper(), new PluginRepository());
    ((RestClientTransport) extendedClient._transport())
        .restClient()
        .getHttpClient()
        .execute(HttpHost.create("localhost:9200"), new HttpGet(), context, NoopCallback.INSTANCE);

    // then - no exception thrown, client was built successfully with proxy
    final HttpRequestWrapper reqWrapper = (HttpRequestWrapper) context.getAttribute("http.request");
    Assertions.assertThat(reqWrapper).isNotNull();
    // No proxy auth header since no credentials were configured
    Assertions.assertThat(reqWrapper.getFirstHeader("Proxy-Authorization")).isNull();
  }

  @Test
  void buildClientWithProxyAuthSetsProxyAuthorizationHeader() {
    // given
    final BasicHttpContext context = new BasicHttpContext();
    final ConfigurationService config = Mockito.mock(ConfigurationService.class);
    final ElasticSearchConfiguration esConfig = Mockito.mock(ElasticSearchConfiguration.class);
    final DatabaseConnection connection = Mockito.mock(DatabaseConnection.class);
    final DatabaseConnectionNodeConfiguration host = new DatabaseConnectionNodeConfiguration();
    host.setHost("localhost");
    host.setHttpPort(9876);
    final ProxyConfiguration proxyConfig =
        new ProxyConfiguration(true, "proxy.example.com", 8080, false, "proxyUser", "proxyPass");
    final Map<String, PluginConfiguration> pluginConfigurations = Map.of();

    Mockito.when(config.getElasticSearchConfiguration()).thenReturn(esConfig);
    Mockito.when(esConfig.getProxyConfig()).thenReturn(proxyConfig);
    Mockito.when(esConfig.getInterceptorPlugins()).thenReturn(pluginConfigurations);
    Mockito.when(connection.getAwsEnabled()).thenReturn(false);
    Mockito.when(esConfig.getConnectionNodes()).thenReturn(List.of(host));
    Mockito.when(esConfig.getConnection()).thenReturn(connection);

    // when
    final ElasticsearchClient extendedClient =
        ElasticsearchClientBuilder.build(config, new ObjectMapper(), new PluginRepository());
    ((RestClientTransport) extendedClient._transport())
        .restClient()
        .getHttpClient()
        .execute(HttpHost.create("localhost:9200"), new HttpGet(), context, NoopCallback.INSTANCE);

    // then
    final HttpRequestWrapper reqWrapper = (HttpRequestWrapper) context.getAttribute("http.request");
    Assertions.assertThat(reqWrapper).isNotNull();
    final String expectedEncoded =
        Base64.getEncoder().encodeToString("proxyUser:proxyPass".getBytes(StandardCharsets.UTF_8));
    Assertions.assertThat(reqWrapper.getFirstHeader("Proxy-Authorization").getValue())
        .isEqualTo("Basic " + expectedEncoded);
  }

  @Test
  void buildClientWithProxyDisabledHasNoProxyAuthorizationHeader() {
    // given
    final BasicHttpContext context = new BasicHttpContext();
    final ConfigurationService config = Mockito.mock(ConfigurationService.class);
    final ElasticSearchConfiguration esConfig = Mockito.mock(ElasticSearchConfiguration.class);
    final DatabaseConnection connection = Mockito.mock(DatabaseConnection.class);
    final DatabaseConnectionNodeConfiguration host = new DatabaseConnectionNodeConfiguration();
    host.setHost("localhost");
    host.setHttpPort(9876);
    // Proxy disabled, even though credentials are set - should not add header
    final ProxyConfiguration proxyConfig =
        new ProxyConfiguration(false, "proxy.example.com", 8080, false, "proxyUser", "proxyPass");
    final Map<String, PluginConfiguration> pluginConfigurations = Map.of();

    Mockito.when(config.getElasticSearchConfiguration()).thenReturn(esConfig);
    Mockito.when(esConfig.getProxyConfig()).thenReturn(proxyConfig);
    Mockito.when(esConfig.getInterceptorPlugins()).thenReturn(pluginConfigurations);
    Mockito.when(connection.getAwsEnabled()).thenReturn(false);
    Mockito.when(esConfig.getConnectionNodes()).thenReturn(List.of(host));
    Mockito.when(esConfig.getConnection()).thenReturn(connection);

    // when
    final ElasticsearchClient extendedClient =
        ElasticsearchClientBuilder.build(config, new ObjectMapper(), new PluginRepository());
    ((RestClientTransport) extendedClient._transport())
        .restClient()
        .getHttpClient()
        .execute(HttpHost.create("localhost:9200"), new HttpGet(), context, NoopCallback.INSTANCE);

    // then
    final HttpRequestWrapper reqWrapper = (HttpRequestWrapper) context.getAttribute("http.request");
    Assertions.assertThat(reqWrapper).isNotNull();
    Assertions.assertThat(reqWrapper.getFirstHeader("Proxy-Authorization")).isNull();
  }

  private static final class NoopCallback implements FutureCallback<HttpResponse> {

    private static final NoopCallback INSTANCE = new NoopCallback();

    @Override
    public void completed(final HttpResponse result) {}

    @Override
    public void failed(final Exception ex) {}

    @Override
    public void cancelled() {}
  }
}
