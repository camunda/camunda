/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.os;

import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.SecurityConfiguration;
import io.camunda.search.connect.util.SecurityUtil;
import io.github.acm19.aws.interceptor.http.AwsRequestSigningApacheInterceptor;
import java.util.Optional;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig.Builder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.opensearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;

public class OpensearchRestClientConnector {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchRestClientConnector.class);
  private static final String AWS_OPENSEARCH_SERVICE_NAME = "es";

  private final ConnectConfiguration configuration;

  public OpensearchRestClientConnector(final ConnectConfiguration configuration) {
    this.configuration = configuration;
  }

  public RestClient createRestClient() {
    final var httpHost = getHttpHost();
    return RestClient.builder(httpHost)
        .setRequestConfigCallback(this::configureTimeouts)
        .setHttpClientConfigCallback(this::configureHttpClient)
        .build();
  }

  private HttpHost getHttpHost() {
    final var uri = configuration.getUrlAsUri();
    return new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
  }

  private Builder configureTimeouts(final Builder builder) {
    if (configuration.getSocketTimeout() != null) {
      builder.setSocketTimeout(configuration.getSocketTimeout());
    }
    if (configuration.getConnectTimeout() != null) {
      builder.setConnectTimeout(configuration.getConnectTimeout());
    }
    return builder;
  }

  private HttpAsyncClientBuilder configureHttpClient(final HttpAsyncClientBuilder builder) {
    configureAuthentication(builder);
    configureSSLContext(builder, configuration.getSecurity());
    return builder;
  }

  private void configureAuthentication(final HttpAsyncClientBuilder builder) {
    final var interceptorAdded =
        createAwsSigningInterceptorIfPresent().map(builder::addInterceptorLast);

    if (interceptorAdded.isEmpty()) {
      createDefaultCredentialsProviderIfPresent().map(builder::setDefaultCredentialsProvider);
    }
  }

  private Optional<HttpRequestInterceptor> createAwsSigningInterceptorIfPresent() {
    return createAwsCredentialsProvider().map(this::createAwsSigningInterceptor);
  }

  private Optional<AwsCredentialsProvider> createAwsCredentialsProvider() {
    try {
      final var credentialsProvider = DefaultCredentialsProvider.create();
      credentialsProvider.resolveCredentials();
      LOGGER.info("AWS Credentials can be resolved. Use AWS Opensearch");
      return Optional.of(credentialsProvider);
    } catch (Exception e) {
      LOGGER.warn("AWS not configured due to: {} ", e.getMessage());
      return Optional.empty();
    }
  }

  private HttpRequestInterceptor createAwsSigningInterceptor(
      final AwsCredentialsProvider provider) {
    final var signer = Aws4Signer.create();
    return new AwsRequestSigningApacheInterceptor(
        AWS_OPENSEARCH_SERVICE_NAME,
        signer,
        provider,
        new DefaultAwsRegionProviderChain().getRegion());
  }

  private Optional<CredentialsProvider> createDefaultCredentialsProviderIfPresent() {
    if (configuration.hasBasicAuthenticationConfigured()) {
      final var credentialsProvider = new BasicCredentialsProvider();
      credentialsProvider.setCredentials(
          new AuthScope(getHttpHost()),
          new UsernamePasswordCredentials(
              configuration.getUsername(), configuration.getPassword()));
      return Optional.of(credentialsProvider);
    }
    return Optional.empty();
  }

  private void configureSSLContext(
      final HttpAsyncClientBuilder httpAsyncClientBuilder,
      final SecurityConfiguration configuration) {
    if (configuration != null && configuration.isEnabled()) {
      try {
        final var sslContext = SecurityUtil.getSSLContext(configuration, "opensearch-host");
        httpAsyncClientBuilder.setSSLContext(sslContext);
        if (!configuration.isVerifyHostname()) {
          httpAsyncClientBuilder.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
        }
      } catch (Exception e) {
        LOGGER.error("Error in setting up SSLContext", e);
      }
    }
  }
}
