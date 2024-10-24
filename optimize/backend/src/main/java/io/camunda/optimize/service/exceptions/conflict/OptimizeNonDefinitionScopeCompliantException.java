/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.exceptions.conflict;

import io.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import java.util.Set;

public class OptimizeNonDefinitionScopeCompliantException extends OptimizeConflictException {

  public static final String ERROR_CODE = "nonDefinitionScopeCompliantConflict";

  public OptimizeNonDefinitionScopeCompliantException(
      final Set<ConflictedItemDto> conflictedItems) {
    super(
        "Could not apply action due to conflicts with the collection data source. The report definition is not defined "
            + "in the data source.",
        conflictedItems);
  }

  @Override
  public String getErrorCode() {
    return ERROR_CODE;
  }
}
