/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.exceptions;

import io.camunda.optimize.dto.optimize.rest.DefinitionExceptionItemDto;
import java.util.Set;

public class OptimizeImportForbiddenException extends OptimizeRuntimeException {
  public static final String ERROR_CODE = "importDefinitionForbidden";

  private final Set<DefinitionExceptionItemDto> forbiddenDefinitions;

  public OptimizeImportForbiddenException(
      final String message, final Set<DefinitionExceptionItemDto> forbiddenDefinitions) {
    super(message);
    this.forbiddenDefinitions = forbiddenDefinitions;
  }

  public Set<DefinitionExceptionItemDto> getForbiddenDefinitions() {
    return forbiddenDefinitions;
  }

  @Override
  public String getErrorCode() {
    return ERROR_CODE;
  }
}
