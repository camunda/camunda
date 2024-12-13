/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.pagination;

import static io.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class PaginationRequestDto {

  public static final String LIMIT_PARAM = "limit";
  public static final String OFFSET_PARAM = "offset";

  @Min(0)
  @Max(MAX_RESPONSE_SIZE_LIMIT)
  protected Integer limit;

  @Min(0)
  protected Integer offset;

  public PaginationRequestDto(
      @Min(0) @Max(MAX_RESPONSE_SIZE_LIMIT) final Integer limit, @Min(0) final Integer offset) {
    this.limit = limit;
    this.offset = offset;
  }

  public PaginationRequestDto() {}

  public @Min(0) @Max(MAX_RESPONSE_SIZE_LIMIT) Integer getLimit() {
    return limit;
  }

  public void setLimit(@Min(0) @Max(MAX_RESPONSE_SIZE_LIMIT) final Integer limit) {
    this.limit = limit;
  }

  public @Min(0) Integer getOffset() {
    return offset;
  }

  public void setOffset(@Min(0) final Integer offset) {
    this.offset = offset;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof PaginationRequestDto;
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
    return "PaginationRequestDto(limit=" + getLimit() + ", offset=" + getOffset() + ")";
  }
}
