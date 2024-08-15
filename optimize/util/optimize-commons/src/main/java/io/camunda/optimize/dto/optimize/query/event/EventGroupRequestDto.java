/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.QueryParam;
import org.apache.commons.lang3.StringUtils;

public class EventGroupRequestDto {

  @QueryParam("searchTerm")
  private String searchTerm;

  @QueryParam("limit")
  @NotNull
  private int limit;

  public EventGroupRequestDto(final String searchTerm, @NotNull final int limit) {
    this.searchTerm = searchTerm;
    this.limit = limit;
  }

  public EventGroupRequestDto() {
  }

  public void validateRequest() {
    if (StringUtils.isEmpty(searchTerm)) {
      searchTerm = null;
    }
  }

  public String getSearchTerm() {
    return searchTerm;
  }

  public void setSearchTerm(final String searchTerm) {
    this.searchTerm = searchTerm;
  }

  public @NotNull int getLimit() {
    return limit;
  }

  public void setLimit(@NotNull final int limit) {
    this.limit = limit;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EventGroupRequestDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $searchTerm = getSearchTerm();
    result = result * PRIME + ($searchTerm == null ? 43 : $searchTerm.hashCode());
    result = result * PRIME + getLimit();
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EventGroupRequestDto)) {
      return false;
    }
    final EventGroupRequestDto other = (EventGroupRequestDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$searchTerm = getSearchTerm();
    final Object other$searchTerm = other.getSearchTerm();
    if (this$searchTerm == null ? other$searchTerm != null
        : !this$searchTerm.equals(other$searchTerm)) {
      return false;
    }
    if (getLimit() != other.getLimit()) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "EventGroupRequestDto(searchTerm=" + getSearchTerm() + ", limit=" + getLimit()
        + ")";
  }
}
