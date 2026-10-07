/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Node;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import io.camunda.search.connect.util.SecurityUtil;
import java.io.IOException;
import org.apache.hc.client5.http.auth.BasicUserPrincipal;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.impl.BasicEntityDetails;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@Execution(ExecutionMode.CONCURRENT)
final class ElasticsearchClientFactoryTest {
  private final ElasticsearchExporterConfiguration config =
      new ElasticsearchExporterConfiguration();

  private static Rest5Client extractRestClient(final ElasticsearchClient client) {
    return ((Rest5ClientTransport) client._transport()).restClient();
  }

  @Test
  void shouldConfigureMultipleHosts() throws java.net.URISyntaxException {
    // given
    config.url = "http://localhost:9201,https://localhost:9202";

    // when
    final var esClient = ElasticsearchClientFactory.of(config);
    final var restClient = extractRestClient(esClient);

    // then
    assertThat(restClient.getNodes())
        .hasSize(2)
        .map(Node::getHost)
        .containsExactly(
            HttpHost.create("http://localhost:9201"), HttpHost.create("https://localhost:9202"));
  }

  @Test
  void shouldConfigureBasicAuth() {
    // given
    config.getAuthentication().setUsername("user");
    config.getAuthentication().setPassword("password");
    final var builder = Mockito.mock(HttpAsyncClientBuilder.class);

    // when
    ElasticsearchClientFactory.INSTANCE.configureHttpClient(config, builder);

    // then
    final var providerCaptor = ArgumentCaptor.forClass(CredentialsProvider.class);
    Mockito.verify(builder).setDefaultCredentialsProvider(providerCaptor.capture());
    final Credentials credentials =
        providerCaptor.getValue().getCredentials(SecurityUtil.ANY_AUTH_SCOPE, null);
    assertThat(credentials.getUserPrincipal()).isEqualTo(new BasicUserPrincipal("user"));
    assertThat(((UsernamePasswordCredentials) credentials).getUserPassword())
        .isEqualTo("password".toCharArray());
  }

  @Test
  void shouldNotConfigureAuthenticationByDefault() {
    // given
    final var builder = Mockito.mock(HttpAsyncClientBuilder.class);

    // when
    ElasticsearchClientFactory.INSTANCE.configureHttpClient(config, builder);

    // then
    Mockito.verify(builder, Mockito.never())
        .setDefaultCredentialsProvider(Mockito.any(CredentialsProvider.class));
  }

  @Test
  void shouldApplyRequestInterceptorsInOrder()
      throws IOException, org.apache.hc.core5.http.HttpException {
    // given
    final var context = HttpCoreContext.create();
    final var builder = Mockito.mock(HttpAsyncClientBuilder.class);

    // when
    ElasticsearchClientFactory.INSTANCE.configureHttpClient(
        config,
        builder,
        (req, entity, ctx) -> ctx.setAttribute("foo", "bar"),
        (req, entity, ctx) -> ctx.setAttribute("foo", "baz"));

    final var interceptorCaptor =
        ArgumentCaptor.forClass(org.apache.hc.core5.http.HttpRequestInterceptor.class);
    Mockito.verify(builder, Mockito.times(2))
        .addRequestInterceptorLast(interceptorCaptor.capture());
    for (final var interceptor : interceptorCaptor.getAllValues()) {
      interceptor.process(
          new BasicHttpRequest("GET", "localhost"), new BasicEntityDetails(0, null), context);
    }

    // then
    assertThat(context.getAttribute("foo")).isEqualTo("baz");
  }

  @Test
  void shouldEnableTcpKeepAlive() {
    // given
    final var builder = mock(HttpAsyncClientBuilder.class);

    // when
    ElasticsearchClientFactory.INSTANCE.configureHttpClient(config, builder);

    // then
    final var captor = ArgumentCaptor.forClass(IOReactorConfig.class);
    verify(builder).setIOReactorConfig(captor.capture());
    assertThat(captor.getValue().isSoKeepAlive()).isTrue();
  }
}
