/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest;

import java.util.Set;

public class ConflictResponseDto {
  private Set<ConflictedItemDto> conflictedItems;

  public ConflictResponseDto() {
  }

  public ConflictResponseDto(Set<ConflictedItemDto> conflictedItems) {
    this.conflictedItems = conflictedItems;
  }

  public Set<ConflictedItemDto> getConflictedItems() {
    return conflictedItems;
  }
}
