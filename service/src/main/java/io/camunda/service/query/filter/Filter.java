/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query.filter;

public class Filter {
  private String field;
  private Operator operator;
  private Object value;

  public Filter() {}

  public Filter(final String field, final Operator operator, final Object value) {
    this.field = field;
    this.operator = operator;
    this.value = value;
  }

  // Getters and Setters
  public String getField() {
    return field;
  }

  public void setField(final String field) {
    this.field = field;
  }

  public Operator getOperator() {
    return operator;
  }

  public void setOperator(final Operator operator) {
    this.operator = operator;
  }

  public Object getValue() {
    return value;
  }

  public void setValue(final Object value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return "Filter{"
        + "field='"
        + field
        + '\''
        + ", operator="
        + operator
        + ", value="
        + value
        + '}';
  }
}
