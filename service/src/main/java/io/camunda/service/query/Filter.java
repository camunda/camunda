/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.service.query;

import java.util.List;

public class Filter {

  private String field;
  private String operator; // e.g., $eq, $gt, $lt, $in, $or, etc.
  private Object value;
  private List<Filter> orFilters; // For handling $or logic

  // Default constructor
  public Filter() {}

  // Constructor for basic filters
  public Filter(final String field, final String operator, final Object value) {
    this.field = field;
    this.operator = operator;
    this.value = value;
  }

  // Constructor for $or filters
  public Filter(final String operator, final List<Filter> orFilters) {
    this.operator = operator;
    this.orFilters = orFilters;
  }

  // Getters and setters
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

  public Object getValue() {
    return value;
  }

  public void setValue(final Object value) {
    this.value = value;
  }

  public List<Filter> getOrFilters() {
    return orFilters;
  }

  public void setOrFilters(final List<Filter> orFilters) {
    this.orFilters = orFilters;
  }

  @Override
  public String toString() {
    return "Filter{" +
        "field='" + field + '\'' +
        ", operator='" + operator + '\'' +
        ", value=" + value +
        ", orFilters=" + orFilters +
        '}';
  }
}
