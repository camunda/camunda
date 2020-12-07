/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest.sorting;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;

import javax.ws.rs.BeanParam;
import java.util.List;
import java.util.Optional;

/**
 * The Sorter and its subclasses are responsible for applying sorting after data has been fetched from Elasticsearch
 */
@NoArgsConstructor
@ToString
public abstract class Sorter<T> {

  @Getter
  @BeanParam
  SortRequestDto sortRequestDto;

  public Optional<String> getSortBy() {
    return sortRequestDto.getSortBy();
  }

  public Optional<SortOrder> getSortOrder() {
    return sortRequestDto.getSortOrder();
  }

  public void setSortBy(final String sortBy) {
    sortRequestDto.setSortBy(sortBy);
  }

  public void setSortOrder(final SortOrder sortOrder) {
    sortRequestDto.setSortOrder(sortOrder);
  }

  public abstract List<T> applySort(List<T> toSort);

}
