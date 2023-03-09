/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.gcs;

import com.google.api.services.storage.StorageScopes;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.NoCredentials;
import io.camunda.zeebe.backup.gcs.GcsConnectionConfig.Authentication.Auto;
import java.io.IOException;
import java.util.Objects;

record GcsConnectionConfig(String host, Authentication auth) {

  GcsConnectionConfig(String host, Authentication auth) {
    this.host = host;
    this.auth = Objects.requireNonNullElseGet(auth, Auto::new);
  }

  sealed interface Authentication {
    Credentials credentials();

    /** Use no authentication, only useful for testing. */
    record None() implements Authentication {
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
        } catch (IOException e) {
          throw new IllegalStateException("Failed to retrieve application default credentials", e);
        }
      }
    }
  }
}
