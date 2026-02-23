/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.gcs;

public abstract class GcsBackupStoreException extends RuntimeException {
  public GcsBackupStoreException(final String message) {
    super(message);
  }

  public GcsBackupStoreException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public static class ConfigurationException extends GcsBackupStoreException {
    public ConfigurationException(final String message) {
      super(message);
    }

    public ConfigurationException(final String message, final Exception cause) {
      super(message, cause);
    }

    public static final class CouldNotAccessBucketException extends ConfigurationException {

      public CouldNotAccessBucketException(final String bucketName, final Exception cause) {
        super("Bucket %s does not exist".formatted(bucketName), cause);
      }
    }
  }

  public static class UploadException extends GcsBackupStoreException {
    public UploadException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }
}
