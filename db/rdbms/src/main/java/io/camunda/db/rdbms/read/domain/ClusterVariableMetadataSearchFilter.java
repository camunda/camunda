/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.filter.MetadataValueFilter;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.Operator;
import io.camunda.search.filter.UntypedOperation;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * RDBMS-friendly shape of a {@link MetadataValueFilter}. MyBatis XML cannot branch on {@link
 * UntypedOperation#type()}, so the string-vs-numeric routing is precomputed here, mirroring {@code
 * ClusterVariableFilterTransformer#toMetadataValueQueries} to keep RDBMS and ES/OS filter semantics
 * in parity.
 */
public record ClusterVariableMetadataSearchFilter(
    String key,
    boolean notExists,
    List<Operation<String>> stringOperations,
    List<Operation<Double>> numericOperations) {

  public static List<ClusterVariableMetadataSearchFilter> from(
      final List<MetadataValueFilter> filters) {
    if (filters == null || filters.isEmpty()) {
      return List.of();
    }
    return filters.stream().map(ClusterVariableMetadataSearchFilter::from).toList();
  }

  private static ClusterVariableMetadataSearchFilter from(final MetadataValueFilter filter) {
    final var valueOperations = filter.valueOperations();
    if (valueOperations == null || valueOperations.isEmpty()) {
      return new ClusterVariableMetadataSearchFilter(filter.key(), false, List.of(), List.of());
    }

    final var negated =
        valueOperations.stream().anyMatch(op -> op.operator() == Operator.NOT_EXISTS);
    if (negated) {
      if (valueOperations.size() > 1) {
        throw new IllegalArgumentException(
            "NOT_EXISTS cannot be combined with other operations for the same metadata key: "
                + filter.key());
      }
      return new ClusterVariableMetadataSearchFilter(filter.key(), true, List.of(), List.of());
    }

    final var stringOperations = new ArrayList<Operation<String>>();
    final var numericOperations = new ArrayList<Operation<Double>>();
    for (final var operation : valueOperations) {
      switch (operation.type()) {
        case LONG, DOUBLE -> numericOperations.add(toDoubleOperation(operation));
        default -> stringOperations.add(toStringOperation(operation));
      }
    }
    return new ClusterVariableMetadataSearchFilter(
        filter.key(), false, stringOperations, numericOperations);
  }

  private static Operation<String> toStringOperation(final UntypedOperation operation) {
    return toOperation(operation, Object::toString);
  }

  private static Operation<Double> toDoubleOperation(final UntypedOperation operation) {
    return toOperation(operation, v -> ((Number) v).doubleValue());
  }

  private static <T> Operation<T> toOperation(
      final UntypedOperation operation, final Function<Object, T> valueMapper) {
    final var values =
        operation.values() == null
            ? null
            : operation.values().stream()
                .map(v -> v == null ? null : valueMapper.apply(v))
                .toList();
    return new Operation<>(operation.operator(), values);
  }
}
