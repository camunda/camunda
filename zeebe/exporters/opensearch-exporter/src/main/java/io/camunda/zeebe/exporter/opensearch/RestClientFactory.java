/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration.AwsConfiguration;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration.ProxyConfiguration;
import io.github.acm19.aws.interceptor.http.AwsRequestSigningApacheInterceptor;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
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
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;

final class RestClientFactory {
  private static final RestClientFactory INSTANCE = new RestClientFactory();
  private final Logger log = LoggerFactory.getLogger(getClass().getPackageName());

  private RestClientFactory() {}

  /**
   * Returns a {@link RestClient} instance based on the given configuration. The URL is parsed as a
   * comma separated list of "host:port" formatted strings. Authentication is supported only as
   * basic auth; if there is no authentication present, then nothing is configured for it.
   */
  static RestClient of(
      final OpensearchExporterConfiguration config, final HttpRequestInterceptor... interceptors) {
    return of(config, false, interceptors);
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
      final OpensearchExporterConfiguration config,
      final boolean allowAllSelfSignedCertificates,
      final HttpRequestInterceptor... interceptors) {
    return INSTANCE.createRestClient(config, allowAllSelfSignedCertificates, interceptors);
  }

  private RestClient createRestClient(
      final OpensearchExporterConfiguration config,
      final boolean allowAllSelfSignedCertificates,
      final HttpRequestInterceptor... interceptors) {
    final HttpHost[] httpHosts = parseUrl(config);
    final RestClientBuilder builder =
        RestClient.builder(httpHosts)
            .setRequestConfigCallback(
                b ->
                    b.setConnectTimeout(config.requestTimeoutMs)
                        .setSocketTimeout(config.requestTimeoutMs))
            .setHttpClientConfigCallback(
                b -> configureHttpClient(config, b, allowAllSelfSignedCertificates, interceptors));

    return builder.build();
  }

  private HttpAsyncClientBuilder configureHttpClient(
      final OpensearchExporterConfiguration config,
      final HttpAsyncClientBuilder builder,
      final boolean allowAllSelfSignedCertificates,
      final HttpRequestInterceptor... interceptors) {
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

    if (config.hasProxyConfigured()) {
      setupProxy(builder, config.getProxy());
      addPreemptiveProxyAuthInterceptor(builder, config.getProxy());
      log.info("Proxy is enabled.");
    } else {
      log.info("Proxy is disabled.");
    }

    log.trace("Attempt to load interceptor plugins");
    for (final var interceptor : interceptors) {
      builder.addInterceptorLast(interceptor);
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

  private void setupProxy(
      final HttpAsyncClientBuilder builder, final ProxyConfiguration proxyConfig) {
    final String host = proxyConfig.getHost();
    final Integer port = proxyConfig.getPort();

    if (host == null || host.trim().isEmpty()) {
      throw new IllegalArgumentException(
          "Opensearch exporter proxy is enabled but no proxy host is configured");
    }

    if (port == null) {
      throw new IllegalArgumentException(
          "Opensearch exporter proxy is enabled but no proxy port is configured");
    }

    if (port <= 0 || port > 65_535) {
      throw new IllegalArgumentException(
          "Opensearch exporter proxy port must be between 1 and 65535, but was: " + port);
    }

    builder.setProxy(new HttpHost(host, port, proxyConfig.isSslEnabled() ? "https" : "http"));
  }

  private void addPreemptiveProxyAuthInterceptor(
      final HttpAsyncClientBuilder builder, final ProxyConfiguration proxyConfig) {
    final String username = proxyConfig.getUsername();
    final String password = proxyConfig.getPassword();

    if (username == null || password == null || username.isEmpty() || password.isEmpty()) {
      return;
    }

    final String credentials = username + ":" + password;
    final String encodedCredentials =
        Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    final String proxyAuthHeaderValue = "Basic " + encodedCredentials;

    builder.addInterceptorFirst(
        (HttpRequestInterceptor)
            (request, context) -> {
              if (!request.containsHeader("Proxy-Authorization")) {
                request.addHeader("Proxy-Authorization", proxyAuthHeaderValue);
              }
            });
  }

  /**
   * Adds an interceptor to sign requests to AWS. The signing credentials can be provided in
   * multiple ways. See {@link DefaultCredentialsProvider} for more details.
   */
  public void configureAws(
      final HttpAsyncClientBuilder builder, final AwsConfiguration awsConfiguration) {
    final AwsCredentialsProvider credentialsProvider = DefaultCredentialsProvider.builder().build();
    credentialsProvider.resolveCredentials();
    final AwsV4HttpSigner signer = AwsV4HttpSigner.create();

    final HttpRequestInterceptor signInterceptor =
        new AwsRequestSigningApacheInterceptor(
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
