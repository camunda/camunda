/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.exceptions.conflict;

import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;

import java.util.Set;

public class OptimizeConflictException extends OptimizeRuntimeException {
  private Set<ConflictedItemDto> conflictedItems;

  public OptimizeConflictException(final String message) {
    super(message);
  }

  public OptimizeConflictException(String detailedErrorMessage, Set<ConflictedItemDto> conflictedItems) {
    super(detailedErrorMessage);
    this.conflictedItems = conflictedItems;
  }

  public OptimizeConflictException(Set<ConflictedItemDto> conflictedItems) {
    super("Operation cannot be executed as other entities would be affected.");
    this.conflictedItems = conflictedItems;
  }

  public Set<ConflictedItemDto> getConflictedItems() {
    return conflictedItems;
  }
}
