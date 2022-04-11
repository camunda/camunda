/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest.sorting;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;

import javax.ws.rs.QueryParam;
import java.util.Optional;

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
