/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.gcs;

import com.google.api.services.storage.StorageScopes;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.NoCredentials;
import io.camunda.zeebe.backup.gcs.GcsConnectionConfig.Authentication.Auto;
import java.io.IOException;
import java.util.Objects;

public record GcsConnectionConfig(String host, Authentication auth) {

  public GcsConnectionConfig(final String host, final Authentication auth) {
    this.host = host;
    this.auth = Objects.requireNonNullElseGet(auth, Auto::new);
  }

  public sealed interface Authentication {
    Credentials credentials();

    /** Use no authentication, only useful for testing. */
    record None(String projectId) implements Authentication {
      public None {
        if (projectId == null || projectId.isBlank()) {
          throw new IllegalArgumentException(
              "Project ID must be provided when using no authentication");
        }
      }

      @Override
      public Credentials credentials() {
        return NoCredentials.getInstance();
      }
    }

    /**
     * Use <a
     * href="https://cloud.google.com/docs/authentication/application-default-credentials">Application
     * Default Credentials</a> to automatically discover credentials.
     */
    record Auto() implements Authentication {
      @Override
      public Credentials credentials() {
        try {
          return GoogleCredentials.getApplicationDefault()
              .createScoped(StorageScopes.DEVSTORAGE_READ_WRITE);
        } catch (final IOException e) {
          throw new IllegalStateException("Failed to retrieve application default credentials", e);
        }
      }
    }
  }
}
