/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
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
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.core5.http.impl.BasicEntityDetails;
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
}
