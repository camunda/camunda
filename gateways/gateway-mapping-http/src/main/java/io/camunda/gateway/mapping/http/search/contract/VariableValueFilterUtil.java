/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.util.AdvancedSearchFilterUtil.mapToOperations;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_NULL_VARIABLE_NAME;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_NULL_VARIABLE_VALUE;

import io.camunda.gateway.protocol.model.AdvancedStringFilter;
import io.camunda.gateway.protocol.model.StringFilterProperty;
import io.camunda.gateway.protocol.model.VariableValueFilterProperty;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.VariableValueFilter;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.util.CollectionUtils;

@NullMarked
public final class VariableValueFilterUtil {

  private VariableValueFilterUtil() {}

  public static Either<List<String>, List<VariableValueFilter>> toStrictVariableValueFilters(
      final @Nullable List<VariableValueFilterProperty> filters) {
    final List<VariableValueFilterProperty> safeFilters = filters == null ? List.of() : filters;
    if (CollectionUtils.isEmpty(safeFilters)) {
      return Either.right(List.of());
    }

    final List<String> validationErrors = new ArrayList<>();
    final List<VariableValueFilter> variableValueFilters =
        safeFilters.stream()
            .flatMap(
                filter -> {
                  if (filter.getName() == null) {
                    validationErrors.add(ERROR_MESSAGE_NULL_VARIABLE_NAME);
                  }
                  if (filter.getValue() == null || isEmptyStrictStringFilter(filter.getValue())) {
                    validationErrors.add(ERROR_MESSAGE_NULL_VARIABLE_VALUE);
                  }
                  return validationErrors.isEmpty()
                      ? toVariableValueFiltersFromObject(filter.getName(), filter.getValue())
                          .stream()
                      : Stream.empty();
                })
            .toList();
    return validationErrors.isEmpty()
        ? Either.right(variableValueFilters)
        : Either.left(validationErrors);
  }

  private static boolean isEmptyStrictStringFilter(final StringFilterProperty value) {
    return value instanceof AdvancedStringFilter adv
        && adv.get$eq() == null
        && adv.get$neq() == null
        && adv.get$exists() == null
        && adv.get$in() == null
        && adv.get$notIn() == null
        && adv.get$like() == null;
  }

  private static List<VariableValueFilter> toVariableValueFiltersFromObject(
      final String name, final Object value) {
    final List<Operation<String>> operations = mapToOperations(String.class).apply(value);
    return new VariableValueFilter.Builder()
        .name(name)
        .valueTypedOperations(operations)
        .buildList();
  }
}
