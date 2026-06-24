/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.exceptions;

import io.camunda.optimize.dto.optimize.rest.ImportIndexMismatchDto;
import java.util.Set;

public class OptimizeImportIncorrectIndexVersionException extends OptimizeValidationException {

  public static final String ERROR_CODE = "importDescriptionInvalid";

  private final Set<ImportIndexMismatchDto> mismatchingIndices;

  public OptimizeImportIncorrectIndexVersionException(
      final String message, final Set<ImportIndexMismatchDto> mismatchingIndices) {
    super(message);
    this.mismatchingIndices = mismatchingIndices;
  }

  public Set<ImportIndexMismatchDto> getMismatchingIndices() {
    return mismatchingIndices;
  }
}
