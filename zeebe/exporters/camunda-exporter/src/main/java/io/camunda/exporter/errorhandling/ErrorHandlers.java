/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.errorhandling;

import io.camunda.exporter.exceptions.PersistenceException;

public enum ErrorHandlers implements ErrorHandler {
  /** Ignore the error if the document does not exist, otherwise, throw. */
  IGNORE_DOCUMENT_DOES_NOT_EXIST {
    @Override
    public void handle(final Error error) {
      if (error.status() == 404 || error.type().equals("document_missing_exception")) {
        return;
      }
      throw new PersistenceException(error.message());
    }
  },
  /** Always throw when an error occurs */
  THROWING {
    @Override
    public void handle(final Error error) {
      throw new PersistenceException(error.message());
    }
  }
}
