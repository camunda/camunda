/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event;

import com.google.common.collect.ImmutableList;
import io.camunda.optimize.dto.optimize.query.event.process.EventDto;
import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationRequestDto;
import io.camunda.optimize.dto.optimize.rest.sorting.SortRequestDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.QueryParam;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

public class EventSearchRequestDto {

  public static final int DEFAULT_LIMIT = 20;
  public static final int DEFAULT_OFFSET = 0;

  public static final List<String> sortableFields =
      ImmutableList.of(
          EventDto.Fields.group.toLowerCase(Locale.ENGLISH),
          EventDto.Fields.source.toLowerCase(Locale.ENGLISH),
          EventDto.Fields.eventName.toLowerCase(Locale.ENGLISH),
          EventDto.Fields.traceId.toLowerCase(Locale.ENGLISH),
          EventDto.Fields.timestamp.toLowerCase(Locale.ENGLISH));

  @QueryParam("searchTerm")
  private String searchTerm;

  @BeanParam
  @Valid
  @NotNull
  private SortRequestDto sortRequestDto;
  @BeanParam
  @Valid
  @NotNull
  private PaginationRequestDto paginationRequestDto;

  public EventSearchRequestDto(final String searchTerm,
      @Valid @NotNull final SortRequestDto sortRequestDto,
      @Valid @NotNull final PaginationRequestDto paginationRequestDto) {
    this.searchTerm = searchTerm;
    this.sortRequestDto = sortRequestDto;
    this.paginationRequestDto = paginationRequestDto;
  }

  public EventSearchRequestDto() {
  }

  public void validateRequest() {
    if (StringUtils.isEmpty(searchTerm)) {
      searchTerm = null;
    }
    if (paginationRequestDto.getLimit() == null) {
      paginationRequestDto.setLimit(DEFAULT_LIMIT);
    }
    if (paginationRequestDto.getOffset() == null) {
      paginationRequestDto.setOffset(DEFAULT_OFFSET);
    }
    final Optional<String> sortBy = sortRequestDto.getSortBy();
    final Optional<SortOrder> sortOrder = sortRequestDto.getSortOrder();
    if ((sortBy.isPresent() && sortOrder.isEmpty())
        || (sortBy.isEmpty() && sortOrder.isPresent())) {
      throw new BadRequestException(
          String.format(
              "Cannot supply only one of %s and %s",
              SortRequestDto.SORT_BY, SortRequestDto.SORT_ORDER));
    } else if (sortBy.isPresent()
        && !EventSearchRequestDto.sortableFields.contains(
        sortBy.get().toLowerCase(Locale.ENGLISH))) {
      throw new BadRequestException(String.format("%s is not a sortable field", sortBy.get()));
    }
  }

  public String getSearchTerm() {
    return searchTerm;
  }

  public void setSearchTerm(final String searchTerm) {
    this.searchTerm = searchTerm;
  }

  public @Valid @NotNull SortRequestDto getSortRequestDto() {
    return sortRequestDto;
  }

  public void setSortRequestDto(@Valid @NotNull final SortRequestDto sortRequestDto) {
    this.sortRequestDto = sortRequestDto;
  }

  public @Valid @NotNull PaginationRequestDto getPaginationRequestDto() {
    return paginationRequestDto;
  }

  public void setPaginationRequestDto(
      @Valid @NotNull final PaginationRequestDto paginationRequestDto) {
    this.paginationRequestDto = paginationRequestDto;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EventSearchRequestDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $searchTerm = getSearchTerm();
    result = result * PRIME + ($searchTerm == null ? 43 : $searchTerm.hashCode());
    final Object $sortRequestDto = getSortRequestDto();
    result = result * PRIME + ($sortRequestDto == null ? 43 : $sortRequestDto.hashCode());
    final Object $paginationRequestDto = getPaginationRequestDto();
    result =
        result * PRIME + ($paginationRequestDto == null ? 43 : $paginationRequestDto.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EventSearchRequestDto)) {
      return false;
    }
    final EventSearchRequestDto other = (EventSearchRequestDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$searchTerm = getSearchTerm();
    final Object other$searchTerm = other.getSearchTerm();
    if (this$searchTerm == null ? other$searchTerm != null
        : !this$searchTerm.equals(other$searchTerm)) {
      return false;
    }
    final Object this$sortRequestDto = getSortRequestDto();
    final Object other$sortRequestDto = other.getSortRequestDto();
    if (this$sortRequestDto == null ? other$sortRequestDto != null
        : !this$sortRequestDto.equals(other$sortRequestDto)) {
      return false;
    }
    final Object this$paginationRequestDto = getPaginationRequestDto();
    final Object other$paginationRequestDto = other.getPaginationRequestDto();
    if (this$paginationRequestDto == null ? other$paginationRequestDto != null
        : !this$paginationRequestDto.equals(other$paginationRequestDto)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "EventSearchRequestDto(searchTerm=" + getSearchTerm() + ", sortRequestDto="
        + getSortRequestDto() + ", paginationRequestDto=" + getPaginationRequestDto()
        + ")";
  }
}
