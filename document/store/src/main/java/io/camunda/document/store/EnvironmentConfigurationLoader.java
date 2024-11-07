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

  private static final Pattern DOCUMENT_STORE_PROPERTY_PATTERN =
      Pattern.compile("^" + STORE_PREFIX + "(?<id>[^_]+)_(?<property>.+)$");
  private static final String STORE_CLASS = "CLASS";

  private final Map<String, String> environmentVariableOverrides = new HashMap<>();

  @Override
  public DocumentStoreConfiguration loadConfiguration() {
    return new DocumentStoreConfiguration(
        getRootLevelProperty(DEFAULT_DOCUMENT_STORE_ID).map(String::toLowerCase).orElse(null),
        loadDocumentStoreProperties());
  }

  // For testing purposes
  void addEnvironmentVariableOverride(final String key, final String value) {
    environmentVariableOverrides.put(key, value);
  }

  private List<DocumentStoreConfigurationRecord> loadDocumentStoreProperties() {
    final var envVars = new HashSet<>(environmentVariableOverrides.keySet());
    envVars.addAll(System.getenv().keySet());
    final var matches =
        envVars.stream().map(DOCUMENT_STORE_PROPERTY_PATTERN::matcher).filter(Matcher::matches);
    final Map<String, Map<String, String>> storeProperties = new HashMap<>();
    matches.forEach(
        matcher -> {
          final var id = matcher.group("id");
          final var property = matcher.group("property");
          final var value = getEnvVariable(matcher.group()).orElse(null);
          storeProperties.computeIfAbsent(id, __ -> new HashMap<>()).put(property, value);
        });
    return storeProperties.entrySet().stream()
        .map(
            entry -> {
              final var id = entry.getKey();
              final Map<String, String> properties = entry.getValue();
              final var storeClass = properties.get(STORE_CLASS);
              if (storeClass == null) {
                throw new IllegalArgumentException(
                    "Document store class not defined for document store: "
                        + id
                        + ". Please define the class in the environment variable: "
                        + STORE_PREFIX
                        + id
                        + "_"
                        + STORE_CLASS);
              }
              properties.remove(STORE_CLASS);
              final Class<? extends DocumentStoreProvider> storeClassInstance;
              try {
                storeClassInstance =
                    (Class<? extends DocumentStoreProvider>) Class.forName(storeClass);
              } catch (final ClassNotFoundException e) {
                throw new IllegalArgumentException(
                    "Document store class not found: " + storeClass, e);
              }
              return new DocumentStoreConfigurationRecord(
                  id.toLowerCase(), storeClassInstance, properties);
            })
        .toList();
  }

  private Optional<String> getRootLevelProperty(final String name) {
    return getEnvVariable(CONFIGURATION_PREFIX + name);
  }

  private Optional<String> getEnvVariable(final String name) {
    return Optional.ofNullable(environmentVariableOverrides.get(name))
        .or(() -> Optional.ofNullable(System.getenv().get(name)));
  }
}
