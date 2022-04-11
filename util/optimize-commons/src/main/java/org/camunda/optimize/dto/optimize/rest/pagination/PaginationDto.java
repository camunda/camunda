/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest.pagination;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaginationDto {
  protected Integer limit;
  protected Integer offset;

  public static PaginationDto fromPaginationRequest(final PaginationRequestDto paginationRequestDto) {
    final PaginationDto paginationDto = new PaginationDto();
    paginationDto.setLimit(paginationRequestDto.getLimit());
    paginationDto.setOffset(paginationRequestDto.getOffset());
    return paginationDto;
  }

  @JsonIgnore
  public boolean isValid() {
    return limit != null;
  }
}
