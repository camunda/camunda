/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import io.camunda.document.api.DocumentStore;
import io.camunda.document.api.DocumentStoreConfiguration;
import io.camunda.document.api.DocumentStoreConfiguration.DocumentStoreConfigurationRecord;
import io.camunda.document.api.DocumentStoreProvider;
import io.camunda.document.store.inmemory.InMemoryDocumentStore;
import io.camunda.document.store.inmemory.InMemoryDocumentStoreProvider;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.Test;

public class SimpleDocumentStoreRegistryTest {

  @Test
  public void shouldRegisterDocumentStore() {
    // given
    final DocumentStoreConfiguration.DocumentStoreConfigurationRecord configurationRecord =
        new DocumentStoreConfiguration.DocumentStoreConfigurationRecord(
            "custom-in-memory", InMemoryDocumentStoreProvider.class, Map.of());
    final DocumentStoreConfiguration configuration =
        new DocumentStoreConfiguration("custom-in-memory", null, List.of(configurationRecord));

    // when
    final var registry = new SimpleDocumentStoreRegistry(() -> configuration);

    // then
    final var storeRecord = registry.getDocumentStore("custom-in-memory");
    assertThat(storeRecord.instance()).isInstanceOf(InMemoryDocumentStore.class);
    assertThat(storeRecord.storeId()).isEqualTo("custom-in-memory");
    assertThat(registry.getDefaultDocumentStore()).isEqualTo(storeRecord);
    assertThat(registry.getConfiguration()).isEqualTo(configuration);
  }

