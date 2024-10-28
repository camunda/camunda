/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.exceptions.conflict;

import io.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.util.Set;

public class OptimizeConflictException extends OptimizeRuntimeException {

  public static final String ERROR_CODE = "conflict";

  @SuppressWarnings("checkstyle:MutableException")
  private Set<ConflictedItemDto> conflictedItems;

  public OptimizeConflictException(final String message) {
    super(message);
  }

  public OptimizeConflictException(
      final String detailedErrorMessage, final Set<ConflictedItemDto> conflictedItems) {
    super(detailedErrorMessage);
    this.conflictedItems = conflictedItems;
  }

  public OptimizeConflictException(final Set<ConflictedItemDto> conflictedItems) {
    super("Operation cannot be executed as other entities would be affected.");
    this.conflictedItems = conflictedItems;
  }

  public Set<ConflictedItemDto> getConflictedItems() {
    return conflictedItems;
  }

  @Override
  public String getErrorCode() {
    return ERROR_CODE;
  }
}
