/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.os;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;

import io.camunda.db.search.engine.config.PluginConfiguration;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.OpenSearchConfiguration;
import io.camunda.optimize.service.util.configuration.db.DatabaseConnection;
import io.camunda.optimize.service.util.configuration.elasticsearch.DatabaseConnectionNodeConfiguration;
import io.camunda.optimize.upgrade.util.TestPlugin;
import io.camunda.search.connect.plugin.PluginRepository;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5Transport;

// TODO: when Zeebe parent becomes also a parent for Optimize,
// the wiremock config needs to be replaced similar as in this
// commit https://github.com/camunda/camunda/commit/d9bc7a5b380b80c69a7e86f4f295686fc697c85d
class OpenSearchClientBuilderTest {

  private static final String BASE_URL = "http://localhost:8090/";
  private static ClientAndServer mockServer;

  @BeforeAll
  static void before() {
    mockServer = startClientAndServer(8090);
    mockServer
        .when(HttpRequest.request().withMethod("GET").withPath("/"))
        .respond(HttpResponse.response().withStatusCode(200).withBody("mocked response"));
  }

  @AfterAll
  static void after() {
    mockServer.stop();
  }

  @Test
  void buildExtendedClientHappyPath() throws Exception {
    final var context = new HttpClientContext();
    final var config = Mockito.mock(ConfigurationService.class);
    final var osConfig = Mockito.mock(OpenSearchConfiguration.class);
    final var connection = Mockito.mock(DatabaseConnection.class);
    final var host = new DatabaseConnectionNodeConfiguration();
    host.setHost("localhost");
    host.setHttpPort(9876);
    final Map<String, PluginConfiguration> pluginConfigurations =
        Map.of("0", new PluginConfiguration("plg1", TestPlugin.class.getName(), null));

    Mockito.when(config.getOpenSearchConfiguration()).thenReturn(osConfig);
    Mockito.when(osConfig.getInterceptorPlugins()).thenReturn(pluginConfigurations);
    Mockito.when(connection.getAwsEnabled()).thenReturn(false);
    Mockito.when(osConfig.getConnectionNodes()).thenReturn(List.of(host));
    Mockito.when(osConfig.getConnection()).thenReturn(connection);

    final var extendedClient =
        OpenSearchClientBuilder.buildOpenSearchClientFromConfig(config, new PluginRepository());

    final var client =
        getOpensearchApacheClient(((ApacheHttpClient5Transport) extendedClient._transport()));
    final var asyncResp =
        client.execute(SimpleHttpRequest.create("GET", BASE_URL), context, NoopCallback.INSTANCE);

    try {
      asyncResp.get(2000, TimeUnit.MILLISECONDS);
    } catch (final Exception e) {
      // ignore as we don't really care about the outcome
    }

    // then
    final var reqWrapper = context.getRequest();

    Assertions.assertThat(reqWrapper.getFirstHeader("foo").getValue()).isEqualTo("bar");
  }

  @Test
  void buildAsyncClientHappyPath() throws Exception {
    final var context = new HttpClientContext();
    final var config = Mockito.mock(ConfigurationService.class);
    final var osConfig = Mockito.mock(OpenSearchConfiguration.class);
    final var connection = Mockito.mock(DatabaseConnection.class);
    final var host = new DatabaseConnectionNodeConfiguration();
    host.setHost("localhost");
    host.setHttpPort(9876);
    final Map<String, PluginConfiguration> pluginConfigurations =
        Map.of("0", new PluginConfiguration("plg1", TestPlugin.class.getName(), null));

    Mockito.when(config.getOpenSearchConfiguration()).thenReturn(osConfig);
    Mockito.when(osConfig.getInterceptorPlugins()).thenReturn(pluginConfigurations);
    Mockito.when(connection.getAwsEnabled()).thenReturn(false);
    Mockito.when(osConfig.getConnectionNodes()).thenReturn(List.of(host));
    Mockito.when(osConfig.getConnection()).thenReturn(connection);

    final var extendedClient =
        OpenSearchClientBuilder.buildOpenSearchAsyncClientFromConfig(
            config, new PluginRepository());

    final var client =
        getOpensearchApacheClient(((ApacheHttpClient5Transport) extendedClient._transport()));
    final var asyncResp =
        client.execute(SimpleHttpRequest.create("GET", BASE_URL), context, NoopCallback.INSTANCE);

    try {
      asyncResp.get(2000, TimeUnit.MILLISECONDS);
    } catch (final Exception e) {
      // ignore as we don't really care about the outcome
    }

    // then
    final var reqWrapper = context.getRequest();

    Assertions.assertThat(reqWrapper.getFirstHeader("foo").getValue()).isEqualTo("bar");
  }

