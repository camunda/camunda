/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import static io.camunda.zeebe.exporter.utils.PluginTestUtils.createCustomHeaderInterceptorJar;
import static io.camunda.zeebe.exporter.utils.PluginTestUtils.createPluginFromJar;
import static io.camunda.zeebe.exporter.utils.TestStaticCustomHeaderInterceptor.X_CUSTOM_HEADER;
import static io.camunda.zeebe.exporter.utils.TestStaticCustomHeaderInterceptor.X_CUSTOM_HEADER_VALUE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration.InterceptorPlugin;
import io.camunda.zeebe.exporter.utils.NoopHTTPCallback;
import io.camunda.zeebe.exporter.utils.TestDynamicCustomHeaderInterceptor;
import io.camunda.zeebe.exporter.utils.TestStaticCustomHeaderInterceptor;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.protocol.BasicHttpContext;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ElasticsearchExporterPluginTest {

  private static final String HTTP_CONTEXT_REQUEST_ATTRIBUTE_ID = "http.request";

  private ElasticsearchExporterConfiguration configuration;

  @BeforeEach
  void beforeEach() {
    configuration = new ElasticsearchExporterConfiguration();
    configuration.url = "localhost:9200";
  }

  @Test
  public void shouldAddStaticCustomHeader() {
    // given
    final InterceptorPlugin plugin =
        createPluginFromJar(
            createCustomHeaderInterceptorJar(TestStaticCustomHeaderInterceptor.class),
            TestStaticCustomHeaderInterceptor.class);
    configuration.interceptorPlugins.put("my-id", plugin);
    final RestClient client = RestClientFactory.of(configuration);
    final var context = new BasicHttpContext();

    // when
    client
        .getHttpClient()
        .execute(
            HttpHost.create("localhost:45678"), new HttpGet(), context, NoopHTTPCallback.INSTANCE);

    // then
    assertThat(
            ((HttpRequestWrapper) context.getAttribute(HTTP_CONTEXT_REQUEST_ATTRIBUTE_ID))
                .getFirstHeader(X_CUSTOM_HEADER)
                .getValue())
        .isEqualTo(X_CUSTOM_HEADER_VALUE);
  }

  @Test
  public void dynamicCustomHeadersShouldChange() {
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
    client
        .getHttpClient()
        .execute(
            HttpHost.create("localhost:45678"),
            new HttpGet(),
            contextRequest1,
            NoopHTTPCallback.INSTANCE);
    client
        .getHttpClient()
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
}
