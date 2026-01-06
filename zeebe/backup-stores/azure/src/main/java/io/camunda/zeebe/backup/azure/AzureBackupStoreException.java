/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.azure;

public abstract class AzureBackupStoreException extends RuntimeException {

  protected AzureBackupStoreException(final String message, final Throwable cause) {
    super(message, cause);
  }

  protected AzureBackupStoreException(final String message) {
    super(message);
  }

  public static final class BlobAlreadyExists extends AzureBackupStoreException {
    public BlobAlreadyExists(final String message, final Throwable cause) {
      super(message, cause);
    }
  }

  public static final class ContainerDoesNotExist extends AzureBackupStoreException {
    public ContainerDoesNotExist(final String message) {
      super(message);
    }
  }

  public static final class IndexWriteException extends AzureBackupStoreException {
    public IndexWriteException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }

  public static final class IndexReadException extends AzureBackupStoreException {
    public IndexReadException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }
}
