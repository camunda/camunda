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
import org.jspecify.annotations.Nullable;

public record DbQueryPage(
    Integer size,
    @Nullable Integer from,
    @Nullable Integer maxTotalHits,
    @Nullable List<KeySetPagination> keySetPagination) {

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
      private @Nullable SortOrder order;
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
        // NULL ordering is standardized across all supported databases (see Commons.xml orderBy):
        // ASC sorts NULLs FIRST, DESC sorts NULLs LAST. The strict comparator for a NULL cursor
        // value therefore depends on the direction: for the boundary where NULLs are on the far
        // side of the traversal there is no row beyond them, which must yield NO_MATCH rather than
        // IS_NULL (which would match every NULL row and stall the cursor).
        final Operator operator;
        if (isSearchAfter) {
          operator =
              order == SortOrder.ASC
                  // ASC NULLs FIRST: rows after a NULL cursor are the non-NULL rows
                  ? fieldValue == null ? Operator.IS_NOT_NULL : Operator.GREATER
                  // DESC NULLs LAST: nothing sorts after a NULL cursor
                  : fieldValue == null ? Operator.NO_MATCH : Operator.LOWER;
        } else {
          // searchBefore: traverse the result set backwards
          operator =
              order == SortOrder.ASC
                  // ASC NULLs FIRST: nothing sorts before a NULL cursor
                  ? fieldValue == null ? Operator.NO_MATCH : Operator.LOWER
                  // DESC NULLs LAST: rows before a NULL cursor are the non-NULL rows
                  : fieldValue == null ? Operator.IS_NOT_NULL : Operator.GREATER;
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
    EQUALS("="),
    // marks an OR-group that can never match (e.g. nothing sorts beyond a boundary NULL); such
    // groups are dropped before rendering SQL and never reach the mapper
    NO_MATCH("");

    private final String symbol;

    Operator(final String symbol) {
      this.symbol = symbol;
    }
  }
}
