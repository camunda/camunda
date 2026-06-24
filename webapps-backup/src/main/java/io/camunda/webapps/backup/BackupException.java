/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.backup;

import java.util.Collection;

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

  public static final class MissingRepositoryException extends BackupException {
    public MissingRepositoryException(final String message) {
      super(message);
    }
  }

  public static final class IndexNotFoundException extends BackupException {
    public IndexNotFoundException(final Collection<String> missingIndices) {
      super(String.format("Missing indices: %s", missingIndices));
    }
  }
}
