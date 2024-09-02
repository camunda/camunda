/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.pagination;

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

  public static PaginationDto fromPaginationRequest(
      final PaginationRequestDto paginationRequestDto) {
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
