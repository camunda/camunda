/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.sorting;

import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import jakarta.ws.rs.QueryParam;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SortRequestDto {

  public static final String SORT_BY = "sortBy";
  public static final String SORT_ORDER = "sortOrder";

  @QueryParam(SORT_BY)
  private String sortBy;

  @QueryParam(SORT_ORDER)
  private SortOrder sortOrder;

  public Optional<String> getSortBy() {
    return Optional.ofNullable(sortBy);
  }

  public Optional<SortOrder> getSortOrder() {
    return Optional.ofNullable(sortOrder);
  }
}
