/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.es;

import static io.camunda.search.connect.plugin.util.TestDatabaseCustomHeaderSupplierImpl.KEY_CUSTOM_HEADER;
import static io.camunda.search.connect.plugin.util.TestDatabaseCustomHeaderSupplierImpl.VALUE_CUSTOM_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.plugin.PluginConfiguration;
import io.camunda.search.connect.plugin.PluginRepository;
import io.camunda.search.connect.plugin.util.TestDatabaseCustomHeaderSupplierImpl;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import net.bytebuddy.ByteBuddy;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.http.impl.BasicEntityDetails;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ElasticsearchConnectorTest {

  @Test
  void shouldApplyRequestInterceptorsWithinClasspathForNativeRestClient()
      throws IOException, org.apache.hc.core5.http.HttpException {
    // given
    final var configuration = new ConnectConfiguration();
    configuration.setInterceptorPlugins(
        List.of(
            new PluginConfiguration(
                "my-plg", TestDatabaseCustomHeaderSupplierImpl.class.getName(), null)));
    final PluginRepository pluginRepository = new PluginRepository();
    pluginRepository.load(configuration.getInterceptorPlugins());
    final var connector =
        new ElasticsearchConnector(configuration, new ObjectMapper(), pluginRepository);
    final var builder = Mockito.mock(HttpAsyncClientBuilder.class);

    // when
    connector.configureHttpClient(builder, configuration, pluginRepository.asRequestInterceptor());
    final var interceptorCaptor =
        org.mockito.ArgumentCaptor.forClass(org.apache.hc.core5.http.HttpRequestInterceptor.class);
    Mockito.verify(builder).addRequestInterceptorLast(interceptorCaptor.capture());

    final var request = new BasicHttpRequest("GET", "localhost");
    final var entity = new BasicEntityDetails(0, null);
    interceptorCaptor.getValue().process(request, entity, new BasicHttpContext());

    // then
    assertThat(request.getFirstHeader("foo").getValue()).isEqualTo("bar");
  }

  @Test
  void shouldApplyExternalRequestInterceptorsForNativeRestClient()
      throws IOException, org.apache.hc.core5.http.HttpException {
    // given
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
    pluginRepository.load(configuration.getInterceptorPlugins());
    final var connector =
        new ElasticsearchConnector(configuration, new ObjectMapper(), pluginRepository);
    final var builder = Mockito.mock(HttpAsyncClientBuilder.class);

    // when
    connector.configureHttpClient(builder, configuration, pluginRepository.asRequestInterceptor());
    final var interceptorCaptor =
        org.mockito.ArgumentCaptor.forClass(org.apache.hc.core5.http.HttpRequestInterceptor.class);
    Mockito.verify(builder).addRequestInterceptorLast(interceptorCaptor.capture());

    final var request = new BasicHttpRequest("GET", "localhost");
    final var entity = new BasicEntityDetails(0, null);
    interceptorCaptor.getValue().process(request, entity, new BasicHttpContext());

    // then
    assertThat(request.getFirstHeader(KEY_CUSTOM_HEADER).getValue()).isEqualTo(VALUE_CUSTOM_HEADER);
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
    connector.setRequestTimeouts(requestConfigBuilder, configuration);

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
    connector.setRequestTimeouts(requestConfigBuilder, configuration);

    // then
    Mockito.verify(connectionConfigBuilder).setSocketTimeout(Timeout.ofMilliseconds(30_000));
    Mockito.verify(connectionConfigBuilder).setConnectTimeout(Timeout.ofMilliseconds(5_000));
    Mockito.verify(requestConfigBuilder)
        .setConnectionRequestTimeout(Timeout.ofMilliseconds(180_000));
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
    assertThat(captor.getValue().isSoKeepalive()).isTrue();
  }
}
