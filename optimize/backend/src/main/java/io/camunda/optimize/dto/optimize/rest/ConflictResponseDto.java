/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Collections;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConflictResponseDto extends ErrorResponseDto {

  private Set<ConflictedItemDto> conflictedItems;

  protected ConflictResponseDto() {
    this(null, null, null, Collections.emptySet());
  }

  public ConflictResponseDto(final Set<ConflictedItemDto> conflictedItems) {
    this(null, null, null, conflictedItems);
  }

  public ConflictResponseDto(
      final String errorCode, final String errorMessage, final String detailedErrorMessage) {
    this(errorCode, errorMessage, detailedErrorMessage, Collections.emptySet());
  }

  public ConflictResponseDto(
      final String errorCode,
      final String errorMessage,
      final String detailedErrorMessage,
      final Set<ConflictedItemDto> conflictedItems) {
    super(errorCode, errorMessage, detailedErrorMessage);
    this.conflictedItems = conflictedItems;
  }

  public Set<ConflictedItemDto> getConflictedItems() {
    return conflictedItems;
  }

  public void setConflictedItems(final Set<ConflictedItemDto> conflictedItems) {
    this.conflictedItems = conflictedItems;
  }

  @Override
  public String toString() {
    return "ConflictResponseDto(conflictedItems=" + getConflictedItems() + ")";
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof ConflictResponseDto;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }
}