  @Test
  public void shouldThrowExceptionWhenDocumentStoreNotFound() {
    // given
    final DocumentStoreConfiguration.DocumentStoreConfigurationRecord configurationRecord =
        new DocumentStoreConfiguration.DocumentStoreConfigurationRecord(
            "custom-in-memory", InMemoryDocumentStoreProvider.class, Map.of());
    final DocumentStoreConfiguration configuration =
        new DocumentStoreConfiguration("custom-in-memory", null, List.of(configurationRecord));

    // when
    final var registry = new SimpleDocumentStoreRegistry(() -> configuration);

    // then
    assertThatThrownBy(() -> registry.getDocumentStore("custom-in-memory-1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("No such document store: custom-in-memory-1");
  }

  @Test
  public void shouldThrowExceptionWhenDefaultDocumentStoreNotFound() {
    // given
    final DocumentStoreConfiguration.DocumentStoreConfigurationRecord configurationRecord =
        new DocumentStoreConfiguration.DocumentStoreConfigurationRecord(
            "custom-in-memory", InMemoryDocumentStoreProvider.class, Map.of());
    final DocumentStoreConfiguration configuration =
        new DocumentStoreConfiguration("custom-in-memory", null, List.of(configurationRecord));

    // when
    final var registry = new SimpleDocumentStoreRegistry(() -> configuration);

    // then
    assertThatThrownBy(() -> registry.getDocumentStore("custom-in-memory-1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("No such document store: custom-in-memory-1");
  }

  @Test
  public void shouldThrowExceptionWhenDefaultDocumentStoreIdNotConfigured() {
    // given
    final DocumentStoreConfiguration.DocumentStoreConfigurationRecord configurationRecord =
        new DocumentStoreConfiguration.DocumentStoreConfigurationRecord(
            "custom-in-memory", InMemoryDocumentStoreProvider.class, Map.of());
    final DocumentStoreConfiguration configuration =
        new DocumentStoreConfiguration(null, null, List.of(configurationRecord));

    // when
    // then
    assertThatThrownBy(() -> new SimpleDocumentStoreRegistry(() -> configuration))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("No default document store ID configured.");
  }

  @Test
  public void shouldUseDefaultExecutorWhenSizeNotSpecified() {
    // given
    final DocumentStoreConfiguration.DocumentStoreConfigurationRecord configurationRecord =
        new DocumentStoreConfiguration.DocumentStoreConfigurationRecord(
            "custom-in-memory", TestDocumentStoreProvider.class, Map.of());
    final DocumentStoreConfiguration configuration =
        new DocumentStoreConfiguration("custom-in-memory", null, List.of(configurationRecord));

    // when
    final var registry = new SimpleDocumentStoreRegistry(() -> configuration);
    final var store = registry.getDocumentStore("custom-in-memory").instance();

    // then
    assertThat(store).isInstanceOf(TestDocumentStoreProvider.DummyDocumentStore.class);
    final var executor = ((TestDocumentStoreProvider.DummyDocumentStore) store).executorService();
    assertThat(executor).isInstanceOf(ThreadPoolExecutor.class);
    assertThat(((ThreadPoolExecutor) executor).getMaximumPoolSize()).isEqualTo(5);
  }

  @Test
  public void shouldUseSpecifiedExecutorSize() {
    // given
    final DocumentStoreConfiguration.DocumentStoreConfigurationRecord configurationRecord =
        new DocumentStoreConfiguration.DocumentStoreConfigurationRecord(
            "custom-in-memory", TestDocumentStoreProvider.class, Map.of());
    final DocumentStoreConfiguration configuration =
        new DocumentStoreConfiguration("custom-in-memory", 10, List.of(configurationRecord));

    // when
    final var registry = new SimpleDocumentStoreRegistry(() -> configuration);
    final var store = registry.getDocumentStore("custom-in-memory").instance();

    // then
    assertThat(store).isInstanceOf(TestDocumentStoreProvider.DummyDocumentStore.class);
    final var executor = ((TestDocumentStoreProvider.DummyDocumentStore) store).executorService();
    assertThat(executor).isInstanceOf(ThreadPoolExecutor.class);
    assertThat(((ThreadPoolExecutor) executor).getMaximumPoolSize()).isEqualTo(10);
  }

  @Test
  public void shouldNotExposeCredentialsInConfigurationErrorMessage() {
    // given
    final DocumentStoreConfigurationRecord configurationRecord =
        new DocumentStoreConfigurationRecord(
            "azure-store",
            UnregisteredDocumentStoreProvider.class,
            Map.of("CONTAINER", "documents", "CONNECTION_STRING", "AccountKey=super-secret-key"));
    final DocumentStoreConfiguration configuration =
        new DocumentStoreConfiguration("azure-store", null, List.of(configurationRecord));

    // when
    // then
    assertThatThrownBy(() -> new SimpleDocumentStoreRegistry(() -> configuration))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("azure-store")
        .hasMessageContaining("documents")
        .hasMessageContaining("<redacted>")
        .hasMessageNotContaining("super-secret-key");
  }

  @Test
  public void shouldNotExposeCredentialsUnderAnUnanticipatedPropertyName() {
    // given — the legacy DOCUMENT_STORE_<ID>_<PROPERTY> bridge forwards whatever property name it
    // finds, so a store can carry a credential under a key no allowlist was written for
    final DocumentStoreConfigurationRecord configurationRecord =
        new DocumentStoreConfigurationRecord(
            "azure-store",
            UnregisteredDocumentStoreProvider.class,
            Map.of("CONTAINER", "documents", "SAS_TOKEN", "sv=2024-01-01&sig=super-secret-sig"));
    final DocumentStoreConfiguration configuration =
        new DocumentStoreConfiguration("azure-store", null, List.of(configurationRecord));

    // when
    // then
    assertThatThrownBy(() -> new SimpleDocumentStoreRegistry(() -> configuration))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("documents")
        .hasMessageContaining("<redacted>")
        .hasMessageNotContaining("super-secret-sig");
  }

  @Test
  public void shouldNotExposeTheAwsAccessKey() {
    // given — the access key names the IAM principal the store acts as, so it is masked alongside
    // the secret it pairs with
    final DocumentStoreConfigurationRecord configurationRecord =
        new DocumentStoreConfigurationRecord(
            "aws-store",
            UnregisteredDocumentStoreProvider.class,
            Map.of("BUCKET", "documents", "ACCESS_KEY", "AKIAEXAMPLEPRINCIPAL"));
    final DocumentStoreConfiguration configuration =
        new DocumentStoreConfiguration("aws-store", null, List.of(configurationRecord));

    // when
    // then
    assertThatThrownBy(() -> new SimpleDocumentStoreRegistry(() -> configuration))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("documents")
        .hasMessageContaining("<redacted>")
        .hasMessageNotContaining("AKIAEXAMPLEPRINCIPAL");
  }

  /** Deliberately absent from {@code META-INF/services}, so the registry fails to resolve it. */
  private static final class UnregisteredDocumentStoreProvider implements DocumentStoreProvider {

    @Override
    public DocumentStore createDocumentStore(
        final DocumentStoreConfigurationRecord configuration,
        final ExecutorService executorService) {
      throw new UnsupportedOperationException("not reachable");
    }
  }
}
