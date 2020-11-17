/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class Page<T> {
  private Integer offset;
  private Integer limit;
  private Long total;
  private String sortBy;
  private SortOrder sortOrder;
  private List<T> results;
}
