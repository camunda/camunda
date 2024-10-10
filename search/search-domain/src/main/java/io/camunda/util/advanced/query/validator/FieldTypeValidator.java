/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.util.advanced.query.validator;

import io.camunda.util.advanced.query.filter.Operator;

public abstract class FieldTypeValidator {
  public static boolean isNumericOperator(final Operator operator) {
    if (operator == Operator.GTE
        || operator == Operator.GT
        || operator == Operator.LTE
        || operator == Operator.LT
        || operator == Operator.EQ
        || operator == Operator.EXISTS
        || operator == Operator.IN) {
      return true;
    } else {
      throw new IllegalArgumentException("Unsupported operator for Numeric data type: " + operator);
    }
  }

  public static boolean isStringOperator(final Operator operator) {
    if (operator == Operator.LIKE
        || operator == Operator.EQ
        || operator == Operator.EXISTS
        || operator == Operator.IN) {
      return true;
    } else {
      throw new IllegalArgumentException("Unsupported operator for String data type: " + operator);
    }
  }

  public static boolean isDateOperator(final Operator operator) {
    if (operator == Operator.GTE
        || operator == Operator.GT
        || operator == Operator.LTE
        || operator == Operator.LT
        || operator == Operator.EQ
        || operator == Operator.EXISTS) {
      return true;
    } else {
      throw new IllegalArgumentException("Unsupported operator for Date data type: " + operator);
    }
  }

  public static Operator convertToFilterOperator(final String operatorKey) {
    switch (operatorKey) {
      case "$eq":
        return Operator.EQ;
      case "$like":
        return Operator.LIKE;
      case "$gt":
        return Operator.GT;
      case "$gte":
        return Operator.GTE;
      case "$lt":
        return Operator.LT;
      case "$lte":
        return Operator.LTE;
      case "$exists":
        return Operator.EXISTS;
      case "$in":
        return Operator.IN;
      default:
        throw new IllegalArgumentException("Unsupported operator: " + operatorKey);
    }
  }
}
