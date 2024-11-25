/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.backup;

public sealed class BackupException extends RuntimeException {
  public BackupException(final String message) {
    super(message);
  }

  public BackupException(final String message, final Exception cause) {
    super(message, cause);
  }

  public static final class InvalidRequestException extends BackupException {
    public InvalidRequestException(final String message) {
      super(message);
    }
  }

  public static final class ResourceNotFoundException extends BackupException {
    public ResourceNotFoundException(final String message) {
      super(message);
    }

    public ResourceNotFoundException(final String message, final Exception e) {
      super(message, e);
    }
  }

  public static final class BackupRepositoryConnectionException extends BackupException {
    public BackupRepositoryConnectionException(final String message, final Exception cause) {
      super(message, cause);
    }

    public BackupRepositoryConnectionException(final String message) {
      super(message);
    }
  }

  public static final class GenericBackupException extends BackupException {
    public GenericBackupException(final String message) {
      super(message);
    }

    public GenericBackupException(final String message, final Exception cause) {
      super(message, cause);
    }
  }

  public static final class MissingRepositoryException extends BackupException {
    public MissingRepositoryException(final String message) {
      super(message);
    }
  }
}
