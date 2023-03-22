/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.opensearch;

import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration.AwsConfiguration;
import io.camunda.zeebe.exporter.opensearch.aws.AwsSignHttpRequestInterceptor;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.ssl.SSLContextBuilder;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;

final class RestClientFactory {
  private static final RestClientFactory INSTANCE = new RestClientFactory();
  private final Logger log = LoggerFactory.getLogger(getClass().getPackageName());

  private RestClientFactory() {}

  /**
   * Returns a {@link RestClient} instance based on the given configuration. The URL is parsed as a
   * comma separated list of "host:port" formatted strings. Authentication is supported only as
   * basic auth; if there is no authentication present, then nothing is configured for it.
   */
  static RestClient of(final OpensearchExporterConfiguration config) {
    return of(config, false);
  }

  /**
   * Returns a {@link RestClient} instance based on the given configuration. The URL is parsed as a
   * comma separated list of "host:port" formatted strings. Authentication is supported only as
   * basic auth; if there is no authentication present, then nothing is configured for it.
   *
   * @param config the exporter configuration
   * @param allowAllSelfSignedCertificates if set to true, ALL self-signed certificates will be
   *     accepted. This is meant for testing purposes only!
   * @return the created {@link RestClient}
   */
  static RestClient of(
      final OpensearchExporterConfiguration config, final boolean allowAllSelfSignedCertificates) {
    return INSTANCE.createRestClient(config, allowAllSelfSignedCertificates);
  }

  private RestClient createRestClient(
      final OpensearchExporterConfiguration config, final boolean allowAllSelfSignedCertificates) {
    final HttpHost[] httpHosts = parseUrl(config);
    final RestClientBuilder builder =
        RestClient.builder(httpHosts)
            .setRequestConfigCallback(
                b ->
                    b.setConnectTimeout(config.requestTimeoutMs)
                        .setSocketTimeout(config.requestTimeoutMs))
            .setHttpClientConfigCallback(
                b -> configureHttpClient(config, b, allowAllSelfSignedCertificates));

    return builder.build();
  }

  private HttpAsyncClientBuilder configureHttpClient(
      final OpensearchExporterConfiguration config,
      final HttpAsyncClientBuilder builder,
      final boolean allowAllSelfSignedCertificates) {
    // use single thread for rest client
    builder.setDefaultIOReactorConfig(IOReactorConfig.custom().setIoThreadCount(1).build());

    if (config.hasAuthenticationPresent()) {
      setupBasicAuthentication(config, builder);
      log.info("Basic authentication is enabled.");
    } else {
      log.info("Basic authentication is disabled.");
    }

    if (config.aws.enabled) {
      configureAws(builder, config.aws);
      log.info("AWS Signing is enabled.");
    } else {
      log.info("AWS Signing is disabled.");
    }

    if (allowAllSelfSignedCertificates) {
      // This code makes it so ALL self-signed certificates are accepted. This is meant for testing
      // purposes only.
      try {
        final var sslContext =
            SSLContextBuilder.create().loadTrustMaterial(null, new TrustAllStrategy()).build();
        builder.setSSLContext(sslContext);
      } catch (final NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
        throw new RuntimeException(e);
      }
    }

    return builder;
  }

  private void setupBasicAuthentication(
      final OpensearchExporterConfiguration config, final HttpAsyncClientBuilder builder) {
    final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(
        AuthScope.ANY,
        new UsernamePasswordCredentials(
            config.getAuthentication().getUsername(), config.getAuthentication().getPassword()));

    builder.setDefaultCredentialsProvider(credentialsProvider);
  }

  /**
   * Adds an interceptor to sign requests to AWS. The signing credentials can be provided in
   * multiple ways. See {@link DefaultCredentialsProvider} for more details.
   */
  public void configureAws(
      final HttpAsyncClientBuilder builder, final AwsConfiguration awsConfiguration) {
    final AwsCredentialsProvider credentialsProvider = DefaultCredentialsProvider.create();
    credentialsProvider.resolveCredentials();
    final Aws4Signer signer = Aws4Signer.create();

    final HttpRequestInterceptor signInterceptor =
        new AwsSignHttpRequestInterceptor(
            awsConfiguration.serviceName, signer, credentialsProvider, awsConfiguration.region);
    builder.addInterceptorLast(signInterceptor);
  }

  private HttpHost[] parseUrl(final OpensearchExporterConfiguration config) {
    final var urls = config.url.split(",");
    final var hosts = new HttpHost[urls.length];

    for (int i = 0; i < urls.length; i++) {
      hosts[i] = HttpHost.create(urls[i]);
    }

    return hosts;
  }
}
