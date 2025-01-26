/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.document.store.gcp.GcpDocumentStoreProvider;
import io.camunda.document.store.inmemory.InMemoryDocumentStoreProvider;
import org.junit.jupiter.api.Test;

public class EnvironmentConfigurationLoaderTest {

  @Test
  public void shouldLoadConfiguration() {
    // given
    final EnvironmentConfigurationLoader loader = new EnvironmentConfigurationLoader();
    System.setProperty("DOCUMENT_DEFAULT_STORE_ID", "MYGCP");
    System.setProperty(
        "DOCUMENT_STORE_MYGCP_CLASS", "io.camunda.document.store.gcp.GcpDocumentStoreProvider");
    System.setProperty("DOCUMENT_STORE_MYGCP_BUCKET", "my-bucket");

    // when
    final var configuration = loader.loadConfiguration();

    // then
    assertThat(configuration.defaultDocumentStoreId()).isEqualTo("mygcp");
    assertThat(configuration.documentStores()).hasSize(1);
    final var store = configuration.documentStores().get(0);
    assertThat(store.id()).isEqualTo("mygcp");
    assertThat(store.providerClass()).isEqualTo(GcpDocumentStoreProvider.class);
    assertThat(store.properties()).containsEntry("BUCKET", "my-bucket");

    System.clearProperty("DOCUMENT_DEFAULT_STORE_ID");
    System.clearProperty("DOCUMENT_STORE_MYGCP_CLASS");
    System.clearProperty("DOCUMENT_STORE_MYGCP_BUCKET");
  }

  @Test
  public void shouldLoadConfigurationWithMultipleStores() {
    // given
    final EnvironmentConfigurationLoader loader = new EnvironmentConfigurationLoader();
    System.setProperty("DOCUMENT_DEFAULT_STORE_ID", "MYGCP");
    System.setProperty(
        "DOCUMENT_STORE_MYGCP_CLASS", "io.camunda.document.store.gcp.GcpDocumentStoreProvider");
    System.setProperty("DOCUMENT_STORE_MYGCP_BUCKET", "my-bucket");
    System.setProperty(
        "DOCUMENT_STORE_MYINMEMORY_CLASS",
        "io.camunda.document.store.inmemory.InMemoryDocumentStoreProvider");

    // when
    final var configuration = loader.loadConfiguration();

    // then
    assertThat(configuration.defaultDocumentStoreId()).isEqualTo("mygcp");
    assertThat(configuration.documentStores()).hasSize(2);
    final var gcpStore =
        configuration.documentStores().stream()
            .filter(s -> s.id().equals("mygcp"))
            .findFirst()
            .get();
    assertThat(gcpStore.providerClass()).isEqualTo(GcpDocumentStoreProvider.class);
    assertThat(gcpStore.properties()).containsEntry("BUCKET", "my-bucket");
    final var inMemoryStore =
        configuration.documentStores().stream()
            .filter(s -> s.id().equals("myinmemory"))
            .findFirst()
            .get();
    assertThat(inMemoryStore.providerClass()).isEqualTo(InMemoryDocumentStoreProvider.class);
    assertThat(inMemoryStore.properties()).isEmpty();

    System.clearProperty("DOCUMENT_DEFAULT_STORE_ID");
    System.clearProperty("DOCUMENT_STORE_MYGCP_CLASS");
    System.clearProperty("DOCUMENT_STORE_MYGCP_BUCKET");
    System.clearProperty("DOCUMENT_STORE_MYINMEMORY_CLASS");
  }

  @Test
  public void shouldLoadWhenNoStores() {
    // given
    final EnvironmentConfigurationLoader loader = new EnvironmentConfigurationLoader();

    // when
    final var configuration = loader.loadConfiguration();

    // then
    assertThat(configuration.defaultDocumentStoreId()).isNull();
    assertThat(configuration.documentStores()).isEmpty();
  }

  @Test
  public void shouldLoadThreadPoolSize() {
    // given
    final EnvironmentConfigurationLoader loader = new EnvironmentConfigurationLoader();
    System.setProperty("DOCUMENT_THREAD_POOL_SIZE", "10");

    // when
    final var configuration = loader.loadConfiguration();

    // then
    assertThat(configuration.threadPoolSize()).isEqualTo(10);

    System.clearProperty("DOCUMENT_THREAD_POOL_SIZE");
  }
}
