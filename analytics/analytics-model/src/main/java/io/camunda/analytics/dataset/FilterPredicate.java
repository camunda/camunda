/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset;

import java.util.Objects;

/**
 * A WHERE predicate of a {@link DatasetDeclaration}: keep only facts whose {@code field} relates to
 * {@code value} by {@code operator}. Equality is the first operator (the deck's {@code var.name =
 * foo}); more can be added without changing the shape. The value is carried as a string and coerced
 * to the field's type when applied.
 */
public record FilterPredicate(String field, Operator operator, String value) {

  public enum Operator {
    EQUALS,
    NOT_EQUALS
  }

  public FilterPredicate {
    Objects.requireNonNull(field, "field");
    Objects.requireNonNull(operator, "operator");
    Objects.requireNonNull(value, "value");
    if (field.isBlank()) {
      throw new IllegalArgumentException("filter field must not be blank");
    }
  }

  public static FilterPredicate equals(final String field, final String value) {
    return new FilterPredicate(field, Operator.EQUALS, value);
  }

  public static FilterPredicate notEquals(final String field, final String value) {
    return new FilterPredicate(field, Operator.NOT_EQUALS, value);
  }
}
