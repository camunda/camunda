/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Collections;
import java.util.Set;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConflictResponseDto extends ErrorResponseDto {
  private Set<ConflictedItemDto> conflictedItems;

  protected ConflictResponseDto() {
    this(null, Collections.emptySet());
  }

  public ConflictResponseDto(final Set<ConflictedItemDto> conflictedItems) {
    this(null, conflictedItems);
  }

  public ConflictResponseDto(final String errorMessage) {
    this(errorMessage, Collections.emptySet());
  }

  public ConflictResponseDto(final String errorMessage, final Set<ConflictedItemDto> conflictedItems) {
    super(errorMessage);
    this.conflictedItems = conflictedItems;
  }
}
