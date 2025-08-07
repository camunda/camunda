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
import com.github.bohnman.squiggly.Squiggly;
import io.camunda.zeebe.exporter.http.client.ExporterHttpClient;
import io.camunda.zeebe.exporter.http.matcher.CombinedMatcher;
import io.camunda.zeebe.exporter.http.matcher.Filter;
import io.camunda.zeebe.exporter.http.matcher.FilterRecordMatcher;
import io.camunda.zeebe.exporter.http.matcher.RuleRecordMatcher;
import io.camunda.zeebe.exporter.http.subscription.Batch;
import io.camunda.zeebe.exporter.http.subscription.Subscription;
import io.camunda.zeebe.exporter.http.subscription.SubscriptionConfig;
import io.camunda.zeebe.protocol.record.ValueType;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Factory for creating {@link SubscriptionConfig} instances from a configuration file. */
public class SubscriptionConfigFactory {

  private final ObjectMapper objectMapper;

  public SubscriptionConfigFactory(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public SubscriptionConfig readConfigFrom(final HttpExporterConfiguration configuration) {
    final var configUrl = getUrlFor(configuration.getConfigPath());
    try {
      return readConfigNode(objectMapper.readTree(configUrl), configuration);
    } catch (final IOException e) {
      throw new RuntimeException("Failed to read subscription config from " + configUrl, e);
    }
  }

  /**
   * Reads a configuration node from the provided JSON node and merges it with the provided
   * configuration. It is a quite manual parsing step as we need the rules as raw JSON strings.
   *
   * @param configNode
   * @param configuration
   * @return
   */
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
    final var jsonFilter =
        configuration.getJsonFilter() != null
            ? configuration.getJsonFilter()
            : configNode.has("jsonFilter") ? configNode.get("jsonFilter").asText() : null;
    final var rules = extractRules(configNode);
    final var filters = extractFilters(configNode);
    return new SubscriptionConfig(url, batchSize, batchInterval, rules, filters, jsonFilter);
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

  private List<String> extractRules(final JsonNode configNode) {
    final var ruleArrayNode = configNode.withArrayProperty("rules");
    if (ruleArrayNode != null) {
      final var rules = new ArrayList<String>(ruleArrayNode.size());
      for (final JsonNode ruleNode : ruleArrayNode) {
        final var rule = ruleNode.toString();
        rules.add(rule);
      }
      return rules;
    } else {
      // If no rules are defined, return an empty list
      return List.of();
    }
  }

  private List<Filter> extractFilters(final JsonNode configNode) {
    final var filtersNode = configNode.withArrayProperty("filters");
    if (filtersNode != null) {
      final var rules = new ArrayList<Filter>(filtersNode.size());
      for (final JsonNode filterNode : filtersNode) {
        final var valueType = ValueType.valueOf(filterNode.get("valueType").asText());
        final var intentsNode = filterNode.withArrayProperty("intents");
        final Set<String> intents = new HashSet<>();
        if (intentsNode != null) {
          for (final JsonNode intentNode : intentsNode) {
            intents.add(intentNode.asText());
          }
        }
        rules.add(new Filter(valueType, intents));
      }
      return rules;
    } else {
      // If no filters are defined, return an empty list
      return List.of();
    }
  }

  public Subscription createSubscription(
      final SubscriptionConfig config, final ExporterHttpClient httpClient) {

    RuleRecordMatcher ruleRecordMatcher = null;
    if (config.rules() != null && !config.rules().isEmpty()) {
      ruleRecordMatcher = new RuleRecordMatcher(config.rules());
    }

    FilterRecordMatcher valueTypeMatcher = null;
    if (config.filters() != null && !config.filters().isEmpty()) {
      valueTypeMatcher = new FilterRecordMatcher(config.filters());
    }

    final CombinedMatcher combinedMatcher =
        new CombinedMatcher(valueTypeMatcher, ruleRecordMatcher);
    final Batch batch = new Batch(config.batchSize(), config.batchInterval());

    final ObjectMapper filteredObjectMapper;
    if (config.jsonFilter() != null) {
      filteredObjectMapper = Squiggly.init(objectMapper.copy(), config.jsonFilter());
    } else {
      filteredObjectMapper = objectMapper;
    }

    return new Subscription(
        httpClient, objectMapper, combinedMatcher, config.url(), filteredObjectMapper, batch);
  }
}
