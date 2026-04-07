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

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAdvancedStringFilterStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedStringFilterPropertyStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedVariableValueFilterPropertyStrictContract;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.VariableValueFilter;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;
import org.springframework.util.CollectionUtils;

@NullMarked
public final class VariableValueFilterUtil {

  private VariableValueFilterUtil() {}

  public static Either<List<String>, List<VariableValueFilter>> toStrictVariableValueFilters(
      final List<GeneratedVariableValueFilterPropertyStrictContract> filters) {
    if (CollectionUtils.isEmpty(filters)) {
      return Either.right(List.of());
    }

    final List<String> validationErrors = new ArrayList<>();
    final List<VariableValueFilter> variableValueFilters =
        filters.stream()
            .flatMap(
                filter -> {
                  if (filter.name() == null) {
                    validationErrors.add(ERROR_MESSAGE_NULL_VARIABLE_NAME);
                  }
                  if (filter.value() == null || isEmptyStrictStringFilter(filter.value())) {
                    validationErrors.add(ERROR_MESSAGE_NULL_VARIABLE_VALUE);
                  }
                  return validationErrors.isEmpty()
                      ? toVariableValueFiltersFromObject(filter.name(), filter.value()).stream()
                      : Stream.empty();
                })
            .toList();
    return validationErrors.isEmpty()
        ? Either.right(variableValueFilters)
        : Either.left(validationErrors);
  }

  private static boolean isEmptyStrictStringFilter(
      final GeneratedStringFilterPropertyStrictContract value) {
    return value instanceof GeneratedAdvancedStringFilterStrictContract adv
        && adv.$eq() == null
        && adv.$neq() == null
        && adv.$exists() == null
        && adv.$in() == null
        && adv.$notIn() == null
        && adv.$like() == null;
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
