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
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;

public class PaginationScrollableRequestDto {

  public static final String QUERY_LIMIT_PARAM = "limit";
  public static final String QUERY_SCROLL_ID_PARAM = "searchRequestId";
  public static final String QUERY_SCROLL_TIMEOUT_PARAM = "paginationTimeout";

  @QueryParam(QUERY_LIMIT_PARAM)
  @Min(0)
  @DefaultValue("1000")
  @Max(MAX_RESPONSE_SIZE_LIMIT)
  protected Integer limit;

  @QueryParam(QUERY_SCROLL_ID_PARAM)
  protected String scrollId;

  @QueryParam(QUERY_SCROLL_TIMEOUT_PARAM)
  @Min(60)
  @DefaultValue("120")
  protected Integer scrollTimeout;

  public PaginationScrollableRequestDto(
      @Min(0) @Max(MAX_RESPONSE_SIZE_LIMIT) final Integer limit,
      final String scrollId,
      @Min(60) final Integer scrollTimeout) {
    this.limit = limit;
    this.scrollId = scrollId;
    this.scrollTimeout = scrollTimeout;
  }

  public PaginationScrollableRequestDto() {}

  public @Min(0) @Max(MAX_RESPONSE_SIZE_LIMIT) Integer getLimit() {
    return limit;
  }

  public void setLimit(@Min(0) @Max(MAX_RESPONSE_SIZE_LIMIT) final Integer limit) {
    this.limit = limit;
  }

  public String getScrollId() {
    return scrollId;
  }

  public void setScrollId(final String scrollId) {
    this.scrollId = scrollId;
  }

  public @Min(60) Integer getScrollTimeout() {
    return scrollTimeout;
  }

  public void setScrollTimeout(@Min(60) final Integer scrollTimeout) {
    this.scrollTimeout = scrollTimeout;
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
        + ", scrollId="
        + getScrollId()
        + ", scrollTimeout="
        + getScrollTimeout()
        + ")";
  }
}
