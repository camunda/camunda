/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.exceptions;

import java.util.Set;

@SuppressWarnings("squid:MaximumInheritanceDepth")
public class OptimizeImportNameNotValidException extends OptimizeValidationException {

  public static final String ERROR_CODE = "importNameInvalid";

  private final Set<String> invalidEntityIds;

  public OptimizeImportNameNotValidException(final Set<String> invalidEntityIds) {
    super(
        "Could not apply action due to invalid names. Names must be null or not exceed the configured maximum "
            + "length. Invalid entities: "
            + invalidEntityIds);
    this.invalidEntityIds = invalidEntityIds;
  }

  @Override
  public String getErrorCode() {
    return ERROR_CODE;
  }

  public Set<String> getInvalidEntityIds() {
    return invalidEntityIds;
  }
}
