/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.sorting;

import java.util.Optional;
import lombok.Data;

@Data
public class ReportSortingDto {

  public static final String SORT_BY_KEY = "key";
  public static final String SORT_BY_VALUE = "value";
  public static final String SORT_BY_LABEL = "label";

  private String by;
  private SortOrder order;

  public ReportSortingDto(String by, SortOrder order) {
    this.by = by;
    this.order = order;
  }

  public ReportSortingDto() {}

  public Optional<String> getBy() {
    return Optional.ofNullable(by);
  }

  public Optional<SortOrder> getOrder() {
    return Optional.ofNullable(order);
  }
}
