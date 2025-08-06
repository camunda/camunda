/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.http.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.exporter.http.client.ExporterHttpClient;
import io.camunda.zeebe.exporter.http.matcher.RecordMatcher;
import io.camunda.zeebe.exporter.http.matcher.RuleRecordMatcher;
import io.camunda.zeebe.exporter.http.subscription.Batch;
import io.camunda.zeebe.exporter.http.subscription.Subscription;
import io.camunda.zeebe.exporter.http.subscription.SubscriptionConfig;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.List;

public class SubscriptionConfigFactory {

  private final ObjectMapper objectMapper;

  public SubscriptionConfigFactory(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public SubscriptionConfig readConfigFrom(final HttpExporterConfiguration configuration) {
    final var configNode = readConfigNode(configuration.getConfigPath());
    return readConfigNode(configNode, configuration);
  }

  private SubscriptionConfig readConfigNode(
      final JsonNode configNode, final HttpExporterConfiguration configuration) {
    final var url =
        configuration.getUrl() != null ? configuration.getUrl() : configNode.get("url").asText();
    final var batchSize =
        configuration.getBatchSize() != null
            ? configuration.getBatchSize()
            : configNode.get("batchSize").asInt();
    final var batchInterval =
        configuration.getBatchInterval() != null
            ? configuration.getBatchInterval()
            : configNode.get("batchInterval").asLong();
    final var rules = extractRules(configNode);
    return new SubscriptionConfig(url, batchSize, batchInterval, rules);
  }

  private JsonNode readConfigNode(String path) {
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
    try {
      return objectMapper.readTree(file);
    } catch (final IOException e) {
      throw new RuntimeException("Failed to read subscription config from " + path, e);
    }
  }

  private List<String> extractRules(final JsonNode configNode) {
    final var ruleArrayNode = configNode.withArrayProperty("rules");
    final var rules = new ArrayList<String>(ruleArrayNode.size());
    for (final JsonNode ruleNode : ruleArrayNode) {
      final var rule = ruleNode.toString();
      rules.add(rule);
    }
    return rules;
  }

  public Subscription createSubscription(
      final SubscriptionConfig config, final ExporterHttpClient httpClient) {
    final RecordMatcher recordMatcher = new RuleRecordMatcher(config.rules());
    final Batch batch = new Batch(config.batchSize(), config.batchInterval());
    return new Subscription(httpClient, config.url(), recordMatcher, objectMapper, batch);
  }
}
