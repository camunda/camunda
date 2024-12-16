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

import io.camunda.document.api.DocumentStoreConfiguration;
import io.camunda.document.store.inmemory.InMemoryDocumentStore;
import io.camunda.document.store.inmemory.InMemoryDocumentStoreProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class SimpleDocumentStoreRegistryTest {

  @Test
  public void shouldRegisterDocumentStore() {
    // given
    final DocumentStoreConfiguration.DocumentStoreConfigurationRecord configurationRecord =
        new DocumentStoreConfiguration.DocumentStoreConfigurationRecord(
            "custom-in-memory", InMemoryDocumentStoreProvider.class, Map.of());
    final DocumentStoreConfiguration configuration =
        new DocumentStoreConfiguration("custom-in-memory", List.of(configurationRecord));

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
        new DocumentStoreConfiguration("custom-in-memory", List.of(configurationRecord));

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
        new DocumentStoreConfiguration("custom-in-memory", List.of(configurationRecord));

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
        new DocumentStoreConfiguration(null, List.of(configurationRecord));

    // when
    // then
    assertThatThrownBy(() -> new SimpleDocumentStoreRegistry(() -> configuration))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("No default document store ID configured.");
  }
}
