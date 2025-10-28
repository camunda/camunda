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
import static org.mockito.Mockito.spy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.camunda.operate.property.OperateOpensearchProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.TestPlugin;
import io.camunda.search.connect.plugin.PluginConfiguration;
import io.camunda.search.connect.plugin.PluginRepository;
import java.util.List;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5Transport;

@ExtendWith(MockitoExtension.class)
public class OpensearchConnectorTest {

  @RegisterExtension
  // Native OS clients refuse to work while
  // actual HTTP(S) connection is available
  static WireMockExtension osServer =
      WireMockExtension.newInstance()
          .options(WireMockConfiguration.wireMockConfig().dynamicPort())
          .build();

  private static final String AWS_REGION = "eu-west-1";
  private static final String AWS_SECRET_ACCESS_KEY = "awsSecretAcessKey";
  private static final String AWS_ACCESS_KEY_ID = "awsAccessKeyId";
  ObjectMapper objectMapper = new ObjectMapper();

  private final OperateOpensearchProperties opensearchProperties =
      new OperateOpensearchProperties();

  private final OperateProperties operateProperties = new OperateProperties();

  private OpensearchConnector opensearchConnector;

  @BeforeEach
  public void setup() {
    operateProperties.setOpensearch(opensearchProperties);
    opensearchConnector = new OpensearchConnector(operateProperties, objectMapper);
  }

  @Test
  public void syncHasAwsEnabledAndAwsCredentialsSetAndShouldUseAwsCredentials() {
    System.setProperty("aws.accessKeyId", AWS_ACCESS_KEY_ID);
    System.setProperty("aws.secretAccessKey", AWS_SECRET_ACCESS_KEY);
    System.setProperty("aws.region", AWS_REGION);
    opensearchProperties.setUsername("demo");
    opensearchProperties.setPassword("demo");
    opensearchProperties.setAwsEnabled(true);

    final OpenSearchClient client =
        opensearchConnector.createOsClient(opensearchProperties, new PluginRepository());

    assertThat(client._transport().getClass()).isEqualTo(AwsSdk2Transport.class);
  }

  @Test
  public void syncHasAwsCredentialsButShouldUseBasicAuth() {
    System.setProperty("aws.accessKeyId", AWS_ACCESS_KEY_ID);
    System.setProperty("aws.secretAccessKey", AWS_SECRET_ACCESS_KEY);
    System.setProperty("aws.region", AWS_REGION);
    opensearchProperties.setUsername("demo");
    opensearchProperties.setPassword("demo");
    // awsEnabled not set -> should default to false
    final OpensearchConnector spyConnector = spy(opensearchConnector);
    doReturn(true).when(spyConnector).checkHealth(any(OpenSearchClient.class));

    final OpenSearchClient client =
        spyConnector.createOsClient(opensearchProperties, new PluginRepository());

    assertThat(client._transport().getClass()).isEqualTo(ApacheHttpClient5Transport.class);
  }

  @Test
  void shouldApplyRequestInterceptorsForOSOperateClient() throws Exception {
    final var context = HttpClientContext.create();
    final var operateProps = new OperateProperties();
    final PluginRepository pluginRepository = new PluginRepository();
    pluginRepository.load(
        List.of(new PluginConfiguration("plg1", TestPlugin.class.getName(), null)));

    final var connector = Mockito.spy(new OpensearchConnector(operateProps, new ObjectMapper()));
    Mockito.doReturn(true).when(connector).checkHealth(Mockito.any(OpenSearchClient.class));
    connector.setOsClientRepository(pluginRepository);

    // Regular Operate client
    final var client = connector.operateOpenSearchClient();

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

    Assertions.assertThat(reqWrapper.getFirstHeader("foo").getValue()).isEqualTo("bar");
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
