/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.exceptions.conflict;

import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;

import java.util.Set;

public class OptimizeScopeComplianceException extends OptimizeConflictException {

  public OptimizeScopeComplianceException(Set<ConflictedItemDto> conflictedItems) {
    super(
      "Operation cannot be executed as entity is non-compliant with the collections data source.",
      conflictedItems
    );
  }

  @Override
  public String getErrorCode() {
    return "nonCompliantConflict";
  }
}
