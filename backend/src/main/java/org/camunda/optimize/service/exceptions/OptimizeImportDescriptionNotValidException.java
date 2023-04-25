/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.exceptions;

import lombok.Getter;

import java.util.Set;

@SuppressWarnings("squid:MaximumInheritanceDepth")
public class OptimizeImportDescriptionNotValidException extends OptimizeValidationException {

  public static final String ERROR_CODE = "importDescriptionInvalid";

  @Getter
  private final Set<String> invalidEntityIds;

  public OptimizeImportDescriptionNotValidException(Set<String> invalidEntityIds) {
    super(
      "Could not apply action due to invalid descriptions. Descriptions must be null or not greater than 400 characters. " +
        "Invalid entities: " + invalidEntityIds);
    this.invalidEntityIds = invalidEntityIds;
  }

  @Override
  public String getErrorCode() {
    return ERROR_CODE;
  }

}
