/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.pagination;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class PaginationDto {

  protected Integer limit;
  protected Integer offset;

  public PaginationDto(final Integer limit, final Integer offset) {
    this.limit = limit;
    this.offset = offset;
  }

  public PaginationDto() {}

  public static PaginationDto fromPaginationRequest(
      final PaginationRequestDto paginationRequestDto) {
    final PaginationDto paginationDto = new PaginationDto();
    paginationDto.setLimit(paginationRequestDto.getLimit());
    paginationDto.setOffset(paginationRequestDto.getOffset());
    return paginationDto;
  }

  @JsonIgnore
  public boolean isValid() {
    return limit != null;
  }

  public Integer getLimit() {
    return limit;
  }

  public void setLimit(final Integer limit) {
    this.limit = limit;
  }

  public Integer getOffset() {
    return offset;
  }

  public void setOffset(final Integer offset) {
    this.offset = offset;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof PaginationDto;
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
    return "PaginationDto(limit=" + getLimit() + ", offset=" + getOffset() + ")";
  }
}
