/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.api;

public sealed interface TopologyRequestFailedException {

  final class InvalidRequest extends RuntimeException implements TopologyRequestFailedException {
    public InvalidRequest(final String message) {
      super(message);
    }

    public InvalidRequest(final Throwable cause) {
      super(cause);
    }
  }

  final class OperationNotAllowed extends RuntimeException
      implements TopologyRequestFailedException {
    public OperationNotAllowed(final String message) {
      super(message);
    }
  }

  final class ConcurrentModificationException extends RuntimeException
      implements TopologyRequestFailedException {
    public ConcurrentModificationException(final String message) {
      super(message);
    }
  }

  final class InternalError extends RuntimeException implements TopologyRequestFailedException {
    public InternalError(final Throwable cause) {
      super(cause);
    }

    public InternalError(final String message) {
      super(message);
    }
  }
}
