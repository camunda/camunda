/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.clients.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.camunda.exporter.config.ElasticsearchProperties;
import io.camunda.exporter.exceptions.ElasticsearchExporterException;
import io.camunda.exporter.utils.RetryOperation;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ElasticsearchClientFactory {
  public static final ElasticsearchClientFactory INSTANCE = new ElasticsearchClientFactory();
  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchClientFactory.class);

  private ElasticsearchClientFactory() {}

  public ElasticsearchClient create(final ElasticsearchProperties elsConfig) {
    LOGGER.debug("Creating ElasticsearchClient ...");
    final RestClientBuilder restClientBuilder = RestClient.builder(getHttpHost(elsConfig));
    if (elsConfig.getConnectTimeout() != null || elsConfig.getSocketTimeout() != null) {
      restClientBuilder.setRequestConfigCallback(
          configCallback -> setTimeouts(configCallback, elsConfig));
    }
    final RestClient restClient =
        restClientBuilder
            .setHttpClientConfigCallback(
                httpClientBuilder -> configureHttpClient(httpClientBuilder, elsConfig))
            .build();

    // Create the transport with a Jackson mapper
    final ElasticsearchTransport transport =
        new RestClientTransport(restClient, new JacksonJsonpMapper());

    // And create the API client
    final ElasticsearchClient elasticsearchClient = new ElasticsearchClient(transport);
    if (!checkHealth(elasticsearchClient, elsConfig)) {
      LOGGER.warn("Elasticsearch cluster is not accessible");
    } else {
      LOGGER.debug("Elasticsearch connection was successfully created.");
    }
    return elasticsearchClient;
  }

  public boolean checkHealth(
      final ElasticsearchClient elasticsearchClient, final ElasticsearchProperties elsConfig) {
    try {
      return RetryOperation.<Boolean>newBuilder()
          .noOfRetry(50)
          .retryOn(
              IOException.class,
              co.elastic.clients.elasticsearch._types.ElasticsearchException.class)
          .delayInterval(3, TimeUnit.SECONDS)
          .message(
              String.format(
                  "Connect to Elasticsearch cluster [%s] at %s",
                  elsConfig.getClusterName(), elsConfig.getUrl()))
          .retryConsumer(
              () -> {
                final HealthResponse healthResponse = elasticsearchClient.cluster().health();
                LOGGER.info("Elasticsearch cluster health: {}", healthResponse.status());
                return healthResponse.clusterName().equals(elsConfig.getClusterName());
              })
          .build()
          .retry();
    } catch (final Exception e) {
      throw new ElasticsearchExporterException("Couldn't connect to Elasticsearch. Abort.", e);
    }
  }

  private HttpAsyncClientBuilder configureHttpClient(
      final HttpAsyncClientBuilder httpAsyncClientBuilder,
      final ElasticsearchProperties elsConfig) {
    setupAuthentication(httpAsyncClientBuilder, elsConfig);
    // TODO setup SSLContext if configured
    return httpAsyncClientBuilder;
  }

  private void setupAuthentication(
      final HttpAsyncClientBuilder builder, final ElasticsearchProperties elsConfig) {
    final String username = elsConfig.getUsername();
    final String password = elsConfig.getPassword();

    if (username == null || password == null || username.isEmpty() || password.isEmpty()) {
      LOGGER.warn(
          "Username and/or password for are empty. Basic authentication for elasticsearch is not used.");
      return;
    }
    final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(
        AuthScope.ANY, new UsernamePasswordCredentials(username, password));
    builder.setDefaultCredentialsProvider(credentialsProvider);
  }

  private HttpHost getHttpHost(final ElasticsearchProperties elsConfig) {
    try {
      final URI uri = new URI(elsConfig.getUrl());
      return new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
    } catch (final URISyntaxException e) {
      throw new ElasticsearchExporterException("Error in url: " + elsConfig.getUrl(), e);
    }
  }

  private RequestConfig.Builder setTimeouts(
      final RequestConfig.Builder builder, final ElasticsearchProperties elsConfig) {
    if (elsConfig.getSocketTimeout() != null) {
      builder.setSocketTimeout(elsConfig.getSocketTimeout());
    }
    if (elsConfig.getConnectTimeout() != null) {
      builder.setConnectTimeout(elsConfig.getConnectTimeout());
    }
    return builder;
  }
}
