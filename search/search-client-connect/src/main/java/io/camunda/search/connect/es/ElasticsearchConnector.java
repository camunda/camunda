/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.connect.SearchClientConnectException;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.SecurityConfiguration;
import io.camunda.search.connect.jackson.JacksonConfiguration;
import io.camunda.search.connect.util.SecurityUtil;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig.Builder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ElasticsearchConnector {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchConnector.class);

  private final ConnectConfiguration configuration;
  private final ObjectMapper objectMapper;

  public ElasticsearchConnector(final ConnectConfiguration configuration) {
    this(configuration, new JacksonConfiguration(configuration).createObjectMapper());
  }

  public ElasticsearchConnector(
      final ConnectConfiguration configuration, final ObjectMapper objectMapper) {
    this.configuration = configuration;
    this.objectMapper = objectMapper;
  }

  public ElasticsearchClient createClient() {
    LOGGER.debug("Creating Elasticsearch Client ...");

    // create rest client
    final var restClient = createRestClient(configuration);

    // Create the transport with a Jackson mapper
    final var transport = new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper));

    // And create the API client
    return new ElasticsearchClient(transport);
  }

  private RestClient createRestClient(final ConnectConfiguration configuration) {
    final var httpHost = getHttpHost(configuration);
    final var restClientBuilder = RestClient.builder(httpHost);

    if (configuration.getConnectTimeout() != null || configuration.getSocketTimeout() != null) {
      restClientBuilder.setRequestConfigCallback(
          configCallback -> setTimeouts(configCallback, configuration));
    }
    final var restClient =
        restClientBuilder
            .setHttpClientConfigCallback(
                httpClientBuilder -> configureHttpClient(httpClientBuilder, configuration))
            .build();

    return restClient;
  }

  protected HttpAsyncClientBuilder configureHttpClient(
      final HttpAsyncClientBuilder httpAsyncClientBuilder,
      final ConnectConfiguration configuration) {
    setupAuthentication(httpAsyncClientBuilder, configuration);
    final var security = configuration.getSecurity();
    if (security != null && security.isEnabled()) {
      setupSSLContext(httpAsyncClientBuilder, security);
    }
    return httpAsyncClientBuilder;
  }

  private void setupSSLContext(
      final HttpAsyncClientBuilder httpAsyncClientBuilder,
      final SecurityConfiguration configuration) {
    try {
      final var sslContext = SecurityUtil.getSSLContext(configuration, "elasticsearch-host");
      httpAsyncClientBuilder.setSSLContext(sslContext);
      if (!configuration.isVerifyHostname()) {
        httpAsyncClientBuilder.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
      }
    } catch (Exception e) {
      LOGGER.error("Error in setting up SSLContext", e);
    }
  }

  private Builder setTimeouts(final Builder builder, final ConnectConfiguration elsConfig) {
    if (elsConfig.getSocketTimeout() != null) {
      builder.setSocketTimeout(elsConfig.getSocketTimeout());
    }
    if (elsConfig.getConnectTimeout() != null) {
      builder.setConnectTimeout(elsConfig.getConnectTimeout());
    }
    return builder;
  }

  private HttpHost getHttpHost(final ConnectConfiguration elsConfig) {
    try {
      final var uri = new URI(elsConfig.getUrl());
      return new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
    } catch (URISyntaxException e) {
      throw new SearchClientConnectException("Error in url: " + elsConfig.getUrl(), e);
    }
  }

  private void setupAuthentication(
      final HttpAsyncClientBuilder builder, final ConnectConfiguration configuration) {
    final var username = configuration.getUsername();
    final var password = configuration.getPassword();

    if (username == null || password == null || username.isEmpty() || password.isEmpty()) {
      LOGGER.warn(
          "Username and/or password for are empty. Basic authentication for elasticsearch is not used.");
      return;
    }

    final var credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(
        AuthScope.ANY, new UsernamePasswordCredentials(username, password));
    builder.setDefaultCredentialsProvider(credentialsProvider);
  }
}
