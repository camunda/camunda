/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.sort.SortOrder;
import java.util.List;

public record DbQueryPage(
    Integer size, Integer from, Integer maxTotalHits, List<KeySetPagination> keySetPagination) {

  public record KeySetPagination(List<KeySetPaginationFieldEntry> entries) {}

  public record KeySetPaginationFieldEntry(String fieldName, Operator operator, Object fieldValue) {

    public static Builder of(final String fieldName, final Object fieldValue) {
      return new Builder(fieldName, fieldValue);
    }

    public static KeySetPaginationFieldEntry equality(
        final String fieldName, final Object fieldValue) {
      final var operator = fieldValue == null ? Operator.IS_NULL : Operator.EQUALS;
      return new KeySetPaginationFieldEntry(fieldName, operator, fieldValue);
    }

    public static final class Builder {
      private final String fieldName;
      private final Object fieldValue;
      private SortOrder order;
      private boolean isSearchAfter;

      private Builder(final String fieldName, final Object fieldValue) {
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
      }

      public Builder order(final SortOrder order) {
        this.order = order;
        return this;
      }

      public Builder isSearchAfter(final boolean isSearchAfter) {
        this.isSearchAfter = isSearchAfter;
        return this;
      }

      public KeySetPaginationFieldEntry build() {
        final Operator operator;
        if (isSearchAfter) {
          operator =
              order == SortOrder.ASC
                  ? fieldValue == null ? Operator.IS_NOT_NULL : Operator.GREATER
                  : fieldValue == null ? Operator.IS_NULL : Operator.LOWER;
        } else {
          // searchBefore: mirror of searchAfter with directions reversed
          operator =
              order == SortOrder.ASC
                  ? fieldValue == null ? Operator.IS_NOT_NULL : Operator.LOWER
                  : fieldValue == null ? Operator.IS_NULL : Operator.GREATER;
        }
        return new KeySetPaginationFieldEntry(fieldName, operator, fieldValue);
      }
    }
  }

  public enum Operator {
    IS_NULL("IS NULL"),
    IS_NOT_NULL("IS NOT NULL"),
    GREATER(">"),
    LOWER("<"),
    EQUALS("=");

    private final String symbol;

    Operator(final String symbol) {
      this.symbol = symbol;
    }
  }
}
