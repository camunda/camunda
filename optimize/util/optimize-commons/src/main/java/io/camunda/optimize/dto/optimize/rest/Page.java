/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import java.util.List;
import lombok.Data;

@Data
public class Page<T> {

  private Integer offset;
  private Integer limit;
  private Long total;
  private String sortBy;
  private SortOrder sortOrder;
  private List<T> results;

  public Page(
      Integer offset,
      Integer limit,
      Long total,
      String sortBy,
      SortOrder sortOrder,
      List<T> results) {
    this.offset = offset;
    this.limit = limit;
    this.total = total;
    this.sortBy = sortBy;
    this.sortOrder = sortOrder;
    this.results = results;
  }

  public Page() {}
}
