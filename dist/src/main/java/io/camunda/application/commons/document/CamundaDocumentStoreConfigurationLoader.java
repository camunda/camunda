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
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
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
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Loads document store configuration from unified properties under {@code camunda.document.*}.
 *
 * <p>Legacy {@code DOCUMENT_*} configuration is supported through a bridge and is used as fallback
 * when no equivalent unified property is provided.
 */
public final class CamundaDocumentStoreConfigurationLoader
    implements DocumentStoreConfigurationLoader {

  private static final String PREFIX = "camunda.document.";
  private static final String LEGACY_STORE_PREFIX = "DOCUMENT_STORE_";
  private static final String AWS = "aws";
  private static final String GCP = "gcp";
  private static final String AZURE = "azure";
  private static final String LOCAL = "local";

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
    registerStores(document.getAws(), storesById, this::toAwsStore);
    registerStores(document.getGcp(), storesById, this::toGcpStore);
    registerStores(document.getAzure(), storesById, this::toAzureStore);
    registerStores(document.getLocal(), storesById, this::toLocalStore);
    registerStores(document.getInMemory(), storesById, this::toInMemoryStore);

    return new DocumentStoreConfiguration(
        document.getDefaultStoreId(),
        document.getThreadPoolSize(),
        List.copyOf(storesById.values()));
  }

  private <T> void registerStores(
      final Map<String, T> storeConfigs,
      final Map<String, DocumentStoreConfigurationRecord> storesById,
      final BiFunction<String, T, DocumentStoreConfigurationRecord> factory) {
    storeConfigs.forEach(
        (id, store) -> storesById.put(id.toLowerCase(), factory.apply(id.toLowerCase(), store)));
  }

  private DocumentStoreConfigurationRecord toAwsStore(
      final String storeId, final Document.AwsStore store) {
    final Map<String, String> properties = new LinkedHashMap<>();
    putResolved(properties, AWS, storeId, "bucket-name", "BUCKET", store.getBucketName());
    putResolved(properties, AWS, storeId, "bucket-path", "BUCKET_PATH", store.getBucketPath());
    putResolved(properties, AWS, storeId, "region", "REGION", store.getRegion());
    putResolved(properties, AWS, storeId, "bucket-ttl", "BUCKET_TTL", store.getBucketTtl());
    return toRecord(storeId, AwsDocumentStoreProvider.class, properties);
  }

  private DocumentStoreConfigurationRecord toGcpStore(
      final String storeId, final Document.GcpStore store) {
    final Map<String, String> properties = new LinkedHashMap<>();
    putResolved(properties, GCP, storeId, "bucket-name", "BUCKET", store.getBucketName());
    putResolved(properties, GCP, storeId, "prefix", "PREFIX", store.getPrefix());
    return toRecord(storeId, GcpDocumentStoreProvider.class, properties);
  }

  private DocumentStoreConfigurationRecord toAzureStore(
      final String storeId, final Document.AzureStore store) {
    final Map<String, String> properties = new LinkedHashMap<>();
    putResolved(
        properties, AZURE, storeId, "container-name", "CONTAINER", store.getContainerName());
    putResolved(
        properties, AZURE, storeId, "container-path", "CONTAINER_PATH", store.getContainerPath());
    putResolved(properties, AZURE, storeId, "endpoint", "ENDPOINT", store.getEndpoint());
    putResolved(
        properties,
        AZURE,
        storeId,
        "connection-string",
        "CONNECTION_STRING",
        store.getConnectionString());
    return toRecord(storeId, AzureBlobDocumentStoreProvider.class, properties);
  }

  private DocumentStoreConfigurationRecord toLocalStore(
      final String storeId, final Document.LocalStore store) {
    final Map<String, String> properties = new LinkedHashMap<>();
    putResolved(properties, LOCAL, storeId, "path", "PATH", store.getPath());
    return toRecord(storeId, LocalStorageDocumentStoreProvider.class, properties);
  }

  private DocumentStoreConfigurationRecord toInMemoryStore(
      final String storeId, final Document.InMemoryStore store) {
    return toRecord(storeId, InMemoryDocumentStoreProvider.class, Map.of());
  }

  private static void putResolved(
      final Map<String, String> properties,
      final String storeType,
      final String storeId,
      final String unifiedField,
      final String propertyKey,
      final Object unifiedValue) {
    final String resolved =
        UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
            PREFIX + storeType + "." + storeId + "." + unifiedField,
            unifiedValue == null ? null : String.valueOf(unifiedValue),
            String.class,
            BackwardsCompatibilityMode.SUPPORTED,
            Set.of(LEGACY_STORE_PREFIX + storeId.toUpperCase() + "_" + propertyKey));
    if (resolved != null) {
      properties.put(propertyKey, resolved);
    }
  }

  private static DocumentStoreConfigurationRecord toRecord(
      final String storeId,
      final Class<? extends DocumentStoreProvider> providerClass,
      final Map<String, String> properties) {
    return new DocumentStoreConfigurationRecord(storeId, providerClass, properties);
  }
}
