/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.connect;

import static io.camunda.operate.util.PluginTestUtils.createCustomHeaderInterceptorJar;
import static io.camunda.operate.util.PluginTestUtils.createPluginFromJar;
import static io.camunda.operate.util.TestStaticCustomHeaderInterceptor.X_CUSTOM_HEADER;
import static io.camunda.operate.util.TestStaticCustomHeaderInterceptor.X_CUSTOM_HEADER_VALUE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.property.ElasticsearchProperties;
import io.camunda.operate.util.TestDynamicCustomHeaderInterceptor;
import io.camunda.operate.util.TestStaticCustomHeaderInterceptor;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.http.HttpException;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.protocol.BasicHttpContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class OperateESPluginLoadingTest {

  @Mock private HttpAsyncClientBuilder clientBuilder;
  @InjectMocks private ElasticsearchConnector connector;

  @Test
  public void shouldAddStaticCustomHeader() throws HttpException, IOException {
    final var plugin =
        createPluginFromJar(
            createCustomHeaderInterceptorJar(TestStaticCustomHeaderInterceptor.class),
            TestStaticCustomHeaderInterceptor.class);
    final var elasticsearchProperties = new ElasticsearchProperties();
    elasticsearchProperties.setInterceptorPlugins(Map.of("plugin-id", plugin));
    final var getReq = new HttpGet("localhost:12345");

    connector.configureHttpClient(clientBuilder, elasticsearchProperties);

    final ArgumentCaptor<HttpRequestInterceptor> requestCaptor =
        ArgumentCaptor.forClass(HttpRequestInterceptor.class);
    Mockito.verify(clientBuilder).addInterceptorLast(requestCaptor.capture());
    requestCaptor.getValue().process(getReq, new BasicHttpContext());
    getReq.containsHeader(X_CUSTOM_HEADER);
    assertThat(getReq.containsHeader(X_CUSTOM_HEADER)).isTrue();
    assertThat(getReq.getFirstHeader(X_CUSTOM_HEADER).getValue()).isEqualTo(X_CUSTOM_HEADER_VALUE);
  }

  @Test
  public void dynamicCustomHeadersShouldChange() throws HttpException, IOException {
    final var plugin =
        createPluginFromJar(
            createCustomHeaderInterceptorJar(TestDynamicCustomHeaderInterceptor.class),
            TestDynamicCustomHeaderInterceptor.class);
    final var elasticsearchProperties = new ElasticsearchProperties();
    elasticsearchProperties.setInterceptorPlugins(Map.of("plugin-id", plugin));

    final var getReq1 = new HttpGet("localhost:12345");
    final ArgumentCaptor<HttpRequestInterceptor> requestCaptor1 =
        ArgumentCaptor.forClass(HttpRequestInterceptor.class);

    final var getReq2 = new HttpGet("localhost:12345");
    final ArgumentCaptor<HttpRequestInterceptor> requestCaptor2 =
        ArgumentCaptor.forClass(HttpRequestInterceptor.class);

    connector.configureHttpClient(clientBuilder, elasticsearchProperties);

    Mockito.verify(clientBuilder).addInterceptorLast(requestCaptor1.capture());
    requestCaptor1.getValue().process(getReq1, new BasicHttpContext());

    Mockito.verify(clientBuilder).addInterceptorLast(requestCaptor2.capture());
    requestCaptor2.getValue().process(getReq2, new BasicHttpContext());

    assertThat(getReq1.getFirstHeader(X_CUSTOM_HEADER).getValue())
        .isNotEqualTo(getReq2.getFirstHeader(X_CUSTOM_HEADER).getValue());
  }

  @Test
  public void shouldRaiseExceptionWhenJarIsNull() {
    final var plugin =
        createPluginFromJar(
            createCustomHeaderInterceptorJar(TestStaticCustomHeaderInterceptor.class),
            TestStaticCustomHeaderInterceptor.class);
    plugin.setJarPath(null);
    final var elasticsearchProperties = new ElasticsearchProperties();
    elasticsearchProperties.setInterceptorPlugins(Map.of("plugin-id", plugin));

    Assertions.assertThrows(
        RuntimeException.class,
        () -> connector.configureHttpClient(clientBuilder, elasticsearchProperties));
  }

  @Test
  public void shouldRaiseExceptionWhenIncorrectPluginType() {
    final var plugin =
        createPluginFromJar(createCustomHeaderInterceptorJar(HashMap.class), HashMap.class);
    final var elasticsearchProperties = new ElasticsearchProperties();
    elasticsearchProperties.setInterceptorPlugins(Map.of("plugin-id", plugin));

    Assertions.assertThrows(
        RuntimeException.class,
        () -> connector.configureHttpClient(clientBuilder, elasticsearchProperties));
  }
}
