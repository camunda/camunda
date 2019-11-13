/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.exceptions.conflict;

import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;

import java.util.Set;

public class OptimizeReportConflictException extends OptimizeConflictException {
  public OptimizeReportConflictException(final String message) {
    super(message);
  }

  public OptimizeReportConflictException(final Set<ConflictedItemDto> conflictedItems) {
    super(conflictedItems);
  }

  @Override
  public String getErrorCode() {
    return "reportConflict";
  }
}
