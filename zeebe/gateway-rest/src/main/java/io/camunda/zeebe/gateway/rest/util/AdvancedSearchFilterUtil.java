/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.util;

import io.camunda.search.filter.Operation;
import io.camunda.zeebe.gateway.protocol.rest.AdvancedIntegerFilter;
import io.camunda.zeebe.gateway.protocol.rest.AdvancedLongFilter;
import io.camunda.zeebe.gateway.protocol.rest.BasicLongFilter;
import io.camunda.zeebe.gateway.protocol.rest.BasicLongFilterProperty;
import io.camunda.zeebe.gateway.protocol.rest.IntegerFilterProperty;
import io.camunda.zeebe.gateway.protocol.rest.LongFilterProperty;
import java.util.ArrayList;
import java.util.List;

public class AdvancedSearchFilterUtil {

  public static <T> Operation<T> mapToOperation(final T value) {
    return Operation.eq(value);
  }

  public static List<Operation<Long>> mapBasicLongFilter(final BasicLongFilterProperty value) {
    if (!(value instanceof final BasicLongFilter filter)) {
      throw new IllegalStateException("Unexpected value instance: " + value);
    }

    final var operations = new ArrayList<Operation<Long>>();
    if (filter.get$Eq() != null) {
      operations.add(Operation.eq(filter.get$Eq()));
    }
    if (filter.get$Neq() != null) {
      operations.add(Operation.neq(filter.get$Neq()));
    }
    if (filter.get$Exists() != null) {
      operations.add(Operation.exists(filter.get$Exists()));
    }
    if (filter.get$In() != null && !filter.get$In().isEmpty()) {
      operations.add(Operation.in(filter.get$In()));
    }
    return operations;
  }

  public static List<Operation<Long>> mapLongFilter(final LongFilterProperty value) {
    if (!(value instanceof final AdvancedLongFilter filter)) {
      throw new IllegalStateException("Unexpected value instance: " + value);
    }

    final var operations = mapBasicLongFilter(filter);
    if (filter.get$Gt() != null) {
      operations.add(Operation.gt(filter.get$Gt()));
    }
    if (filter.get$Gte() != null) {
      operations.add(Operation.gte(filter.get$Gte()));
    }
    if (filter.get$Lt() != null) {
      operations.add(Operation.lt(filter.get$Lt()));
    }
    if (filter.get$Lte() != null) {
      operations.add(Operation.lte(filter.get$Lte()));
    }
    return operations;
  }

  public static List<Operation<Integer>> mapIntegerFilter(final IntegerFilterProperty value) {
    if (!(value instanceof final AdvancedIntegerFilter filter)) {
      throw new IllegalStateException("Unexpected value instance: " + value);
    }

    final var operations = new ArrayList<Operation<Integer>>();
    if (filter.get$Eq() != null) {
      operations.add(Operation.eq(filter.get$Eq()));
    }
    if (filter.get$Neq() != null) {
      operations.add(Operation.neq(filter.get$Neq()));
    }
    if (filter.get$Exists() != null) {
      operations.add(Operation.exists(filter.get$Exists()));
    }
    if (filter.get$Gt() != null) {
      operations.add(Operation.gt(filter.get$Gt()));
    }
    if (filter.get$Gte() != null) {
      operations.add(Operation.gte(filter.get$Gte()));
    }
    if (filter.get$Lt() != null) {
      operations.add(Operation.lt(filter.get$Lt()));
    }
    if (filter.get$Lte() != null) {
      operations.add(Operation.lte(filter.get$Lte()));
    }
    if (filter.get$In() != null && !filter.get$In().isEmpty()) {
      operations.add(Operation.in(filter.get$In()));
    }
    return operations;
  }
}
