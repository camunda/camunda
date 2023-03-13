/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.BasicUserPrincipal;
import org.apache.http.auth.Credentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.protocol.BasicHttpContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.opensearch.client.Node;

@Execution(ExecutionMode.CONCURRENT)
final class RestClientFactoryTest {
  private final ElasticsearchExporterConfiguration config =
      new ElasticsearchExporterConfiguration();

  @Test
  void shouldConfigureMultipleHosts() {
    // given
    config.url = "http://localhost:9201,https://localhost:9202";

    // when
    final var client = RestClientFactory.of(config);

    // then
    assertThat(client.getNodes())
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
    final var context = new BasicHttpContext();

    // when
    final var client = RestClientFactory.of(config);
    //    client
    //        .getHttpClient()
    //        .execute(HttpHost.create("localhost:9200"), new HttpGet(), context,
    // NoopCallback.INSTANCE);

    // then
    final var credentialsProvider =
        (CredentialsProvider) context.getAttribute(HttpClientContext.CREDS_PROVIDER);
    assertThat(credentialsProvider)
        .extracting(c -> c.getCredentials(AuthScope.ANY))
        .extracting(Credentials::getUserPrincipal, Credentials::getPassword)
        .containsExactly(new BasicUserPrincipal("user"), "password");
  }

  @Test
  void shouldNotConfigureAuthenticationByDefault() {
    // given
    final var context = new BasicHttpContext();

    // when
    final var client = RestClientFactory.of(config);
    //    client
    //        .getHttpClient()
    //        .execute(HttpHost.create("localhost:9200"), new HttpGet(), context,
    // NoopCallback.INSTANCE);

    // then
    final var credentialsProvider =
        (CredentialsProvider) context.getAttribute(HttpClientContext.CREDS_PROVIDER);
    assertThat(credentialsProvider.getCredentials(AuthScope.ANY)).isNull();
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
