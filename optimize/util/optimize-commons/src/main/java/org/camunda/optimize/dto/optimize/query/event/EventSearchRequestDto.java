/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.event;

import com.google.common.collect.ImmutableList;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.QueryParam;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationRequestDto;
import org.camunda.optimize.dto.optimize.rest.sorting.SortRequestDto;

@Data
@AllArgsConstructor
@NoArgsConstructor
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

  @BeanParam @Valid @NotNull private SortRequestDto sortRequestDto;
  @BeanParam @Valid @NotNull private PaginationRequestDto paginationRequestDto;

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
}
