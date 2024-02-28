/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.exceptions;

import java.util.Set;
import org.camunda.optimize.dto.optimize.rest.DefinitionExceptionItemDto;

public class OptimizeImportDefinitionDoesNotExistException extends OptimizeValidationException {
  public static final String ERROR_CODE = "importDefinitionDoesNotExist";

  private final Set<DefinitionExceptionItemDto> missingDefinitions;

  public OptimizeImportDefinitionDoesNotExistException(
      final String message, final Set<DefinitionExceptionItemDto> missingDefinitions) {
    super(message);
    this.missingDefinitions = missingDefinitions;
  }

  public Set<DefinitionExceptionItemDto> getMissingDefinitions() {
    return missingDefinitions;
  }

  @Override
  public String getErrorCode() {
    return ERROR_CODE;
  }
}
