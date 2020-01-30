/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.exceptions.conflict;

import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;

import java.util.Set;

public class OptimizeNonDefinitionScopeCompliantException extends OptimizeConflictException {

  public OptimizeNonDefinitionScopeCompliantException(Set<ConflictedItemDto> conflictedItems) {
    super(
      "Could not apply action due to conflicts with the collection data source. The report definition is not defined " +
        "in the data source.",
      conflictedItems
    );
  }

  @Override
  public String getErrorCode() {
    return "nonDefinitionScopeCompliantConflict";
  }
}
