/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.es;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.camunda.db.search.engine.config.PluginConfiguration;
import io.camunda.search.connect.plugin.PluginRepository;
import io.camunda.tasklist.property.ElasticsearchProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.TestPlugin;
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
import org.mockito.Mockito;

class ElasticsearchConnectorTest {

  @Test
  void shouldApplyRequestInterceptorsInOrderForNativeRestClient() {
    final var context = new BasicHttpContext();
    final var taskListProperties = new TasklistProperties();
    final PluginRepository pluginRepository = new PluginRepository();
    pluginRepository.load(
        List.of(new PluginConfiguration("plg1", TestPlugin.class.getName(), null)));
    final var connector = Mockito.spy(new ElasticsearchConnector());
    Mockito.doReturn(true).when(connector).checkHealth(Mockito.any(ElasticsearchClient.class));
    connector.setEsClientRepository(pluginRepository);
    connector.setTasklistProperties(taskListProperties);
    final var client = connector.tasklistElasticsearchClient();

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
    final var taskListProperties = new TasklistProperties();
    final PluginRepository pluginRepository = new PluginRepository();
    pluginRepository.load(
        List.of(new PluginConfiguration("plg1", TestPlugin.class.getName(), null)));
    final var connector = Mockito.spy(new ElasticsearchConnector());
    Mockito.doReturn(true).when(connector).checkHealth(Mockito.any(RestHighLevelClient.class));
    connector.setTasklistProperties(taskListProperties);
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
