/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.exceptions.conflict;

import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;

import java.util.Set;

public class OptimizeNonTenantScopeCompliantException extends OptimizeConflictException {

  public static final String ERROR_CODE = "nonTenantScopeCompliantConflict";

  public OptimizeNonTenantScopeCompliantException(Set<ConflictedItemDto> conflictedItems) {
    super(
      "Could not apply action due to conflicts with the collection data source. The definition for the report is " +
        "available in the data source yet at least one tenant defined in the report is not available in the data " +
        "source.",
      conflictedItems
    );
  }

  @Override
  public String getErrorCode() {
    return ERROR_CODE;
  }
}
