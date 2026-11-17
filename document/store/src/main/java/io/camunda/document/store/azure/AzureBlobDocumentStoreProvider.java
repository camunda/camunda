/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store.azure;

import com.azure.storage.blob.BlobServiceClientBuilder;
import io.camunda.document.api.DocumentStore;
import io.camunda.document.api.DocumentStoreConfiguration.DocumentStoreConfigurationRecord;
import io.camunda.document.api.DocumentStoreProvider;
import io.camunda.document.store.DocumentStorePaths;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

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

  /**
   * The blob endpoint a store built from these two properties addresses. A connection string takes
   * precedence, and {@link #createDocumentStore} never reads the endpoint on that branch, so the
   * account the SDK derives from the string is the answer — a configured endpoint beside it is dead
   * configuration. Only the URL is read, so no credential is returned.
   *
   * <p>The SDK resolves it because the rules are not obvious enough to restate: a path-style {@code
   * BlobEndpoint} keeps its path only when the host is an IP address. A connection string the SDK
   * rejects throws, which callers comparing configuration are expected to handle.
   */
  public static String effectiveEndpoint(
      final @Nullable String connectionString, final @Nullable String endpoint) {
    if (connectionString == null) {
      return Objects.requireNonNullElse(endpoint, "");
    }
    return new BlobServiceClientBuilder()
        .connectionString(connectionString)
        .buildClient()
        .getAccountUrl();
  }

  private static String getContainerPath(final DocumentStoreConfigurationRecord configuration) {
    final String containerPath =
        Objects.requireNonNullElse(configuration.properties().get(CONTAINER_PATH), "");

    if (INVALID_CHARACTERS.matcher(containerPath).find()) {
      throw new IllegalArgumentException(
          "Failed to configure document store with id '"
              + configuration.id()
              + "': '"
              + CONTAINER_PATH
              + " is invalid. Must not contain \\ character'");
    }

    return DocumentStorePaths.keyPrefix(containerPath);
  }
}
