/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Collections;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConflictResponseDto extends ErrorResponseDto {
  private Set<ConflictedItemDto> conflictedItems;

  protected ConflictResponseDto() {
    this(null, null, null, Collections.emptySet());
  }

  public ConflictResponseDto(final Set<ConflictedItemDto> conflictedItems) {
    this(null, null, null, conflictedItems);
  }

  public ConflictResponseDto(final String errorCode, final String errorMessage, final String detailedErrorMessage) {
    this(errorCode, errorMessage, detailedErrorMessage, Collections.emptySet());
  }

  public ConflictResponseDto(final String errorCode, final String errorMessage, final String detailedErrorMessage,
                             final Set<ConflictedItemDto> conflictedItems) {
    super(errorCode, errorMessage, detailedErrorMessage);
    this.conflictedItems = conflictedItems;
  }
}
