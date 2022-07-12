/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest.pagination;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class PaginationScrollableDto extends PaginationDto {
  protected String scrollId;
  protected Integer scrollTimeout;

  public static PaginationScrollableDto fromPaginationDto(final PaginationDto pagination) {
    PaginationScrollableDto paginationObject = new PaginationScrollableDto();
    paginationObject.limit = pagination.getLimit();
    paginationObject.offset = pagination.getOffset();
    if(pagination instanceof PaginationScrollableDto)
    {
      paginationObject.scrollId = ((PaginationScrollableDto)pagination).getScrollId();
      paginationObject.scrollTimeout = ((PaginationScrollableDto)pagination).getScrollTimeout();
    }
    return paginationObject;
  }

  public static PaginationDto fromPaginationRequest(final PaginationScrollableRequestDto paginationRequestDto) {
    final PaginationScrollableDto paginationDto = new PaginationScrollableDto();
    paginationDto.setLimit(paginationRequestDto.getLimit());
    paginationDto.setOffset(null);
    paginationDto.setScrollId(paginationRequestDto.getScrollId());
    paginationDto.setScrollTimeout(paginationRequestDto.getScrollTimeout());
    return paginationDto;
  }

  @JsonIgnore
  @Override
  public boolean isValid() {
    return limit != null &&
      ((offset != null && scrollTimeout == null && scrollId == null)
      || (offset == null && scrollTimeout != null));
  }
}
