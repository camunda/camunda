/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class NoSecondaryStorageException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public NoSecondaryStorageException() {
    super("This endpoint requires a secondary storage and none is set.");
  }

  public NoSecondaryStorageException(final String message) {
    super(message);
  }
}
