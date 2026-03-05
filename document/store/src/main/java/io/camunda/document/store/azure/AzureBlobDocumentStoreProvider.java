/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store.azure;

import io.camunda.document.api.DocumentStore;
import io.camunda.document.api.DocumentStoreConfiguration.DocumentStoreConfigurationRecord;
import io.camunda.document.api.DocumentStoreProvider;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

public class AzureBlobDocumentStoreProvider implements DocumentStoreProvider {

  private static final Pattern INVALID_CHARACTERS = Pattern.compile("[\\u0000-\\u001F\\\\]");

  private static final String CONTAINER_PROPERTY = "CONTAINER";
  private static final String CONTAINER_PATH = "CONTAINER_PATH";
  private static final String CONNECTION_STRING = "CONNECTION_STRING";
  private static final String ENDPOINT = "ENDPOINT";

  @Override
  public DocumentStore createDocumentStore(
      final DocumentStoreConfigurationRecord configuration, final ExecutorService executorService) {
    final String containerName =
        Optional.ofNullable(configuration.properties().get(CONTAINER_PROPERTY))
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Failed to configure document store with id '"
                            + configuration.id()
                            + "': missing required property '"
                            + CONTAINER_PROPERTY
                            + "'"));

    final String connectionString = configuration.properties().get(CONNECTION_STRING);
    final String endpoint = configuration.properties().get(ENDPOINT);

    if (connectionString == null && endpoint == null) {
      throw new IllegalArgumentException(
          "Failed to configure document store with id '"
              + configuration.id()
              + "': either '"
              + CONNECTION_STRING
              + "' or '"
              + ENDPOINT
              + "' must be set");
    }

    final String containerPath = getContainerPath(configuration);

    if (connectionString != null) {
      return AzureBlobDocumentStoreFactory.createWithConnectionString(
          connectionString, containerName, containerPath, executorService);
    } else {
      return AzureBlobDocumentStoreFactory.createWithDefaultCredential(
          endpoint, containerName, containerPath, executorService);
    }
  }

  private static String getContainerPath(final DocumentStoreConfigurationRecord configuration) {
    String containerPath =
        Objects.requireNonNullElse(configuration.properties().get(CONTAINER_PATH), "");

    if (INVALID_CHARACTERS.matcher(containerPath).find()) {
      throw new IllegalArgumentException(
          "Failed to configure document store with id '"
              + configuration.id()
              + "': '"
              + CONTAINER_PATH
              + " is invalid. Must not contain \\ character'");
    }

    if (!containerPath.isEmpty() && !containerPath.endsWith("/")) {
      containerPath = containerPath + "/";
    }

    return containerPath;
  }
}
