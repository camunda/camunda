/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.sorting;

import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import java.util.List;
import java.util.Optional;

/**
 * The Sorter and its subclasses are responsible for applying sorting after data has been fetched
 * from Elasticsearch
 */
public abstract class Sorter<T> {

  SortRequestDto sortRequestDto;

  public Sorter() {}

  public Optional<String> getSortBy() {
    return sortRequestDto.getSortBy();
  }

  public void setSortBy(final String sortBy) {
    sortRequestDto.setSortBy(sortBy);
  }

  public Optional<SortOrder> getSortOrder() {
    return sortRequestDto.getSortOrder();
  }

  public void setSortOrder(final SortOrder sortOrder) {
    sortRequestDto.setSortOrder(sortOrder);
  }

  public abstract List<T> applySort(List<T> toSort);

  @Override
  public String toString() {
    return "Sorter(sortRequestDto=" + sortRequestDto + ")";
  }

  public SortRequestDto getSortRequestDto() {
    return sortRequestDto;
  }
}
