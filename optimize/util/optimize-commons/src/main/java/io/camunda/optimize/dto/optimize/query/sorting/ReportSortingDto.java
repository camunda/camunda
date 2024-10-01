/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.sorting;

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
    final int PRIME = 59;
    int result = 1;
    final Object $by = getBy();
    result = result * PRIME + ($by == null ? 43 : $by.hashCode());
    final Object $order = getOrder();
    result = result * PRIME + ($order == null ? 43 : $order.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ReportSortingDto)) {
      return false;
    }
    final ReportSortingDto other = (ReportSortingDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$by = getBy();
    final Object other$by = other.getBy();
    if (this$by == null ? other$by != null : !this$by.equals(other$by)) {
      return false;
    }
    final Object this$order = getOrder();
    final Object other$order = other.getOrder();
    if (this$order == null ? other$order != null : !this$order.equals(other$order)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "ReportSortingDto(by=" + getBy() + ", order=" + getOrder() + ")";
  }
}
