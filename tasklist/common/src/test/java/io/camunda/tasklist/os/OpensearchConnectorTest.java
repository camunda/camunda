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
import io.camunda.search.connect.plugin.PluginConfiguration;
import io.camunda.search.connect.plugin.PluginRepository;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.TestPlugin;
import java.util.List;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;
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
    Mockito.doReturn(true).when(connector).isHealthy(Mockito.any(OpenSearchClient.class));
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
  void shouldResolveCredentialsForAllConfiguredUrls() throws Exception {
    final var tasklistProperties = new TasklistProperties();
    tasklistProperties
        .getOpenSearch()
        .setUrls(List.of("http://opensearch-1:9205", "http://opensearch-2:9206"));
    tasklistProperties.getOpenSearch().setUsername("user");
    tasklistProperties.getOpenSearch().setPassword("secret");

    final var connector = Mockito.spy(new OpenSearchConnector());
    Mockito.doReturn(true).when(connector).isHealthy(Mockito.any(OpenSearchClient.class));
    connector.setTasklistProperties(tasklistProperties);
    connector.setTasklistObjectMapper(new ObjectMapper());

    final var client = connector.tasklistOsClient();
    final var asyncClient =
        getOpensearchApacheClient((ApacheHttpClient5Transport) client._transport());
    final var credentialsProvider = getCredentialsProvider(asyncClient);
    final var context = HttpClientContext.create();

    assertThat(
            credentialsProvider.getCredentials(
                new AuthScope(new HttpHost("http", "opensearch-1", 9205)), context))
        .isNotNull();
    assertThat(
            credentialsProvider.getCredentials(
                new AuthScope(new HttpHost("http", "opensearch-2", 9206)), context))
        .isNotNull();
  }

  private static CloseableHttpAsyncClient getOpensearchApacheClient(
      final ApacheHttpClient5Transport client) throws Exception {
    final var field = client.getClass().getDeclaredField("client");
    field.setAccessible(true);
    return (CloseableHttpAsyncClient) field.get(client);
  }

  private static CredentialsProvider getCredentialsProvider(final CloseableHttpAsyncClient client)
      throws Exception {
    Class<?> current = client.getClass();
    while (current != null) {
      for (final var field : current.getDeclaredFields()) {
        if (CredentialsProvider.class.isAssignableFrom(field.getType())) {
          field.setAccessible(true);
          return (CredentialsProvider) field.get(client);
        }
      }
      current = current.getSuperclass();
    }
    throw new IllegalStateException("CredentialsProvider not found on client");
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
