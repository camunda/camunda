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
    loader.addEnvironmentVariableOverride("DOCUMENT_DEFAULT_STORE_ID", "MYGCP");
    loader.addEnvironmentVariableOverride(
        "DOCUMENT_STORE_MYGCP_CLASS", "io.camunda.document.store.gcp.GcpDocumentStoreProvider");
    loader.addEnvironmentVariableOverride("DOCUMENT_STORE_MYGCP_BUCKET", "my-bucket");

    // when
    final var configuration = loader.loadConfiguration();

    // then
    assertThat(configuration.defaultDocumentStoreId()).isEqualTo("mygcp");
    assertThat(configuration.documentStores()).hasSize(1);
    final var store = configuration.documentStores().get(0);
    assertThat(store.id()).isEqualTo("mygcp");
    assertThat(store.providerClass()).isEqualTo(GcpDocumentStoreProvider.class);
    assertThat(store.properties()).containsEntry("BUCKET", "my-bucket");
  }

  @Test
  public void shouldLoadConfigurationWithMultipleStores() {
    // given
    final EnvironmentConfigurationLoader loader = new EnvironmentConfigurationLoader();
    loader.addEnvironmentVariableOverride("DOCUMENT_DEFAULT_STORE_ID", "MYGCP");
    loader.addEnvironmentVariableOverride("DOCUMENT_STORE_MYGCP_BUCKET", "my-bucket");
    loader.addEnvironmentVariableOverride(
        "DOCUMENT_STORE_MYGCP_CLASS", "io.camunda.document.store.gcp.GcpDocumentStoreProvider");
    loader.addEnvironmentVariableOverride(
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
}
