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
public class ImportedIndexMismatchResponseDto extends ErrorResponseDto {

  private Set<ImportIndexMismatchDto> mismatchingIndices;

  protected ImportedIndexMismatchResponseDto() {
    this(null, null, null, Collections.emptySet());
  }

  public ImportedIndexMismatchResponseDto(
      final String errorCode,
      final String errorMessage,
      final String detailedErrorMessage,
      final Set<ImportIndexMismatchDto> mismatchingIndices) {
    super(errorCode, errorMessage, detailedErrorMessage);
    this.mismatchingIndices = mismatchingIndices;
  }

  public Set<ImportIndexMismatchDto> getMismatchingIndices() {
    return mismatchingIndices;
  }

  public void setMismatchingIndices(final Set<ImportIndexMismatchDto> mismatchingIndices) {
    this.mismatchingIndices = mismatchingIndices;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof ImportedIndexMismatchResponseDto;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "ImportedIndexMismatchResponseDto(mismatchingIndices=" + getMismatchingIndices() + ")";
  }
}
