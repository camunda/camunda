/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.azure;

import io.camunda.zeebe.backup.azure.manifest.Manifest.StatusCode;

public abstract class AzureBackupStoreException extends RuntimeException {
  protected AzureBackupStoreException(final String message) {
    super(message);
  }

  protected AzureBackupStoreException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public static class InvalidBackupPath extends AzureBackupStoreException {

    protected InvalidBackupPath(final String message, final Throwable cause) {
      super(message, cause);
    }
  }

  public static class UnexpectedManifestState extends AzureBackupStoreException {
    public UnexpectedManifestState(final StatusCode expected, final StatusCode actual) {
      super("Expected manifest in state '%s', but was in '%s'".formatted(expected, actual));
    }
  }
}
