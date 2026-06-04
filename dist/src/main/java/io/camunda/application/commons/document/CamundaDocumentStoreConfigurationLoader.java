/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.document;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.Document;
import io.camunda.document.api.DocumentStoreConfiguration;
import io.camunda.document.api.DocumentStoreConfiguration.DocumentStoreConfigurationRecord;
import io.camunda.document.api.DocumentStoreProvider;
import io.camunda.document.store.DocumentStoreConfigurationLoader;
import io.camunda.document.store.EnvironmentConfigurationLoader;
import io.camunda.document.store.aws.AwsDocumentStoreProvider;
import io.camunda.document.store.azure.AzureBlobDocumentStoreProvider;
import io.camunda.document.store.gcp.GcpDocumentStoreProvider;
import io.camunda.document.store.inmemory.InMemoryDocumentStoreProvider;
import io.camunda.document.store.localstorage.LocalStorageDocumentStoreProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads document store configuration from unified properties under {@code camunda.document.*}.
 *
 * <p>Legacy {@code DOCUMENT_*} configuration is supported through a bridge and is used as fallback
 * when no equivalent unified property is provided.
 */
public final class CamundaDocumentStoreConfigurationLoader
    implements DocumentStoreConfigurationLoader {

  private final Document document;
  private final EnvironmentConfigurationLoader legacyConfigurationLoader;

  public CamundaDocumentStoreConfigurationLoader(final Camunda camunda) {
    this.document = camunda.getDocument();
    legacyConfigurationLoader = new EnvironmentConfigurationLoader();
  }

  @Override
  public DocumentStoreConfiguration loadConfiguration() {
    final DocumentStoreConfiguration legacyConfiguration =
        legacyConfigurationLoader.loadConfiguration();
    final Map<String, DocumentStoreConfigurationRecord> storesById = new LinkedHashMap<>();

    legacyConfiguration.documentStores().forEach(store -> storesById.put(store.id(), store));
    document.getAws().forEach((id, store) -> storesById.put(id, toAwsStore(id, store)));
    document.getGcp().forEach((id, store) -> storesById.put(id, toGcpStore(id, store)));
    document.getAzure().forEach((id, store) -> storesById.put(id, toAzureStore(id, store)));
    document.getLocal().forEach((id, store) -> storesById.put(id, toLocalStore(id, store)));
    document.getInMemory().forEach((id, store) -> storesById.put(id, toInMemoryStore(id, store)));

    return new DocumentStoreConfiguration(
        Optional.ofNullable(document.getDefaultStoreId())
            .orElse(legacyConfiguration.defaultDocumentStoreId()),
        Optional.ofNullable(document.getThreadPoolSize())
            .orElse(legacyConfiguration.threadPoolSize()),
        List.copyOf(storesById.values()));
  }

  private static DocumentStoreConfigurationRecord toAwsStore(
      final String storeId, final Document.AwsStore store) {
    final Map<String, String> properties = new LinkedHashMap<>();
    putIfPresent(properties, "BUCKET", store.getBucketName());
    putIfPresent(properties, "BUCKET_PATH", store.getBucketPath());
    putIfPresent(properties, "REGION", store.getRegion());
    putIfPresent(properties, "BUCKET_TTL", store.getBucketTtl());
    return toRecord(storeId, AwsDocumentStoreProvider.class, properties);
  }

  private static DocumentStoreConfigurationRecord toGcpStore(
      final String storeId, final Document.GcpStore store) {
    final Map<String, String> properties = new LinkedHashMap<>();
    putIfPresent(properties, "BUCKET", store.getBucketName());
    putIfPresent(properties, "PREFIX", store.getPrefix());
    return toRecord(storeId, GcpDocumentStoreProvider.class, properties);
  }

  private static DocumentStoreConfigurationRecord toAzureStore(
      final String storeId, final Document.AzureStore store) {
    final Map<String, String> properties = new LinkedHashMap<>();
    putIfPresent(properties, "CONTAINER", store.getContainerName());
    putIfPresent(properties, "CONTAINER_PATH", store.getContainerPath());
    putIfPresent(properties, "ENDPOINT", store.getEndpoint());
    putIfPresent(properties, "CONNECTION_STRING", store.getConnectionString());
    return toRecord(storeId, AzureBlobDocumentStoreProvider.class, properties);
  }

  private static DocumentStoreConfigurationRecord toLocalStore(
      final String storeId, final Document.LocalStore store) {
    final Map<String, String> properties = new LinkedHashMap<>();
    putIfPresent(properties, "PATH", store.getPath());
    return toRecord(storeId, LocalStorageDocumentStoreProvider.class, properties);
  }

  private static DocumentStoreConfigurationRecord toInMemoryStore(
      final String storeId, final Document.InMemoryStore store) {
    return toRecord(storeId, InMemoryDocumentStoreProvider.class, Map.of());
  }

  private static void putIfPresent(
      final Map<String, String> properties, final String key, final Object value) {
    if (value != null) {
      properties.put(key, String.valueOf(value));
    }
  }

  private static DocumentStoreConfigurationRecord toRecord(
      final String storeId,
      final Class<? extends DocumentStoreProvider> providerClass,
      final Map<String, String> properties) {
    return new DocumentStoreConfigurationRecord(storeId, providerClass, properties);
  }
}
