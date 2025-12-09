/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store.localstorage;

import static io.camunda.zeebe.util.VersionUtil.LOG;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.document.api.DocumentStore;
import io.camunda.document.api.DocumentStoreConfiguration.DocumentStoreConfigurationRecord;
import io.camunda.document.api.DocumentStoreProvider;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

public class LocalStorageDocumentStoreProvider implements DocumentStoreProvider {

  private static final String STORAGE_PATH = "PATH";

  @Override
  public DocumentStore createDocumentStore(
      final DocumentStoreConfigurationRecord configuration, final ExecutorService executor) {
    final Path storagePath = getStoragePath(configuration);

    LOG.info("Storage path created at {}", storagePath);

    return new LocalStorageDocumentStore(
        storagePath, new ObjectMapper().registerModule(new JavaTimeModule()), executor);
  }

  private Path getStoragePath(final DocumentStoreConfigurationRecord configuration) {

    final String pathString = configuration.properties().get(STORAGE_PATH);
    if (pathString == null) {
      LOG.warn("Local Storage Document store {} property is not set", STORAGE_PATH);
      return null;
    }

    try {
      return Path.of(pathString);
    } catch (final Exception e) {
      throw new IllegalArgumentException(
          "Failed to configure document store with id '"
              + configuration.id()
              + "': '"
              + pathString
              + " must be a valid path'");
    }
  }
}
