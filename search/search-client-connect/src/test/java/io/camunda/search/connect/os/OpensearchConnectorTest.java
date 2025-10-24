/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.os;

import static io.camunda.search.connect.plugin.util.TestDatabaseCustomHeaderSupplierImpl.KEY_CUSTOM_HEADER;
import static io.camunda.search.connect.plugin.util.TestDatabaseCustomHeaderSupplierImpl.VALUE_CUSTOM_HEADER;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.plugin.PluginConfiguration;
import io.camunda.search.connect.plugin.PluginRepository;
import io.camunda.search.connect.plugin.util.TestDatabaseCustomHeaderSupplierImpl;
import java.util.List;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5Transport;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

class OpensearchConnectorTest {

  @RegisterExtension
  // Native OS clients refuse to work while
  // actual HTTP(S) connection is available
  static WireMockExtension osServer =
      WireMockExtension.newInstance()
          .options(WireMockConfiguration.wireMockConfig().dynamicPort())
          .build();

  @Test
  void shouldApplyRequestInterceptorsWithinClasspathForNativeSyncClient() throws Exception {
    final var context = HttpClientContext.create();
    final var configuration = new ConnectConfiguration();
    configuration.setInterceptorPlugins(
        List.of(
            new PluginConfiguration(
                "my-plg", TestDatabaseCustomHeaderSupplierImpl.class.getName(), null)));
    final PluginRepository pluginRepository = new PluginRepository();
    final var connector =
        Mockito.spy(
            new OpensearchConnector(configuration, new ObjectMapper(), null, pluginRepository));
    final var client = connector.createClient();

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

    final var reqWrapper = context.getRequest();

    Assertions.assertThat(reqWrapper.getFirstHeader(KEY_CUSTOM_HEADER).getValue())
        .isEqualTo(VALUE_CUSTOM_HEADER);
  }

  @ParameterizedTest
  @CsvSource({
    "false, false, 'Basic auth even with AWS credentials present'",
    "true, true, 'AWS config used when enabled and credentials present'"
  })
  @DisplayName("Should handle AWS configuration scenarios correctly")
  void shouldHandleAwsConfiguration(
      final boolean credentialsPresent, final boolean isAwsTransport, final String scenarioName) {
    System.setProperty("aws.region", "us-west-1");

    try {
      final var credentialsProvider =
          StaticCredentialsProvider.create(AwsBasicCredentials.create("username", "password"));

      final var connectConfig = new ConnectConfiguration();
      connectConfig.setAwsEnabled(credentialsPresent);

      final var connector =
          new OpensearchConnector(
              connectConfig, new ObjectMapper(), credentialsProvider, new PluginRepository());

      final var client = connector.createClient();

      Assertions.assertThat(client._transport() instanceof AwsSdk2Transport)
          .as("Expected AWS transport for scenario: %s", scenarioName)
          .isEqualTo(isAwsTransport);
    } finally {
      System.clearProperty("aws.region");
    }
  }

  @Test
  void shouldApplyRequestInterceptorsWithinClasspathForNativeAsyncClient() throws Exception {
    final var context = HttpClientContext.create();
    final var configuration = new ConnectConfiguration();
    configuration.setInterceptorPlugins(
        List.of(
            new PluginConfiguration(
                "my-plg", TestDatabaseCustomHeaderSupplierImpl.class.getName(), null)));
    final PluginRepository pluginRepository = new PluginRepository();
    final var connector =
        Mockito.spy(
            new OpensearchConnector(configuration, new ObjectMapper(), null, pluginRepository));
    final var client = connector.createAsyncClient();

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

    final var reqWrapper = context.getRequest();

    Assertions.assertThat(reqWrapper.getFirstHeader(KEY_CUSTOM_HEADER).getValue())
        .isEqualTo(VALUE_CUSTOM_HEADER);
  }

  private static CloseableHttpAsyncClient getOpensearchApacheClient(
      final ApacheHttpClient5Transport client) throws Exception {
    final var field = client.getClass().getDeclaredField("client");
    field.setAccessible(true);
    return (CloseableHttpAsyncClient) field.get(client);
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
