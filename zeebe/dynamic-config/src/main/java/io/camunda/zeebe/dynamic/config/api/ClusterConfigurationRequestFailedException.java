/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

public sealed interface ClusterConfigurationRequestFailedException {

  final class InvalidRequest extends RuntimeException
      implements ClusterConfigurationRequestFailedException {
    public InvalidRequest(final String message) {
      super(message);
    }

    public InvalidRequest(final Throwable cause) {
      super(cause);
    }
  }

  /**
   * Signals that the request is well-formed but cannot be satisfied given the current state of the
   * system (e.g. no backup covers the requested restore point). Distinct from {@link
   * InvalidRequest}, which signals a malformed request, and {@link InternalError}, which signals an
   * unexpected failure.
   */
  final class InvalidState extends RuntimeException
      implements ClusterConfigurationRequestFailedException {
    public InvalidState(final String message) {
      super(message);
    }

    public InvalidState(final String message, final Throwable cause) {
      super(message, cause);
    }
  }

  /** Signals that the requested resource (e.g. a backup) does not exist. */
  final class NotFound extends RuntimeException
      implements ClusterConfigurationRequestFailedException {
    public NotFound(final String message) {
      super(message);
    }

    public NotFound(final String message, final Throwable cause) {
      super(message, cause);
    }
  }

  final class OperationNotAllowed extends RuntimeException
      implements ClusterConfigurationRequestFailedException {
    public OperationNotAllowed(final String message) {
      super(message);
    }
  }

  final class ConcurrentModificationException extends RuntimeException
      implements ClusterConfigurationRequestFailedException {
    public ConcurrentModificationException(final String message) {
      super(message);
    }
  }

  final class InternalError extends RuntimeException
      implements ClusterConfigurationRequestFailedException {
    public InternalError(final Throwable cause) {
      super(cause);
    }

    public InternalError(final String message) {
      super(message);
    }
  }
}
