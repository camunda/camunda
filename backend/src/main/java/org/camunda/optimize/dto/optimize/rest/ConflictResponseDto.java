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
