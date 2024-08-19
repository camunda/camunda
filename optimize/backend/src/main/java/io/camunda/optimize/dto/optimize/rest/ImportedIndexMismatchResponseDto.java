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
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $mismatchingIndices = getMismatchingIndices();
    result = result * PRIME + ($mismatchingIndices == null ? 43 : $mismatchingIndices.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ImportedIndexMismatchResponseDto)) {
      return false;
    }
    final ImportedIndexMismatchResponseDto other = (ImportedIndexMismatchResponseDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$mismatchingIndices = getMismatchingIndices();
    final Object other$mismatchingIndices = other.getMismatchingIndices();
    if (this$mismatchingIndices == null
        ? other$mismatchingIndices != null
        : !this$mismatchingIndices.equals(other$mismatchingIndices)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "ImportedIndexMismatchResponseDto(mismatchingIndices=" + getMismatchingIndices() + ")";
  }
}
