/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.http.config;

import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.bohnman.squiggly.Squiggly;
import io.camunda.exporter.http.client.ExporterHttpClientImpl;
import io.camunda.exporter.http.client.HttpConfig;
import io.camunda.exporter.http.matcher.FilterRecordMatcher;
import io.camunda.exporter.http.matcher.RecordMatcherImpl;
import io.camunda.exporter.http.subscription.Batch;
import io.camunda.exporter.http.subscription.Subscription;
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

  private SubscriptionConfig mergeConfigs(
      final SubscriptionConfig fileConfig, final HttpExporterConfig exporterConfig) {
    final var url = exporterConfig.getUrl() != null ? exporterConfig.getUrl() : fileConfig.url();

    final var optFileConfig = ofNullable(fileConfig);

    final var batchSize =
        ofNullable(exporterConfig.getBatchSize())
            .or(() -> optFileConfig.map(SubscriptionConfig::batchSize))
            .orElse(ConfigDefaults.DEFAULT_BATCH_SIZE);

    final var batchInterval =
        ofNullable(exporterConfig.getBatchInterval())
            .or(() -> optFileConfig.map(SubscriptionConfig::batchInterval))
            .orElse(ConfigDefaults.DEFAULT_BATCH_INTERVAL);

    final var maxRetries =
        ofNullable(exporterConfig.getMaxRetries())
            .or(() -> optFileConfig.map(SubscriptionConfig::maxRetries))
            .orElse(ConfigDefaults.DEFAULT_MAX_RETRIES);

    final var jsonFilter =
        ofNullable(exporterConfig.getJsonFilter())
            .or(() -> optFileConfig.map(SubscriptionConfig::jsonFilter))
            .orElse(null);

    final var retryDelay =
        ofNullable(exporterConfig.getRetryDelay())
            .or(() -> optFileConfig.map(SubscriptionConfig::retryDelay))
            .orElse(ConfigDefaults.DEFAULT_RETRY_DELAY);

    final var timeout =
        ofNullable(exporterConfig.getTimeout())
            .or(() -> optFileConfig.map(SubscriptionConfig::timeout))
            .orElse(ConfigDefaults.DEFAULT_TIMEOUT);

    final var continueOnError =
        ofNullable(exporterConfig.getContinueOnError())
            .or(() -> optFileConfig.map(SubscriptionConfig::continueOnError))
            .orElse(ConfigDefaults.DEFAULT_CONTINUE_ON_ERROR);

    final var filters =
        ofNullable(exporterConfig.getFilters())
            .or(() -> optFileConfig.map(SubscriptionConfig::filters))
            .orElse(null);

    return new SubscriptionConfig(
        url,
        batchSize,
        batchInterval,
        filters,
        jsonFilter,
        continueOnError,
        maxRetries,
        retryDelay,
        timeout);
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

    final var httpClient =
        new ExporterHttpClientImpl(
            new HttpConfig(config.maxRetries(), config.retryDelay(), config.timeout()));

    return new Subscription(
        httpClient,
        subscriptionObjectMapper,
        recordMatcherImpl,
        config.url(),
        batch,
        config.continueOnError());
  }

  public SubscriptionConfig readConfigFrom(final HttpExporterConfig configuration) {
    SubscriptionConfig fileConfig = null;
    if (configuration.getConfigPath() != null) {
      final var configUrl = getUrlFor(configuration.getConfigPath());
      try {
        fileConfig = objectMapper.readValue(configUrl, SubscriptionConfig.class);
      } catch (final IOException e) {
        throw new RuntimeException("Failed to read subscription config from " + configUrl, e);
      }
    }
    return mergeConfigs(fileConfig, configuration);
  }
}
