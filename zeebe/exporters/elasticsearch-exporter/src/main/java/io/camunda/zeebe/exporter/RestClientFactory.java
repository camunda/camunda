/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import io.camunda.plugin.search.header.DatabaseCustomHeaderSupplier;
import io.camunda.zeebe.util.ReflectUtil;
import io.camunda.zeebe.util.jar.ExternalJarClassLoader;
import io.camunda.zeebe.util.jar.ThreadContextUtil;
import java.nio.file.Paths;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RestClientFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(RestClientFactory.class);

  private static final RestClientFactory INSTANCE = new RestClientFactory();

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
            .setHttpClientConfigCallback(b -> configureHttpClient(config, b));

    return builder.build();
  }

  private HttpAsyncClientBuilder configureHttpClient(
      final ElasticsearchExporterConfiguration config, final HttpAsyncClientBuilder builder) {
    // use single thread for rest client
    builder.setDefaultIOReactorConfig(IOReactorConfig.custom().setIoThreadCount(1).build());

    if (config.hasAuthenticationPresent()) {
      setupBasicAuthentication(config, builder);
    }

    LOGGER.trace("Attempt to load interceptor plugins");
    if (config.getInterceptorPlugins() != null) {
      loadInterceptorPlugins(builder, config);
    }

    return builder;
  }

  private void loadInterceptorPlugins(
      final HttpAsyncClientBuilder httpAsyncClientBuilder,
      final ElasticsearchExporterConfiguration elsConfig) {
    LOGGER.trace("Plugins detected to be not empty {}", elsConfig.getInterceptorPlugins());

    final var interceptors = elsConfig.getInterceptorPlugins();
    interceptors.forEach(
        (id, interceptor) -> {
          LOGGER.trace("Attempting to register {}", interceptor.getId());
          try {
            // WARNING! Due to the nature of interceptors, by the moment they (interceptors)
            // are executed, the below class loader will close the JAR file hence
            // to avoid NoClassDefFoundError we must not close this class loader.
            final var classLoader =
                ExternalJarClassLoader.ofPath(Paths.get(interceptor.getJarPath()));

            final var pluginClass = classLoader.loadClass(interceptor.getClassName());
            final var plugin = ReflectUtil.newInstance(pluginClass);

            if (plugin instanceof final DatabaseCustomHeaderSupplier dchs) {
              LOGGER.trace(
                  "Plugin {} appears to be a DB Header Provider. Registering with interceptor",
                  interceptor.getId());
              httpAsyncClientBuilder.addInterceptorLast(
                  (HttpRequestInterceptor)
                      (httpRequest, httpContext) -> {
                        final var customHeader =
                            ThreadContextUtil.supplyWithClassLoader(
                                dchs::getSearchDatabaseCustomHeader, classLoader);
                        httpRequest.addHeader(customHeader.key(), customHeader.value());
                      });
            } else {
              throw new RuntimeException(
                  "Unknown type of interceptor plugin or wrong class specified");
            }
          } catch (final Exception e) {
            throw new RuntimeException("Failed to load interceptor plugin due to exception", e);
          }
        });
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
