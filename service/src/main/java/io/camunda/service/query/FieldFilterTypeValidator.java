/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query;

import io.camunda.service.query.filter.FilterOperator;

public abstract class FieldFilterTypeValidator {
    public static boolean isNumericOperator(final FilterOperator operator) {
      if(operator == FilterOperator.GTE || operator == FilterOperator.GT ||
          operator == FilterOperator.LTE || operator == FilterOperator.LT || operator == FilterOperator.EQ || operator == FilterOperator.EXISTS) {
        return true;
      } else {
        throw new IllegalArgumentException("Unsupported operator for Numeric data type: " + operator);
      }
    }

    public static boolean isStringOperator(final FilterOperator operator) {
      if(operator == FilterOperator.LIKE || operator == FilterOperator.EQ || operator == FilterOperator.EXISTS || operator == FilterOperator.IN) {
        return true;
      } else {
        throw new IllegalArgumentException("Unsupported operator for String data type: " + operator);
      }
    }
    public static boolean isDateOperator(final FilterOperator operator) {
      if (operator == FilterOperator.GTE || operator == FilterOperator.GT ||
          operator == FilterOperator.LTE || operator == FilterOperator.LT || operator == FilterOperator.EQ || operator == FilterOperator.EXISTS) {
        return true;
      } else {
        throw new IllegalArgumentException("Unsupported operator for Date data type: " + operator);
      }
    }

}
