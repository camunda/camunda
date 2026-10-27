/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.api;

public sealed interface DocumentError {
  record DocumentNotFound(String documentId) implements DocumentError {}

  record DocumentAlreadyExists(String documentId) implements DocumentError {}

  record InvalidInput(String message) implements DocumentError {}

  record StoreDoesNotExist(String storeId) implements DocumentError {}

  record OperationNotSupported(String message) implements DocumentError {}

  record DocumentHashMismatch(String documentId, String providedHash) implements DocumentError {}

  record UnknownDocumentError(String message, Throwable cause) implements DocumentError {
    public UnknownDocumentError(final Throwable cause) {
      this(cause.getMessage(), cause);
    }
  }
}
