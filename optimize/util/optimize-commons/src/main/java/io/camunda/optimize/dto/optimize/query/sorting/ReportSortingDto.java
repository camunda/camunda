/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.sorting;

import java.util.Objects;
import java.util.Optional;

public class ReportSortingDto {

  public static final String SORT_BY_KEY = "key";
  public static final String SORT_BY_VALUE = "value";
  public static final String SORT_BY_LABEL = "label";

  private String by;
  private SortOrder order;

  public ReportSortingDto(final String by, final SortOrder order) {
    this.by = by;
    this.order = order;
  }

  public ReportSortingDto() {}

  public Optional<String> getBy() {
    return Optional.ofNullable(by);
  }

  public void setBy(final String by) {
    this.by = by;
  }

  public Optional<SortOrder> getOrder() {
    return Optional.ofNullable(order);
  }

  public void setOrder(final SortOrder order) {
    this.order = order;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ReportSortingDto;
  }

  @Override
  public int hashCode() {
    return Objects.hash(by, order);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ReportSortingDto that = (ReportSortingDto) o;
    return Objects.equals(by, that.by) && Objects.equals(order, that.order);
  }

  @Override
  public String toString() {
    return "ReportSortingDto(by=" + getBy() + ", order=" + getOrder() + ")";
  }
}
