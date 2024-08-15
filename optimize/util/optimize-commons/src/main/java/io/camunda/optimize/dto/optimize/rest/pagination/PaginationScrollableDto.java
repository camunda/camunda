/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.pagination;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class PaginationScrollableDto extends PaginationDto {

  protected String scrollId;
  protected Integer scrollTimeout;

  public PaginationScrollableDto(final String scrollId, final Integer scrollTimeout) {
    this.scrollId = scrollId;
    this.scrollTimeout = scrollTimeout;
  }

  public PaginationScrollableDto() {}

  public static PaginationScrollableDto fromPaginationDto(final PaginationDto pagination) {
    final PaginationScrollableDto paginationObject = new PaginationScrollableDto();
    paginationObject.limit = pagination.getLimit();
    paginationObject.offset = pagination.getOffset();
    if (pagination instanceof PaginationScrollableDto) {
      paginationObject.scrollId = ((PaginationScrollableDto) pagination).getScrollId();
      paginationObject.scrollTimeout = ((PaginationScrollableDto) pagination).getScrollTimeout();
    }
    return paginationObject;
  }

  public static PaginationDto fromPaginationRequest(
      final PaginationScrollableRequestDto paginationRequestDto) {
    final PaginationScrollableDto paginationDto = new PaginationScrollableDto();
    paginationDto.setLimit(paginationRequestDto.getLimit());
    paginationDto.setOffset(null);
    paginationDto.setScrollId(paginationRequestDto.getScrollId());
    paginationDto.setScrollTimeout(paginationRequestDto.getScrollTimeout());
    return paginationDto;
  }

  public String getScrollId() {
    return scrollId;
  }

  public void setScrollId(final String scrollId) {
    this.scrollId = scrollId;
  }

  public Integer getScrollTimeout() {
    return scrollTimeout;
  }

  public void setScrollTimeout(final Integer scrollTimeout) {
    this.scrollTimeout = scrollTimeout;
  }

  @Override
  public String toString() {
    return "PaginationScrollableDto(scrollId="
        + getScrollId()
        + ", scrollTimeout="
        + getScrollTimeout()
        + ")";
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof PaginationScrollableDto)) {
      return false;
    }
    final PaginationScrollableDto other = (PaginationScrollableDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$scrollId = getScrollId();
    final Object other$scrollId = other.getScrollId();
    if (this$scrollId == null ? other$scrollId != null : !this$scrollId.equals(other$scrollId)) {
      return false;
    }
    final Object this$scrollTimeout = getScrollTimeout();
    final Object other$scrollTimeout = other.getScrollTimeout();
    if (this$scrollTimeout == null
        ? other$scrollTimeout != null
        : !this$scrollTimeout.equals(other$scrollTimeout)) {
      return false;
    }
    return true;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof PaginationScrollableDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $scrollId = getScrollId();
    result = result * PRIME + ($scrollId == null ? 43 : $scrollId.hashCode());
    final Object $scrollTimeout = getScrollTimeout();
    result = result * PRIME + ($scrollTimeout == null ? 43 : $scrollTimeout.hashCode());
    return result;
  }

  @JsonIgnore
  @Override
  public boolean isValid() {
    return limit != null
        && ((offset != null && scrollTimeout == null && scrollId == null)
            || (offset == null && scrollTimeout != null));
  }
}
