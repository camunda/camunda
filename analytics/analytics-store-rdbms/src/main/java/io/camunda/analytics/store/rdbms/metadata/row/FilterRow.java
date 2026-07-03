/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms.metadata.row;

/** Row model for {@code ANALYTICS_DATASET_FILTER}. */
public final class FilterRow {

  private long cubeId;
  private int ordinal;
  private String field;
  private String operator;
  private String filterValue;

  public FilterRow() {}

  public FilterRow(
      final long cubeId,
      final int ordinal,
      final String field,
      final String operator,
      final String filterValue) {
    this.cubeId = cubeId;
    this.ordinal = ordinal;
    this.field = field;
    this.operator = operator;
    this.filterValue = filterValue;
  }

  public long getCubeId() {
    return cubeId;
  }

  public void setCubeId(final long cubeId) {
    this.cubeId = cubeId;
  }

  public int getOrdinal() {
    return ordinal;
  }

  public void setOrdinal(final int ordinal) {
    this.ordinal = ordinal;
  }

  public String getField() {
    return field;
  }

  public void setField(final String field) {
    this.field = field;
  }

  public String getOperator() {
    return operator;
  }

  public void setOperator(final String operator) {
    this.operator = operator;
  }

  public String getFilterValue() {
    return filterValue;
  }

  public void setFilterValue(final String filterValue) {
    this.filterValue = filterValue;
  }
}
