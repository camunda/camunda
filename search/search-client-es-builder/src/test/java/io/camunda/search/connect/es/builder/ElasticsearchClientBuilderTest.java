/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.es.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import co.elastic.clients.elasticsearch.cluster.ElasticsearchClusterClient;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ElasticsearchClientBuilderTest {

  @Test
  void shouldThrowWhenNoUrlConfigured() {
    final var builder = ElasticsearchClientBuilder.newInstance();
    assertThatThrownBy(builder::buildRestClient)
        .isInstanceOf(ElasticsearchClientBuilderException.class)
        .hasMessageContaining("At least one Elasticsearch URL must be configured");
  }

  @Test
  void shouldBuildRestClientWithSingleUrl() throws IOException {
    final var restClient =
        ElasticsearchClientBuilder.newInstance().withUrl("http://localhost:9200").buildRestClient();
    assertThat(restClient).isNotNull();
    restClient.close();
  }

  @Test
  void shouldBuildRestClientWithMultipleUrls() throws IOException {
    final var restClient =
        ElasticsearchClientBuilder.newInstance()
            .withUrls(List.of("http://localhost:9200", "http://localhost:9201"))
            .buildRestClient();
    assertThat(restClient).isNotNull();
    restClient.close();
  }

  @Test
  void shouldThrowOnInvalidUrl() {
    final var builder = ElasticsearchClientBuilder.newInstance().withUrl("not a valid url");
    assertThatThrownBy(builder::buildRestClient)
        .isInstanceOf(ElasticsearchClientBuilderException.class)
        .hasMessageContaining("Invalid Elasticsearch URL");
  }

  @Test
  void shouldThrowWhenBothUrlAndUrlsAreCalled() {
    assertThatThrownBy(
            () ->
                ElasticsearchClientBuilder.newInstance()
                    .withUrl("http://localhost:9200")
                    .withUrls(List.of("http://localhost:9201")))
        .isInstanceOf(ElasticsearchClientBuilderException.class)
        .hasMessageContaining("Cannot call withUrls() after withUrl()");
  }

  @Test
  void shouldBuildClientWithBasicAuth() throws IOException {
    final var restClient =
        ElasticsearchClientBuilder.newInstance()
            .withUrl("http://localhost:9200")
            .withBasicAuth("elastic", "changeme")
            .buildRestClient();
    assertThat(restClient).isNotNull();
    restClient.close();
  }

  @Test
  void shouldBuildClientWithTimeouts() throws IOException {
    final var restClient =
        ElasticsearchClientBuilder.newInstance()
            .withUrl("http://localhost:9200")
            .withConnectTimeout(5000)
            .withSocketTimeout(30000)
            .buildRestClient();
    assertThat(restClient).isNotNull();
    restClient.close();
  }

  @Test
  void shouldBuildClientWithInfiniteSocketTimeout() throws IOException {
    // Optimize uses socketTimeout=0 for infinite
    final var restClient =
        ElasticsearchClientBuilder.newInstance()
            .withUrl("http://localhost:9200")
            .withSocketTimeout(0)
            .buildRestClient();
    assertThat(restClient).isNotNull();
    restClient.close();
  }

  @Test
  void shouldBuildClientWithIoThreadCount() throws IOException {
    // Zeebe exporter uses ioThreadCount=1
    final var restClient =
        ElasticsearchClientBuilder.newInstance()
            .withUrl("http://localhost:9200")
            .withIoThreadCount(1)
            .buildRestClient();
    assertThat(restClient).isNotNull();
    restClient.close();
  }

  @Test
  void shouldBuildClientWithPathPrefix() throws IOException {
    final var restClient =
        ElasticsearchClientBuilder.newInstance()
            .withUrl("http://localhost:9200")
            .withPathPrefix("/elasticsearch")
            .buildRestClient();
    assertThat(restClient).isNotNull();
    restClient.close();
  }

  @Test
  void shouldAlwaysUseCompatibilityVersion9() {
    assertThat(ElasticsearchClientBuilder.COMPATIBILITY_VERSION).isEqualTo(9);
  }

  @Test
  void shouldBuildClientWithCompatibilityHeaders() throws IOException {
    // Compatibility headers (compatible-with=9) are always sent
    final var restClient =
        ElasticsearchClientBuilder.newInstance().withUrl("http://localhost:9200").buildRestClient();
    assertThat(restClient).isNotNull();
    restClient.close();
  }

  @Test
  void shouldBuildClientWithSslDisabled() throws IOException {
    final var restClient =
        ElasticsearchClientBuilder.newInstance()
            .withUrl("http://localhost:9200")
            .withSslConfig(SslConfig.disabled())
            .buildRestClient();
    assertThat(restClient).isNotNull();
    restClient.close();
  }

  @Test
  void shouldThrowOnInvalidProxyPort() {
    final var builder =
        ElasticsearchClientBuilder.newInstance()
            .withUrl("http://localhost:9200")
            .withProxyConfig(ProxyConfig.builder().host("proxy.example.com").port(0).build());
    assertThatThrownBy(builder::buildRestClient)
        .isInstanceOf(ElasticsearchClientBuilderException.class)
        .hasMessageContaining("Proxy port must be between 1 and 65535");
  }

  @Test
  void shouldThrowOnEmptyProxyHost() {
    final var builder =
        ElasticsearchClientBuilder.newInstance()
            .withUrl("http://localhost:9200")
            .withProxyConfig(ProxyConfig.builder().host("").port(8080).build());
    assertThatThrownBy(builder::buildRestClient)
        .isInstanceOf(ElasticsearchClientBuilderException.class)
        .hasMessageContaining("no proxy host is specified");
  }

  @Test
  void shouldBuildClientWithProxy() throws IOException {
    final var restClient =
        ElasticsearchClientBuilder.newInstance()
            .withUrl("http://localhost:9200")
            .withProxyConfig(ProxyConfig.builder().host("proxy.example.com").port(8080).build())
            .buildRestClient();
    assertThat(restClient).isNotNull();
    restClient.close();
  }

  @Test
  void shouldBuildClientWithProxyAuth() throws IOException {
    final var restClient =
        ElasticsearchClientBuilder.newInstance()
            .withUrl("http://localhost:9200")
            .withProxyConfig(
                ProxyConfig.builder()
                    .host("proxy.example.com")
                    .port(8080)
                    .username("proxyuser")
                    .password("proxypass")
                    .build())
            .buildRestClient();
    assertThat(restClient).isNotNull();
    restClient.close();
  }

  @Test
  void shouldBuildElasticsearchClient() {
    final var client =
        ElasticsearchClientBuilder.newInstance()
            .withUrl("http://localhost:9200")
            .withBasicAuth("elastic", "changeme")
            .withConnectTimeout(5000)
            .withSocketTimeout(30000)
            .build();
    assertThat(client).isNotNull();
  }

  @Test
  void shouldBuildAsyncClient() {
    final var client =
        ElasticsearchClientBuilder.newInstance().withUrl("http://localhost:9200").buildAsync();
    assertThat(client).isNotNull();
  }

  @Test
  void shouldSupportFluentChaining() throws IOException {
    // Verifies the builder supports full fluent chaining with all options
    final var restClient =
        ElasticsearchClientBuilder.newInstance()
            .withUrl("http://localhost:9200")
            .withBasicAuth("elastic", "changeme")
            .withSslConfig(SslConfig.disabled())
            .withConnectTimeout(5000)
            .withSocketTimeout(30000)
            .withIoThreadCount(2)
            .withPathPrefix("/es")
            .buildRestClient();
    assertThat(restClient).isNotNull();
    restClient.close();
  }

  @Test
  void shouldCreateSslConfigDisabled() {
    final var config = SslConfig.disabled();
    assertThat(config.isEnabled()).isFalse();
    assertThat(config.getCertificatePath()).isNull();
    assertThat(config.getCertificateAuthorities()).isEmpty();
    assertThat(config.isSelfSigned()).isFalse();
    assertThat(config.isVerifyHostname()).isTrue();
  }

  @Test
  void shouldCreateProxyConfigWithoutAuth() {
    final var config = ProxyConfig.builder().host("host").port(8080).build();
    assertThat(config.getHost()).isEqualTo("host");
    assertThat(config.getPort()).isEqualTo(8080);
    assertThat(config.isSslEnabled()).isFalse();
    assertThat(config.hasAuth()).isFalse();
  }

  @Test
  void shouldCreateProxyConfigWithAuth() {
    final var config =
        ProxyConfig.builder()
            .host("host")
            .port(8080)
            .sslEnabled(true)
            .username("user")
            .password("pass")
            .build();
    assertThat(config.hasAuth()).isTrue();
    assertThat(config.isSslEnabled()).isTrue();
  }

  // ── ElasticsearchHealthCheck tests ──

  @Test
  void checkHealthShouldReturnTrueWhenClusterNameMatches() throws IOException {
    final var client = mock(ElasticsearchClient.class);
    final var clusterClient = mock(ElasticsearchClusterClient.class);
    final var healthResponse = mock(HealthResponse.class);

    when(client.cluster()).thenReturn(clusterClient);
    when(clusterClient.health()).thenReturn(healthResponse);
    when(healthResponse.clusterName()).thenReturn("my-cluster");

    assertThat(
            ElasticsearchHealthCheck.builder()
                .client(client)
                .expectedClusterName("my-cluster")
                .maxRetries(1)
                .delaySeconds(0)
                .build()
                .check())
        .isTrue();
  }

  @Test
  void checkHealthShouldReturnFalseWhenClusterNameDoesNotMatch() throws IOException {
    final var client = mock(ElasticsearchClient.class);
    final var clusterClient = mock(ElasticsearchClusterClient.class);
    final var healthResponse = mock(HealthResponse.class);

    when(client.cluster()).thenReturn(clusterClient);
    when(clusterClient.health()).thenReturn(healthResponse);
    when(healthResponse.clusterName()).thenReturn("wrong-cluster");

    assertThat(
            ElasticsearchHealthCheck.builder()
                .client(client)
                .expectedClusterName("my-cluster")
                .maxRetries(1)
                .delaySeconds(0)
                .build()
                .check())
        .isFalse();
  }

  @Test
  void checkHealthShouldRetryOnIOException() throws IOException {
    final var client = mock(ElasticsearchClient.class);
    final var clusterClient = mock(ElasticsearchClusterClient.class);
    final var healthResponse = mock(HealthResponse.class);

    when(client.cluster()).thenReturn(clusterClient);
    when(clusterClient.health())
        .thenThrow(new IOException("Connection refused"))
        .thenReturn(healthResponse);
    when(healthResponse.clusterName()).thenReturn("my-cluster");

    assertThat(
            ElasticsearchHealthCheck.builder()
                .client(client)
                .expectedClusterName("my-cluster")
                .maxRetries(3)
                .delaySeconds(0)
                .build()
                .check())
        .isTrue();
    verify(clusterClient, times(2)).health();
  }

  @Test
  void checkHealthShouldRetryOnElasticsearchException() throws IOException {
    final var client = mock(ElasticsearchClient.class);
    final var clusterClient = mock(ElasticsearchClusterClient.class);
    final var healthResponse = mock(HealthResponse.class);

    final var errorCause = ErrorCause.of(b -> b.type("cluster_block_exception").reason("blocked"));
    final var errorResponse = ErrorResponse.of(b -> b.error(errorCause).status(503));
    final var esException =
        new co.elastic.clients.elasticsearch._types.ElasticsearchException(
            "es-error", errorResponse);

    when(client.cluster()).thenReturn(clusterClient);
    when(clusterClient.health()).thenThrow(esException).thenReturn(healthResponse);
    when(healthResponse.clusterName()).thenReturn("my-cluster");

    assertThat(
            ElasticsearchHealthCheck.builder()
                .client(client)
                .expectedClusterName("my-cluster")
                .maxRetries(3)
                .delaySeconds(0)
                .build()
                .check())
        .isTrue();
    verify(clusterClient, times(2)).health();
  }

  @Test
  void checkHealthShouldThrowAfterAllRetriesExhausted() throws IOException {
    final var client = mock(ElasticsearchClient.class);
    final var clusterClient = mock(ElasticsearchClusterClient.class);

    when(client.cluster()).thenReturn(clusterClient);
    when(clusterClient.health()).thenThrow(new IOException("Connection refused"));

    final var healthCheck =
        ElasticsearchHealthCheck.builder()
            .client(client)
            .expectedClusterName("my-cluster")
            .maxRetries(3)
            .delaySeconds(0)
            .build();

    assertThatThrownBy(healthCheck::check)
        .isInstanceOf(ElasticsearchClientBuilderException.class)
        .hasMessageContaining("Health check failed")
        .hasCauseInstanceOf(IOException.class);
    verify(clusterClient, times(3)).health();
  }

  @Test
  void checkHealthShouldRetryOnClusterNameMismatch() throws IOException {
    final var client = mock(ElasticsearchClient.class);
    final var clusterClient = mock(ElasticsearchClusterClient.class);
    final var wrongResponse = mock(HealthResponse.class);
    final var correctResponse = mock(HealthResponse.class);

    when(client.cluster()).thenReturn(clusterClient);
    when(clusterClient.health()).thenReturn(wrongResponse).thenReturn(correctResponse);
    when(wrongResponse.clusterName()).thenReturn("wrong-cluster");
    when(correctResponse.clusterName()).thenReturn("my-cluster");

    assertThat(
            ElasticsearchHealthCheck.builder()
                .client(client)
                .expectedClusterName("my-cluster")
                .maxRetries(3)
                .delaySeconds(0)
                .build()
                .check())
        .isTrue();
    verify(clusterClient, times(2)).health();
  }

  @Test
  void checkHealthShouldThrowWhenClientIsNull() {
    assertThatThrownBy(
            () -> ElasticsearchHealthCheck.builder().expectedClusterName("my-cluster").build())
        .isInstanceOf(ElasticsearchClientBuilderException.class)
        .hasMessageContaining("requires a client");
  }

  @Test
  void checkHealthShouldThrowWhenExpectedClusterNameIsNull() {
    final var client = mock(ElasticsearchClient.class);
    assertThatThrownBy(() -> ElasticsearchHealthCheck.builder().client(client).build())
        .isInstanceOf(ElasticsearchClientBuilderException.class)
        .hasMessageContaining("requires an expectedClusterName");
  }
}
