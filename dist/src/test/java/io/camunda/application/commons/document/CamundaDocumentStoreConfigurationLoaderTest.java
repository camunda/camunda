/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.document;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.Document;
import io.camunda.document.api.DocumentStoreConfiguration.DocumentStoreConfigurationRecord;
import io.camunda.document.store.aws.AwsDocumentStoreProvider;
import io.camunda.document.store.azure.AzureBlobDocumentStoreProvider;
import io.camunda.document.store.gcp.GcpDocumentStoreProvider;
import io.camunda.document.store.localstorage.LocalStorageDocumentStoreProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

class CamundaDocumentStoreConfigurationLoaderTest {

  @Test
  void shouldLoadStoresFromUnifiedConfiguration() {
    // given
    final Camunda camunda = new Camunda();
    camunda.getDocument().setDefaultStoreId("aws1");
    camunda.getDocument().setThreadPoolSize(10);

    final Document.AwsStore awsStore = new Document.AwsStore();
    awsStore.setBucketName("docs");
    awsStore.setBucketPath("prod/");
    awsStore.setBucketTtl(30L);
    camunda.getDocument().getAws().put("aws1", awsStore);

    final Document.GcpStore gcpStore = new Document.GcpStore();
    gcpStore.setBucketName("gcp-docs");
    gcpStore.setPrefix("temp/");
    camunda.getDocument().getGcp().put("gcp1", gcpStore);

    final Document.AzureStore azureStore = new Document.AzureStore();
    azureStore.setContainerName("container");
    azureStore.setEndpoint("https://account.blob.core.windows.net");
    camunda.getDocument().getAzure().put("az1", azureStore);

    final Document.LocalStore localStore = new Document.LocalStore();
    localStore.setPath("/var/camunda/documents");
    camunda.getDocument().getLocal().put("local1", localStore);

    final var loader = new CamundaDocumentStoreConfigurationLoader(camunda);

    // when
    final var configuration = loader.loadConfiguration();

    // then
    assertThat(configuration.defaultDocumentStoreId()).isEqualTo("aws1");
    assertThat(configuration.threadPoolSize()).isEqualTo(10);
    assertThat(configuration.documentStores())
        .extracting(DocumentStoreConfigurationRecord::id)
        .containsExactly("aws1", "gcp1", "az1", "local1");

    assertThat(store("aws1", configuration.documentStores()).providerClass())
        .isEqualTo(AwsDocumentStoreProvider.class);
    assertThat(store("aws1", configuration.documentStores()).properties())
        .containsEntry("BUCKET", "docs")
        .containsEntry("BUCKET_PATH", "prod/")
        .containsEntry("BUCKET_TTL", "30");

    assertThat(store("gcp1", configuration.documentStores()).providerClass())
        .isEqualTo(GcpDocumentStoreProvider.class);
    assertThat(store("gcp1", configuration.documentStores()).properties())
        .containsEntry("BUCKET", "gcp-docs")
        .containsEntry("PREFIX", "temp/");

    assertThat(store("az1", configuration.documentStores()).providerClass())
        .isEqualTo(AzureBlobDocumentStoreProvider.class);
    assertThat(store("az1", configuration.documentStores()).properties())
        .containsEntry("CONTAINER", "container")
        .containsEntry("ENDPOINT", "https://account.blob.core.windows.net");

    assertThat(store("local1", configuration.documentStores()).providerClass())
        .isEqualTo(LocalStorageDocumentStoreProvider.class);
    assertThat(store("local1", configuration.documentStores()).properties())
        .containsEntry("PATH", "/var/camunda/documents");
  }

  @Test
  void shouldUseLegacyConfigurationAsFallback() {
    // given
    final Camunda camunda = new Camunda();
    final var loader = new CamundaDocumentStoreConfigurationLoader(camunda);
    System.setProperty("DOCUMENT_DEFAULT_STORE_ID", "LEGACY");
    System.setProperty(
        "DOCUMENT_STORE_LEGACY_CLASS", "io.camunda.document.store.gcp.GcpDocumentStoreProvider");
    System.setProperty("DOCUMENT_STORE_LEGACY_BUCKET", "legacy-bucket");

    try {
      // when
      final var configuration = loader.loadConfiguration();

      // then
      assertThat(configuration.defaultDocumentStoreId()).isEqualTo("legacy");
      assertThat(configuration.documentStores())
          .extracting(DocumentStoreConfigurationRecord::id)
          .containsExactly("legacy");
      assertThat(store("legacy", configuration.documentStores()).properties())
          .containsEntry("BUCKET", "legacy-bucket");
    } finally {
      System.clearProperty("DOCUMENT_DEFAULT_STORE_ID");
      System.clearProperty("DOCUMENT_STORE_LEGACY_CLASS");
      System.clearProperty("DOCUMENT_STORE_LEGACY_BUCKET");
    }
  }

  @Test
  void shouldPreferUnifiedConfigurationOverLegacy() {
    // given
    final Camunda camunda = new Camunda();
    camunda.getDocument().setDefaultStoreId("aws1");
    camunda.getDocument().setThreadPoolSize(12);
    final Document.AwsStore awsStore = new Document.AwsStore();
    awsStore.setBucketName("new-bucket");
    camunda.getDocument().getAws().put("aws1", awsStore);

    final var loader = new CamundaDocumentStoreConfigurationLoader(camunda);
    System.setProperty("DOCUMENT_DEFAULT_STORE_ID", "LEGACY");
    System.setProperty("DOCUMENT_THREAD_POOL_SIZE", "5");
    System.setProperty(
        "DOCUMENT_STORE_AWS1_CLASS", "io.camunda.document.store.gcp.GcpDocumentStoreProvider");
    System.setProperty("DOCUMENT_STORE_AWS1_BUCKET", "legacy-bucket");

    try {
      // when
      final var configuration = loader.loadConfiguration();

      // then
      assertThat(configuration.defaultDocumentStoreId()).isEqualTo("aws1");
      assertThat(configuration.threadPoolSize()).isEqualTo(12);
      assertThat(store("aws1", configuration.documentStores()).providerClass())
          .isEqualTo(AwsDocumentStoreProvider.class);
      assertThat(store("aws1", configuration.documentStores()).properties())
          .containsEntry("BUCKET", "new-bucket");
    } finally {
      System.clearProperty("DOCUMENT_DEFAULT_STORE_ID");
      System.clearProperty("DOCUMENT_THREAD_POOL_SIZE");
      System.clearProperty("DOCUMENT_STORE_AWS1_CLASS");
      System.clearProperty("DOCUMENT_STORE_AWS1_BUCKET");
    }
  }

  private static DocumentStoreConfigurationRecord store(
      final String id, final List<DocumentStoreConfigurationRecord> stores) {
    return stores.stream().filter(store -> store.id().equals(id)).findFirst().orElseThrow();
  }
}
