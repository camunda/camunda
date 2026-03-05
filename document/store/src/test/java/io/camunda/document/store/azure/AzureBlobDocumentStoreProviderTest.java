/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store.azure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import io.camunda.document.api.DocumentStore;
import io.camunda.document.api.DocumentStoreConfiguration.DocumentStoreConfigurationRecord;
import java.util.HashMap;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class AzureBlobDocumentStoreProviderTest {

  @Test
  void shouldCreateDocumentStoreWithConnectionString() {
    try (final var mockedFactory = mockStatic(AzureBlobDocumentStoreFactory.class)) {
      // given
      final String containerName = "test-container";
      final String connectionString = "DefaultEndpointsProtocol=https;AccountName=test";
      final AzureBlobDocumentStore mockDocumentStore = mock(AzureBlobDocumentStore.class);

      mockedFactory
          .when(
              () ->
                  AzureBlobDocumentStoreFactory.createWithConnectionString(
                      eq(connectionString), eq(containerName), eq(""), any()))
          .thenReturn(mockDocumentStore);

      final DocumentStoreConfigurationRecord configuration =
          new DocumentStoreConfigurationRecord(
              "azure", AzureBlobDocumentStoreProvider.class, new HashMap<>());
      configuration.properties().put("CONTAINER", containerName);
      configuration.properties().put("CONNECTION_STRING", connectionString);
      final AzureBlobDocumentStoreProvider provider = new AzureBlobDocumentStoreProvider();

      // when
      final DocumentStore documentStore =
          provider.createDocumentStore(configuration, Executors.newSingleThreadExecutor());

      // then
      assertThat(documentStore).isNotNull();
    }
  }

  @Test
  void shouldCreateDocumentStoreWithEndpoint() {
    try (final var mockedFactory = mockStatic(AzureBlobDocumentStoreFactory.class)) {
      // given
      final String containerName = "test-container";
      final String endpoint = "https://myaccount.blob.core.windows.net";
      final AzureBlobDocumentStore mockDocumentStore = mock(AzureBlobDocumentStore.class);

      mockedFactory
          .when(
              () ->
                  AzureBlobDocumentStoreFactory.createWithDefaultCredential(
                      eq(endpoint), eq(containerName), eq(""), any()))
          .thenReturn(mockDocumentStore);

      final DocumentStoreConfigurationRecord configuration =
          new DocumentStoreConfigurationRecord(
              "azure", AzureBlobDocumentStoreProvider.class, new HashMap<>());
      configuration.properties().put("CONTAINER", containerName);
      configuration.properties().put("ENDPOINT", endpoint);
      final AzureBlobDocumentStoreProvider provider = new AzureBlobDocumentStoreProvider();

      // when
      final DocumentStore documentStore =
          provider.createDocumentStore(configuration, Executors.newSingleThreadExecutor());

      // then
      assertThat(documentStore).isNotNull();
    }
  }

  @Test
  void shouldThrowIfContainerNameMissing() {
    // given
    final DocumentStoreConfigurationRecord configuration =
        new DocumentStoreConfigurationRecord(
            "my-azure", AzureBlobDocumentStoreProvider.class, new HashMap<>());
    final AzureBlobDocumentStoreProvider provider = new AzureBlobDocumentStoreProvider();

    // when / then
    final var ex =
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(
                () ->
                    provider.createDocumentStore(
                        configuration, Executors.newSingleThreadExecutor()))
            .actual();
    assertThat(ex.getMessage())
        .isEqualTo(
            "Failed to configure document store with id 'my-azure':"
                + " missing required property 'CONTAINER'");
  }

  @Test
  void shouldThrowIfNeitherEndpointNorConnectionStringProvided() {
    // given
    final DocumentStoreConfigurationRecord configuration =
        new DocumentStoreConfigurationRecord(
            "azure", AzureBlobDocumentStoreProvider.class, new HashMap<>());
    configuration.properties().put("CONTAINER", "test-container");
    final AzureBlobDocumentStoreProvider provider = new AzureBlobDocumentStoreProvider();

    // when / then
    final var ex =
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(
                () ->
                    provider.createDocumentStore(
                        configuration, Executors.newSingleThreadExecutor()))
            .actual();
    assertThat(ex.getMessage())
        .isEqualTo(
            "Failed to configure document store with id 'azure':"
                + " either 'CONNECTION_STRING' or 'ENDPOINT' must be set");
  }

  @Test
  void shouldThrowIfContainerPathInvalid() {
    // given
    final DocumentStoreConfigurationRecord configuration =
        new DocumentStoreConfigurationRecord(
            "azure", AzureBlobDocumentStoreProvider.class, new HashMap<>());
    configuration.properties().put("CONTAINER", "test-container");
    configuration.properties().put("CONNECTION_STRING", "some-connection-string");
    configuration.properties().put("CONTAINER_PATH", "test\\path");
    final AzureBlobDocumentStoreProvider provider = new AzureBlobDocumentStoreProvider();

    // when / then
    final var ex =
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(
                () ->
                    provider.createDocumentStore(
                        configuration, Executors.newSingleThreadExecutor()))
            .actual();
    assertThat(ex.getMessage())
        .isEqualTo(
            "Failed to configure document store with id 'azure':"
                + " 'CONTAINER_PATH is invalid. Must not contain \\ character'");
  }

  @Test
  void shouldAppendTrailingSlashToContainerPath() {
    try (final var mockedFactory = mockStatic(AzureBlobDocumentStoreFactory.class)) {
      // given
      final String containerName = "test-container";
      final String connectionString = "DefaultEndpointsProtocol=https;AccountName=test";
      final AzureBlobDocumentStore mockDocumentStore = mock(AzureBlobDocumentStore.class);

      mockedFactory
          .when(
              () ->
                  AzureBlobDocumentStoreFactory.createWithConnectionString(
                      eq(connectionString), eq(containerName), eq("documents/"), any()))
          .thenReturn(mockDocumentStore);

      final DocumentStoreConfigurationRecord configuration =
          new DocumentStoreConfigurationRecord(
              "azure", AzureBlobDocumentStoreProvider.class, new HashMap<>());
      configuration.properties().put("CONTAINER", containerName);
      configuration.properties().put("CONNECTION_STRING", connectionString);
      configuration.properties().put("CONTAINER_PATH", "documents");
      final AzureBlobDocumentStoreProvider provider = new AzureBlobDocumentStoreProvider();

      // when
      final DocumentStore documentStore =
          provider.createDocumentStore(configuration, Executors.newSingleThreadExecutor());

      // then
      assertThat(documentStore).isNotNull();
      mockedFactory.verify(
          () ->
              AzureBlobDocumentStoreFactory.createWithConnectionString(
                  eq(connectionString), eq(containerName), eq("documents/"), any()));
    }
  }
}
