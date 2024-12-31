/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store;

import io.camunda.document.api.DocumentStoreConfiguration;
import io.camunda.document.api.DocumentStoreConfiguration.DocumentStoreConfigurationRecord;
import io.camunda.document.api.DocumentStoreProvider;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DOCUMENT_HASH_VERIFICATION_ENABLED=true
 * DOCUMENT_STORE_GCP_CLASS=io.camunda.document.store.gcp.GcpDocumentStoreProvider
 * DOCUMENT_STORE_GCP_BUCKET=my-bucket
 * DOCUMENT_STORE_INMEMORY_CLASS=io.camunda.document.store.inmemory.InMemoryDocumentStoreProvider
 */
public class EnvironmentConfigurationLoader implements DocumentStoreConfigurationLoader {

  private static final String CONFIGURATION_PREFIX = "DOCUMENT_";
  private static final String STORE_PREFIX = "DOCUMENT_STORE_";
  private static final String DEFAULT_DOCUMENT_STORE_ID = "DEFAULT_STORE_ID";
  private static final String THREAD_POOL_SIZE = "THREAD_POOL_SIZE";

  private static final Pattern DOCUMENT_STORE_PROPERTY_PATTERN =
      Pattern.compile("^" + STORE_PREFIX + "(?<id>[^_]+)_(?<property>.+)$");
  private static final String STORE_CLASS = "CLASS";

  @Override
  public DocumentStoreConfiguration loadConfiguration() {
    return new DocumentStoreConfiguration(
        getRootLevelProperty(DEFAULT_DOCUMENT_STORE_ID).map(String::toLowerCase).orElse(null),
        getRootLevelProperty(THREAD_POOL_SIZE).map(Integer::parseInt).orElse(null),
        loadDocumentStoreProperties());
  }

  private List<DocumentStoreConfigurationRecord> loadDocumentStoreProperties() {
    final var storeProperties = extractStorePropertiesFromEnvVars();
    return storeProperties.entrySet().stream().map(this::createConfigurationRecord).toList();
  }

  private Map<String, Map<String, String>> extractStorePropertiesFromEnvVars() {
    final Set<String> envVars = new HashSet<>();
    envVars.addAll(System.getenv().keySet());
    envVars.addAll(System.getProperties().stringPropertyNames());
    final Map<String, Map<String, String>> storeProperties = new HashMap<>();
    envVars.stream()
        .map(DOCUMENT_STORE_PROPERTY_PATTERN::matcher)
        .filter(Matcher::matches)
        .forEach(
            matcher -> {
              final var storeId = matcher.group("id");
              final var storeProperty = matcher.group("property");
              final var value = getEnvVariable(matcher.group()).orElse(null);
              storeProperties
                  .computeIfAbsent(storeId, k -> new HashMap<>())
                  .put(storeProperty, value);
            });
    return storeProperties;
  }

  private DocumentStoreConfigurationRecord createConfigurationRecord(
      final Map.Entry<String, Map<String, String>> entry) {
    final var storeId = entry.getKey();
    final var properties = entry.getValue();
    final var storeClassName = properties.remove(STORE_CLASS);
    if (storeClassName == null) {
      throw new IllegalArgumentException("Store class not defined for store: " + storeId);
    }
    final Class<? extends DocumentStoreProvider> storeClass = resolveStoreClass(storeClassName);
    return new DocumentStoreConfigurationRecord(storeId.toLowerCase(), storeClass, properties);
  }

  private Class<? extends DocumentStoreProvider> resolveStoreClass(final String storeClassName) {
    try {
      return (Class<? extends DocumentStoreProvider>) Class.forName(storeClassName);
    } catch (final ClassNotFoundException e) {
      throw new IllegalArgumentException("Store class not found: " + storeClassName, e);
    }
  }

  private Optional<String> getRootLevelProperty(final String name) {
    return getEnvVariable(CONFIGURATION_PREFIX + name);
  }

  private Optional<String> getEnvVariable(final String name) {
    return Optional.ofNullable(System.getenv().get(name))
        .or(() -> Optional.ofNullable(System.getProperty(name)));
  }
}
