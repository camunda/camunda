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

public class PaginationScrollableRequestDto {
  @Min(0)
  @Max(MAX_RESPONSE_SIZE_LIMIT)
  protected Integer limit = 1000;

  protected String searchRequestId;

  @Min(60)
  protected Integer paginationTimeout = 120;

  public PaginationScrollableRequestDto(
      @Min(0) @Max(MAX_RESPONSE_SIZE_LIMIT) final Integer limit,
      final String scrollId,
      @Min(60) final Integer scrollTimeout) {
    this.limit = limit;
    searchRequestId = scrollId;
    paginationTimeout = scrollTimeout;
  }

  public PaginationScrollableRequestDto() {}

  public @Min(0) @Max(MAX_RESPONSE_SIZE_LIMIT) Integer getLimit() {
    return limit;
  }

  public void setLimit(@Min(0) @Max(MAX_RESPONSE_SIZE_LIMIT) final Integer limit) {
    this.limit = limit;
  }

  public String getSearchRequestId() {
    return searchRequestId;
  }

  public void setSearchRequestId(final String searchRequestId) {
    this.searchRequestId = searchRequestId;
  }

  public @Min(60) Integer getPaginationTimeout() {
    return paginationTimeout;
  }

  public void setPaginationTimeout(@Min(60) final Integer paginationTimeout) {
    this.paginationTimeout = paginationTimeout;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof PaginationScrollableRequestDto;
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
    return "PaginationScrollableRequestDto(limit="
        + getLimit()
        + ", searchRequestId="
        + getSearchRequestId()
        + ", paginationTimeout="
        + getPaginationTimeout()
        + ")";
  }
}
