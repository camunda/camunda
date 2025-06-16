/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots;

public class SnapshotException extends RuntimeException {

  public SnapshotException(final String message) {
    super(message);
  }

  public SnapshotException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public static class SnapshotCopyForBootstrapException extends SnapshotException {

    public SnapshotCopyForBootstrapException(final String message) {
      super(message);
    }

    public SnapshotCopyForBootstrapException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }

  public static class SnapshotAlreadyExistsException extends SnapshotException {
    public SnapshotAlreadyExistsException(final String message) {
      super(message);
    }
  }

  public static class StateClosedException extends SnapshotException {
    public StateClosedException(final String message) {
      super(message);
    }
  }

  public static class SnapshotNotFoundException extends SnapshotException {
    public SnapshotNotFoundException(final String message) {
      super(message);
    }
  }

  public static class CorruptedSnapshotException extends SnapshotException {
    public CorruptedSnapshotException(final String message) {
      super(message);
    }
  }
}
