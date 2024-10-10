/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query.filter;

public enum Operator {
  GTE("gte"),
  GT("gt"),
  LTE("lte"),
  LT("lt"),
  LIKE("like"),
  EXISTS("exists"),
  EQ("eq"),
  IN("in");

  private final Object value;

  Operator(final String value) {
    this.value = value;
  }

  public Object getValue() {
    return value;
  }

  public static Operator fromString(final String operator) {
    for (final Operator op : values()) {
      if (op.value.equals(operator)) {
        return op;
      }
    }
    throw new IllegalArgumentException("Unsupported operator: " + operator);
  }
}
