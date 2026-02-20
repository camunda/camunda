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

    public static Operator determineOperator(final SortOrder order, final boolean isSearchAfter) {
      if (isSearchAfter) {
        return order == SortOrder.ASC ? Operator.GREATER : Operator.LOWER;
      }

      return order == SortOrder.ASC ? Operator.LOWER : Operator.GREATER;
    }
  }

  public enum Operator {
    GREATER(">"),
    LOWER("<"),
    EQUALS("=");

    private final String symbol;

    Operator(final String symbol) {
      this.symbol = symbol;
    }
  }
}
