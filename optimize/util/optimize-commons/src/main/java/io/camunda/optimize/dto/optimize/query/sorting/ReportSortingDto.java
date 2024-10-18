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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "ReportSortingDto(by=" + getBy() + ", order=" + getOrder() + ")";
  }
}
