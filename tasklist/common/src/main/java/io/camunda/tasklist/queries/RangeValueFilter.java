/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.queries;

import graphql.annotations.annotationTypes.GraphQLConstructor;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import io.swagger.v3.oas.annotations.media.Schema;

public class RangeValueFilter {

  @Schema(description = "Start value range to search from.")
  @GraphQLField
  private Object from;

  @Schema(description = "End value range to search to.")
  @GraphQLField
  private Object to;

  @Schema(description = "Value to compare to.")
  @GraphQLField
  private Object value;

  @Schema(description = "Comparison operator")
  @GraphQLField
  @GraphQLNonNull
  private ComparisonOperator operator;

  @GraphQLConstructor
  public RangeValueFilter(
      final Object from, final Object to, final Object value, final ComparisonOperator operator) {
    this.from = from;
    this.to = to;
    this.value = value;
    this.operator = operator;
  }

  public RangeValueFilter() {}

  public Object getFrom() {
    return from;
  }

  public Object getTo() {
    return to;
  }

  public Object getValue() {
    return value;
  }

  public @GraphQLNonNull ComparisonOperator getOperator() {
    return operator;
  }

  public static class RangeValueFilterBuilder {
    private Object from;
    private Object to;
    private ComparisonOperator operator;
    private Object value;

    public RangeValueFilterBuilder from(final Object from) {
      this.from = from;
      return this;
    }

    public RangeValueFilterBuilder to(final Object to) {
      this.to = to;
      return this;
    }

    public RangeValueFilterBuilder operator(final ComparisonOperator operator) {
      this.operator = operator;
      return this;
    }

    public RangeValueFilterBuilder value(final Object value) {
      this.value = value;
      return this;
    }

    public RangeValueFilter build() {
      return new RangeValueFilter(from, to, value, operator);
    }
  }
}
