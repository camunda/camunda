/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.api;

public sealed interface DocumentOperationResponse<T> {

  record Success<T>(T result) implements DocumentOperationResponse<T> {}

  record Failure<T>(String message, DocumentErrorCode errorCode, Throwable cause)
      implements DocumentOperationResponse<T> {

    public Failure(final String message, final DocumentErrorCode errorCode) {
      this(message, errorCode, null);
    }

    public Failure(final DocumentErrorCode errorCode, final Throwable cause) {
      this(null, errorCode, cause);
    }

    public Failure(final DocumentErrorCode errorCode) {
      this(null, errorCode, null);
    }

    public Failure(final String message) {
      this(message, null, null);
    }

    public Failure(final Throwable cause) {
      this(null, null, cause);
    }
  }

  enum DocumentErrorCode {
    DOCUMENT_NOT_FOUND,
    DOCUMENT_ALREADY_EXISTS,
    INVALID_INPUT,
    OPERATION_NOT_SUPPORTED,
    UNKNOWN_ERROR
  }
}
