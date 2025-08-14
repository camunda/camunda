/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.http.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.bohnman.squiggly.Squiggly;
import io.camunda.exporter.http.client.ExporterHttpClientImpl;
import io.camunda.exporter.http.matcher.FilterRecordMatcher;
import io.camunda.exporter.http.matcher.RecordMatcherImpl;
import io.camunda.exporter.http.subscription.Batch;
import io.camunda.exporter.http.subscription.Subscription;
import io.camunda.exporter.http.subscription.SubscriptionConfig;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileSystems;

/** Factory for creating {@link SubscriptionConfig} instances from a configuration file. */
public class SubscriptionConfigFactory {

  private final ObjectMapper objectMapper;

  public SubscriptionConfigFactory(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public SubscriptionConfig readConfigFrom(final HttpExporterConfiguration configuration) {
    final var configUrl = getUrlFor(configuration.getConfigPath());
    try {
      return mergeConfigs(
          objectMapper.readValue(configUrl, SubscriptionConfig.class), configuration);
    } catch (final IOException e) {
      throw new RuntimeException("Failed to read subscription config from " + configUrl, e);
    }
  }

  private SubscriptionConfig mergeConfigs(
      final SubscriptionConfig fileConfig, final HttpExporterConfiguration exporterConfig) {
    final var url = exporterConfig.getUrl() != null ? exporterConfig.getUrl() : fileConfig.url();
    final var batchSize =
        exporterConfig.getBatchSize() != null
            ? exporterConfig.getBatchSize()
            : fileConfig.batchSize();
    final var batchInterval =
        exporterConfig.getBatchInterval() != null
            ? exporterConfig.getBatchInterval()
            : fileConfig.batchInterval();
    final var jsonFilter =
        exporterConfig.getJsonFilter() != null
            ? exporterConfig.getJsonFilter()
            : fileConfig.jsonFilter();
    final var filters = fileConfig.filters();
    return new SubscriptionConfig(url, batchSize, batchInterval, filters, jsonFilter);
  }

  private URL getUrlFor(String path) {
    final URL file;
    if (path.startsWith("classpath:")) {
      // Remove "classpath:" prefix for resource loading
      path = path.substring("classpath:".length());
      file = getClass().getClassLoader().getResource(path);
    } else {
      try {
        file = FileSystems.getDefault().getPath(path).toUri().toURL();
      } catch (final MalformedURLException e) {
        throw new RuntimeException(e);
      }
    }
    return file;
  }

  public Subscription createSubscription(final SubscriptionConfig config) {

    FilterRecordMatcher valueTypeMatcher = null;
    if (config.filters() != null && !config.filters().isEmpty()) {
      valueTypeMatcher = new FilterRecordMatcher(config.filters());
    }

    final RecordMatcherImpl recordMatcherImpl = new RecordMatcherImpl(valueTypeMatcher);
    final Batch batch = new Batch(config.batchSize(), config.batchInterval());

    final ObjectMapper subscriptionObjectMapper;
    if (config.jsonFilter() != null) {
      subscriptionObjectMapper = Squiggly.init(objectMapper.copy(), config.jsonFilter());
    } else {
      subscriptionObjectMapper = objectMapper;
    }

    final var httpClient = new ExporterHttpClientImpl();

    return new Subscription(
        httpClient, subscriptionObjectMapper, recordMatcherImpl, config.url(), batch);
  }
}
