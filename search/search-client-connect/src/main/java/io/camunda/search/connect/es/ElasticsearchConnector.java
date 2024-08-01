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
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.SecurityConfiguration;
import io.camunda.search.connect.jackson.JacksonConfiguration;
import io.camunda.search.connect.util.SecurityUtil;
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

public class ElasticsearchConnector {

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
    final var restClient = createRestClient();

    // Create the transport with a Jackson mapper
    final var transport = new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper));

    // And create the API client
    return new ElasticsearchClient(transport);
  }

  public RestClient createRestClient() {
    final var httpHost = getHttpHost();
    return RestClient.builder(httpHost)
        .setHttpClientConfigCallback(this::configureHttpClient)
        .setRequestConfigCallback(this::configureTimeouts)
        .build();
  }

  protected HttpAsyncClientBuilder configureHttpClient(
      final HttpAsyncClientBuilder httpAsyncClientBuilder) {
    configureAuthentication(httpAsyncClientBuilder);
    configureSSLContext(httpAsyncClientBuilder, configuration.getSecurity());
    return httpAsyncClientBuilder;
  }

  private void configureSSLContext(
      final HttpAsyncClientBuilder httpAsyncClientBuilder,
      final SecurityConfiguration configuration) {
    if (configuration != null && configuration.isEnabled()) {
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
  }

  private Builder configureTimeouts(final Builder builder) {
    if (configuration.getSocketTimeout() != null) {
      // builder.setSocketTimeout(os.getSocketTimeout());
      builder.setSocketTimeout(configuration.getSocketTimeout());
    }
    if (configuration.getConnectTimeout() != null) {
      builder.setConnectTimeout(configuration.getConnectTimeout());
    }
    return builder;
  }

  private HttpHost getHttpHost() {
    final var uri = configuration.getUrlAsUri();
    return new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
  }

  private void configureAuthentication(final HttpAsyncClientBuilder builder) {
    if (configuration.hasBasicAuthenticationConfigured()) {
      final var credentialsProvider = new BasicCredentialsProvider();
      credentialsProvider.setCredentials(
          AuthScope.ANY,
          new UsernamePasswordCredentials(
              configuration.getUsername(), configuration.getPassword()));
      builder.setDefaultCredentialsProvider(credentialsProvider);
    } else {
      LOGGER.warn(
          "Username and/or password for are empty. Basic authentication for elasticsearch is not used.");
    }
  }
}
