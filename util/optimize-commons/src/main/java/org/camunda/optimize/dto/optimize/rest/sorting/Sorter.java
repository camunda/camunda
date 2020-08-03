/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest.sorting;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;

import javax.ws.rs.QueryParam;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public abstract class Sorter<T> {

  public static final String SORT_BY = "sortBy";
  public static final String SORT_ORDER = "sortOrder";

  @QueryParam(SORT_BY)
  protected String sortBy;
  @QueryParam(SORT_ORDER)
  protected SortOrder sortOrder;

  public abstract List<T> applySort(List<T> toSort);

}
