/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.connect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.camunda.db.search.engine.config.PluginConfiguration;
import io.camunda.operate.property.ElasticsearchProperties;
import io.camunda.operate.property.OperateElasticsearchProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.plugin.search.header.CustomHeader;
import io.camunda.plugin.search.header.DatabaseCustomHeaderSupplier;
import io.camunda.search.connect.plugin.PluginRepository;
import java.util.List;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.nio.client.HttpAsyncClient;
import org.apache.http.protocol.BasicHttpContext;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.Test;

class ElasticsearchConnectorTest {

  @Test
  public void shouldNotDoClusterHealthCheckWhenDisabled() {
    final OperateProperties operateProperties = new OperateProperties();
    final OperateElasticsearchProperties esProperties = new OperateElasticsearchProperties();
    esProperties.setHealthCheckEnabled(false);
    operateProperties.setElasticsearch(esProperties);
    final ElasticsearchConnector connector = spy(new ElasticsearchConnector(operateProperties));

    connector.createEsClient(esProperties, mock());

    verify(connector, never()).checkHealth(any(RestHighLevelClient.class));
  }

  @Test
  public void shouldDoClusterHealthCheckWhenDefaultPropertyValuesUsed() {
    final OperateProperties operateProperties = new OperateProperties();
    final OperateElasticsearchProperties esProperties = new OperateElasticsearchProperties();
    operateProperties.setElasticsearch(esProperties);
    final ElasticsearchConnector connector = spy(new ElasticsearchConnector(operateProperties));
    doReturn(true).when(connector).checkHealth(any(RestHighLevelClient.class));

    connector.createEsClient(esProperties, mock());

    verify(connector, times(1)).checkHealth(any(RestHighLevelClient.class));
  }

  @Test
  void shouldApplyRequestInterceptorsInOrderForNativeRestClient() {
    final var context = new BasicHttpContext();
    final var operateProperties = new OperateProperties();
    final PluginRepository pluginRepository = new PluginRepository();
    pluginRepository.load(
        List.of(new PluginConfiguration("plg1", TestPlugin.class.getName(), null)));
    final var connector = spy(new ElasticsearchConnector(operateProperties));
    doReturn(true).when(connector).checkHealth(any(ElasticsearchClient.class));
    connector.setEsClientRepository(pluginRepository);
    final var client = connector.elasticsearchClient();

    // when
    ((RestClientTransport) client._transport())
        .restClient()
        .getHttpClient()
        .execute(HttpHost.create("localhost:9200"), new HttpGet(), context, NoopCallback.INSTANCE);

    // then
    final HttpRequestWrapper reqWrapper = (HttpRequestWrapper) context.getAttribute("http.request");

    assertThat(reqWrapper.getFirstHeader("foo").getValue()).isEqualTo("bar");
  }

  // TODO: this test will may be removed once RestHighLevelClient is gone
  @Test
  void shouldApplyRequestInterceptorsInOrderForES7RestClient() throws Exception {
    final var context = new BasicHttpContext();
    final var esProperties = new ElasticsearchProperties();
    final var operateProperties = new OperateProperties();
    final PluginRepository pluginRepository = new PluginRepository();
    pluginRepository.load(
        List.of(new PluginConfiguration("plg1", TestPlugin.class.getName(), null)));
    final var connector = spy(new ElasticsearchConnector(operateProperties));
    doReturn(true).when(connector).checkHealth(any(RestHighLevelClient.class));
    final var client = connector.createEsClient(esProperties, pluginRepository);

    // when
    getRestHighLevelClient(client)
        .execute(HttpHost.create("localhost:9200"), new HttpGet(), context, NoopCallback.INSTANCE);

    // then
    final HttpRequestWrapper reqWrapper = (HttpRequestWrapper) context.getAttribute("http.request");

    assertThat(reqWrapper.getFirstHeader("foo").getValue()).isEqualTo("bar");
  }

  private static HttpAsyncClient getRestHighLevelClient(final RestHighLevelClient client)
      throws Exception {
    final var field = client.getClass().getDeclaredField("client");
    field.setAccessible(true);
    final RestClient restClient = (RestClient) field.get(client);
    return restClient.getHttpClient();
  }

  public static final class TestPlugin implements DatabaseCustomHeaderSupplier {

    @Override
    public CustomHeader getSearchDatabaseCustomHeader() {
      return new CustomHeader("foo", "bar");
    }
  }

  private static final class NoopCallback implements FutureCallback<HttpResponse> {

    private static final NoopCallback INSTANCE = new NoopCallback();

    @Override
    public void completed(final HttpResponse result) {
    }

    @Override
    public void failed(final Exception ex) {
    }

    @Override
    public void cancelled() {
    }
  }
}
