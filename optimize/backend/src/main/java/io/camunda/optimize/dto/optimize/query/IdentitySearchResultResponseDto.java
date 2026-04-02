/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query;

import io.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class IdentitySearchResultResponseDto {

  private List<IdentityWithMetadataResponseDto> result = new ArrayList<>();

  public IdentitySearchResultResponseDto(final List<IdentityWithMetadataResponseDto> result) {
    this.result = result;
  }

  public IdentitySearchResultResponseDto() {}

  public List<IdentityWithMetadataResponseDto> getResult() {
    return result;
  }

  public void setResult(final List<IdentityWithMetadataResponseDto> result) {
    this.result = result;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof IdentitySearchResultResponseDto;
  }

  @Override
  public int hashCode() {
    return Objects.hash(result);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final IdentitySearchResultResponseDto that = (IdentitySearchResultResponseDto) o;
    return Objects.equals(result, that.result);
  }

  @Override
  public String toString() {
    return "IdentitySearchResultResponseDto(result=" + getResult() + ")";
  }
}
