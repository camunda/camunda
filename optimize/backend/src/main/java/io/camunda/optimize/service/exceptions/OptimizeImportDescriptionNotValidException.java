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
public class OptimizeImportDescriptionNotValidException extends OptimizeValidationException {

  public static final String ERROR_CODE = "importDescriptionInvalid";

  private final Set<String> invalidEntityIds;

  public OptimizeImportDescriptionNotValidException(final Set<String> invalidEntityIds) {
    super(
        "Could not apply action due to invalid descriptions. Descriptions must be null or not greater than 400 characters. "
            + "Invalid entities: "
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
