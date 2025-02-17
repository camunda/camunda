/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store.localstorage;

import static org.junit.jupiter.api.Assertions.*;

import io.camunda.document.api.DocumentStore;
import io.camunda.document.api.DocumentStoreConfiguration.DocumentStoreConfigurationRecord;
import java.util.HashMap;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class LocalStorageDocumentStoreProviderTest {

  public static final String STORAGE_PATH = "PATH";

  @Test
  public void shouldCreateDocumentStore() {
    // given
    final DocumentStoreConfigurationRecord configuration =
        new DocumentStoreConfigurationRecord(
            "localstorage", LocalStorageDocumentStoreProvider.class, new HashMap<>());
    configuration.properties().put(STORAGE_PATH, "random-path");
    final LocalStorageDocumentStoreProvider provider = new LocalStorageDocumentStoreProvider();

    // when
    final DocumentStore documentStore =
        provider.createDocumentStore(configuration, Executors.newSingleThreadExecutor());

    // then
    assertNotNull(documentStore);
  }
}
