/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

import static io.camunda.zeebe.exporter.opensearch.utils.PluginTestUtils.createCustomHeaderInterceptorJar;
import static io.camunda.zeebe.exporter.opensearch.utils.PluginTestUtils.createPluginFromJar;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration.InterceptorPlugin;
import io.camunda.zeebe.exporter.opensearch.utils.NoopHTTPCallback;
import io.camunda.zeebe.exporter.opensearch.utils.TestDynamicCustomHeaderInterceptor;
import io.camunda.zeebe.exporter.opensearch.utils.TestStaticCustomHeaderInterceptor;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.nio.client.HttpAsyncClient;
import org.apache.http.protocol.BasicHttpContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.RestClient;

public class OpensearchExporterPluginTest {
  private static final String HTTP_CONTEXT_REQUEST_ATTRIBUTE_ID = "http.request";

  private OpensearchExporterConfiguration configuration;

  @BeforeEach
  void beforeEach() {
    configuration = new OpensearchExporterConfiguration();
    configuration.url = "localhost:9200";
  }

  @Test
  public void shouldAddStaticCustomHeader() throws Exception {
    // given
    final InterceptorPlugin plugin =
        createPluginFromJar(
            createCustomHeaderInterceptorJar(TestStaticCustomHeaderInterceptor.class),
            TestStaticCustomHeaderInterceptor.class);
    configuration.interceptorPlugins.put("my-id", plugin);
    final RestClient client = RestClientFactory.of(configuration);
    final var context = new BasicHttpContext();

    // when
    getHttpClient(client)
        .execute(
            HttpHost.create("localhost:45678"), new HttpGet(), context, NoopHTTPCallback.INSTANCE);

    // then
    assertThat(
            ((HttpRequestWrapper) context.getAttribute(HTTP_CONTEXT_REQUEST_ATTRIBUTE_ID))
                .getFirstHeader(TestStaticCustomHeaderInterceptor.X_CUSTOM_HEADER)
                .getValue())
        .isEqualTo(TestStaticCustomHeaderInterceptor.X_CUSTOM_HEADER_VALUE);
  }

  @Test
  public void dynamicCustomHeadersShouldChange() throws Exception {
    // given
    final InterceptorPlugin plugin =
        createPluginFromJar(
            createCustomHeaderInterceptorJar(TestDynamicCustomHeaderInterceptor.class),
            TestDynamicCustomHeaderInterceptor.class);
    configuration.interceptorPlugins.put("my-id", plugin);
    final RestClient client = RestClientFactory.of(configuration);
    final var contextRequest1 = new BasicHttpContext();
    final var contextRequest2 = new BasicHttpContext();

    // when
    getHttpClient(client)
        .execute(
            HttpHost.create("localhost:45678"),
            new HttpGet(),
            contextRequest1,
            NoopHTTPCallback.INSTANCE);
    getHttpClient(client)
        .execute(
            HttpHost.create("localhost:45678"),
            new HttpGet(),
            contextRequest2,
            NoopHTTPCallback.INSTANCE);

    // then
    final HttpRequestWrapper requestWrapper1 =
        (HttpRequestWrapper) contextRequest1.getAttribute(HTTP_CONTEXT_REQUEST_ATTRIBUTE_ID);
    final HttpRequestWrapper requestWrapper2 =
        (HttpRequestWrapper) contextRequest2.getAttribute(HTTP_CONTEXT_REQUEST_ATTRIBUTE_ID);
    assertThat(requestWrapper1.getFirstHeader(TestDynamicCustomHeaderInterceptor.X_CUSTOM_HEADER))
        .isNotEqualTo(
            requestWrapper2.getFirstHeader(TestDynamicCustomHeaderInterceptor.X_CUSTOM_HEADER));
  }

  @Test
  public void shouldThrowRuntimeExceptionWhenJarIsNull() {
    // given
    final InterceptorPlugin plugin =
        createPluginFromJar(
            createCustomHeaderInterceptorJar(TestStaticCustomHeaderInterceptor.class),
            TestStaticCustomHeaderInterceptor.class);
    plugin.setJarPath(null);
    configuration.interceptorPlugins.put("my-id", plugin);

    // when & then
    Assertions.assertThrows(RuntimeException.class, () -> RestClientFactory.of(configuration));
  }

  private static HttpAsyncClient getHttpClient(final RestClient client) throws Exception {
    final var field = client.getClass().getDeclaredField("client");
    field.setAccessible(true);
    return (HttpAsyncClient) field.get(client);
  }
}
