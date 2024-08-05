/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import io.camunda.plugin.search.header.DatabaseCustomHeaderSupplier;
import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration.InterceptorPlugin;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
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

    LOGGER.error("IGPETROV Attempt to load plugins");
    if (config.getInterceptorPlugin() != null) {
      LOGGER.error("IGPETROV Plugins detected to be not empty {}", config.getInterceptorPlugin());

      final InterceptorPlugin interceptor = config.getInterceptorPlugin();
      LOGGER.error("IGPETROV Attempting to register {}", interceptor.getId());
      final var interceptorClazz = createInterceptorClass(interceptor);
      if (interceptorClazz != null) {
        // TODO: only default constructor
        final Constructor<?> constructor = interceptorClazz.getConstructors()[0];
        try {
          final var createdInterceptor = constructor.newInstance();
          if (createdInterceptor instanceof final DatabaseCustomHeaderSupplier dchs) {
            LOGGER.error(
                "IGPETROV Plugin {} appears to be a DB Header Provider. Registering with interceptor",
                interceptor.getId());
            builder.addInterceptorLast(
                (HttpRequestInterceptor)
                    (httpRequest, httpContext) -> {
                      httpRequest.addHeader(
                          dchs.getElasticsearchCustomHeader().key(),
                          dchs.getElasticsearchCustomHeader().value());
                    });
          }
        } catch (final InstantiationException
            | IllegalAccessException
            | InvocationTargetException e) {
          LOGGER.error(
              "IGPETROV Plugin {} failed to register due to exception. Ignoring",
              interceptor.getId(),
              e);
        }
      }
    }

    if (config.hasAuthenticationPresent()) {
      setupBasicAuthentication(config, builder);
    }

    return builder;
  }

  Class<?> createInterceptorClass(final InterceptorPlugin interceptorPlugin) {
    // File type and path are checked by class loader
    try (final var classLoader =
        ExternalJarClassLoaderMaybeRemove.ofPath(Paths.get(interceptorPlugin.getJarPath()))) {
      return classLoader.loadClass(interceptorPlugin.getClassName());
    } catch (final IOException | ClassNotFoundException e) {
      LOGGER.error(
          "IGPETROV Plugin {} failed to register due to exception. Ignoring",
          interceptorPlugin.getId(),
          e);
      return null;
    }

    // TODO other validations?

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
