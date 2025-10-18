/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.pagination;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Objects;

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
    paginationDto.setScrollId(paginationRequestDto.getSearchRequestId());
    paginationDto.setScrollTimeout(paginationRequestDto.getPaginationTimeout());
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

  @JsonIgnore
  @Override
  public boolean isValid() {
    return limit != null
        && ((offset != null && scrollTimeout == null && scrollId == null)
            || (offset == null && scrollTimeout != null));
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof PaginationScrollableDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final PaginationScrollableDto that = (PaginationScrollableDto) o;
    return Objects.equals(scrollId, that.scrollId)
        && Objects.equals(scrollTimeout, that.scrollTimeout);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), scrollId, scrollTimeout);
  }

  @Override
  public String toString() {
    return "PaginationScrollableDto(scrollId="
        + getScrollId()
        + ", scrollTimeout="
        + getScrollTimeout()
        + ")";
  }
}
