/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter;

import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration.AwsConfiguration;
import io.camunda.zeebe.exporter.aws.AwsSignHttpRequestInterceptor;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
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
  static RestClient of(final ElasticsearchExporterConfiguration config) {
    return INSTANCE.createRestClient(config);
  }

  private RestClient createRestClient(final ElasticsearchExporterConfiguration config) {
    final HttpHost[] httpHosts = parseUrl(config);
    final RestClientBuilder builder =
        RestClient.builder(httpHosts)
            .setRequestConfigCallback(
                b ->
                    b.setConnectTimeout(config.requestTimeoutMs)
                        .setSocketTimeout(config.requestTimeoutMs))
            .setHttpClientConfigCallback(
                b -> {
                  configureHttpClient(config, b);
                  configureAws(b, config.getAwsConfiguration());
                  return b;
                });

    return builder.build();
  }

  private HttpAsyncClientBuilder configureHttpClient(
      final ElasticsearchExporterConfiguration config, final HttpAsyncClientBuilder builder) {
    // use single thread for rest client
    builder.setDefaultIOReactorConfig(IOReactorConfig.custom().setIoThreadCount(1).build());

    if (config.hasAuthenticationPresent()) {
      setupBasicAuthentication(config, builder);
    }

    return builder;
  }

  public void configureAws(
      final HttpAsyncClientBuilder builder, final AwsConfiguration awsConfiguration) {
    // AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY and AWS_SESSION_TOKEN
    // needs to be set as environment variables
    final AwsCredentialsProvider credentialsProvider = DefaultCredentialsProvider.create();
    try {
      credentialsProvider.resolveCredentials();
      log.info("AWS Credentials can be resolved.");
    } catch (final Exception e) {
      log.info("Could not resolve AWS credentials. AWS disabled.", e);
      return;
    }
    final Aws4Signer signer = Aws4Signer.create();
    final HttpRequestInterceptor signInterceptor =
        new AwsSignHttpRequestInterceptor(
            awsConfiguration.getServiceName(),
            signer,
            credentialsProvider,
            awsConfiguration.getRegion());
    builder.addInterceptorLast(signInterceptor);
    log.info("AWS Signing is enabled.");
  }

  private void setupBasicAuthentication(
      final ElasticsearchExporterConfiguration config, final HttpAsyncClientBuilder builder) {
    final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(
        AuthScope.ANY,
        new UsernamePasswordCredentials(
            config.getAuthentication().getUsername(), config.getAuthentication().getPassword()));

    builder.setDefaultCredentialsProvider(credentialsProvider);
  }

  private HttpHost[] parseUrl(final ElasticsearchExporterConfiguration config) {
    final var urls = config.url.split(",");
    final var hosts = new HttpHost[urls.length];

    for (int i = 0; i < urls.length; i++) {
      hosts[i] = HttpHost.create(urls[i]);
    }

    return hosts;
  }
}
