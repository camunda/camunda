/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest.pagination;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PaginationDto {
  protected Integer limit;
  protected Integer offset;

  public static PaginationDto fromPaginationRequest(final PaginationRequestDto paginationRequestDto) {
    final PaginationDto paginationDto = new PaginationDto();
    paginationDto.setLimit(paginationRequestDto.getLimit());
    paginationDto.setOffset(paginationRequestDto.getOffset());
    return paginationDto;
  }
}
