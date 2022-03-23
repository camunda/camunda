/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.exceptions;

import org.camunda.optimize.dto.optimize.rest.ImportIndexMismatchDto;

import java.util.Set;

public class OptimizeImportIncorrectIndexVersionException extends OptimizeValidationException {

  private final Set<ImportIndexMismatchDto> mismatchingIndices;

  public OptimizeImportIncorrectIndexVersionException(final String message,
                                                      final Set<ImportIndexMismatchDto> mismatchingIndices) {
    super(message);
    this.mismatchingIndices = mismatchingIndices;
  }

  public Set<ImportIndexMismatchDto> getMismatchingIndices() {
    return mismatchingIndices;
  }
}