  @Test
  void buildClientWithNoPluginsDoesNotFail() throws Exception {
    final var context = new HttpClientContext();
    final var config = Mockito.mock(ConfigurationService.class);
    final var osConfig = Mockito.mock(OpenSearchConfiguration.class);
    final var connection = Mockito.mock(DatabaseConnection.class);
    final var host = new DatabaseConnectionNodeConfiguration();
    host.setHost("localhost");
    host.setHttpPort(9876);
    final Map<String, PluginConfiguration> pluginConfigurations = Map.of();

    Mockito.when(config.getOpenSearchConfiguration()).thenReturn(osConfig);
    Mockito.when(osConfig.getInterceptorPlugins()).thenReturn(pluginConfigurations);
    Mockito.when(connection.getAwsEnabled()).thenReturn(false);
    Mockito.when(osConfig.getConnectionNodes()).thenReturn(List.of(host));
    Mockito.when(osConfig.getConnection()).thenReturn(connection);

    final var extendedClient =
        OpenSearchClientBuilder.buildOpenSearchClientFromConfig(config, new PluginRepository());

    final var client =
        getOpensearchApacheClient(((ApacheHttpClient5Transport) extendedClient._transport()));
    final var asyncResp =
        client.execute(SimpleHttpRequest.create("GET", BASE_URL), context, NoopCallback.INSTANCE);

    try {
      asyncResp.get(2000, TimeUnit.MILLISECONDS);
    } catch (final Exception e) {
      // ignore as we don't really care about the outcome
    }

    // then
    final var reqWrapper = context.getRequest();

    Assertions.assertThat(reqWrapper.getFirstHeader("foo")).isNull();
  }

  @Test
  void buildClientWithAllEmptyPluginsDoesNotFail() throws Exception {
    final var context = new HttpClientContext();
    final var config = Mockito.mock(ConfigurationService.class);
    final var osConfig = Mockito.mock(OpenSearchConfiguration.class);
    final var connection = Mockito.mock(DatabaseConnection.class);
    final var host = new DatabaseConnectionNodeConfiguration();
    host.setHost("localhost");
    host.setHttpPort(9876);
    final Map<String, PluginConfiguration> pluginConfigurations =
        Map.of(
            "0", new PluginConfiguration(null, null, null),
            "1", new PluginConfiguration(null, null, null),
            "2", new PluginConfiguration(null, null, null));

    Mockito.when(config.getOpenSearchConfiguration()).thenReturn(osConfig);
    Mockito.when(osConfig.getInterceptorPlugins()).thenReturn(pluginConfigurations);
    Mockito.when(connection.getAwsEnabled()).thenReturn(false);
    Mockito.when(osConfig.getConnectionNodes()).thenReturn(List.of(host));
    Mockito.when(osConfig.getConnection()).thenReturn(connection);

    final var extendedClient =
        OpenSearchClientBuilder.buildOpenSearchClientFromConfig(config, new PluginRepository());

    final var client =
        getOpensearchApacheClient(((ApacheHttpClient5Transport) extendedClient._transport()));
    final var asyncResp =
        client.execute(SimpleHttpRequest.create("GET", BASE_URL), context, NoopCallback.INSTANCE);

    try {
      asyncResp.get(2000, TimeUnit.MILLISECONDS);
    } catch (final Exception e) {
      // ignore as we don't really care about the outcome
    }

    // then
    final var reqWrapper = context.getRequest();

    Assertions.assertThat(reqWrapper.getFirstHeader("foo")).isNull();
  }

  private static CloseableHttpAsyncClient getOpensearchApacheClient(
      final ApacheHttpClient5Transport transport) throws Exception {
    final var field = transport.getClass().getDeclaredField("client");
    field.setAccessible(true);
    return (CloseableHttpAsyncClient) field.get(transport);
  }

  private static final class NoopCallback implements FutureCallback<SimpleHttpResponse> {

    private static final NoopCallback INSTANCE = new NoopCallback();

    @Override
    public void completed(final SimpleHttpResponse result) {
    }

    @Override
    public void failed(final Exception ex) {
    }

    @Override
    public void cancelled() {
    }
  }
}
