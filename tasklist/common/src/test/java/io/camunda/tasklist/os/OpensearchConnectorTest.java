/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.os;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.camunda.db.se.config.PluginConfiguration;
import io.camunda.search.connect.plugin.PluginRepository;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.TestPlugin;
import java.util.List;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.HttpGet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;
import org.opensearch.client.RestClient;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5Transport;

class OpensearchConnectorTest {

  @RegisterExtension
  // Native OS clients refuse to work while
  // actual HTTP(S) connection is available
  static WireMockExtension osServer =
      WireMockExtension.newInstance()
          .options(WireMockConfiguration.wireMockConfig().dynamicPort())
          .build();

  @Test
  void shouldApplyRequestInterceptorsForOSTasklistClient() throws Exception {
    final var context = HttpClientContext.create();
    final var taskListProperties = new TasklistProperties();
    final PluginRepository pluginRepository = new PluginRepository();
    pluginRepository.load(
        List.of(new PluginConfiguration("plg1", TestPlugin.class.getName(), null)));

    final var connector = Mockito.spy(new OpenSearchConnector());
    Mockito.doReturn(true).when(connector).checkHealth(Mockito.any(OpenSearchClient.class));
    connector.setOsClientRepository(pluginRepository);
    connector.setTasklistProperties(taskListProperties);
    connector.setTasklistObjectMapper(new ObjectMapper());

    // Regular tasklist client
    final var client = connector.tasklistOsClient();

    // when
    final WireMockRuntimeInfo wmRuntimeInfo = osServer.getRuntimeInfo();
    final var asyncResp =
        getOpensearchApacheClient(((ApacheHttpClient5Transport) client._transport()))
            .execute(
                SimpleHttpRequest.create("GET", wmRuntimeInfo.getHttpBaseUrl()),
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
  void shouldApplyRequestInterceptorsForOSTasklistCamundaClient() throws Exception {
    final var context = HttpClientContext.create();
    final var taskListProperties = new TasklistProperties();
    final PluginRepository pluginRepository = new PluginRepository();
    pluginRepository.load(
        List.of(new PluginConfiguration("plg1", TestPlugin.class.getName(), null)));

    final var connector = Mockito.spy(new OpenSearchConnector());
    Mockito.doReturn(true).when(connector).checkHealth(Mockito.any(OpenSearchClient.class));
    connector.setZeebeOsClientRepository(pluginRepository);
    connector.setTasklistProperties(taskListProperties);
    connector.setTasklistObjectMapper(new ObjectMapper());

    // Regular tasklist client
    final var client = connector.tasklistZeebeOsClient();

    // when
    final WireMockRuntimeInfo wmRuntimeInfo = osServer.getRuntimeInfo();
    final var asyncResp =
        getOpensearchApacheClient(((ApacheHttpClient5Transport) client._transport()))
            .execute(
                SimpleHttpRequest.create("GET", wmRuntimeInfo.getHttpBaseUrl()),
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
  void shouldApplyRequestInterceptorsForOSAsyncTasklistClient() throws Exception {
    final var context = HttpClientContext.create();
    final var taskListProperties = new TasklistProperties();
    final PluginRepository pluginRepository = new PluginRepository();
    pluginRepository.load(
        List.of(new PluginConfiguration("plg1", TestPlugin.class.getName(), null)));

    final var connector = Mockito.spy(new OpenSearchConnector());
    Mockito.doReturn(true).when(connector).checkHealth(Mockito.any(OpenSearchAsyncClient.class));
    connector.setOsClientRepository(pluginRepository);
    connector.setTasklistProperties(taskListProperties);
    connector.setTasklistObjectMapper(new ObjectMapper());

    // Regular tasklist client
    final var client = connector.tasklistOsAsyncClient();

    // when
    final WireMockRuntimeInfo wmRuntimeInfo = osServer.getRuntimeInfo();
    final var asyncResp =
        getOpensearchApacheClient(((ApacheHttpClient5Transport) client._transport()))
            .execute(
                SimpleHttpRequest.create("GET", wmRuntimeInfo.getHttpBaseUrl()),
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
  void shouldApplyRequestInterceptorsForOSRestTasklistClient() throws Exception {
    final var context = new org.apache.http.protocol.BasicHttpContext();
    final var taskListProperties = new TasklistProperties();
    final PluginRepository pluginRepository = new PluginRepository();
    pluginRepository.load(
        List.of(new PluginConfiguration("plg1", TestPlugin.class.getName(), null)));

    final var connector = Mockito.spy(new OpenSearchConnector());
    Mockito.doReturn(true).when(connector).checkHealth(Mockito.any(OpenSearchAsyncClient.class));
    connector.setOsClientRepository(pluginRepository);
    connector.setTasklistProperties(taskListProperties);
    connector.setTasklistObjectMapper(new ObjectMapper());

    // Regular tasklist client
    final var client = connector.tasklistOsRestClient();

    // when
    final WireMockRuntimeInfo wmRuntimeInfo = osServer.getRuntimeInfo();
    final var asyncResp =
        getOpensearchNativeRestClient(client)
            .execute(HttpHost.create(wmRuntimeInfo.getHttpBaseUrl()), new HttpGet(), context, null);

    try {
      asyncResp.get();
    } catch (final Exception e) {
      // ignore as we don't really care about the outcome
    }

    // then
    final var reqWrapper =
        (org.apache.http.client.methods.HttpRequestWrapper) context.getAttribute("http.request");
    assertThat(reqWrapper.getFirstHeader("foo").getValue()).isEqualTo("bar");
  }

  private static CloseableHttpAsyncClient getOpensearchApacheClient(
      final ApacheHttpClient5Transport client) throws Exception {
    final var field = client.getClass().getDeclaredField("client");
    field.setAccessible(true);
    return (CloseableHttpAsyncClient) field.get(client);
  }

  private static org.apache.http.impl.nio.client.CloseableHttpAsyncClient
      getOpensearchNativeRestClient(final RestClient client) throws Exception {
    final var field = client.getClass().getDeclaredField("client");
    field.setAccessible(true);
    return (org.apache.http.impl.nio.client.CloseableHttpAsyncClient) field.get(client);
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
