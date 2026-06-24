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
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.document.api.DocumentStoreConfiguration.DocumentStoreConfigurationRecord;
import io.camunda.document.store.aws.AwsDocumentStoreProvider;
import io.camunda.document.store.azure.AzureBlobDocumentStoreProvider;
import io.camunda.document.store.gcp.GcpDocumentStoreProvider;
import io.camunda.document.store.localstorage.LocalStorageDocumentStoreProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

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

    final var mockEnv = new MockEnvironment();
    mockEnv.setProperty("camunda.document.default-store-id", "aws1");
    mockEnv.setProperty("camunda.document.thread-pool-size", "10");
    mockEnv.setProperty("camunda.document.aws.aws1.bucket-name", "docs");
    mockEnv.setProperty("camunda.document.aws.aws1.bucket-path", "prod/");
    mockEnv.setProperty("camunda.document.aws.aws1.bucket-ttl", "30");
    mockEnv.setProperty("camunda.document.gcp.gcp1.bucket-name", "gcp-docs");
    mockEnv.setProperty("camunda.document.gcp.gcp1.prefix", "temp/");
    mockEnv.setProperty("camunda.document.azure.az1.container-name", "container");
    mockEnv.setProperty(
        "camunda.document.azure.az1.endpoint", "https://account.blob.core.windows.net");
    mockEnv.setProperty("camunda.document.local.local1.path", "/var/camunda/documents");
    UnifiedConfigurationHelper.setCustomEnvironment(mockEnv);

    try {
      // when
      final var configuration =
          new CamundaDocumentStoreConfigurationLoader(camunda).loadConfiguration();

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
    } finally {
      UnifiedConfigurationHelper.setCustomEnvironment(null);
    }
  }

  @Test
  void shouldLoadOnlyLegacyStoreDefinitions() {
    // given
    final var mockEnv = new MockEnvironment();
    mockEnv.setProperty("DOCUMENT_DEFAULT_STORE_ID", "GCP");
    mockEnv.setProperty(
        "DOCUMENT_STORE_GCP_CLASS", "io.camunda.document.store.gcp.GcpDocumentStoreProvider");
    mockEnv.setProperty("DOCUMENT_STORE_GCP_BUCKET", "legacy-bucket");
    UnifiedConfigurationHelper.setCustomEnvironment(mockEnv);

    System.setProperty("DOCUMENT_DEFAULT_STORE_ID", "GCP");
    System.setProperty(
        "DOCUMENT_STORE_GCP_CLASS", "io.camunda.document.store.gcp.GcpDocumentStoreProvider");
    System.setProperty("DOCUMENT_STORE_GCP_BUCKET", "legacy-bucket");

    try {
      // when
      final var configuration =
          new CamundaDocumentStoreConfigurationLoader(new Camunda()).loadConfiguration();

      // then
      assertThat(configuration.defaultDocumentStoreId()).isEqualTo("gcp");
      assertThat(configuration.threadPoolSize()).isNull();
      assertThat(configuration.documentStores())
          .extracting(DocumentStoreConfigurationRecord::id)
          .containsExactly("gcp");
      assertThat(store("gcp", configuration.documentStores()).properties())
          .containsEntry("BUCKET", "legacy-bucket");
    } finally {
      UnifiedConfigurationHelper.setCustomEnvironment(null);
      System.clearProperty("DOCUMENT_DEFAULT_STORE_ID");
      System.clearProperty("DOCUMENT_STORE_GCP_CLASS");
      System.clearProperty("DOCUMENT_STORE_GCP_BUCKET");
    }
  }

  @Test
  void shouldOmitNullUnifiedFieldsFromStoreProperties() {
    // given
    final Camunda camunda = new Camunda();
    final Document.AwsStore awsStore = new Document.AwsStore();
    awsStore.setBucketName("my-bucket");
    camunda.getDocument().getAws().put("s3", awsStore);

    final var mockEnv = new MockEnvironment();
    mockEnv.setProperty("camunda.document.aws.s3.bucket-name", "my-bucket");
    UnifiedConfigurationHelper.setCustomEnvironment(mockEnv);

    try {
      // when
      final var configuration =
          new CamundaDocumentStoreConfigurationLoader(camunda).loadConfiguration();

      // then
      assertThat(store("s3", configuration.documentStores()).properties())
          .containsOnlyKeys("BUCKET")
          .containsEntry("BUCKET", "my-bucket");
    } finally {
      UnifiedConfigurationHelper.setCustomEnvironment(null);
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

    final var mockEnv = new MockEnvironment();
    mockEnv.setProperty("camunda.document.default-store-id", "aws1");
    mockEnv.setProperty("camunda.document.thread-pool-size", "12");
    mockEnv.setProperty("camunda.document.aws.aws1.bucket-name", "new-bucket");
    mockEnv.setProperty("DOCUMENT_DEFAULT_STORE_ID", "LEGACY");
    mockEnv.setProperty("DOCUMENT_THREAD_POOL_SIZE", "5");
    mockEnv.setProperty(
        "DOCUMENT_STORE_AWS1_CLASS", "io.camunda.document.store.aws.AwsDocumentStoreProvider");
    mockEnv.setProperty("DOCUMENT_STORE_AWS1_BUCKET", "legacy-bucket");
    UnifiedConfigurationHelper.setCustomEnvironment(mockEnv);

    System.setProperty("DOCUMENT_DEFAULT_STORE_ID", "LEGACY");
    System.setProperty("DOCUMENT_THREAD_POOL_SIZE", "5");
    System.setProperty(
        "DOCUMENT_STORE_AWS1_CLASS", "io.camunda.document.store.aws.AwsDocumentStoreProvider");
    System.setProperty("DOCUMENT_STORE_AWS1_BUCKET", "legacy-bucket");

    try {
      // when
      final var configuration =
          new CamundaDocumentStoreConfigurationLoader(camunda).loadConfiguration();

      // then
      assertThat(configuration.defaultDocumentStoreId()).isEqualTo("aws1");
      assertThat(configuration.threadPoolSize()).isEqualTo(12);
      assertThat(store("aws1", configuration.documentStores()).providerClass())
          .isEqualTo(AwsDocumentStoreProvider.class);
      assertThat(store("aws1", configuration.documentStores()).properties())
          .containsEntry("BUCKET", "new-bucket");
    } finally {
      UnifiedConfigurationHelper.setCustomEnvironment(null);
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
