/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.exception;

public class NoSecondaryStorageException extends CamundaSearchException {

  private static final long serialVersionUID = 1L;

  public NoSecondaryStorageException() {
    super(
        "The search client requires a secondary storage, but none is set",
        Reason.SECONDARY_STORAGE_NOT_SET);
  }

  public NoSecondaryStorageException(final String message) {
    super(message);
  }
}
