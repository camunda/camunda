/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store.gcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import io.camunda.document.api.DocumentStore;
import io.camunda.document.api.DocumentStoreConfiguration.DocumentStoreConfigurationRecord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class GcpDocumentStoreProviderTest {

  @Test
  public void shouldCreateDocumentStore() {
    // given
    final DocumentStoreConfigurationRecord configuration =
        new DocumentStoreConfigurationRecord(
            "gcp", GcpDocumentStoreProvider.class, new HashMap<>());
    configuration.properties().put("BUCKET", "bucketName");
    final GcpDocumentStoreProvider provider = new GcpDocumentStoreProvider();

    // when
    final DocumentStore documentStore =
        provider.createDocumentStore(configuration, Executors.newSingleThreadExecutor());

    // then
    assertThat(documentStore).isNotNull();
  }

  @Test
  public void shouldThrowIfBucketNameIsMissing() {
    // given
    final DocumentStoreConfigurationRecord configuration =
        new DocumentStoreConfigurationRecord(
            "my-gcp", GcpDocumentStoreProvider.class, new HashMap<>());
    final GcpDocumentStoreProvider provider = new GcpDocumentStoreProvider();

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
            "Failed to configure document store with id 'my-gcp': missing required property 'BUCKET'");
  }

  @Test
  public void shouldUseTempPrefixIfNotProvided() {
    // given
    final DocumentStoreConfigurationRecord configuration =
        new DocumentStoreConfigurationRecord(
            "gcp", GcpDocumentStoreProvider.class, new HashMap<>());
    configuration.properties().put("BUCKET", "bucketName");
    final GcpDocumentStoreProvider provider = new GcpDocumentStoreProvider();

    // when
    final DocumentStore documentStore =
        provider.createDocumentStore(configuration, Executors.newSingleThreadExecutor());

    // then
    assertThat(documentStore).isNotNull();
  }

  @Test
  public void shouldUsePrefixIfProvided() {
    // given
    final DocumentStoreConfigurationRecord configuration =
        new DocumentStoreConfigurationRecord(
            "gcp", GcpDocumentStoreProvider.class, new HashMap<>());
    configuration.properties().put("BUCKET", "bucketName");
    configuration.properties().put("PREFIX", "prefix");
    final GcpDocumentStoreProvider provider = new GcpDocumentStoreProvider();

    // when
    final DocumentStore documentStore =
        provider.createDocumentStore(configuration, Executors.newSingleThreadExecutor());

    // then
    assertThat(documentStore).isNotNull();
  }

  @Test
  public void shouldUseTheConfiguredCredentialsFile(@TempDir final Path tempDir)
      throws IOException {
    // given
    final Path keyFile = tempDir.resolve("tenant-a.json");
    Files.writeString(
        keyFile,
        """
        {
          "type": "authorized_user",
          "client_id": "tenant-a-client",
          "client_secret": "tenant-a-secret",
          "refresh_token": "tenant-a-refresh-token"
        }
        """);
    final DocumentStoreConfigurationRecord configuration =
        new DocumentStoreConfigurationRecord(
            "gcp", GcpDocumentStoreProvider.class, new HashMap<>());
    configuration.properties().put("BUCKET", "bucketName");
    configuration.properties().put("CREDENTIALS_PATH", keyFile.toString());
    final GcpDocumentStoreProvider provider = new GcpDocumentStoreProvider();

    // when
    final DocumentStore documentStore =
        provider.createDocumentStore(configuration, Executors.newSingleThreadExecutor());

    // then
    assertThat(documentStore).isNotNull();
  }

  @Test
  public void shouldThrowIfCredentialsFileCannotBeRead(@TempDir final Path tempDir) {
    // given
    final Path missing = tempDir.resolve("absent.json");
    final DocumentStoreConfigurationRecord configuration =
        new DocumentStoreConfigurationRecord(
            "my-gcp", GcpDocumentStoreProvider.class, new HashMap<>());
    configuration.properties().put("BUCKET", "bucketName");
    configuration.properties().put("CREDENTIALS_PATH", missing.toString());
    final GcpDocumentStoreProvider provider = new GcpDocumentStoreProvider();

    // when / then
    final var ex =
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(
                () ->
                    provider.createDocumentStore(
                        configuration, Executors.newSingleThreadExecutor()))
            .actual();
    assertThat(ex.getMessage())
        .startsWith(
            "Failed to configure document store with id 'my-gcp': could not read GCP credentials from");
  }
}
