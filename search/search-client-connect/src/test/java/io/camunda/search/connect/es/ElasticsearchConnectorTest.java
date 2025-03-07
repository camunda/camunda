/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.es;

import static io.camunda.search.connect.plugin.util.TestDatabaseCustomHeaderSupplierImpl.KEY_CUSTOM_HEADER;
import static io.camunda.search.connect.plugin.util.TestDatabaseCustomHeaderSupplierImpl.VALUE_CUSTOM_HEADER;
import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.db.search.engine.config.ConnectConfiguration;
import io.camunda.db.search.engine.config.PluginConfiguration;
import io.camunda.search.connect.plugin.PluginRepository;
import io.camunda.search.connect.plugin.util.TestDatabaseCustomHeaderSupplierImpl;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import net.bytebuddy.ByteBuddy;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.protocol.BasicHttpContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ElasticsearchConnectorTest {

  @Test
  void shouldApplyRequestInterceptorsWithinClasspathForNativeRestClient() {
    final var context = new BasicHttpContext();
    final var configuration = new ConnectConfiguration();
    configuration.setInterceptorPlugins(
        List.of(
            new PluginConfiguration(
                "my-plg", TestDatabaseCustomHeaderSupplierImpl.class.getName(), null)));
    final PluginRepository pluginRepository = new PluginRepository();
    final var connector =
        Mockito.spy(
            new ElasticsearchConnector(configuration, new ObjectMapper(), pluginRepository));
    final var client = connector.createClient();

    // when
    ((RestClientTransport) client._transport())
        .restClient()
        .getHttpClient()
        .execute(HttpHost.create("localhost:9200"), new HttpGet(), context, NoopCallback.INSTANCE);

    // then
    final HttpRequestWrapper reqWrapper = (HttpRequestWrapper) context.getAttribute("http.request");

    assertThat(reqWrapper.getFirstHeader("foo").getValue()).isEqualTo("bar");
  }

  @Test
  void shouldApplyExternalRequestInterceptorsForNativeRestClient() throws IOException {
    final var context = new BasicHttpContext();
    final var jar =
        new ByteBuddy()
            .subclass(TestDatabaseCustomHeaderSupplierImpl.class)
            .name("com.acme.Foo")
            .make()
            .toJar(Files.createTempDirectory("plugin").resolve("plugin.jar").toFile())
            .toPath();
    final var plugin = new PluginConfiguration("test", "com.acme.Foo", jar);
    final var configuration = new ConnectConfiguration();
    configuration.setInterceptorPlugins(List.of(plugin));
    final PluginRepository pluginRepository = new PluginRepository();
    final var connector =
        Mockito.spy(
            new ElasticsearchConnector(configuration, new ObjectMapper(), pluginRepository));
    final var client = connector.createClient();

    // when
    ((RestClientTransport) client._transport())
        .restClient()
        .getHttpClient()
        .execute(HttpHost.create("localhost:9200"), new HttpGet(), context, NoopCallback.INSTANCE);

    // then
    final HttpRequestWrapper reqWrapper = (HttpRequestWrapper) context.getAttribute("http.request");

    assertThat(reqWrapper.getFirstHeader(KEY_CUSTOM_HEADER).getValue())
        .isEqualTo(VALUE_CUSTOM_HEADER);
  }

  private static final class NoopCallback implements FutureCallback<HttpResponse> {

    private static final NoopCallback INSTANCE = new NoopCallback();

    @Override
    public void completed(final HttpResponse result) {}

    @Override
    public void failed(final Exception ex) {}

    @Override
    public void cancelled() {}
  }
}
