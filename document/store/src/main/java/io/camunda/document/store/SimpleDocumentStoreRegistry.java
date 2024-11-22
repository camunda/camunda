/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store;

import io.camunda.document.api.DocumentStore;
import io.camunda.document.api.DocumentStoreConfiguration;
import io.camunda.document.api.DocumentStoreConfiguration.DocumentStoreConfigurationRecord;
import io.camunda.document.api.DocumentStoreProvider;
import io.camunda.document.api.DocumentStoreRecord;
import io.camunda.document.api.DocumentStoreRegistry;
import io.camunda.document.store.inmemory.InMemoryDocumentStore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.slf4j.Logger;

public class SimpleDocumentStoreRegistry implements DocumentStoreRegistry {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(SimpleDocumentStoreRegistry.class);

  private static final String FALLBACK_STORE_ID_IN_MEMORY = "in-memory";

  private final DocumentStoreConfiguration configuration;
  private final Map<String, DocumentStore> stores = new HashMap<>();
  private final String defaultDocumentStoreId;

  public SimpleDocumentStoreRegistry(final DocumentStoreConfigurationLoader configurationLoader) {
    configuration = configurationLoader.loadConfiguration();

    if (configuration.documentStores().isEmpty()) {
      LOG.warn("No document stores configured. Using fallback in-memory document store.");
      stores.put(FALLBACK_STORE_ID_IN_MEMORY, new InMemoryDocumentStore());
      defaultDocumentStoreId = FALLBACK_STORE_ID_IN_MEMORY;
      return;
    }
    if (configuration.defaultDocumentStoreId() == null) {
      throw new IllegalArgumentException("No default document store ID configured.");
    }
    defaultDocumentStoreId = configuration.defaultDocumentStoreId();
    loadDocumentStores(configuration.documentStores());
    if (!stores.containsKey(defaultDocumentStoreId)) {
      throw new IllegalArgumentException(
          "Default document store ID does not match any configured document store: "
              + defaultDocumentStoreId);
    }
  }

  @Override
  public DocumentStoreConfiguration getConfiguration() {
    return configuration;
  }

  @Override
  public DocumentStoreRecord getDocumentStore(final String id) {
    final DocumentStore store = stores.get(id);
    if (store == null) {
      throw new IllegalArgumentException("No such document store: " + id);
    }
    return new DocumentStoreRecord(id, store);
  }

  @Override
  public DocumentStoreRecord getDefaultDocumentStore() {
    return getDocumentStore(defaultDocumentStoreId);
  }

  private void loadDocumentStores(final List<DocumentStoreConfigurationRecord> configurations) {
    final ServiceLoader<DocumentStoreProvider> loader =
        ServiceLoader.load(DocumentStoreProvider.class);

    for (final DocumentStoreConfigurationRecord configuration : configurations) {
      final DocumentStoreProvider provider = findProvider(loader, configuration);
      final DocumentStore store = provider.createDocumentStore(configuration);
      stores.put(configuration.id(), store);
    }
  }

  private DocumentStoreProvider findProvider(
      final ServiceLoader<DocumentStoreProvider> loader,
      final DocumentStoreConfigurationRecord configuration) {
    final List<DocumentStoreProvider> matchingProviders =
        loader.stream()
            .map(ServiceLoader.Provider::get)
            .filter(provider -> provider.getClass().equals(configuration.providerClass()))
            .toList();

    if (matchingProviders.isEmpty()) {
      throw new IllegalArgumentException("No provider found for configuration: " + configuration);
    }
    if (matchingProviders.size() > 1) {
      throw new IllegalArgumentException(
          "Multiple providers found for configuration: " + configuration);
    }
    return matchingProviders.getFirst();
  }
}
